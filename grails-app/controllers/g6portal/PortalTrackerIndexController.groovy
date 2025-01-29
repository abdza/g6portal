package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerIndexController {

    PortalTrackerIndexService portalTrackerIndexService
    PortalTrackerService portalTrackerService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerIndexService.list(params), model:[portalTrackerIndexCount: portalTrackerIndexService.count()]
    }

    def show(Long id) {
        respond portalTrackerIndexService.get(id)
    }

    def createIndex(Long id) {
        println "In create index"
        def portalTrackerIndex = portalTrackerIndexService.get(id)
        portalTrackerIndex.updateDb()
        session.flash = "Index created for table " + portalTrackerIndex.tracker.data_table()
        redirect portalTrackerIndex
    }

    def create() {
        if(params.tracker_id) {
          def tracker = portalTrackerService.get(params.tracker_id)
          params.tracker = tracker
          params.fields = tracker.fields.sort{ it.name }*.name
        }
        respond new PortalTrackerIndex(params)
    }

    def save(PortalTrackerIndex portalTrackerIndex) {
        if (portalTrackerIndex == null) {
            notFound()
            return
        }

        try {
            portalTrackerIndexService.save(portalTrackerIndex)
        } catch (ValidationException e) {
            respond portalTrackerIndex.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerIndex.label', default: 'PortalTrackerIndex'), portalTrackerIndex.id])
                redirect portalTrackerIndex.tracker
            }
            '*'{ respond portalTrackerIndex, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerIndexService.get(id)
    }

    def update(PortalTrackerIndex portalTrackerIndex) {
        if (portalTrackerIndex == null) {
            notFound()
            return
        }

        try {
            portalTrackerIndexService.save(portalTrackerIndex)
        } catch (ValidationException e) {
            respond portalTrackerIndex.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerIndex.label', default: 'PortalTrackerIndex'), portalTrackerIndex.id])
                redirect portalTrackerIndex.tracker
            }
            '*'{ respond portalTrackerIndex, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        def portalTrackerIndex = portalTrackerIndexService.get(id)
        def currentTracker = portalTrackerIndex.tracker
        portalTrackerIndex.deleteIndex()
        portalTrackerIndexService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerIndex.label', default: 'PortalTrackerIndex'), id])
                redirect controller: "portalTracker", action: "show", id:currentTracker.id
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerIndex.label', default: 'PortalTrackerIndex'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
