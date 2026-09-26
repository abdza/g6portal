package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import groovy.sql.Sql

class PortalSchedulerController {

    PortalSchedulerService portalSchedulerService
    PortalEmailService portalEmailService
    PortalPageService portalPageService
    PortalService   portalService
    UserService userService
    def sessionFactory
    def mailService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    // How far back each tick looks for due slots that never got a Job row.
    static final int MISSED_LOOKBACK_HOURS = 48
    // A Job/Tick still Running after this long did not finish (restart, OOM, killed thread).
    static final int STUCK_AFTER_HOURS = 6

    // groovy-dateutil (Date.format) is not on this app's classpath.
    private static String fmt(Date d, String pattern) {
        return new java.text.SimpleDateFormat(pattern).format(d)
    }

    /**
     * Called hourly by an external cron (whitelisted, so usually anonymous). Every tick, job,
     * failure and missed slot is written to PortalSchedulerRun - see that class for why each
     * row type exists. Logging must never be what breaks a job, so every log write is
     * best-effort and swallowed on failure.
     */
    def run() {
        def now = new Date()
        def slot = PortalScheduler.truncateToHour(now)
        def host = PortalSchedulerRun.hostname()
        def curuser = session.curuser
        def triggered_by = curuser ? ('manual: ' + (curuser.userID ?: curuser.id)) : 'cron'
        println "Scheduler tick " + fmt(now, 'yyyy-MM-dd HH:mm:ss') + " (" + triggered_by + ")"

        def tick = logRun([run_type:'Tick', status:'Running', due_slot:slot, started:now,
                           triggered_by:triggered_by, host:host])
        def counts = [due:0, succeeded:0, failed:0, missing:0, missed:0]
        def problems = []

        // Small table and second-level cached; isDue() applies the exact hour/day matching
        def schedules = PortalScheduler.findAllByEnabled(true)
        schedules.findAll { it.isDue(now) }.each { schedule ->
            schedule.slugs?.tokenize(',')*.trim()?.findAll { it }?.each { script ->
                counts.due++
                def job = logRun([run_type:'Job', status:'Running', scheduler_id:schedule.id, tick_id:tick?.id,
                                  name:schedule.name, module:schedule.module, slug:script,
                                  due_slot:slot, started:new Date(), triggered_by:triggered_by, host:host])
                def scriptpage = PortalPage.findByModuleAndSlug(schedule.module,script)
                if(!scriptpage) {
                    // Used to be skipped without a word - a renamed or deleted page just stopped running
                    counts.missing++
                    def msg = "No page '" + script + "' in module '" + schedule.module + "'"
                    closeRun(job, 'Page missing', msg, null)
                    problems << schedule.name + " - " + script + ": " + msg
                    return
                }
                println "Running script " + scriptpage.module + ":" + scriptpage.slug + " - " + scriptpage.title
                try {
                    def content = null
                    PortalTracker.withTransaction { transaction ->
                        def sql = new Sql(sessionFactory.currentSession.connection())
                        Binding binding = new Binding()
                        binding.setVariable("datasource",sessionFactory.currentSession.connection())
                        binding.setVariable("sessionFactory",sessionFactory)
                        binding.setVariable("session",session)
                        binding.setVariable("sql",sql)
                        binding.setVariable("mailService",mailService)
                        binding.setVariable("portalService",portalService)
                        binding.setVariable("userService",userService)
                        // A page written for /run/... reads params and curuser; without these it
                        // died with MissingPropertyException on every scheduled run. Empty params
                        // means "default mode", which is what a scheduled run wants.
                        binding.setVariable("params",[:])
                        binding.setVariable("curuser",null)
                        def shell = new GroovyShell(this.class.classLoader,binding)
                        content = shell.evaluate(scriptpage.content)
                    }
                    counts.succeeded++
                    closeRun(job, 'Success', null, content)
                    try {
                        // bulk update: leaves lastUpdated alone, which bounds the missed-run check
                        PortalScheduler.withNewTransaction {
                            PortalScheduler.executeUpdate("update PortalScheduler s set s.lastrun = :d where s.id = :id",
                                                          [d:new Date(), id:schedule.id])
                        }
                    } catch(Throwable t) { println "Scheduler could not stamp lastrun on " + schedule.name + ": " + t }
                }
                catch(Throwable e){
                    counts.failed++
                    def desc = PortalErrorLog.describe(e)
                    println 'Error running scheduler ' + schedule.name + '-' + script + ' : ' + desc
                    closeRun(job, 'Failed', desc, null)
                    problems << schedule.name + " - " + script + ": FAILED\n" + desc
                    PortalErrorLog.record(params,null,controllerName,actionName,e,script,schedule.module)
                }
            }
        }

        try {
            findMissed(schedules, slot, now, host, tick?.id, counts, problems)
        } catch(Throwable t) {
            problems << "Missed-run check itself failed: " + PortalErrorLog.describe(t)
        }
        housekeeping(now)

        def summary = "Due: ${counts.due}, succeeded: ${counts.succeeded}, failed: ${counts.failed}, " +
                      "page missing: ${counts.missing}, missed runs found: ${counts.missed}"
        closeRun(tick, problems ? 'Problems' : 'Success', problems ? problems.join('\n\n') : null, summary)
        println "Scheduler tick done - " + summary

        if(problems) {
            def emailpagerror = PortalSetting.findByName("emailpagerror")
            if(emailpagerror){
                try {
                    sendMail {
                        to emailpagerror.value().trim()
                        subject "Scheduler problems at " + fmt(now, 'yyyy-MM-dd HH:mm') + (host ? " on " + host : "")
                        body summary + "\n\n" + problems.join('\n\n') +
                             "\n\nDetails: " + createLink(controller:'portalScheduler', action:'status', absolute:true)
                    }
                } catch(Throwable t) { println "Scheduler could not send the problem alert: " + t }
            }
        }

        // Same dialect choice as PortalEmailController.run: tosend() compares the boolean
        // email_sent column to '0', which h2 and Postgres reject - on those the whole tick
        // ended in a 500 after every job had already run.
        def dburl = grails.util.Holders.config.dataSource.url
        def emails = (dburl.contains("jdbc:postgresql") || dburl.contains("jdbc:h2")) ?
                     portalEmailService.h2tosend() : portalEmailService.tosend()
        def emailfrom = PortalSetting.namedefault("portal.emailfrom","portal@portal.com")
        PortalEmail.withTransaction { etrans -> 
            emails.each { email->
                email.send(mailService)
            }
        }
        [summary:summary, problems:problems]
    }

    /**
     * Records a Missed row for every due slot in the lookback window that has no Job row.
     * The window never reaches back before logging existed (the first Tick) or before the
     * schedule was last edited, so switching this on - or moving a job's hour - does not
     * report a flood of slots that were never really owed.
     */
    private void findMissed(List<PortalScheduler> schedules, Date slot, Date now, String host, Long tick_id, Map counts, List problems) {
        def firstTick = PortalSchedulerRun.executeQuery(
            "select min(r.due_slot) from PortalSchedulerRun r where r.run_type = 'Tick'")[0]
        if(!firstTick) return
        def from = new Date(slot.time - MISSED_LOOKBACK_HOURS * 3600000L)
        if(firstTick > from) from = firstTick
        if(!(from < slot)) return

        def accounted = PortalSchedulerRun.executeQuery(
            "select r.scheduler_id, r.due_slot from PortalSchedulerRun r " +
            "where r.run_type in ('Job','Missed') and r.due_slot >= :from and r.due_slot < :to",
            [from:from, to:slot]).collect { it[0] + '|' + it[1].time } as Set
        def ticked = PortalSchedulerRun.executeQuery(
            "select distinct r.due_slot from PortalSchedulerRun r " +
            "where r.run_type = 'Tick' and r.due_slot >= :from and r.due_slot < :to",
            [from:from, to:slot]).collect { it.time } as Set

        schedules.each { schedule ->
            def sfrom = (schedule.lastUpdated && schedule.lastUpdated > from) ? schedule.lastUpdated : from
            // lastrun inside the slot's hour also proves it ran - covers runs from before this
            // log existed, so the first ticks after deploying do not report jobs that did run
            def ranslot = schedule.lastrun ? PortalScheduler.truncateToHour(schedule.lastrun).time : null
            schedule.dueSlots(sfrom, slot).each { ds ->
                if(ds.time != ranslot && !((schedule.id + '|' + ds.time) in accounted)) {
                    def why = (ds.time in ticked) ?
                        "The scheduler ran in that hour but did not start this job" :
                        "The scheduler was not called in that hour - check the cron that calls /portalScheduler/run"
                    logRun([run_type:'Missed', status:'Missed', scheduler_id:schedule.id, tick_id:tick_id, name:schedule.name,
                            module:schedule.module, slug:schedule.slugs, due_slot:ds, started:now,
                            finished:now, duration_ms:0L, error:why, host:host])
                    counts.missed++
                    problems << schedule.name + ": missed the " + fmt(ds, 'yyyy-MM-dd HH:00') + " run. " + why
                }
            }
        }
    }

    /** Marks runs that never finished, and prunes old rows. Both are single indexed statements. */
    private void housekeeping(Date now) {
        try {
            PortalSchedulerRun.withNewTransaction {
                PortalSchedulerRun.executeUpdate(
                    "update PortalSchedulerRun r set r.status = 'Interrupted', r.error = :why " +
                    "where r.status = 'Running' and r.started < :cutoff",
                    [why:'Still marked Running after ' + STUCK_AFTER_HOURS + ' hours - the server probably restarted or the thread died mid-run',
                     cutoff:new Date(now.time - STUCK_AFTER_HOURS * 3600000L)])
                def keepdays = 180
                try { keepdays = PortalSetting.namedefault('portal.scheduler_log_retention_days',180).toString().toInteger() } catch(Exception e) { }
                PortalSchedulerRun.executeUpdate("delete from PortalSchedulerRun r where r.started < :cutoff",
                                                 [cutoff:new Date(now.time - keepdays * 86400000L)])
            }
        } catch(Throwable t) { println "Scheduler housekeeping failed: " + t }
    }

    private PortalSchedulerRun logRun(Map props) {
        try {
            return PortalSchedulerRun.withNewTransaction {
                def r = new PortalSchedulerRun(props)
                r.error = PortalSchedulerRun.clip(r.error, PortalSchedulerRun.MAX_ERROR)
                r.save(flush:true, failOnError:true)
                return r
            }
        } catch(Throwable t) {
            println "Scheduler could not write its run log (" + props.run_type + " " + props.slug + "): " + t
            return null
        }
    }

    private void closeRun(PortalSchedulerRun run, String status, String error, Object content) {
        if(!run) return
        try {
            PortalSchedulerRun.withNewTransaction {
                def r = PortalSchedulerRun.get(run.id)
                if(!r) return
                r.status = status
                r.finished = new Date()
                r.duration_ms = r.finished.time - r.started.time
                r.error = PortalSchedulerRun.clip(error, PortalSchedulerRun.MAX_ERROR)
                r.result = PortalSchedulerRun.summarise(content)
                r.save(flush:true, failOnError:true)
            }
        } catch(Throwable t) {
            println "Scheduler could not close its run log " + run.id + " as " + status + ": " + t
        }
    }

    /**
     * One row per schedule: is it running when it should, and if not, what went wrong.
     * Scoped like index(): a module admin sees their modules' schedules only.
     */
    def status() {
        def now = new Date()
        def schedules = PortalScheduler.list(sort:'module')
        if(!session.enablesuperuser) {
            def mine = session.adminmodules ?: []
            schedules = schedules.findAll { it.module in mine }
        }
        def ids = schedules*.id
        def since = new Date(now.time - 7 * 86400000L)

        def stats = [:].withDefault { [:] }
        def lastjob = [:]
        def lastok = [:]
        def recent = []
        if(ids) {
            PortalSchedulerRun.executeQuery(
                "select r.scheduler_id, r.status, count(r) from PortalSchedulerRun r " +
                "where r.run_type in ('Job','Missed') and r.started >= :since and r.scheduler_id in (:ids) " +
                "group by r.scheduler_id, r.status", [since:since, ids:ids]).each { stats[it[0]][it[1]] = it[2] }
            PortalSchedulerRun.executeQuery(
                "from PortalSchedulerRun r where r.id in (select max(r2.id) from PortalSchedulerRun r2 " +
                "where r2.run_type in ('Job','Missed') and r2.scheduler_id in (:ids) group by r2.scheduler_id)",
                [ids:ids]).each { lastjob[it.scheduler_id] = [it] }
            // A Job row stands for one page of its run; widen it to every page that tick ran
            def tickids = lastjob.values().collect { it[0] }.findAll { it.run_type == 'Job' && it.tick_id }*.tick_id
            if(tickids) {
                PortalSchedulerRun.executeQuery(
                    "from PortalSchedulerRun r where r.run_type = 'Job' and r.tick_id in (:tids) and r.scheduler_id in (:ids) order by r.id",
                    [tids:tickids, ids:ids]).groupBy { it.scheduler_id }.each { sid, rs ->
                        if(lastjob[sid] && lastjob[sid][0].tick_id == rs[0].tick_id) lastjob[sid] = rs
                    }
            }
            PortalSchedulerRun.executeQuery(
                "select r.scheduler_id, max(r.finished) from PortalSchedulerRun r " +
                "where r.run_type = 'Job' and r.status = 'Success' and r.scheduler_id in (:ids) group by r.scheduler_id",
                [ids:ids]).each { lastok[it[0]] = it[1] }
            recent = PortalSchedulerRun.executeQuery(
                "from PortalSchedulerRun r where r.run_type in ('Job','Missed') and r.scheduler_id in (:ids) " +
                "and r.status not in ('Success','Running') order by r.id desc", [ids:ids], [max:30])
        }

        def lasttick = PortalSchedulerRun.executeQuery(
            "from PortalSchedulerRun r where r.run_type = 'Tick' order by r.id desc", [:], [max:1])[0]
        def tickgap = lasttick ? ((now.time - lasttick.started.time) / 60000L).intValue() : null
        def ticks24 = PortalSchedulerRun.executeQuery(
            "select count(r) from PortalSchedulerRun r where r.run_type = 'Tick' and r.started >= :since",
            [since:new Date(now.time - 86400000L)])[0]

        [schedules:schedules, stats:stats, lastjob:lastjob, lastok:lastok, recent:recent,
         lasttick:lasttick, tickgap:tickgap, ticks24:ticks24, now:now]
    }

    /** Run history for one schedule, or the ticks themselves when no id is given. */
    def runs(Long id) {
        def max = Math.min(params.int('max') ?: 50, 200)
        def offset = params.int('offset') ?: 0
        def schedule = id ? PortalScheduler.get(id) : null
        if(id && (!schedule || (!session.enablesuperuser && !(schedule.module in (session.adminmodules ?: []))))) {
            flash.message = "Schedule not found"
            redirect action:'status'
            return
        }
        def rows
        def total
        if(schedule) {
            rows = PortalSchedulerRun.executeQuery("from PortalSchedulerRun r where r.scheduler_id = :id order by r.id desc",
                                                   [id:schedule.id], [max:max, offset:offset])
            total = PortalSchedulerRun.executeQuery("select count(r) from PortalSchedulerRun r where r.scheduler_id = :id",
                                                    [id:schedule.id])[0]
        }
        else {
            rows = PortalSchedulerRun.executeQuery("from PortalSchedulerRun r where r.run_type = 'Tick' order by r.id desc",
                                                   [:], [max:max, offset:offset])
            total = PortalSchedulerRun.executeQuery("select count(r) from PortalSchedulerRun r where r.run_type = 'Tick'")[0]
        }
        [schedule:schedule, rows:rows, total:total, max:max, offset:offset]
    }

    def index(Integer max) {
      def dparam = [max:params.max?:10,offset:params.offset?:0]
      // def curuser = User.get(session.userid)
      def curuser = session.curuser
      params.max = dparam.max
      if(params.q) {
        def query = '%' + params.q + '%'
        if(session.enablesuperuser) {
            def thelist = portalSchedulerService.list(query,dparam)
            respond thelist, model:[portalSchedulerCount: portalSchedulerService.count(query), params:params, curuser:curuser]
        }
        else {
            def thelist = portalSchedulerService.list(query,session.adminmodules,dparam)
            respond thelist, model:[portalSchedulerCount: portalSchedulerService.count(query,session.adminmodules), params:params, curuser:curuser]
        }
      }
      else {
        if(session.enablesuperuser) {
            def thelist = portalSchedulerService.list(dparam)
            respond thelist, model:[portalSchedulerCount: portalSchedulerService.count(), params:params, curuser:curuser]
        }
        else {
            def thelist = portalSchedulerService.list(session.adminmodules,dparam)
            respond thelist, model:[portalSchedulerCount: portalSchedulerService.count(session.adminmodules), params:params, curuser:curuser]
        }
      }
    }

    def show(Long id) {
        respond portalSchedulerService.get(id)
    }

    def create() {
        respond new PortalScheduler(params)
    }

    def save(PortalScheduler portalScheduler) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (portalScheduler == null) {
                notFound()
                return
            }

            try {
                portalSchedulerService.save(portalScheduler)
            } catch (ValidationException e) {
                respond portalScheduler.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'portalScheduler.label', default: 'PortalScheduler'), portalScheduler.id])
                    redirect portalScheduler
                }
                '*' { respond portalScheduler, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond portalSchedulerService.get(id)
    }

    def update(PortalScheduler portalScheduler) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (portalScheduler == null) {
                notFound()
                return
            }

            try {
                portalSchedulerService.save(portalScheduler)
            } catch (ValidationException e) {
                respond portalScheduler.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'portalScheduler.label', default: 'PortalScheduler'), portalScheduler.id])
                    redirect portalScheduler
                }
                '*'{ respond portalScheduler, [status: OK] }
            }
        }
    }

    def delete(Long id) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (id == null) {
                notFound()
                return
            }

            portalSchedulerService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalScheduler.label', default: 'PortalScheduler'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalScheduler.label', default: 'PortalScheduler'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
