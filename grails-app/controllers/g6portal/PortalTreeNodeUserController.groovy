package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTreeNodeUserController {

    PortalTreeNodeUserService portalTreeNodeUserService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTreeNodeUserService.list(params), model:[portalTreeNodeUserCount: portalTreeNodeUserService.count()]
    }

    def show(Long id) {
        respond portalTreeNodeUserService.get(id)
    }

    def create() {
        respond new PortalTreeNodeUser(params)
    }

    def save(PortalTreeNodeUser portalTreeNodeUser) {
        if (portalTreeNodeUser == null) {
            notFound()
            return
        }

        try {
            portalTreeNodeUserService.save(portalTreeNodeUser)
        } catch (ValidationException e) {
            respond portalTreeNodeUser.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTreeNodeUser.label', default: 'PortalTreeNodeUser'), portalTreeNodeUser.id])
                redirect portalTreeNodeUser
            }
            '*' { respond portalTreeNodeUser, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTreeNodeUserService.get(id)
    }

    def update(PortalTreeNodeUser portalTreeNodeUser) {
        if (portalTreeNodeUser == null) {
            notFound()
            return
        }

        try {
            portalTreeNodeUserService.save(portalTreeNodeUser)
        } catch (ValidationException e) {
            respond portalTreeNodeUser.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTreeNodeUser.label', default: 'PortalTreeNodeUser'), portalTreeNodeUser.id])
                redirect portalTreeNodeUser
            }
            '*'{ respond portalTreeNodeUser, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalTreeNodeUserService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTreeNodeUser.label', default: 'PortalTreeNodeUser'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTreeNodeUser.label', default: 'PortalTreeNodeUser'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
