package g6portal

import groovy.json.JsonOutput
import java.text.SimpleDateFormat

class PortalTree {

    static hasMany = [nodes:PortalTreeNode]

    static constraints = {
        module(nullable:true)
        name()
        root(nullable:true)
        expire(nullable:true)
        valid(nullable:true)
    }

    static mapping = {
        cache true
    }

    String module
    String name
    PortalTreeNode root
    Date valid
    Date expire

    String toString(){
        name
    }

    def static load_tree(name,module='portal') {
        def tokens = name.tokenize(':')
        name = tokens[0]
        if(tokens.size()==2) {
            module = tokens[1]
        }
        return PortalTree.findByModuleAndName(module,name)
    }

    def userbyrole(role) {
        def dnode = PortalTreeNodeUser.findByNodeInListAndRole(this.nodes,role,[cache:true])
        return dnode
    }

    def alluserbyrole(role) {
        def dnode = PortalTreeNodeUser.findAllByNodeInListAndRole(this.nodes,role,[cache:true])
        return dnode
    }

    def useris(role,curuser){
        def dnode = PortalTreeNodeUser.findAll("from PortalTreeNodeUser as tnu where tnu.node in :dnode and tnu.role=:drole and tnu.user=:duser",[dnode:this.nodes,drole:role,duser:curuser],[cache:true])
        return dnode
    }

    // ---------------------------------------------------------------------------------
    // Migration format
    //
    // A tree travels as one nested structure (treelist.json inside a module package, or a
    // single .json file for one tree on its own). Nodes are matched by the path of their key
    // - the slug when they have one, the name otherwise - and never by id: ids are per
    // server, and TreeNode tracker fields store raw node ids in the trak tables, so an
    // existing node is updated in place instead of being recreated. Import is additive: it
    // adds and updates, and never deletes what the target has of its own.
    // ---------------------------------------------------------------------------------

    static String treenodekey(node) {
        def key = node.slug?.toString()?.trim()
        if(!key) {
            key = node.name?.toString()?.trim()
        }
        return key ?: ''
    }

    // Dates are written by JsonOutput as yyyy-MM-dd'T'HH:mm:ssZ; the offset is optional so
    // hand-edited files with a plain timestamp still read back.
    static Date parseexportdate(value) {
        if(!value) {
            return null
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(value.toString())
        }
        catch(Exception e) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(value.toString())
            }
            catch(Exception e2) {
                println "PortalTree: could not read date " + value
                return null
            }
        }
    }

    // The natural key of the object a node links to, so domainid can be looked up again on
    // the target server instead of being copied across as a meaningless number.
    static final List DOMAIN_KEY_FIELDS = ['slug','code','branch_code','number','name','title']

    static def treedomainkey(node) {
        if(!node.domain || !node.domainid) {
            return null
        }
        def dobject = null
        try {
            dobject = node.getdomain()
        }
        catch(Exception e) {
            dobject = null
        }
        if(dobject == null) {
            return null
        }
        // Match the candidates case-insensitively but record the name as the object spells it -
        // tracker columns like Branch_Code are matched loosely by MSSQL but quoted verbatim on
        // Postgres, so the lookup on import has to ask for the real name.
        def available = [:]
        try {
            if(dobject instanceof Map) {
                dobject.keySet().each { available[it.toString().toLowerCase()] = it.toString() }
            }
            else {
                dobject.metaClass.properties*.name.each { available[it.toLowerCase()] = it }
            }
        }
        catch(Exception e) {
            return null
        }
        def toreturn = null
        DOMAIN_KEY_FIELDS.each { keyfield->
            if(toreturn) {
                return
            }
            def realfield = available[keyfield]
            if(!realfield) {
                return
            }
            def value = null
            try {
                value = dobject[realfield]
            }
            catch(Exception e) {
                value = null
            }
            if(value != null && value.toString().trim()) {
                toreturn = [field:realfield, value:value.toString().trim()]
            }
        }
        return toreturn
    }

    static def exportnode(node,staff_on) {
        def users = null
        if(staff_on) {
            users = []
            node.users?.sort { a,b -> (a.user?.userID?:'') <=> (b.user?.userID?:'') ?: (a.role?:'') <=> (b.role?:'') }.each { nodeuser->
                if(nodeuser.user?.userID) {
                    users << [
                        userid: nodeuser.user.userID,
                        role: nodeuser.role
                    ]
                }
            }
        }
        def domainkey = treedomainkey(node)
        // children keep their sibling order (lft), which is what the menus and pickers show
        def childnodes = node.nodes?.sort { a,b -> (a.lft?:0) <=> (b.lft?:0) ?: a.id <=> b.id }?.collect { child->
            exportnode(child,staff_on)
        }
        return [
            name: node.name,
            slug: node.slug,
            data: node.data,
            domain: node.domain,
            domainid: node.domainid,
            domainkeyfield: domainkey?.field,
            domainkey: domainkey?.value,
            mainrole: node.mainrole,
            hiderole: node.hiderole,
            disabled: node.disabled,
            users: users,
            nodes: childnodes ?: []
        ]
    }

    // One tree as a plain map, ready to be written out on its own or as part of a module
    static def exportdata(tree,staff_on) {
        println "Exporting tree:" + tree
        def rootnodes = PortalTreeNode.findAllByTreeAndParentIsNull(tree).sort { a,b -> (a.lft?:0) <=> (b.lft?:0) ?: a.id <=> b.id }
        return [
            module: tree.module,
            name: tree.name,
            valid: tree.valid,
            expire: tree.expire,
            root: tree.root ? treenodekey(tree.root) : null,
            nodes: rootnodes.collect { exportnode(it,staff_on) }
        ]
    }

    // Always a list, so a one-tree file and a module's treelist.json read the same way
    static String exportjson(trees,staff_on) {
        def treearray = (trees instanceof Collection ? trees : [trees]).collect { exportdata(it,staff_on) }
        return JsonOutput.prettyPrint(JsonOutput.toJson(treearray))
    }

    // Renders incoming export data in the exporter's own key order, so a preview diff shows
    // real differences rather than however the file happened to be written.
    static String normalizejson(treearray,staff_on) {
        def norm
        norm = { inode ->
            [
                name: inode.name,
                slug: inode.slug,
                data: inode.data,
                domain: inode.domain,
                domainid: inode.domainid,
                domainkeyfield: inode.domainkeyfield,
                domainkey: inode.domainkey,
                mainrole: inode.mainrole,
                hiderole: inode.hiderole,
                disabled: inode.disabled,
                users: staff_on ? (inode.users ?: []) : null,
                nodes: (inode.nodes ?: []).collect { norm(it) }
            ]
        }
        def out = treearray.collect { itree ->
            [
                module: itree.module,
                name: itree.name,
                valid: itree.valid,
                expire: itree.expire,
                root: itree.root,
                nodes: (itree.nodes ?: []).collect { norm(it) }
            ]
        }
        return JsonOutput.prettyPrint(JsonOutput.toJson(out))
    }

    static def lookupdomainid(domain,field,value) {
        try {
            def indomain = domain.replace('csdportal.','').replace('g5portal.','')
            def trackerObjects = PortalSetting.namedefault("tracker_objects",[])
            if(indomain in trackerObjects) {
                def dtrck = trackerObjects[indomain].tokenize('.')
                def tobj = null
                if(dtrck.size()>1) {
                    tobj = PortalTracker.findByModuleAndSlug(dtrck[0].trim(),dtrck[1].trim())
                }
                else {
                    tobj = PortalTracker.findBySlug(dtrck[0].trim())
                }
                def row = tobj?.firstRow([(field):value])
                return row ? row['id'] : null
            }
            def grailsApplication = grails.util.Holders.getGrailsApplication()
            def dclass = grailsApplication.getClassForName(indomain)
            if(dclass) {
                return dclass.findWhere([(field):value])?.id
            }
        }
        catch(Exception e) {
            println "PortalTree import: domain lookup failed for " + domain + "." + field + "=" + value + ": " + e
        }
        return null
    }

    // domainid is a raw primary key of some other table, so it means nothing on another
    // server. Re-resolve it from the natural key recorded at export; only fall back to the
    // number in the file when the export could not name the object either.
    static def resolvedomainid(inode,curnode) {
        if(!inode.domain) {
            return null
        }
        if(inode.domainkey && inode.domainkeyfield) {
            def resolved = lookupdomainid(inode.domain,inode.domainkeyfield,inode.domainkey)
            if(resolved != null) {
                return resolved
            }
            // whatever this node already points at here beats an id belonging to another server
            if(curnode.id && curnode.domainid) {
                return curnode.domainid
            }
            println "PortalTree import: could not resolve " + inode.domain + " '" + inode.domainkey + "' for node '" + inode.name + "', leaving it unlinked"
            return null
        }
        // no natural key was recorded (the exporter could not load the object), so the raw id
        // is all there is to go on
        return inode.domainid
    }

    // Assignments are added, never removed: the target server's own role holders are not part
    // of what is being imported. Staff who do not exist here are reported and skipped.
    static def importnodeusers(node,iusers,stats = null) {
        if(!iusers) {
            return
        }
        iusers.each { iuser->
            def cuser = User.findByUserID(iuser.userid)
            if(!cuser) {
                println "PortalTree import: no user with user id " + iuser.userid + " for node '" + node.name + "', role " + iuser.role + " skipped"
                if(stats!=null) { stats['userskipped'] = (stats['userskipped'] ?: 0) + 1 }
                return
            }
            def curnodeuser = PortalTreeNodeUser.findByNodeAndUserAndRole(node,cuser,iuser.role)
            if(!curnodeuser) {
                curnodeuser = new PortalTreeNodeUser(node:node,user:cuser,role:iuser.role)
                if(curnodeuser.save(flush:true)) {
                    if(stats!=null) { stats['useradded'] = (stats['useradded'] ?: 0) + 1 }
                }
                else {
                    println "PortalTree import: error saving node user: " + curnodeuser.errors.allErrors
                }
            }
        }
    }

    static def importnodes(tree,parent,inodes,staff_on,stats = null) {
        if(!inodes) {
            return
        }
        // Siblings already here, keyed the way the export keys them. Same-key siblings are
        // consumed in file order so two nodes sharing a name each keep their own row.
        def existing = [:]
        def children = parent ? PortalTreeNode.findAllByParent(parent) : PortalTreeNode.findAllByTreeAndParentIsNull(tree)
        children.sort { a,b -> (a.lft?:0) <=> (b.lft?:0) ?: a.id <=> b.id }.each { child->
            def key = treenodekey(child)
            if(!existing.containsKey(key)) {
                existing[key] = []
            }
            existing[key] << child
        }
        inodes.each { inode->
            def key = (inode.slug?.toString()?.trim() ?: inode.name?.toString()?.trim()) ?: ''
            def curnode = null
            if(existing[key]) {
                curnode = existing[key].removeAt(0)
            }
            def isnew = false
            if(!curnode) {
                curnode = new PortalTreeNode(tree:tree,parent:parent)
                isnew = true
            }
            curnode.name = inode.name
            curnode.slug = inode.slug
            curnode.data = inode.data
            curnode.domain = inode.domain
            curnode.domainid = resolvedomainid(inode,curnode)
            curnode.mainrole = inode.mainrole
            curnode.hiderole = inode.hiderole
            curnode.disabled = inode.disabled
            if(!curnode.save(flush:true)) {
                println "PortalTree import: error saving node '" + inode.name + "': " + curnode.errors.allErrors
                return
            }
            if(isnew) {
                if(stats!=null) { stats['nodeadded'] = (stats['nodeadded'] ?: 0) + 1 }
                if(parent) {
                    // beforeInsert reads parent.rgt for the next sibling, and the insert just
                    // moved it in the database
                    parent.refresh()
                }
            }
            else {
                if(stats!=null) { stats['nodeupdated'] = (stats['nodeupdated'] ?: 0) + 1 }
            }
            if(staff_on) {
                importnodeusers(curnode,inode.users,stats)
            }
            importnodes(tree,curnode,inode.nodes,staff_on,stats)
        }
    }

    // Node inserts maintain lft/rgt one row at a time, which drifts once a whole tree is
    // written in one pass. Renumber the way PortalTreeNodeController.fixnodes does, but only
    // for this tree.
    static def fixnodes(tree) {
        def rootnodes = PortalTreeNode.findAllByTreeAndParentIsNull(tree).sort { a,b -> (a.lft?:0) <=> (b.lft?:0) ?: a.id <=> b.id }
        rootnodes.each { rootnode->
            rootnode.lft = 1
            rootnode.save(flush:true)
            rootnode.rgt = fixnode(rootnode,1)
            rootnode.save(flush:true)
        }
    }

    static Integer fixnode(node,Integer curleft) {
        def children = PortalTreeNode.findAllByParent(node).sort { a,b -> (a.lft?:0) <=> (b.lft?:0) ?: a.id <=> b.id }
        children.each { child->
            child.lft = curleft + 1
            child.save(flush:true)
            child.rgt = fixnode(child,curleft + 1)
            child.save(flush:true)
            curleft = child.rgt
        }
        return curleft + 1
    }

    // Create or update one tree from its exported map. stats, when given, collects counts for
    // the caller to report. Returns the tree, or null when it could not be saved.
    static def importdata(itree,staff_on,stats = null) {
        def curtree = PortalTree.findByModuleAndName(itree.module,itree.name)
        if(!curtree){
            curtree = new PortalTree(module:itree.module,name:itree.name)
            if(stats!=null) { stats['treeadded'] = (stats['treeadded'] ?: 0) + 1 }
        }
        else {
            if(stats!=null) { stats['treeupdated'] = (stats['treeupdated'] ?: 0) + 1 }
        }
        curtree.valid = parseexportdate(itree.valid)
        curtree.expire = parseexportdate(itree.expire)
        if(!curtree.save(flush:true)){
            println "PortalTree import: error saving tree '" + itree.name + "': " + curtree.errors.allErrors
            return null
        }
        importnodes(curtree,null,itree.nodes,staff_on,stats)
        if(itree.root){
            def rootnode = PortalTreeNode.findAllByTreeAndParentIsNull(curtree).find { treenodekey(it) == itree.root }
            if(rootnode && curtree.root?.id != rootnode.id){
                curtree.root = rootnode
                curtree.save(flush:true)
            }
        }
        fixnodes(curtree)
        return curtree
    }

    static def rdtree(user=null){
        def toret = PortalTree.findByName('RD_2024',[cache:true])
        if(!toret){
            toret = PortalTree.findByName('RD_2020',[cache:true])
        }
        if(!toret){
            toret = PortalTree.findByName('RD_2016',[cache:true])
        }
        if(user){
            def valid=validtrees(user)
            if(toret in valid){
                return toret
            }
            else{
                return PortalTree.findByName('RD_2020',[cache:true])
            }
        }
        return toret
    }

    static def validtrees(user){
        def tc = PortalTree.createCriteria()
        def results = tc.list {
            and {
                or{
                    lt("valid",user.treesdate)
                    isNull("valid")
                }
                or{
                    gt("expire",user.treesdate)
                    isNull("expire")
                }
                not{
                    'in'("name",PortalSetting.namedefault("not_user_trees",["ReportModule"]))
                }
            }
        }
        return results
    }

}
