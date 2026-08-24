package g6portal

import grails.validation.ValidationException
import groovy.json.JsonSlurper
import static org.springframework.http.HttpStatus.*

class PortalTreeController {

    PortalTreeService portalTreeService
    PortalTreeNodeService portalTreeNodeService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", importtree: "POST", confirmimporttree: "POST"]

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

    // ---------------------------------------------------------------------------------
    // Moving one tree on its own
    //
    // A module export carries its trees, but a tree often changes on its own schedule - an
    // org chart is reshuffled without the module around it changing at all. These actions
    // move a single tree between servers using the same format
    // (PortalTree.exportjson / importdata), so a tree file taken from here is also readable
    // as one entry of a module's treelist.json.
    // ---------------------------------------------------------------------------------

    def exporttree(Long id) {
        def tree = portalTreeService.get(id)
        if (tree == null) {
            notFound()
            return
        }
        // Role holders are people rather than structure, so they only travel when asked for
        def staff_on = params.staff ? true : false
        def json = PortalTree.exportjson([tree],staff_on)
        def filename = 'tree_' + (tree.module?:'portal') + '_' + tree.name
        filename = filename.replaceAll('[^A-Za-z0-9_.-]','_') + '.json'
        response.setContentType("application/json")
        response.setCharacterEncoding("UTF-8")
        response.setHeader("Content-disposition", "attachment;filename=${filename}")
        response.outputStream << json.getBytes("UTF-8")
        response.outputStream.flush()
    }

    // Where an uploaded tree file waits between the preview and the confirmation
    private File treeimportfolder() {
        def curfolder = System.getProperty("user.dir")
        def folder = new File(PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/treeimports')
        if(!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    // Writes are scoped to the tree's own module here rather than in SecurityInterceptor,
    // because the module being written is named inside the uploaded file, which the
    // interceptor cannot see. A tree that does not exist here yet is created, so the check is
    // on the module the file names either way.
    private List unauthorizedmodules(treearray, curuser) {
        if(curuser?.isAdmin) {
            return []
        }
        def adminlist = curuser?.adminlist() ?: []
        return treearray.collect { it.module }.unique().findAll { !(it in adminlist) }
    }

    def importtreeform() {
        render view:'importtree'
    }

    def importtree() {
        def curuser = session.curuser
        def f = request.getFile('fileupload')
        if (!f || f.empty) {
            flash.message = "No file uploaded"
            redirect action:"importtreeform"
            return
        }
        def staff_on = params.staff ? true : false
        def text = new String(f.bytes, "UTF-8")
        def treearray = null
        try {
            treearray = new JsonSlurper().parseText(text)
            if(!(treearray instanceof List)) {
                treearray = [treearray]     // a single tree object is accepted too
            }
        }
        catch(Exception e) {
            flash.message = "Could not read that file as a tree export: " + e.message
            redirect action:"importtreeform"
            return
        }
        if(!treearray || !treearray.every { it?.name }) {
            flash.message = "That file does not look like a tree export (no tree name in it)"
            redirect action:"importtreeform"
            return
        }
        def denied = unauthorizedmodules(treearray, curuser)
        if(denied) {
            flash.message = "You need admin rights on module " + denied.join(', ') + " to import that tree"
            redirect action:"importtreeform"
            return
        }
        // Park the upload so the confirmation step imports exactly what was previewed
        def stamp = System.currentTimeMillis()
        def parked = new File(treeimportfolder(), stamp + '_' + (curuser?.userID?:'anon') + '.json')
        parked.setText(text, "UTF-8")
        redirect action:"importtreepreview", params:[parked:parked.name, staff:(staff_on?'on':null)]
    }

    def importtreepreview() {
        def curuser = session.curuser
        def parked = new File(treeimportfolder(), params.parked ?: '')
        if(!params.parked || params.parked.contains('/') || params.parked.contains('\\') || !parked.exists()) {
            flash.message = "That upload is no longer available, please upload the file again"
            redirect action:"importtreeform"
            return
        }
        def staff_on = params.staff ? true : false
        def treearray = new JsonSlurper().parseText(parked.text)
        if(!(treearray instanceof List)) {
            treearray = [treearray]
        }
        def denied = unauthorizedmodules(treearray, curuser)
        if(denied) {
            flash.message = "You need admin rights on module " + denied.join(', ') + " to import that tree"
            redirect action:"importtreeform"
            return
        }
        // Same comparison the module import preview does, but between the incoming tree and
        // what this server holds for it right now
        def current = treearray.collect { itree->
            PortalTree.findByModuleAndName(itree.module,itree.name)
        }
        def currentjson = current.every { it == null } ? '' : PortalTree.exportjson(current.findAll { it != null },staff_on)
        def incomingjson = PortalTree.normalizejson(treearray,staff_on)
        def difftext = ''
        if(currentjson) {
            difftext = PortalService.unifiedDiff('tree.json', currentjson.split('\n', -1) as List, incomingjson.split('\n', -1) as List)
        }
        else {
            difftext = "--- /dev/null\n+++ b/tree.json\n" + incomingjson.split('\n', -1).collect { '+' + it }.join('\n') + "\n"
        }
        render view:'importtreepreview',
               model:[curuser:curuser, diff:difftext, parked:params.parked, staff_on:staff_on,
                      trees:treearray.collect { [module:it.module, name:it.name,
                                                 known:(PortalTree.findByModuleAndName(it.module,it.name) != null)] }]
    }

    def confirmimporttree() {
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
        def curuser = session.curuser
        def parked = new File(treeimportfolder(), params.parked ?: '')
        if(!params.parked || params.parked.contains('/') || params.parked.contains('\\') || !parked.exists()) {
            flash.message = "That upload is no longer available, please upload the file again"
            redirect action:"importtreeform"
            return
        }
        def staff_on = params.staff ? true : false
        def treearray = new JsonSlurper().parseText(parked.text)
        if(!(treearray instanceof List)) {
            treearray = [treearray]
        }
        def denied = unauthorizedmodules(treearray, curuser)
        if(denied) {
            flash.message = "You need admin rights on module " + denied.join(', ') + " to import that tree"
            redirect action:"importtreeform"
            return
        }
        def stats = [:]
        def lasttree = null
        try {
            PortalTree.withTransaction { tstatus ->
                treearray.each { itree->
                    lasttree = PortalTree.importdata(itree,staff_on,stats) ?: lasttree
                }
            }
        }
        catch(Exception e) {
            println "Error importing tree: " + e
            flash.message = "Error importing tree: " + e.message
            redirect action:"index"
            return
        }
        parked.delete()
        flash.message = "Tree imported (" +
            [(stats['treeadded']?:0) + " tree(s) created",
             (stats['treeupdated']?:0) + " updated",
             (stats['nodeadded']?:0) + " nodes added",
             (stats['nodeupdated']?:0) + " nodes updated",
             (stats['useradded']?:0) + " role holders added",
             (stats['userskipped']?:0) + " staff not found here"].join(', ') + ")"
        if(lasttree) {
            redirect action:"show", id:lasttree.id
        }
        else {
            redirect action:"index"
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
