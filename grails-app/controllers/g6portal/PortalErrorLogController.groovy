package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import groovy.sql.Sql

class PortalErrorLogController {

    def sessionFactory

    PortalErrorLogService portalErrorLogService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
      def dparam = [max:params.max?:10,offset:params.offset?:0]
      params.max = dparam.max
      if(params.q) {
        def query = '%' + params.q + '%'
        if(session.enablesuperuser) {
            def thelist = portalErrorLogService.list(query,dparam)
            respond thelist, model:[portalErrorLogCount: portalErrorLogService.count(query), params:params]
        }
        else {
            def thelist = portalErrorLogService.list(query,session.adminmodules,dparam)
            respond thelist, model:[portalErrorLogCount: portalErrorLogService.count(query,session.adminmodules), params:params]
        }
      }
      else {
        if(session.enablesuperuser) {
            def thelist = portalErrorLogService.list(dparam)
            respond thelist, model:[portalErrorLogCount: portalErrorLogService.count(), params:params]
        }
        else {
            def thelist = portalErrorLogService.list(session.adminmodules,dparam)
            respond thelist, model:[portalErrorLogCount: portalErrorLogService.count(session.adminmodules), params:params]
        }
      }
    }

    def show(Long id) {
        respond portalErrorLogService.get(id)
    }

    def create() {
        respond new PortalErrorLog(params)
    }

    def save(PortalErrorLog portalErrorLog) {
        if (portalErrorLog == null) {
            notFound()
            return
        }

        try {
            portalErrorLogService.save(portalErrorLog)
        } catch (ValidationException e) {
            respond portalErrorLog.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalErrorLog.label', default: 'PortalErrorLog'), portalErrorLog.id])
                redirect portalErrorLog
            }
            '*' { respond portalErrorLog, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalErrorLogService.get(id)
    }

    def update(PortalErrorLog portalErrorLog) {
        if (portalErrorLog == null) {
            notFound()
            return
        }

        try {
            portalErrorLogService.save(portalErrorLog)
        } catch (ValidationException e) {
            respond portalErrorLog.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalErrorLog.label', default: 'PortalErrorLog'), portalErrorLog.id])
                redirect portalErrorLog
            }
            '*'{ respond portalErrorLog, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalErrorLogService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalErrorLog.label', default: 'PortalErrorLog'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    def clear_log() {
        def sessiondata = sessionFactory.currentSession.connection()
        def sql = new Sql(sessiondata)
        sql.execute("TRUNCATE TABLE portal_error_log")
        flash.message = "Error Logs Truncated"
        redirect action:"index", method:"GET"
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalErrorLog.label', default: 'PortalErrorLog'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
