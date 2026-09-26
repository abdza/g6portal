package g6portal

import org.apache.directory.ldap.client.api.*
import org.apache.directory.api.ldap.model.message.*
import at.favre.lib.crypto.bcrypt.BCrypt
import static grails.util.Holders.config

class User {

    static hasMany = [nodes:PortalTreeNodeUser]

    // Transient cache for modulerole results — keyed by module, persists for session lifetime
    static transients = ['_modulerole_cache']
    Map _modulerole_cache = [:]

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
        roletargetid(nullable:true,bindable:false)
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
        state(nullable:true)
        date_joined(nullable:true)
        lanid(nullable:true)
        activeSessionId(nullable:true,bindable:false)
        activeSessionUpdated(nullable:true,bindable:false)
        treesdate(nullable:true)
        lastUpdated(nullable:true)
        lastInfoUpdate(nullable:true)
        lastReminder(nullable:true)
        lanidexception(nullable:true)
        // bindable:false: these are never safe to take straight from request parameters.
        // user.register / user.save are in the default portal.whitelist, so an anonymous POST
        // binds into a brand new User - isAdmin would have made a system administrator, and
        // roletargetid grants whatever org-tree role that row carries (SecurityInterceptor
        // checks currentrole() when authorizing pages). The screen that legitimately sets
        // isAdmin/roletargetid (user/edit) assigns them explicitly in UserController.update
        // after checking the acting user is a superuser. The password always goes through
        // hashPassword() from params, never bound raw.
        isAdmin(bindable:false)
        password(nullable:true,password:true,bindable:false)
        password5(nullable:true,password:true,bindable:false)
        profile_id(nullable:true)
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
    String  activeSessionId
    Date    activeSessionUpdated
    Date treesdate
    Date lastUpdated
    Date lastInfoUpdate
    Date lastReminder
    String password5
    Integer profile_id

    String toString(){
        name
    }

    // Sentinel for caching null/false returns in request-scoped cache
    static final _CACHE_NULL = new Object()

    // Helper: compute once per request per key, handles null returns via sentinel
    private def _reqCache(String key, Closure compute) {
        try {
            def req = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes().request
            def cached = req.getAttribute(key)
            if (cached != null) return cached.is(_CACHE_NULL) ? null : cached
            def result = compute()
            req.setAttribute(key, result != null ? result : _CACHE_NULL)
            return result
        } catch(e) {
            return compute()
        }
    }

    String targetname(){
        return _reqCache("_tn_${id}") {
        def nodeuser=PortalTreeNodeUser.get(roletargetid)
        if(nodeuser?.node){
            return nodeuser.node.getdomain()
        }
        else{
            return "No role"
        }
        }
    }

    def switchable() {
        if(isAdmin) {
            return true
        }
        else return userID in PortalSetting.namedefault('portal.switchusers',[])
    }

    def load_profile() {
        if(this.profile_id && config.server?.user_profile){
            def tokens = config.server?.user_profile?.tokenize('.')
            def profile_module = tokens[0]
            def profile_slug = 'user'
            if(tokens.size()>1) {
                profile_slug = tokens[1]
            }
            def rowquery = [:]
            rowquery['id'] = this.profile_id
            def profile = PortalTracker.load_datas(profile_module,profile_slug,rowquery)
            return profile
        }
        return null
    }

    def hashPassword(String tohash) {
        this.password = BCrypt.withDefaults().hashToString(12, tohash.toCharArray())
    }

    def createHash(String tohash) {
        return BCrypt.withDefaults().hashToString(12, tohash.toCharArray())
    }

    def verifyPassword(String toverify) {
        if(!toverify) {
            return false
        }
        // No stored password is not a free pass. An account that never set one, or had it
        // cleared in the database, must not accept an arbitrary password - and BCrypt throws
        // rather than returning false when handed a null hash.
        if(!this.password) {
            return false
        }
        def toreturn = BCrypt.verifyer().verify(toverify.toCharArray(), this.password)
        return toreturn.verified
    }

    // Not called anywhere in this codebase - UserController.updatelanid (wired to the
    // "Update LAN ID" button) is the code path actually in use. Kept as a reusable
    // domain-level equivalent and brought up to the same standard, rather than left with
    // defaults that would misfire the moment something did call it: it pointed at a
    // hardcoded host on plaintext port 389 while every other AD caller here defaults to
    // 636 with SSL, and its error path referenced ErrorLog, which does not exist in this
    // codebase and would have thrown NoClassDefFoundError on top of the original failure.
    def updatelanid() {
        def retmsg
        def gotupdate = false
        println "User.updatelanid for " + this.userID + ": attempting primary domain"
        def connection = null
        try{
            def adserver = PortalSetting.namedefault("adserver","defaultserver")
            def adport = PortalSetting.namedefault("adport",636)
            def adsecure = PortalSetting.namedefault("adsecure",true)
            def addn = PortalSetting.namedefault("addn","defaultdn")
            def topdn = PortalSetting.namedefault("topdn","defaulttopdn")
            println "User.updatelanid (primary): connecting to " + adserver + ":" + adport + " (ssl=" + adsecure + ")"
            connection = new LdapNetworkConnection(adserver, adport, adsecure)
            connection.bind(addn, PortalSetting.namedefault("adpassword","defaultpass"))
            println "User.updatelanid (primary): bound as service account " + addn
            def usersearch = "(employeeID=" + this.userID + ")"
            println "User.updatelanid (primary): searching base " + topdn + " filter " + usersearch
            def cursor = connection.search(topdn,usersearch,SearchScope.SUBTREE,"*")
            def found = false
            while(cursor.next()){
                found = true
                try{
                    def entry = cursor.get()
                    println "User.updatelanid (primary): found entry " + entry.dn + ", sAMAccountName=" + entry.sAMAccountName.toString()
                    def newlanid = entry.sAMAccountName.toString()[16..-1]
                    User.withTransaction { tstatus ->
                        this.lanid = newlanid
                        this.save(flush:true)
                    }
                    retmsg = "Updated user LAN ID to " + newlanid
                    println "User.updatelanid (primary): saved lanid=" + newlanid + " for " + this.userID
                    gotupdate = true
                }
                catch(Exception exp){
                    println "User.updatelanid (primary): could not read entry for " + this.userID + " - " + exp.class.simpleName + ": " + exp.message
                }
            }
            if(!found) {
                println "User.updatelanid (primary): no entry found for filter " + usersearch
            }
            if(!gotupdate){
                retmsg = "Fail to update the LAN ID for " + this
            }
        }
        catch(Exception exp){
            println "User.updatelanid (primary): error connecting/searching for " + this.userID + " - " + exp.class.name + ": " + exp.message
            exp.printStackTrace()
            PortalErrorLog.record(null,this,"user","updatelanid","Error connecting to ldap server:" + PortalErrorLog.describe(exp))
        }
        finally {
            if(connection) {
                try { connection.close() } catch(Exception ce) { println "User.updatelanid (primary): error closing connection - " + ce.message }
            }
        }
        println "User.updatelanid for " + this.userID + ": " + (gotupdate ? "succeeded, lanid=" + this.lanid : "failed")
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
        return _reqCache("_cr_${id}_${tree?.id}") {
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
        return _reqCache("_mrs_${id}") {
        def urole = UserRole.findAllByUser(this,[cache:true])
        return urole
        }
    }

    def modulerole(module) {
        if (_modulerole_cache == null) _modulerole_cache = [:]
        if (_modulerole_cache.containsKey(module)) {
            return _modulerole_cache[module]
        }
        def urole = UserRole.findAllByUserAndModule(this,module,[cache:true])
        def toret = urole*.role
        if(PortalSetting.namedefault('enablesuperuser',false) && this.isAdmin) {
            toret += ['Admin']
        }
        else if('Developer' in toret) {
            toret += ['Admin']
        }
        _modulerole_cache[module] = toret
        return toret
    }

    def clearModuleroleCache() {
        _modulerole_cache = [:]
    }

    def adminlist() {
        return _reqCache("_al_${id}") {
        def urole = UserRole.findAllByUserAndRoleInList(this,['Admin','Developer'],[cache:true])
        def toret = urole*.module
        if(toret.size()==0 && this.isAdmin) {
            toret << 'portal'
        }
        return toret.unique()
        }
    }

    def developerlist() {
        return _reqCache("_dl_${id}") {
        def urole = UserRole.findAllByUserAndRole(this,'Developer',[cache:true])
        def toret = urole*.module
        return toret.unique()
        }
    }


    static final int CONCURRENT_SESSION_TIMEOUT_MINUTES_DEFAULT = 15
    static final int CONCURRENT_SESSION_HEARTBEAT_MINUTES = 5

    // Override via `server.concurrent_session_timeout_minutes` in application.yml
    static int concurrentSessionTimeoutMinutes() {
        try {
            def v = config.server?.concurrent_session_timeout_minutes
            return v ? (v as int) : CONCURRENT_SESSION_TIMEOUT_MINUTES_DEFAULT
        }
        catch(Exception e){
            return CONCURRENT_SESSION_TIMEOUT_MINUTES_DEFAULT
        }
    }

    def concurrentSessionStaleCutoff() {
        new Date(System.currentTimeMillis() - (concurrentSessionTimeoutMinutes() * 60 * 1000))
    }

    // OPT-IN in g6: enforcement is OFF unless `server.enforce_single_session: true`
    // is set in application.yml. Ported from g6portal, where the default is the
    // opposite; left permissive here so migrating the code changes no behaviour
    // until it is deliberately switched on.
    static boolean allowConcurrentSessions() {
        config.server?.enforce_single_session != true
    }

    // Bulk HQL update rather than instance.save() - `this` may be the cross-request
    // cached session.curuser, effectively a detached entity; re-saving it drags in
    // lazy association checks (e.g. profilepic) that blow up with no Hibernate
    // session backing them. A targeted update sidesteps that entirely.
    private def _persistSessionClaim(sessionId, timestamp) {
        User.withTransaction {
            User.executeUpdate("update User u set u.activeSessionId=:sid, u.activeSessionUpdated=:upd where u.id=:id",
                [sid: sessionId, upd: timestamp, id: id])
        }
    }

    // Called only at real login time (authenticate/connexion).
    //
    // A FRESH LOGIN ALWAYS WINS (changed 2026-09-03, on the business's call). It used to
    // refuse when another still-fresh session held the account, which locked people out of
    // their own account for 10 to 15 minutes in the most ordinary situation there is:
    // closing the browser. Closing a browser tells the server nothing, so the lock stayed
    // until activeSessionUpdated went stale - the heartbeat writes at most every
    // CONCURRENT_SESSION_HEARTBEAT_MINUTES (5) and the cutoff is 15, so the wait was
    // 10-15 minutes. A server restart was worse: every session in the building is gone but
    // every lock row survives, so nobody could log in until the cutoff passed.
    //
    // One session at a time is still enforced, by validateSession(): the superseded session
    // fails its next request and is logged out with "You have been logged out because this
    // account was logged in from another browser or device." What is gone is only the
    // REFUSAL, which protected nothing - whoever is logging in has just proved they hold the
    // credentials, and could have taken the account anyway by waiting for the cutoff.
    //
    // Still returns a boolean, and callers still check it: reverting is a one-line change,
    // and their refusal branch stays written and correct for the day it is wanted back.
    def claimSession(sessionId) {
        if(allowConcurrentSessions()){
            return true
        }
        def now = new Date()
        _persistSessionClaim(sessionId, now)
        activeSessionId = sessionId
        activeSessionUpdated = now
        return true
    }

    // Called on every authenticated request (SecurityInterceptor). Returns
    // false if this session has been superseded by a fresher login elsewhere.
    def validateSession(sessionId) {
        if(allowConcurrentSessions()){
            return true
        }
        // The lock MUST be read from the database, never off this instance. SecurityInterceptor
        // passes session.curuser - a User cached in the caller's OWN http session, whose
        // activeSessionId is frozen at whatever it held when that session logged in. Compared
        // against that, every session believes it still owns the lock, so a superseded session
        // was never evicted and this method could only ever return true. Found 2026-09-03 while
        // making a fresh login take the lock instead of being refused: the refusal had been
        // doing all of the enforcing, and the eviction path below had never actually run.
        // One scalar read on the primary key per authenticated request is the cost of the lock
        // meaning anything at all.
        def liveSessionId = activeSessionId
        def liveUpdated = activeSessionUpdated
        try {
            def row = User.executeQuery(
                "select u.activeSessionId, u.activeSessionUpdated from User u where u.id=:id",
                [id: id])[0]
            if(row != null) {
                liveSessionId = row[0]
                liveUpdated = row[1]
            }
        }
        catch(Exception e) {
            // Unreadable lock must not lock anybody out; fall back to what this instance holds.
        }
        def takeover = {
            def now = new Date()
            _persistSessionClaim(sessionId, now)
            activeSessionId = sessionId
            activeSessionUpdated = now
        }
        if(!liveSessionId || liveSessionId == sessionId){
            // heartbeat, throttled so we don't write on literally every request
            if(!liveUpdated || liveUpdated.before(new Date(System.currentTimeMillis() - CONCURRENT_SESSION_HEARTBEAT_MINUTES*60*1000))){
                takeover()
            }
            else {
                // keep the cached instance honest even when we do not write
                activeSessionId = liveSessionId
                activeSessionUpdated = liveUpdated
            }
            return true
        }
        if(!liveUpdated?.after(concurrentSessionStaleCutoff())){
            takeover()
            return true
        }
        return false
    }

    // Called at logout — only releases the lock if this session actually owns it.
    def releaseSession(sessionId) {
        if(activeSessionId == sessionId){
            _persistSessionClaim(null, null)
            activeSessionId = null
            activeSessionUpdated = null
        }
    }

}
