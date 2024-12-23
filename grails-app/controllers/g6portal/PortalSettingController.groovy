package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalSettingController {

    PortalSettingService portalSettingService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All')) {
          def query = '%' + params.q + '%'
              if(params.module && params.module!='All') {
                  def thelist = portalSettingService.list(query,params.module,dparam)
                  respond thelist, model:[portalSettingCount: portalSettingService.count(query,params.module), params:params]
              }
              else{
                  if(session.enablesuperuser) {
                      def thelist = portalSettingService.list(query,dparam)
                      respond thelist, model:[portalSettingCount: portalSettingService.count(query), params:params]
                  }
                  else {
                      def thelist = portalSettingService.list(query,session.adminmodules,dparam)
                      respond thelist, model:[portalSettingCount: portalSettingService.count(query,session.adminmodules), params:params]
                  }
              }
        }
        else {
          if(session.enablesuperuser) {
              def thelist = portalSettingService.list(dparam)
              respond thelist, model:[portalSettingCount: portalSettingService.count(), params:params]
          }
          else {
              def thelist = portalSettingService.list(session.adminmodules,dparam)
              respond thelist, model:[portalSettingCount: portalSettingService.count(session.adminmodules), params:params]
          }
        }
    }

    def show(Long id) {
        respond portalSettingService.get(id)
    }

    def create() {
        respond new PortalSetting(params)
    }

    def save(PortalSetting portalSetting) {
        if (portalSetting == null) {
            notFound()
            return
        }

        try {
            portalSettingService.save(portalSetting)
        } catch (ValidationException e) {
            respond portalSetting.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalSetting.label', default: 'PortalSetting'), portalSetting.id])
                redirect portalSetting
            }
            '*' { respond portalSetting, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalSettingService.get(id)
    }

    def update(PortalSetting portalSetting) {
        if (portalSetting == null) {
            notFound()
            return
        }

        try {
            portalSettingService.save(portalSetting)
        } catch (ValidationException e) {
            respond portalSetting.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalSetting.label', default: 'PortalSetting'), portalSetting.id])
                redirect portalSetting
            }
            '*'{ respond portalSetting, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalSettingService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalSetting.label', default: 'PortalSetting'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalSetting.label', default: 'PortalSetting'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
