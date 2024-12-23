package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerEmailController {

    PortalTrackerEmailService portalTrackerEmailService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerEmailService.list(params), model:[portalTrackerEmailCount: portalTrackerEmailService.count()]
    }

    def show(Long id) {
        respond portalTrackerEmailService.get(id)
    }

    def create() {
        def transition = null
        def status = null
        def tracker = null
        def pages = null
        if(params.transition_id) {
            transition = PortalTrackerTransition.get(params.transition_id)
            if(transition) {
                tracker = transition.tracker
            }
        }
        else if(params.status_id) {
            status = PortalTrackerStatus.get(params.status_id)
            if(status) {
                tracker = status.tracker
            }
        }
        if(tracker) {
            pages = PortalPage.findAllByModule(tracker.module)
        }
        respond new PortalTrackerEmail(params),model:['transition':transition,'status':status,'tracker':tracker,'pages':pages]
    }

    def save(PortalTrackerEmail portalTrackerEmail) {
        if (portalTrackerEmail == null) {
            notFound()
            return
        }

        try {
            portalTrackerEmailService.save(portalTrackerEmail)
        } catch (ValidationException e) {
            respond portalTrackerEmail.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerEmail.label', default: 'PortalTrackerEmail'), portalTrackerEmail.id])
                redirect portalTrackerEmail
            }
            '*' { respond portalTrackerEmail, [status: CREATED] }
        }
    }

    def edit(Long id) {
        def portalTrackerEmail = PortalTrackerEmail.get(id)
        def pages = null
        if(portalTrackerEmail.tracker) {
            pages = PortalPage.findAllByModule(portalTrackerEmail.tracker.module)
        }
        respond portalTrackerEmail, model:['pages':pages]
    }

    def update(PortalTrackerEmail portalTrackerEmail) {
        if (portalTrackerEmail == null) {
            notFound()
            return
        }

        try {
            portalTrackerEmailService.save(portalTrackerEmail)
        } catch (ValidationException e) {
            respond portalTrackerEmail.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerEmail.label', default: 'PortalTrackerEmail'), portalTrackerEmail.id])
                redirect portalTrackerEmail
            }
            '*'{ respond portalTrackerEmail, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalTrackerEmailService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerEmail.label', default: 'PortalTrackerEmail'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerEmail.label', default: 'PortalTrackerEmail'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
