package g6portal

import groovy.xml.MarkupBuilder
import org.springframework.transaction.annotation.Transactional
import grails.util.Holders

class PortalTreeNode {

    static belongsTo = [tree:PortalTree,parent:PortalTreeNode]
    static hasMany = [users:PortalTreeNodeUser,nodes:PortalTreeNode]

    static constraints = {
        name(nullable:true,bindable:true)
        slug(nullable:true,bindable:true)
        data(nullable:true,bindable:true,widget:'textarea',maxSize:500000)
        mainrole(nullable:true,bindable:true)
        hiderole(nullable:true,bindable:true)
        domain(nullable:true,bindable:true)
        domainid(nullable:true,bindable:true)
        lft(nullable:true,bindable:true)
        rgt(nullable:true,bindable:true)
        tree(nullable:true,bindable:true)
        users(nullable:true,bindable:true)
        disabled(nullable:true,bindable:true)
    }

    String name
    String domain
    String mainrole
    String hiderole
    String slug
    Integer domainid
    String data
    Integer lft
    Integer rgt
    Boolean disabled

    static mapping = {
        sort "lft"
        data type: 'text'
        cache true
    }

    static mappedBy = [nodes:"parent"]

    def survey_closed() {
        def toreturn = getdata("closed","false")
        if(toreturn=="true"){
            return true
        }
        else{
            def closingdate = getdata("closingdate","none")
            if(closingdate!="none"){
                def cdate = Date.parse('yyyy-M-d H:m',closingdate)
                if(cdate < new Date()){
                    return true    // closing date less than current date so already closed
                }
                else{
                    return false  // not yet closed
                }
            }
            else{
                return false  // closed not set and closing date not set so not closed
            }
        }
    }

    def getdata(field,defdata=null) {
        if(this.data){
            def datas = this.data.tokenize(';')
            def toreturn = null
            datas.each { ddata->
                def dtoken = ddata.tokenize('=')
                if(dtoken.size()>1){
                    if(dtoken[0].trim().toLowerCase()==field.trim().toLowerCase()){
                        toreturn = dtoken[1].trim()
                    }
                }
            }
            if(toreturn){
                return toreturn
            }
            else{
                if(defdata){
                    return defdata
                }
                else {
                    return null
                }
            }
        }
        else{
            return defdata          
        }
    }

    def getdomain(sql=null) {
        // println "\nIn getdomain of portaltreenode\n"
        if(domain && domainid){
            try {
                domain = domain.replace('csdportal.','')
                def trackerObjects = PortalSetting.namedefault("tracker_objects",[])

                // println "\nCurdomain :::" + domain + "::: "
                // println "\nTrackerobjects: " + trackerObjects

                if(domain in trackerObjects){
                    // println "\nDomain declared in trackerobjects\n"
                    def dtrck = trackerObjects[domain].tokenize('.')
                    def tobj = null
                    if(dtrck.size()>1){
                        // println "\nDefined with module\n"
                        tobj = PortalTracker.findByModuleAndSlug(dtrck[0].trim(),dtrck[1].trim())
                    }
                    else {
                        // println "\nDefined without module\n"
                        tobj = PortalTracker.findBySlug(dtrck[0].trim())
                    }
                    if(tobj) {
                        // println "\nFound the tracker\n"
                        def fdatas = tobj.getdatas(domainid,sql)
                        if(fdatas) {
                            // println "\nFound the object\n"
                            return fdatas
                        }
                        // println "\nNot Found the object\n"
                    }
                    // println "\nNot Found the tracker\n"
                }
                else {
                  // println "\nNot declared\n"
                  def grailsApplication = Holders.getGrailsApplication()
                  if(grailsApplication.getClassForName(domain)){
                      // println "\nGot class\n"
                      def dobject = grailsApplication.getClassForName(domain).get(domainid)
                      return dobject
                  }
                }
            }
            catch(Exception e){
                PortalErrorLog.record(null,null,"PortalTreeNode Domain","getdomain",e.toString(),this.id)
            }
        }
        return null
    }

    def getwhole = {
        def childrens = PortalTreeNode.createCriteria()
        return childrens.list {
            and {
                eq("tree",tree)
                le("rgt",rgt)
                ge("lft",lft)
            }
        }
    }

    def mainuser = {
        if(mainrole){
            def drole = mainrole.tokenize(',')
            def duser = PortalTreeNodeUser.createCriteria()
            return duser.list {
                and {
                    eq("node",this)
                    'in'("role",drole)
                }
            }[0]
        }
        return null
    }

    def mainusers = {
        if(mainrole){
            def drole = mainrole.tokenize(',')
            def duser = PortalTreeNodeUser.createCriteria()
            return duser.list {
                and {
                    eq("node",this)
                            'in'("role",drole)
                }
            }
        }
        return null
    }

    def checkroletext(curuser){
        def toreturn = checkrole(curuser)
        if(toreturn) {
            return toreturn['role']
        }
        else {
            return null
        }
    }

    def checkrole(curuser){
        if(curuser?.isAdmin){
            def treenodeuser = new PortalTreeNodeUser()
            treenodeuser['role'] = 'Admin'
            treenodeuser['user'] = curuser
            treenodeuser['node'] = this
            return treenodeuser
        }
        def directuser = PortalTreeNodeUser.findByNodeAndUser(this,curuser,[cache:true])
        if(directuser){
            return directuser
        }
        def toreturn = null
        def roles = PortalTreeNodeUser.findAllByUser(curuser,[cache:true])
        roles.each { crole->
            if(!toreturn){
                if(this.inmaxchildline(crole.node)){
                    toreturn = crole
                }
            }
        }
        return toreturn
    }

    def userbyrole(role) {
        def dnode = PortalTreeNodeUser.findByNodeAndRole(this,role,[cache:true])
        return dnode
    }

    def alluserbyrole(role) {
        def dnode = PortalTreeNodeUser.findAllByNodeAndRole(this,role,[cache:true])
        return dnode
    }

    def allroleundernode(role) {
        def dnodes = []
        try {
            dnodes = PortalTreeNodeUser.findAll("from PortalTreeNodeUser as tnu where tnu.role=:drole and tnu.node.tree=:dtree and tnu.node.lft<=:dlft and tnu.node.rgt>=:drgt",[drole:role,dtree:this.tree,dlft:this.lft,drgt:this.rgt],[cache:true])
        }
        catch(Exception exp) {
            println "Error searching user role: " + exp
        }
        return dnodes
    }

    def allroleundertree(role) {
        def dnodes = []
        try {
            dnodes = PortalTreeNodeUser.findAll("from PortalTreeNodeUser as tnu where tnu.role=:drole and tnu.node.tree=:dtree",[drole:role,dtree:this.tree],[cache:true])
        }
        catch(Exception exp) {
            println "Error searching user tree role: " + exp
        }
        return dnodes
    }

    def useris(role,curuser){
        def dnode = PortalTreeNodeUser.findAll("from PortalTreeNodeUser as tnu where tnu.node=:dnode and tnu.role=:drole and tnu.user=:duser",[dnode:this,drole:role,duser:curuser],[cache:true])
        return dnode
    }


    boolean inmaxparentline(othernode) {
        if(othernode.tree.id!=tree.id){
            return false
        }
        if(lft<=othernode.lft && rgt>=othernode.rgt){
            return true
        }
        else{
            return false
        }
    }

    boolean inmaxchildline(othernode) {
        if(othernode.tree.id!=tree.id){
            return false
        }        
        if(othernode.lft<=lft && othernode.rgt>=rgt){
            return true
        }
        else{
            return false
        }
    }

    boolean inparentline(othernode) {
        if(othernode.tree.id!=tree.id){
            return false
        }
        if(lft<othernode.lft && rgt>othernode.rgt){
            return true
        }
        else{
            return false
        }
    }

    boolean inchildline(othernode) {
        if(othernode?.tree?.id!=tree.id){
            return false
        }
        if(othernode?.lft<lft && othernode?.rgt>rgt){
            return true
        }
        else{
            return false
        }
    }

    def getcommonparent(commonnodes) {
        def mostleft = 9999999999
        def mostright = -999999999
        def dtree = null
        commonnodes.each { node->
            if(dtree==null){
                dtree = node.tree
            }
            else if(node.tree!=dtree){
                return null
            }
            if(node.lft<mostleft){
                mostleft = node.lft
            }
            if(node.rgt>mostright){
                mostright = node.rgt
            }
        }
        def parent = PortalTreeNode.find("from PortalTreeNode as tn where tn.lft<:mlft and tn.rgt>:mrgt and tn.tree=:ctree order by tn.lft desc",[mlft:mostleft,mrgt:mostright,ctree:dtree],[cache:true])
    }

    def getpath = {
        def path = PortalTreeNode.findAll("from PortalTreeNode as tn where tn.lft<:curlft and tn.rgt>:currgt and tn.tree=:ctree order by tn.lft asc",[curlft:lft,currgt:rgt,ctree:tree],[cache:true])
        return path
    }

    def getfullpath = {
        def toret = this.getpath().join(' / ')
        toret += ' / ' + this
        return toret
    }


    def beforeInsert = {
        if(parent){
            lft = parent.rgt
            tree = parent.tree
        }
        else{
            lft = 0
        }
        rgt = lft + 1
        PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+2 where lft>=:curlft and tree=:ctree",[curlft:lft,ctree:tree])
        PortalTreeNode.executeUpdate("update PortalTreeNode set rgt=rgt+2 where rgt>=:curlft and tree=:ctree",[curlft:lft,ctree:tree])
    }

    def beforeDelete  = {        
        def diff = rgt - lft + 1
        PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft-:diff where lft>=:currgt and tree=:ctree",[diff:diff,currgt:rgt,ctree:tree])
        PortalTreeNode.executeUpdate("update PortalTreeNode set rgt=rgt-:diff where rgt>=:currgt and tree=:ctree",[diff:diff,currgt:rgt,ctree:tree])
	if(parent){
		parent.rgt = parent.lft + 1
	}
    }

    String toString(){
        if(domain && domain.size()>10){
            domain[10..-1] + ": " + name
        }
        else{
            name
        }
    }

    def generateXML = {
        def writer = new StringWriter()
        MarkupBuilder xml = new MarkupBuilder(writer)
        toXml(this, xml)
        String str = writer.toString()
    }

    private void toXml(PortalTreeNode tn, def xml) {
        xml.node(id: tn.id,name: tn.name, slug: tn.slug, data: tn.data, domain: tn.domain, domainid: tn.domainid, mainrole: tn.mainrole, hiderole: tn.hiderole) {
            tn.users.each { role->
                xml.user(id:role.id,userid:role.user.userID,role:role.role)
            }
            tn.nodes.each {
                toXml(it, xml)
            }
        }
    }

    def generatefromxml(xml) {
        def nodes = new XmlSlurper().parseText(xml)
        nodes.each { node->
                this.childgenerate(node)
        }
    }

    def childgenerate(node) {
        node.node.each { cnode->
            def newnode = null
            def domainid = cnode.@domainid.toString().isNumber()?cnode.@domainid.toInteger():null
            def name = cnode.@name.toString()
            if(cnode.@domain.toString()=='csdportal.Branch'){
                def branch = Branch.findByBranch_Code(cnode.@name.toString(),[cache:true])
                // println 'looking for branch code:' + cnode.@name.toString() + ':'
                if(branch){
                    // println 'found branch'
                    domainid=branch.id
                    name=branch.name
                }
            }
            if(cnode.@domain.toString()=='csdportal.Area'){
                def arm = Area.findByNumber(cnode.@name.toString(),[cache:true])
                // println 'looking for arm number:' + cnode.@name.toString() + ':'
                if(arm){
                    // println 'found arm'
                    domainid=arm.id
                    name=arm.number
                }
            }
            if(cnode.@domain.toString()=='csdportal.Region'){
                def region = Region.findByNumber(cnode.@name.toString(),[cache:true])
                // println 'looking for region number:' + cnode.@name.toString() + ':'
                if(region){
                    // println 'found region'
                    domainid=region.id
                    name=region.number
                }
            }
            if(node.@id.toString().isNumber()){
                newnode = PortalTreeNode.get(node.@id.toString().toInteger())
                newnode.name = name
                newnode.data = cnode.@data.toString()
                newnode.domain = cnode.@domain.toString()
                newnode.domainid = domainid
                newnode.slug = cnode.@slug.toString()
                newnode.mainrole = cnode.@mainrole.toString()
                newnode.hiderole = cnode.@hiderole.toString()
            }
            else{
                newnode = new PortalTreeNode(parent:this,name:name,data:cnode.@data.toString(),domain:cnode.@domain.toString(),mainrole:cnode.@mainrole.toString(),hiderole:cnode.@hiderole.toString(),slug:cnode.@slug.toString(),domainid:domainid)
            }
            if(newnode.save()){
                cnode.user.each { user->
                    def newuser = User.findByUserID(user.@userid.toString(),[cache:true])
                    if(newuser){
                        def newrole = null
                        if(user.@id.toString().isNumber()){
                            newrole = PortalTreeNodeUser.get(user.@id.toInteger())
                            newrole.user = newuser
                            newrole.role = user.@role.toString()
                        }
                        else{
                            newrole = new PortalTreeNodeUser(user:newuser,node:newnode,role:user.@role.toString())
                        }
                        if(!newrole.save()){
                            println "Error creating cnode:" + newrole.errors.allErrors
                        }
                    }
                }
                refresh()
                newnode.childgenerate(cnode)
            }
            else{
                println "Error creating node:" + newnode.errors.allErrors
            }
        }
    }
}
