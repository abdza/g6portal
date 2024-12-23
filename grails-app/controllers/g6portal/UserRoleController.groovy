package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class UserRoleController {

    UserRoleService userRoleService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        // def curuser = User.get(session.userid)
        def curuser = session.curuser
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All') || (params.role && params.role!='All')) {
            def query = '%' + params.q + '%'
            if(params.module && params.module!='All' && params.role && params.role!='All') {
                def thelist = userRoleService.list(query,params.module,params.role,dparam)
                respond thelist, model:[curuser:curuser, userRoleCount: userRoleService.count(query,params.module,params.role), params:params]
                return
            }
            else if(params.module && params.module!='All') {
                def thelist = userRoleService.list(query,params.module,dparam)
                respond thelist, model:[curuser:curuser, userRoleCount: userRoleService.count(query,params.module), params:params]
                return
            }
            else {
                if(session.enablesuperuser) {
                    def thelist = userRoleService.list(query,dparam)
                    respond thelist, model:[curuser:curuser, userRoleCount: userRoleService.count(query), params:params]
                }
                else {
                    def thelist = userRoleService.list(query,session.adminmodules,dparam)
                    respond thelist, model:[curuser:curuser, userRoleCount: userRoleService.count(query,session.adminmodules), params:params]
                }
                return
            }
        }
        else {
            if(session.enablesuperuser) {
                def thelist = userRoleService.list(dparam)
                respond thelist, model:[curuser:curuser, userRoleCount: userRoleService.count(), params:params]
            }
            else {
                def thelist = userRoleService.list(session.adminmodules,dparam)
                respond thelist, model:[curuser:curuser, userRoleCount: userRoleService.count(session.adminmodules), params:params]
            }
            return
        }
    }

    def show(Long id) {
        respond userRoleService.get(id)
    }

    def create() {
        // def curuser = User.get(session.userid)
        respond new UserRole(params),model:[curuser:session.curuser]
    }

    def save(UserRole userRole) {
        if (userRole == null) {
            notFound()
            return
        }

        try {
            userRoleService.save(userRole)
        } catch (ValidationException e) {
            respond userRole.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'userRole.label', default: 'UserRole'), userRole.id])
                redirect userRole
            }
            '*' { respond userRole, [status: CREATED] }
        }
    }

    def edit(Long id) {
        // def curuser = User.get(session.userid)
        respond userRoleService.get(id),model:[curuser:session.curuser]
    }

    def update(UserRole userRole) {
        if (userRole == null) {
            notFound()
            return
        }

        try {
            userRoleService.save(userRole)
        } catch (ValidationException e) {
            respond userRole.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'userRole.label', default: 'UserRole'), userRole.id])
                redirect userRole
            }
            '*'{ respond userRole, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        userRoleService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'userRole.label', default: 'UserRole'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'userRole.label', default: 'UserRole'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
