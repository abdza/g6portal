package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerStatusController {

    PortalTrackerStatusService portalTrackerStatusService
    PortalTrackerService portalTrackerService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerStatusService.list(params), model:[portalTrackerStatusCount: portalTrackerStatusService.count()]
    }

    def show(Long id) {
        respond portalTrackerStatusService.get(id)
    }

    def create() {
        if(params.tracker_id) {
          def tracker = portalTrackerService.get(params.tracker_id)
          params.tracker = tracker
        }
        respond new PortalTrackerStatus(params)
    }

    def save(PortalTrackerStatus portalTrackerStatus) {
        if (portalTrackerStatus == null) {
            notFound()
            return
        }

        try {
            portalTrackerStatusService.save(portalTrackerStatus)
        } catch (ValidationException e) {
            respond portalTrackerStatus.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerStatus.label', default: 'PortalTrackerStatus'), portalTrackerStatus.id])
                redirect portalTrackerStatus.tracker
            }
            '*' { respond portalTrackerStatus, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerStatusService.get(id)
    }

    def update(PortalTrackerStatus portalTrackerStatus) {
        if (portalTrackerStatus == null) {
            notFound()
            return
        }

        try {
            portalTrackerStatusService.save(portalTrackerStatus)
        } catch (ValidationException e) {
            respond portalTrackerStatus.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerStatus.label', default: 'PortalTrackerStatus'), portalTrackerStatus.id])
                redirect portalTrackerStatus.tracker
            }
            '*'{ respond portalTrackerStatus, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        def curstatus = portalTrackerStatusService.get(id)
        def currentTracker = curstatus?.tracker
        PortalTrackerTransition.withTransaction { transaction-> 
            currentTracker.transitions.each { tr ->
                tr.removeFromPrev_status(curstatus)
                tr.save()
            }
            def trans = PortalTrackerTransition.findAllByNext_status(curstatus)
            trans.each { tr ->
                currentTracker.removeFromTransitions(tr)
                tr.delete()
            }

            currentTracker.removeFromStatuses(curstatus)
            if(currentTracker.initial_status==curstatus) {
                currentTracker.initial_status = null
                currentTracker.save(flush:true)
            }
            curstatus.delete()

        }
        // portalTrackerStatusService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerStatus.label', default: 'PortalTrackerStatus'), id])
		redirect controller: "portalTracker", action: "show", id:currentTracker.id
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerStatus.label', default: 'PortalTrackerStatus'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
