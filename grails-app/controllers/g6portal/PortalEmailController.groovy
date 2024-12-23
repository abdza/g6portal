package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalEmailController {

    PortalPageService portalPageService
    PortalService portalService
    PortalEmailService portalEmailService
    UserService userService
    def sessionFactory
    def mailService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def run() {
        def emails = portalEmailService.tosend()
        def emailfrom = PortalSetting.namedefault("portal.emailfrom","portal@portal.com")
        PortalEmail.withTransaction { etrans -> 
            emails.each { email->
                email.send(mailService)
            }
        }
    }

    def index(Integer max) {
      def dparam = [max:params.max?:10,offset:params.offset?:0]
      params.max = dparam.max
      if(params.q) {
        def query = '%' + params.q + '%'
        if(session.enablesuperuser) {
            def thelist = portalEmailService.list(query,dparam)
            respond thelist, model:[portalEmailCount: portalEmailService.count(query), params:params]
        }
        else {
            def thelist = portalEmailService.list(query,session.adminmodules,dparam)
            respond thelist, model:[portalEmailCount: portalEmailService.count(query,session.adminmodules), params:params]
        }
      }
      else {
        if(session.enablesuperuser) {
            def thelist = portalEmailService.list(dparam)
            respond thelist, model:[portalEmailCount: portalEmailService.count(), params:params]
        }
        else {
            def thelist = portalEmailService.list(session.adminmodules,dparam)
            respond thelist, model:[portalEmailCount: portalEmailService.count(session.adminmodules), params:params]
        }
      }
    }

    def show(Long id) {
        respond portalEmailService.get(id)
    }

    def create() {
        respond new PortalEmail(params)
    }

    def save(PortalEmail portalEmail) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (portalEmail == null) {
                notFound()
                return
            }

            try {
                portalEmailService.save(portalEmail)
            } catch (ValidationException e) {
                respond portalEmail.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'portalEmail.label', default: 'PortalEmail'), portalEmail.id])
                    redirect portalEmail
                }
                '*' { respond portalEmail, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond portalEmailService.get(id)
    }

    def update(PortalEmail portalEmail) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (portalEmail == null) {
                notFound()
                return
            }

            try {
                portalEmailService.save(portalEmail)
            } catch (ValidationException e) {
                respond portalEmail.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'portalEmail.label', default: 'PortalEmail'), portalEmail.id])
                    redirect portalEmail
                }
                '*'{ respond portalEmail, [status: OK] }
            }
        }
    }

    def delete(Long id) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (id == null) {
                notFound()
                return
            }

            portalEmailService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalEmail.label', default: 'PortalEmail'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalEmail.label', default: 'PortalEmail'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
