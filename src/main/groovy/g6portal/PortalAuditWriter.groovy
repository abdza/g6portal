package g6portal

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Saves PortalAuditTrail and PortalBackgroundTrail rows from a single background thread, so no
 * request or job waits on an audit insert. Rows are queued in memory and written in batches
 * every FLUSH_INTERVAL_MS. Before this the interceptor did a save(flush:true) in its own
 * transaction on every request, before the action even ran.
 *
 * Trade-offs of writing asynchronously:
 *  - a JVM crash loses at most the last FLUSH_INTERVAL_MS of rows (a normal shutdown drains
 *    the queue first, see shutdown(), called from BootStrap.destroy)
 *  - if the database falls behind and the queue fills, new rows are dropped instead of
 *    slowing requests down; the count is reported to PortalErrorLog
 *
 * Request timing is also kept here: SecurityInterceptor opens a frame in before() and closes
 * it in afterView(), after the GSP has rendered. One request can pass through several
 * dispatches (a failing action is forwarded to the error page inside the same request), so
 * each dispatch gets its own frame on a per-request stack and a forward cannot overwrite the
 * original's start time.
 */
class PortalAuditWriter {

    static final int QUEUE_CAPACITY = 20000
    static final int BATCH_SIZE = 500
    static final long FLUSH_INTERVAL_MS = 2000
    static final String FRAMES = 'g6portal.perf.frames'

    private static final LinkedBlockingQueue queue = new LinkedBlockingQueue(QUEUE_CAPACITY)
    private static final AtomicLong dropped = new AtomicLong()
    private static final AtomicLong invalid = new AtomicLong()
    private static final ThreadMXBean threadBean = ManagementFactory.threadMXBean
    private static volatile Thread worker
    private static volatile boolean running = false

    static void enqueue(row) {
        if(row == null) {
            return
        }
        if(!running) {
            start()
        }
        if(!queue.offer(row)) {
            dropped.incrementAndGet()
        }
    }

    private static List frames(request) {
        def stack = request.getAttribute(FRAMES)
        if(stack == null) {
            stack = []
            request.setAttribute(FRAMES, stack)
        }
        return stack
    }

    /** Starts timing one dispatch of the request; trail is saved when the frame is finished. */
    static Map startFrame(request, PortalAuditTrail trail) {
        def frame = [trail:trail, startNanos:System.nanoTime(), startCpu:cpuNanos()]
        frames(request) << frame
        return frame
    }

    /**
     * Closes a frame and queues its trail row. frame null means the innermost open one, which
     * is what afterView() is finishing. Never throws: it runs on every request.
     */
    static void finishFrame(request, response, Map frame, Throwable failure) {
        try {
            def stack = frames(request)
            if(frame == null) {
                if(!stack) {
                    return
                }
                frame = stack.remove(stack.size() - 1)
            }
            else {
                // by identity: two frames can hold equal maps
                def at = stack.findLastIndexOf { it.is(frame) }
                if(at < 0) {
                    return
                }
                stack.remove(at)
            }
            PortalAuditTrail trail = frame.trail
            if(!trail) {
                return
            }
            trail.duration_ms = elapsedMs(frame.startNanos as long)
            trail.cpu_ms = cpuMsSince(frame.startCpu as long)
            try {
                trail.status = response?.status
            }
            catch(Throwable ignored) {
            }
            if(failure) {
                trail.error = truncate(failure.toString(), 1024)
            }
            enqueue(trail)
        }
        catch(Throwable t) {
            println "Audit trail timing failed: " + t
        }
    }

    /**
     * CPU time used so far by the calling thread, in nanoseconds, or -1 if the JVM cannot
     * measure it. Costs about a microsecond.
     */
    static long cpuNanos() {
        try {
            return threadBean.isCurrentThreadCpuTimeSupported() ? threadBean.currentThreadCpuTime : -1L
        }
        catch(Throwable t) {
            return -1L
        }
    }

    static Long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos).intdiv(1000000L)
    }

    static Long cpuMsSince(long startCpuNanos) {
        if(startCpuNanos < 0) {
            return null
        }
        long now = cpuNanos()
        return now < 0 ? null : (now - startCpuNanos).intdiv(1000000L)
    }

    static String truncate(value, int max) {
        if(value == null) {
            return null
        }
        def text = value.toString()
        return text.size() > max ? text[0..<max] : text
    }

    static synchronized void start() {
        if(running) {
            return
        }
        running = true
        worker = new Thread({ loop() } as Runnable, 'audit-writer')
        worker.daemon = true
        worker.start()
    }

    static synchronized void shutdown() {
        running = false
        if(worker) {
            worker.interrupt()
            try {
                worker.join(15000)
            }
            catch(InterruptedException ignored) {
            }
            worker = null
        }
    }

    private static void loop() {
        while(running) {
            try {
                Thread.sleep(FLUSH_INTERVAL_MS)
            }
            catch(InterruptedException e) {
                // shutdown() wakes us early; fall through and write what is left
            }
            drain()
        }
        drain()
    }

    private static void drain() {
        try {
            def batch = []
            while(queue.drainTo(batch, BATCH_SIZE) > 0) {
                write(batch)
                batch.clear()
            }
            long lost = dropped.getAndSet(0)
            if(lost > 0) {
                report('Audit queue was full; dropped ' + lost + ' audit rows', null)
            }
            long rejected = invalid.getAndSet(0)
            if(rejected > 0) {
                report(rejected + ' audit rows failed validation and were not saved', null)
            }
        }
        catch(Throwable t) {
            report('Audit writer failed', t)
        }
    }

    private static void write(List rows) {
        try {
            saveAll(rows)
        }
        catch(Throwable t) {
            // One bad row fails the whole transaction; retry one at a time so only it is lost.
            rows.each { row ->
                try {
                    saveAll([row])
                }
                catch(Throwable rowfailure) {
                    report('Could not save ' + row.getClass().simpleName, rowfailure)
                }
            }
        }
    }

    private static void saveAll(List rows) {
        PortalAuditTrail.withNewSession {
            PortalAuditTrail.withTransaction {
                rows.each { row ->
                    if(!row.save()) {
                        invalid.incrementAndGet()
                    }
                }
            }
        }
    }

    // This thread has no Hibernate session of its own; PortalErrorLog.record needs one.
    private static void report(String what, Throwable t) {
        try {
            PortalErrorLog.withNewSession {
                if(t) {
                    PortalErrorLog.capture(t, what, [controller:'PortalAuditWriter', action:'drain'])
                }
                else {
                    PortalErrorLog.record([:], null, 'PortalAuditWriter', 'drain', what)
                }
            }
        }
        catch(Throwable ignored) {
            println "PortalAuditWriter: " + what + (t ? ': ' + t : '')
        }
    }
}
