package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerTransitionController {

    PortalTrackerTransitionService portalTrackerTransitionService
    PortalTrackerService portalTrackerService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerTransitionService.list(params), model:[portalTrackerTransitionCount: portalTrackerTransitionService.count()]
    }

    def show(Long id) {
        respond portalTrackerTransitionService.get(id)
    }

    def create() {
        if(params.tracker_id) {
          def tracker = portalTrackerService.get(params.tracker_id)
          params.tracker = tracker
        }
        respond new PortalTrackerTransition(params)
    }

    def save(PortalTrackerTransition portalTrackerTransition) {
        if (portalTrackerTransition == null) {
            notFound()
            return
        }

        try {
            if('rolestxt' in params) {
                println("Got roles in params")
                def rtoken = params['rolestxt'].tokenize(',')
                def droles = []
                rtoken.each { ctoken->
                    def crole = PortalTrackerRole.findAllByTrackerAndName(portalTrackerTransition.tracker,ctoken.trim())
                    if(crole) {
                        droles << crole
                    }
                }
                params['roles'] = droles
                portalTrackerTransition.roles = droles
                println("Transferred roles " + droles)
            }
            portalTrackerTransitionService.save(portalTrackerTransition)
        } catch (ValidationException e) {
            respond portalTrackerTransition.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerTransition.label', default: 'PortalTrackerTransition'), portalTrackerTransition.id])
                redirect portalTrackerTransition.tracker
            }
            '*' { respond portalTrackerTransition, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerTransitionService.get(id)
    }

    def update(PortalTrackerTransition portalTrackerTransition) {
        println("In update method")
        if (portalTrackerTransition == null) {
            notFound()
            return
        }

        try {
            if(!('prev_status' in params)) {
                portalTrackerTransition.prev_status = []
            }
            if('rolestxt' in params) {
                println("Got roles in params")
                def rtoken = params['rolestxt'].tokenize(',')
                def droles = []
                rtoken.each { ctoken->
                    def crole = PortalTrackerRole.findAllByTrackerAndName(portalTrackerTransition.tracker,ctoken.trim())
                    if(crole) {
                        droles << crole
                    }
                }
                params['roles'] = droles
                portalTrackerTransition.roles = droles
                println("Transferred roles " + droles)
            }
            if(!params.roles) {
                portalTrackerTransition.roles = null
            }
            portalTrackerTransitionService.save(portalTrackerTransition)
        } catch (ValidationException e) {
            respond portalTrackerTransition.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerTransition.label', default: 'PortalTrackerTransition'), portalTrackerTransition.id])
                redirect portalTrackerTransition.tracker
            }
            '*'{ respond portalTrackerTransition, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

	def currentTracker = portalTrackerTransitionService.get(id)?.tracker

        portalTrackerTransitionService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerTransition.label', default: 'PortalTrackerTransition'), id])
		redirect controller: "portalTracker", action: "show", id:currentTracker.id
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerTransition.label', default: 'PortalTrackerTransition'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
