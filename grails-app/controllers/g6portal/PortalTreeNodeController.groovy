package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTreeNodeController {
    PortalTreeService portalTreeService
    PortalTreeNodeService portalTreeNodeService
    PortalTreeNodeUserService portalTreeNodeUserService
    PortalService portalService
    UserService userService

    static allowedMethods = [save: "POST", update: "PUT", delete: "POST"]

    def jsonlist(Long id) {
        def curnode = portalTreeNodeService.get(id)
        // def nodelist = []
        def nodelist = ['id':curnode.id,'parent':curnode.parent?curnode.parent.id:'#','text':curnode.name,'type':'root','children':false]
        return render(contentType: "application/json") {
            node nodelist
        }
    }

    def json(Long id) {
        def curnode = portalTreeNodeService.get(id)
        if(curnode){
            def users = []
            curnode.users.each { cu-> 
                users << ['id':cu.id,'user_id':cu.user.id,'name':cu.user.name,'role':cu.role,'user_id':cu.user.userID]
            }
            users = users.sort{ it.role }
            def data = ['id':curnode.id,'name':curnode.name,'slug':curnode.slug,'path':curnode.getfullpath(),'domain':curnode.domain,'domainid':curnode.domainid,'data':curnode.data,'lft':curnode.lft,'rgt':curnode.rgt,'parent':curnode.parent?.id,'users':users]
            return render(contentType: "application/json") {
                node data
            }
        }
    }

    def user_form(Long id) {
        def curnode = portalTreeNodeService.get(params.hidenodeid)
        if(params.nodeaction=='new') {
            def seluser = userService.get(params.user)
            if(seluser) {
                def newuser = new PortalTreeNodeUser(user:seluser,node:curnode,role:params.role)
                portalTreeNodeUserService.save(newuser)
            }
        }
        else if(params.nodeaction=='edit'){
            def curnodeuser = portalTreeNodeUserService.get(params.usernodeid)
            def seluser = userService.get(params.user)
            if(curnodeuser && seluser) {
                curnodeuser.role = params.role
                curnodeuser.user = seluser
                portalTreeNodeUserService.save(curnodeuser)
            }
        }
        else if(params.nodeaction=='delete'){
            portalTreeNodeUserService.delete(params.usernodeid)
        }
        def pathid = curnode.getpath()*.id
        redirect(controller:'portalTreeNode',action:'show',id:curnode.tree.root.id,params:['nodepath':pathid,'nodeid':curnode.id])
    }

    def edit_domain = {
        def treeNodeInstance = portalTreeNodeService.get(params.id)
        if (!treeNodeInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'treeNode.label', default: 'TreeNode'), params.id])}"
            redirect(action: "list")
        }
        else {
            def foundtarget = false
            def trackerDomain = PortalSetting.namedefault('tracker_objects',null)
            def domaintype = treeNodeInstance.domain.replace('csdportal.','')
            println "Looking for domaintype:" + domaintype
            println "In " + trackerDomain
            if(domaintype in trackerDomain) {
                def tkn = trackerDomain[domaintype].tokenize('.')
                foundtarget = true
                redirect(controller: 'portalTracker', action: 'display_data', params:[slug:tkn[1],module:tkn[0],id:treeNodeInstance.domainid])
                return

            }
            if(!foundtarget) {
                def controllername = null
                if(treeNodeInstance.domain[-1]==treeNodeInstance.domain[-1].toUpperCase()){
                    controllername = treeNodeInstance.domain[10..-1]
                }
                else{
                    controllername = treeNodeInstance.domain[10].toLowerCase() + treeNodeInstance.domain[11..-1]
                }
                redirect(controller: controllername, action: 'edit', id: treeNodeInstance.domainid)
            }
        }
    }

    def movenode = {
        def o = PortalTreeNode.get(params.o)
        def r = PortalTreeNode.get(params.r)
        def nodes = PortalTreeNode.createCriteria().list {
            'eq'('parent',r)
            order('lft','asc')
        }
        def posamount = nodes.size()-1
        def newposition = params.int('p')
        println 'params p:' + newposition + ' nodes size:' + posamount
        if(newposition==0 && nodes.size()){
            println 'will put before ' + nodes[0]
            portalService.movenode(o,nodes[0],"before")
        }
        else if(newposition > posamount || newposition==0){
            println 'again params p:' + newposition + ' nodes size:' + posamount
            println 'will put last of ' + r
            portalService.movenode(o,r,"last")
        }
        else {
            println 'will put after ' + nodes[newposition-1]
            portalService.movenode(o,nodes[newposition-1],"after")
        }
    }

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTreeNodeService.list(params), model:[portalTreeNodeCount: portalTreeNodeService.count()]
    }

    def show(Long id) {
        respond portalTreeNodeService.get(id)
    }

    def create() {
        respond new PortalTreeNode(params)
    }

    def save(PortalTreeNode portalTreeNode) {
        if (portalTreeNode == null) {
            notFound()
            return
        }

        try {
            if(params.parentid) {
                portalTreeNode.parent = portalTreeNodeService.get(params.parentid)
            }
            portalTreeNodeService.save(portalTreeNode)
        } catch (ValidationException e) {
            respond portalTreeNode.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTreeNode.label', default: 'PortalTreeNode'), portalTreeNode.id])
                def pathid = portalTreeNode.getpath()*.id
                redirect(controller:'portalTreeNode',action:'show',id:portalTreeNode.tree.root.id,params:['nodepath':pathid,'nodeid':portalTreeNode.id])
            }
            '*' { respond portalTreeNode, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTreeNodeService.get(id)
    }

    def update(PortalTreeNode portalTreeNode) {
        if (portalTreeNode == null) {
            notFound()
            return
        }

        try {
            portalTreeNodeService.save(portalTreeNode)
        } catch (ValidationException e) {
            respond portalTreeNode.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTreeNode.label', default: 'PortalTreeNode'), portalTreeNode.id])
                def pathid = portalTreeNode.getpath()*.id
                redirect(controller:'portalTreeNode',action:'show',id:portalTreeNode.tree.root.id,params:['nodepath':pathid,'nodeid':portalTreeNode.id])
            }
            '*'{ respond portalTreeNode, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }
        def portalTreeNode = portalTreeNodeService.get(id)
        def rootid = portalTreeNode.tree.root.id
        def pathid = portalTreeNode.getpath()*.id

        portalTreeNodeService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTreeNode.label', default: 'PortalTreeNode'), id])
                redirect(controller:'portalTreeNode',action:'show',id:rootid,params:['nodepath':pathid,'nodeid':rootid])
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTreeNode.label', default: 'PortalTreeNode'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    def fixnodes = {
        def rootnodes = PortalTreeNode.createCriteria().list {
            'isNull'("parent")
        }
        PortalTreeNode.withTransaction { sqltrans->
            rootnodes.each { curnode->
                println 'processing root node:' + curnode.id
                curnode.lft = 1
                curnode.save(flush:true)
                curnode.rgt = fixitnode(curnode,1)
                curnode.save(flush:true)
            }
        }
        redirect(action: "index", params: params)
    }
    
    Integer fixitnode(PortalTreeNode curnode,Integer curleft){
        println 'fixing: ' + curnode.id
        def childnodes = PortalTreeNode.createCriteria().list {
            'eq'("parent",curnode)
            order("lft", "asc")
        }
        if(childnodes){
            childnodes.each { curchild->
                println 'down into child of:' + curnode.id + ' with id:' + curchild.id
                curchild.lft = curleft + 1
                curchild.save(flush:true)
                curchild.rgt = fixitnode(curchild,curleft + 1)
                curchild.save(flush:true)
                curleft = curchild.rgt
            }
            return curleft + 1
        }
        else{
            return curleft + 1
        }
    }
}
