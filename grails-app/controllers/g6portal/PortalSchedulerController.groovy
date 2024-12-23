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

    def run() {
        def now = new Date()
        def curhour = now.hours
        def curweekday = now.day
        def curmonthday = now.date 
        def curmonth = now.month + 1
        def curweek = now.format("W")
        println "Now:" + curhour
        println "Day:" + curweekday
        println "Date:" + curmonthday
        println "Month:" + curmonth
        println "Week In Month:" + curweek
        def schedules = PortalScheduler.findAll() {
          or {
            like("hour_of_day","%"+curhour+"%")
            like("hour_of_day","*")
          }
          or {
            like("day_of_week","%"+curweekday+"%")
            like("day_of_week","*")
          }
          or {
            like("day_of_month","%"+curmonthday+"%")
            like("day_of_month","*")
          }
          eq("enabled",true)
        }
        schedules.each { schedule ->
            def testhour = false
            if(schedule.hour_of_day.trim()=='*') {
                testhour = true
            }
            else {
                def shours = schedule.hour_of_day.tokenize(',')
                shours.each { shour ->
                    if(shour.trim()==curhour.toString()){
                        testhour = true
                    }
                }
            }
            def testweek = false
            if(schedule.day_of_week.trim()=='*') {
                testweek = true
            }
            else {
                def sweeks = schedule.day_of_week.tokenize(',')
                sweeks.each { sweek ->
                    if(sweek.trim()==curweekday.toString()) {
                        testweek = true
                    }
                }
            }
            def testmonth = false
            if(schedule.day_of_month.trim()=='*') {
                testmonth = true
            }
            else {
                def smonths = schedule.day_of_month.tokenize(',')
                smonths.each { smonth ->
                    if(smonth.trim()==curmonthday.toString()) {
                        testmonth = true
                    }
                }
            }
            if(testhour && testweek && testmonth) {
                def scripts = schedule.slugs.tokenize(',')
                scripts.each { script ->
                    def scriptpage = PortalPage.findByModuleAndSlug(schedule.module,script)
                    if(scriptpage) {
                        println "Running script " + scriptpage.module + ":" + scriptpage.slug + " - " + scriptpage.title
                        try{
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
                                def shell = new GroovyShell(this.class.classLoader,binding)
                                def content = shell.evaluate(scriptpage.content)
                                schedule.lastrun = new Date()
                                schedule.save(flush:true)
                            }
                        }
                        catch(Exception e){
                            println 'Error running scheduler ' + schedule.name + '-' + script + ' : ' + e.toString()
                            PortalErrorLog.record(params,null,controllerName,actionName,e.toString(),script,schedule.module)
                            def emailpagerror = PortalSetting.findByName("emailpagerror")
                            if(emailpagerror){
                                sendMail {
                                    to emailpagerror.value().trim()
                                    subject "Scheduler Error"
                                    body 'Error running scheduler ' + schedule.name + '-' + script + ' : ' + e.toString() + '''
                    Params: ''' + params
                                }
                            }
                        }
                    }
                }
            }
        }

        def emails = portalEmailService.tosend()
        def emailfrom = PortalSetting.namedefault("portal.emailfrom","portal@portal.com")
        PortalEmail.withTransaction { etrans -> 
            emails.each { email->
                email.send(mailService)
            }
        }
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
