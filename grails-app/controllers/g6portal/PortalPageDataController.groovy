package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalPageDataController {

    PortalPageDataService portalPageDataService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q) {
            def query = '%' + params.q + '%'
            def thelist = portalPageDataService.list(query,dparam)
            respond thelist, model:[portalPageDataCount: portalPageDataService.count(query), params:params]
        }
        else {
            def thelist = portalPageDataService.list(dparam)
            respond thelist, model:[portalPageDataCount: portalPageDataService.count(), params:params]
        }
    }

    def show(Long id) {
        respond portalPageDataService.get(id)
    }

    def create() {
        respond new PortalPageData(params)
    }

    def save(PortalPageData portalPageData) {
        if (portalPageData == null) {
            notFound()
            return
        }

        try {
            portalPageDataService.save(portalPageData)
        } catch (ValidationException e) {
            respond portalPageData.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalPageData.label', default: 'PortalPageData'), portalPageData.id])
                redirect portalPageData
            }
            '*' { respond portalPageData, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalPageDataService.get(id)
    }

    def update(PortalPageData portalPageData) {
        if (portalPageData == null) {
            notFound()
            return
        }

        try {
            portalPageDataService.save(portalPageData)
        } catch (ValidationException e) {
            respond portalPageData.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalPageData.label', default: 'PortalPageData'), portalPageData.id])
                redirect portalPageData
            }
            '*'{ respond portalPageData, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalPageDataService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalPageData.label', default: 'PortalPageData'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalPageData.label', default: 'PortalPageData'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
