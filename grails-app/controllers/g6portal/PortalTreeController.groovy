package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTreeController {

    PortalTreeService portalTreeService
    PortalTreeNodeService portalTreeNodeService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def create_root(Long id) {
        def tree = portalTreeService.get(id)
        def root = new PortalTreeNode(tree:tree,name:tree.name)
        portalTreeNodeService.save(root)
        tree.root = root
        portalTreeService.save(tree)
        redirect tree
    }

    def index(Integer max) {
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All')) {
            def query = '%' + params.q + '%'
            if(params.module && params.module!='All') {
                def thelist = portalTreeService.list(query,params.module,dparam)
                respond thelist, model:[portalTreeCount: portalTreeService.count(query,params.module), params:params]
            }
            else {
                if(session.enablesuperuser) {
                    def thelist = portalTreeService.list(query,dparam)
                    respond thelist, model:[portalTreeCount: portalTreeService.count(query), params:params]
                }
                else {
                    def thelist = portalTreeService.list(query,session.adminmodules,dparam)
                    respond thelist, model:[portalTreeCount: portalTreeService.count(query,session.adminmodules), params:params]
                }
            }
        }
        else {
            if(session.enablesuperuser) {
                def thelist = portalTreeService.list(dparam)
                respond thelist, model:[portalTreeCount: portalTreeService.count(), params:params]
            }
            else {
                def thelist = portalTreeService.list(session.adminmodules,dparam)
                respond thelist, model:[portalTreeCount: portalTreeService.count(session.adminmodules), params:params]
            }
        }
    }

    def show(Long id) {
        respond portalTreeService.get(id)
    }

    def create() {
        respond new PortalTree(params)
    }

    def save(PortalTree portalTree) {
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
            if (portalTree == null) {
                notFound()
                return
            }

            try {
                portalTreeService.save(portalTree)
            } catch (ValidationException e) {
                respond portalTree.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'portalTree.label', default: 'PortalTree'), portalTree.id])
                    redirect portalTree
                }
                '*' { respond portalTree, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond portalTreeService.get(id)
    }

    def update(PortalTree portalTree) {
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
            if (portalTree == null) {
                notFound()
                return
            }

            try {
                portalTreeService.save(portalTree)
            } catch (ValidationException e) {
                respond portalTree.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTree.label', default: 'PortalTree'), portalTree.id])
                    redirect portalTree
                }
                '*'{ respond portalTree, [status: OK] }
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

            portalTreeService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTree.label', default: 'PortalTree'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTree.label', default: 'PortalTree'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
