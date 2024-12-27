package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerFlowController {

    PortalTrackerFlowService portalTrackerFlowService
    PortalTrackerService portalTrackerService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerFlowService.list(params), model:[portalTrackerFlowCount: portalTrackerFlowService.count()]
    }

    def show(Long id) {
        respond portalTrackerFlowService.get(id)
    }

    def details(Long id) {
        println "In details"
        def flow = portalTrackerFlowService.get(id)
        def transitions = []
        def tnames = flow.transitions?.tokenize(',')*.trim()
        tnames.each { ft->
            def tran = PortalTrackerTransition.findByTrackerAndName(flow.tracker,ft)
            if(tran) {
                transitions << tran
            }
        }
        def fields = []
        def fnames = flow.fields?.tokenize(',')*.trim()
        fnames.each { ft->
            def fd = PortalTrackerField.findByTrackerAndName(flow.tracker,ft)
            if(fd) {
                fields << fd
            }
        }
        if(params.update) {
            println "Updating systems"
            println "Params:" + params
            PortalTrackerFlow.withTransaction { tr->
                transitions.each { ct->
                    println "For transition:" + ct + " id:" + ct.id
                    def editarray = []
                    def disparray = []
                    def statarray = []
                    fields.each { cf->
                        println "For fields:" + cf + " id:" + cf.id
                        if(params['td_' + ct.id + '_' + cf.id]) {
                            println "Disp:" + cf.name
                            disparray << cf.name
                        }
                        if(params['te_' + ct.id + '_' + cf.id]) {
                            println "Edit:" + cf.name
                            editarray << cf.name
                        }
                        if(params['sd_' + ct.next_status?.id + '_' + cf.id]) {
                            statarray << cf.name
                        }
                    }
                    ct.editfields = editarray.join(',')
                    ct.displayfields = disparray.join(',')
                    println "Edit fields:" + ct.editfields
                    println "Display fields:" + ct.displayfields
                    ct.save(flush:true)
                    ct.next_status?.displayfields = statarray.join(',')
                    ct.next_status?.save(flush:true)
                }
                println "Updated fields"
            }
        }
        def checkboxes = [:]
        transitions.each { ct->
            def tdf = ct.displayfields?.tokenize(',')*.trim()
            tdf.each { cf->
                def fd = PortalTrackerField.findByTrackerAndName(flow.tracker,cf)
                checkboxes['td_' + ct.id + '_' + fd.id] = 1
            }
            def tef = ct.editfields?.tokenize(',')*.trim()
            tef.each { cf->
                def fd = PortalTrackerField.findByTrackerAndName(flow.tracker,cf)
                checkboxes['te_' + ct.id + '_' + fd.id] = 1
            }
            def df = ct.next_status?.displayfields?.tokenize(',')*.trim()
            df.each { cf->
                def fd = PortalTrackerField.findByTrackerAndName(flow.tracker,cf)
                checkboxes['sd_' + ct.next_status?.id + '_' + fd.id] = 1
            }
        }
        respond flow, model:[transitions:transitions,fields:fields,checkboxes:checkboxes]
    }

    def create() {
        if(params.tracker_id) {
          def tracker = portalTrackerService.get(params.tracker_id)
          params.tracker = tracker
          params.fields = tracker.fields.sort{ it.name }*.name
          params.transitions = tracker.transitions.sort{ it.name }*.name
        }
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
