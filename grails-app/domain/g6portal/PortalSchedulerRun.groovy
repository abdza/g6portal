package g6portal

/**
 * One row per thing the Scheduler did or failed to do, so "did job X run when it should have,
 * and if not, why" has an answer. PortalScheduler.lastrun alone cannot give one: it only moves
 * on success, and the error of a failed page could vanish with its rolled-back transaction.
 *
 * run_type
 *   Tick    - one per /portalScheduler/run call, whether or not anything was due. Proves the
 *             external cron is actually calling us; a gap in ticks means nothing below can
 *             be trusted for that period.
 *   Job     - one per slug of a schedule that was due. Inserted as Running before the page
 *             starts and closed afterwards, so a run killed mid-way (restart, OOM) is left
 *             visibly Running instead of leaving no trace.
 *   Missed  - a due slot with no Job row, found by a later tick.
 *
 * Rows are always written in their own transaction (withNewTransaction), never inside the
 * page's - the page's transaction rolls back when it fails, and the log of the failure must
 * not go with it.
 *
 * due_slot is the scheduled hour this row accounts for, truncated to the hour. It is what the
 * missed-run check matches on, so a job that starts at 08:05 still satisfies the 08:00 slot.
 */
class PortalSchedulerRun {

    static constraints = {
        scheduler_id(nullable:true)
        tick_id(nullable:true)
        name(nullable:true)
        module(nullable:true)
        slug(nullable:true)
        due_slot(nullable:true)
        finished(nullable:true)
        duration_ms(nullable:true)
        error(nullable:true)
        result(nullable:true)
        triggered_by(nullable:true)
        host(nullable:true)
    }

    static mapping = {
        error type: 'text'
        result type: 'text'
        scheduler_id index: 'idx_scheduler_run_sched_slot'
        due_slot index: 'idx_scheduler_run_sched_slot'
        started index: 'idx_scheduler_run_started'
        sort started: 'desc'
    }

    // Worst first: how one schedule run with several pages is summarised
    static final List<String> SEVERITY = ['Failed','Page missing','Interrupted','Missed','Running','Success']

    static String worst(Collection<String> statuses) {
        return SEVERITY.find { it in statuses } ?: (statuses ? statuses.first() : null)
    }

    static final int MAX_ERROR = 4000
    static final int MAX_RESULT = 1000

    String run_type
    String status
    Long scheduler_id
    // The Tick row that started this Job or found this Missed slot: groups a multi-page
    // schedule's rows into one run, so "last result" can mean the whole run, not its last page
    Long tick_id
    String name
    String module
    String slug
    Date due_slot
    Date started
    Date finished
    Long duration_ms
    String error
    String result
    String triggered_by
    String host

    static String clip(Object text, int max) {
        if(text == null) return null
        def s = text.toString()
        return s.size() > max ? s.substring(0, max) + ' ...[truncated]' : s
    }

    /**
     * What a page returned, as something short and readable. Most runable pages return an
     * HTML summary that the Scheduler used to throw away; keeping its text is how a page that
     * handles its own errors (and so "succeeds") can still say it completed with problems.
     */
    static String summarise(Object content) {
        if(content == null) return null
        def s = content.toString()
        s = s.replaceAll(/(?is)<(script|style)[^>]*>.*?<\/\1>/, ' ')
             .replaceAll(/<[^>]+>/, ' ')
             .replaceAll(/&nbsp;/, ' ')
             .replaceAll(/\s+/, ' ')
             .trim()
        return s ? clip(s, MAX_RESULT) : null
    }

    static String hostname() {
        try { return InetAddress.localHost.hostName } catch(Exception e) { return null }
    }
}
