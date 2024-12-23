package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerFlowController {

    PortalTrackerFlowService portalTrackerFlowService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerFlowService.list(params), model:[portalTrackerFlowCount: portalTrackerFlowService.count()]
    }

    def show(Long id) {
        respond portalTrackerFlowService.get(id)
    }

    def create() {
        respond new PortalTrackerFlow(params)
    }

    def save(PortalTrackerFlow portalTrackerFlow) {
        if (portalTrackerFlow == null) {
            notFound()
            return
        }

        try {
            portalTrackerFlowService.save(portalTrackerFlow)
        } catch (ValidationException e) {
            respond portalTrackerFlow.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerFlow.label', default: 'PortalTrackerFlow'), portalTrackerFlow.id])
                redirect portalTrackerFlow
            }
            '*' { respond portalTrackerFlow, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerFlowService.get(id)
    }

    def update(PortalTrackerFlow portalTrackerFlow) {
        if (portalTrackerFlow == null) {
            notFound()
            return
        }

        try {
            portalTrackerFlowService.save(portalTrackerFlow)
        } catch (ValidationException e) {
            respond portalTrackerFlow.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerFlow.label', default: 'PortalTrackerFlow'), portalTrackerFlow.id])
                redirect portalTrackerFlow
            }
            '*'{ respond portalTrackerFlow, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalTrackerFlowService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerFlow.label', default: 'PortalTrackerFlow'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerFlow.label', default: 'PortalTrackerFlow'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
