package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import groovy.sql.Sql

class PortalTrackerErrorController {

    def sessionFactory

    PortalTrackerErrorService portalTrackerErrorService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
      def dparam = [max:params.max?:10,offset:params.offset?:0]
      params.max = dparam.max
      if(params.q) {
        def query = '%' + params.q + '%'
        if(session.enablesuperuser) {
            def thelist = portalTrackerErrorService.list(query,dparam)
            respond thelist, model:[portalTrackerErrorCount: portalTrackerErrorService.count(query), params:params]
        }
        else {
            def thelist = portalTrackerErrorService.list(query,session.adminmodules,dparam)
            respond thelist, model:[portalTrackerErrorCount: portalTrackerErrorService.count(query,session.adminmodules), params:params]
        }
      }
      else {
        if(session.enablesuperuser) {
            def thelist = portalTrackerErrorService.list(dparam)
            respond thelist, model:[portalTrackerErrorCount: portalTrackerErrorService.count(), params:params]
        }
        else {
            def thelist = portalTrackerErrorService.list(session.adminmodules,dparam)
            respond thelist, model:[portalTrackerErrorCount: portalTrackerErrorService.count(session.adminmodules), params:params]
        }
      }
    }

    def show(Long id) {
        respond portalTrackerErrorService.get(id)
    }

    def create() {
        def field = null
        if(params.field_id) {
            field = PortalTrackerField.get(params.field_id)
        }
        respond new PortalTrackerError(params), model: ['field':field]
    }

    def save(PortalTrackerError portalTrackerError) {
        if (portalTrackerError == null) {
            notFound()
            return
        }

        try {
            portalTrackerErrorService.save(portalTrackerError)
        } catch (ValidationException e) {
            respond portalTrackerError.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerError.label', default: 'PortalTrackerError'), portalTrackerError.id])
                redirect portalTrackerError
            }
            '*' { respond portalTrackerError, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerErrorService.get(id)
    }

    def update(PortalTrackerError portalTrackerError) {
        if (portalTrackerError == null) {
            notFound()
            return
        }

        try {
            portalTrackerErrorService.save(portalTrackerError)
        } catch (ValidationException e) {
            respond portalTrackerError.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerError.label', default: 'PortalTrackerError'), portalTrackerError.id])
                redirect portalTrackerError
            }
            '*'{ respond portalTrackerError, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalTrackerErrorService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerError.label', default: 'PortalTrackerError'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerError.label', default: 'PortalTrackerError'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
