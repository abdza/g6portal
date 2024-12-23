package g6portal

import org.apache.directory.ldap.client.api.*
import org.apache.directory.api.ldap.model.message.*
import at.favre.lib.crypto.bcrypt.BCrypt

class User {

    static hasMany = [nodes:PortalTreeNodeUser]

    static mapping = {
        version false
        address type: 'text'
        cache true
        table 'portal_user'
    }

    static constraints = {
        userID(unique:true)
        name()
        email(email:true)
        role(nullable:true)
        roletargetid(nullable:true)
        lastlogin(nullable:true)
        nodes(nullable:true)
        profilepic(nullable:true)
        directline(nullable:true)
        handphone(nullable:true)
        company_handphone(nullable:true)
        resetexception(nullable:true)
        secretquestion(nullable:true)
        secretanswer(nullable:true)
        emergency_contact(nullable:true)
        emergency_name(nullable:true)
        address(nullable:true,widget:'textarea')
        date_joined(nullable:true)
        lanid(nullable:true)
        treesdate(nullable:true)
        lastUpdated(nullable:true)
        lastInfoUpdate(nullable:true)
        lastReminder(nullable:true)
        lanidexception(nullable:true)
        password(nullable:true,password:true)
        password5(nullable:true,password:true)
        state(nullable:true)
    }

    String userID
    String name
    String email
    String password
    String address
    String state
    Boolean isAdmin = false
    Boolean isActive = true
    Boolean resetPassword = false
    Boolean lanidexception = false
    String role
    Integer roletargetid
    Date lastlogin
    Date date_joined
    FileLink profilepic
    String directline
    String handphone
    String company_handphone
    Boolean resetexception
    String secretquestion
    String secretanswer
    String emergency_contact
    String emergency_name
    String lanid
    Date treesdate
    Date lastUpdated
    Date lastInfoUpdate
    Date lastReminder
    String password5

    String toString(){
        name
    }

    String targetname(){
        def nodeuser=PortalTreeNodeUser.get(roletargetid)
        if(nodeuser?.node){
            return nodeuser.node.getdomain()
        }
        else{
            return "No role"
        }
    }

    def switchable() {
        if(isAdmin) {
            return true
        }
        else return userID in PortalSetting.namedefault('portal.switchusers',[])
    }

    def hashPassword(String tohash) {
        this.password = BCrypt.withDefaults().hashToString(12, tohash.toCharArray())
    }

    def createHash(String tohash) {
        return BCrypt.withDefaults().hashToString(12, tohash.toCharArray())
    }

    def verifyPassword(String toverify) {
        def toreturn = BCrypt.verifyer().verify(toverify.toCharArray(), this.password)
        return toreturn.verified
    }

    def updatelanid() {
        def retmsg
        def gotupdate = false
        try{
            LdapConnection connection = new LdapNetworkConnection(PortalSetting.namedefault("adserver","10.0.0.1"), PortalSetting.namedefault("adport",389) )
            connection.bind(PortalSetting.namedefault("addn","defaultdn"),PortalSetting.namedefault("adpassword","defaultpass"))
            def usersearch = "(employeeID=" + this.userID + ")"
            def cursor = connection.search(PortalSetting.namedefault("topdn","defaulttopdn"),usersearch,SearchScope.SUBTREE,"*")
            while(cursor.next()){
                try{
                    print "Looking for:" + usersearch
                    def entry = cursor.get()
                    print "User LAN ID is :" + entry.sAMAccountName.toString()[16..-1]
                    this.lanid = entry.sAMAccountName.toString()[16..-1]
                    this.save(flush:true)
                    retmsg = "Updated user LAN ID to " + entry.sAMAccountName.toString()[16..-1]
                    gotupdate = true
                }
                catch(Exception exp){
                    println "Can't get info for user:" + this + " exp:" + exp
                }
            }
            if(!gotupdate){
                retmsg = "Fail to update the LAN ID for " + this
            }
        }
        catch(Exception exp){
            println "Error connecting to ldap server:" + exp.toString()
            ErrorLog.record(null,this,"user","authenticate","Error connecting to ldap server:" + exp.toString())
        }
        return gotupdate
    }

    def treeroles(params){
        def roles = []

        if(this.isAdmin){
            roles << ['role':'Admin','roletargetid':null]
        }
        def droles = PortalTreeNodeUser.createCriteria()
        def results = droles {
            and{
                eq("user",this)
            }
            order("node","desc")
        }
        def vtrees = PortalTree.validtrees(this)
        results.each { cr->
            if(!(cr.node.tree.id in vtrees*.id)){
                results -= cr
            }
        }
        def exproles = exceptionalrole()
        if(exproles){
            results += exproles
        }
        return results
    }

    static carinama(String name){
        def toreturn=User.findByNameLike("%${name}%")
        if(toreturn==null){
            // get rid of comments which most of the time begin with (
            if(name.contains('(')){
                name = name.substring(0,name.indexOf('(')).trim()
                toreturn=User.findByNameLike("%${name}%")
            }
        }
        if(toreturn==null){
            // get rid of these usually misnamed things
            name=name.replaceAll("A/L|bin|Bin|Binti|binti|hj|Haji|Hj|haji",'%')
            toreturn=User.findByNameLike("%${name}%")
        }
        if(toreturn==null){
            // sometimes the name got additional words in between, search for those
            name=name.replace(' ','%')
            toreturn=User.findByNameLike("%${name}%")
        }
        if(toreturn==null){
            // start to eliminate words from the right side
            def tokens=name.tokenize('%')
            while(toreturn==null && tokens.size()>1){                
                tokens=tokens[0..-2]
                toreturn=User.findByNameLike("%" + tokens.join('%') + "%")
            }
            if(toreturn==null){
                toreturn=User.findByNameLike("%" + tokens[0] + "%")
            }
        }
        if(toreturn==null){
            // start to eliminate words from the left pulak
            def tokens=name.tokenize('%')
            while(toreturn==null && tokens.size()>1){                
                tokens=tokens[1..-1]
                toreturn=User.findByNameLike("%" + tokens.join('%') + "%")
            }
            if(toreturn==null){
                toreturn=User.findByNameLike("%" + tokens[0] + "%")
            }
        }
        return toreturn
    }
    
    def currentrole(tree=null) {
        def toret = PortalTreeNodeUser.get(roletargetid)
        if(toret){
            if(tree) {
                if(tree == toret.node.tree) {
                    return toret
                }
                else {
                    toret = PortalTreeNodeUser.findAll([cache:true]){
                        user == this
                        node.tree == tree
                    }
                    if(toret){
                        return toret[0]
                    }
                    else{
                        return null
                    }
                }
            }
            else {
              return toret
            }
        }
        else{
            if(tree) {
              toret = PortalTreeNodeUser.findAll([cache:true]){
                  user == this
                  node.tree == tree
              }
            }
            else {
              toret = PortalTreeNodeUser.findAll([cache:true]){
                  user == this
              }
            }
            if(toret){
                return toret[0]
            }
            else{
                return null
            }
        }
    }
    
    def treerole(fullname=true) {    
        def tree = PortalTree.default_tree(this)
        def toret = PortalTreeNodeUser.findAll([cache:true]){
            node.tree == tree
            user == this
        }
        if(toret){
            if(fullname){
                if(fullname == 'justrole'){
                    return toret*.role
                }
                else{
                    if(toret.size()>1){
                        return toret[0].role + ' of ' + toret[0].node + ' - and ' + (toret.size()-1) + ' more roles'
                    }
                    else{
                        return toret[0].role + ' of ' + toret[0].node
                    }
                }
            }
            else{
                return toret
            }
        }
        else{
            return null
        }        
    }

    def exceptionalrole(role=null) {
        def tree = PortalTree.findByName('Exceptional Role',[cache:true])        
        def toret = null
        if(tree){
            if(role){
                toret = PortalTreeNodeUser.findAll([cache:true]){
                    node.tree == tree
                    role == role
                    user == this
                }
            }
            else{
                toret = PortalTreeNodeUser.findAll([cache:true]){
                    node.tree == tree
                    user == this
                }
            }
            if(toret){
                if(toret.size()==1){
                    if(role){
                        return toret[0].node
                    }
                    else{
                        return toret[0]
                    }
                }
                else{
                    if(role){
                        def intoret = null
                        toret.each { ct->
                            if(ct == currentrole()){
                                intoret = ct.node
                            }
                        }
                        if(intoret){
                            return intoret
                        }
                        else{
                            return toret*.node
                        }
                    }
                    else{
                        return toret
                    }
                }
            }
            else{
                return null
            }
        }
        else{
            return null
        }
    }

    def moduleroles() {
        def urole = UserRole.findAllByUser(this,[cache:true])
        return urole
    }
    
    def modulerole(module) {
        def urole = UserRole.findAllByUserAndModule(this,module,[cache:true])
        def toret = urole*.role
        if(PortalSetting.namedefault('enablesuperuser',false) && this.isAdmin) {
            toret += ['Admin']
        }
        else if('Developer' in toret) {
            toret += ['Admin']
        }
        return toret
    }

    def adminlist() {
        def urole = UserRole.findAllByUserAndRoleInList(this,['Admin','Developer'],[cache:true])
        def toret = urole*.module
        if(toret.size()==0 && this.isAdmin) {
            toret << 'portal'
        }
        return toret.unique()
    }

    def developerlist() {
        def urole = UserRole.findAllByUserAndRole(this,'Developer',[cache:true])
        def toret = urole*.module
        return toret.unique()
    }

}
