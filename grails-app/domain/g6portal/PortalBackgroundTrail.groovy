package g6portal

/**
 * Performance trail for work that runs outside a request: background uploads, data dumps and
 * async Excel exports. Their request returns at once, so its audit trail row says nothing
 * about the cost of the work itself. Wrap the work in track(...) - or start the thread with
 * start(...) - and one row is written per run, whether it finished or threw. Rows are saved
 * by PortalAuditWriter, off the job's thread.
 *
 * success only reflects whether the work threw. Jobs that catch their own errors and report
 * them elsewhere (the export job maps, the upload's messages) still show success here.
 */
class PortalBackgroundTrail {

    static constraints = {
        name(nullable:true)
        details(nullable:true,maxSize:1024)
        user_id(nullable:true)
        start_date(nullable:true)
        duration_ms(nullable:true)
        cpu_ms(nullable:true)
        success(nullable:true)
        error(nullable:true,maxSize:1024)
        thread_name(nullable:true)
    }

    static mapping = {
        version false
        start_date index: 'idx_background_trail_start'
    }

    String name
    String details
    Long user_id
    Date start_date
    Long duration_ms
    Long cpu_ms
    Boolean success
    String error
    String thread_name

    /**
     * Runs work and records how long it took. info may hold:
     *   details - free text identifying this run (record id, slug...)
     *   userid  - the user who triggered it; read it on the request thread, not inside work
     * Exceptions from work are recorded and then rethrown, so callers behave as before.
     */
    static def track(String name, Map info = [:], Closure work) {
        def startDate = new Date()
        long startNanos = System.nanoTime()
        long startCpu = PortalAuditWriter.cpuNanos()
        Throwable failure = null
        try {
            return work.call()
        }
        catch(Throwable t) {
            failure = t
            throw t
        }
        finally {
            try {
                // Plain setters, not the map constructor: that runs data binding, which is not
                // something to depend on from a thread whose request may be gone.
                def row = new PortalBackgroundTrail()
                row.name = PortalAuditWriter.truncate(name, 255)
                row.details = PortalAuditWriter.truncate(info?.details, 1024)
                row.user_id = info?.userid ? info.userid as Long : null
                row.start_date = startDate
                row.duration_ms = PortalAuditWriter.elapsedMs(startNanos)
                row.cpu_ms = PortalAuditWriter.cpuMsSince(startCpu)
                row.success = failure == null
                row.error = PortalAuditWriter.truncate(failure?.toString(), 1024)
                row.thread_name = PortalAuditWriter.truncate(Thread.currentThread().name, 255)
                PortalAuditWriter.enqueue(row)
            }
            catch(Throwable ignored) {
            }
        }
    }

    /** Thread.start { work } with the run tracked. */
    static Thread start(String name, Map info = [:], Closure work) {
        return Thread.start {
            track(name, info, work)
        }
    }
}
