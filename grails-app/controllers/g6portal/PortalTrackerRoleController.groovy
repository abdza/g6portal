package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerRoleController {

    PortalTrackerRoleService portalTrackerRoleService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerRoleService.list(params), model:[portalTrackerRoleCount: portalTrackerRoleService.count()]
    }

    def show(Long id) {
        respond portalTrackerRoleService.get(id)
    }

    def create() {
        respond new PortalTrackerRole(params)
    }

    def save(PortalTrackerRole portalTrackerRole) {
        if (portalTrackerRole == null) {
            notFound()
            return
        }

        try {
            portalTrackerRoleService.save(portalTrackerRole)
        } catch (ValidationException e) {
            respond portalTrackerRole.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerRole.label', default: 'PortalTrackerRole'), portalTrackerRole.id])
                redirect portalTrackerRole.tracker
            }
            '*' { respond portalTrackerRole, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerRoleService.get(id)
    }

    def update(PortalTrackerRole portalTrackerRole) {
        if (portalTrackerRole == null) {
            notFound()
            return
        }

        try {
            portalTrackerRoleService.save(portalTrackerRole)
        } catch (ValidationException e) {
            respond portalTrackerRole.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerRole.label', default: 'PortalTrackerRole'), portalTrackerRole.id])
                redirect portalTrackerRole.tracker
            }
            '*'{ respond portalTrackerRole, [status: OK] }
        }
    }

    def delete(Long id) {
      if (id == null) {
          notFound()
          return
      }

	    def currentTracker = portalTrackerRoleService.get(id)?.tracker

      portalTrackerRoleService.delete(id)

      request.withFormat {
        form multipartForm {
            flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerRole.label', default: 'PortalTrackerRole'), id])
redirect controller: "portalTracker", action: "show", id:currentTracker.id
        }
        '*'{ render status: NO_CONTENT }
      }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerRole.label', default: 'PortalTrackerRole'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
