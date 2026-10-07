package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

// Explicit import even though Credentials is same-package: without it, a dirty/incremental
// build that compiles this controller before Credentials is on the classpath silently binds
// Credentials.check as a dynamic property, shipping a runtime MissingPropertyException on
// every login. With the import, that situation fails loudly at compile time instead.
import g6portal.Credentials

import org.apache.directory.ldap.client.api.*
import org.apache.directory.api.ldap.model.message.*
import static grails.util.Holders.config

class UserController {

    UserService userService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", clearsession: "POST"]

    def completelist = {
        // select2 sends no q when a picker first opens; without the ?: '' that threw and the picker opened on an error
        def dparam = '%' + (params.q?.trim() ?: '').replace(' ','%') + '%'
        def cusers = []
        if(params.value){
          cusers << User.get(params.value)
        }
        if(params.role){
            def usernodes = PortalTreeNodeUser.createCriteria().list() {
                'in'('role',params.role.decodeURL().tokenize(','))
                user {
                    or{
                        'ilike'('name',dparam)
                        'ilike'('userID',dparam)
                        'ilike'('email',dparam)
                        'ilike'('lanid',dparam)
                    }
                }
            }
            if(usernodes){
                cusers += (usernodes*.user).unique()
            }
            else{
                cusers = null
            }
        }
        else{
            cusers += User.createCriteria().list() {
                or{
                    'ilike'('name',dparam)
                    'ilike'('userID',dparam)
                    'ilike'('email',dparam)
                    'ilike'('lanid',dparam)
                }
                maxResults(9)
            }
        }
        cusers = cusers.unique()
        return render(contentType: "application/json"){
            users cusers.collect{ ['id':it.id,'value':it.id,'name':it.name] }
        }
    }

    def activelist = {
        // select2 sends no q when a picker first opens; without the ?: '' that threw and the picker opened on an error
        def dparam = '%' + (params.q?.trim() ?: '').replace(' ','%') + '%'
        def dusers = null
        if(params.role){
            def usernodes = PortalTreeNodeUser.createCriteria().list() {
                'in'('role',params.role.decodeURL().tokenize(','))
                user {
                    or{
                        'ilike'('name',dparam)
                        'ilike'('userID',dparam)
                        'ilike'('email',dparam)
                        'ilike'('lanid',dparam)
                    }
                    'eq'('isActive',true)
                }
            }
            if(usernodes){
                dusers = (usernodes*.user).unique()
            }
            else{
                dusers = null
            }
        }
        else{
            dusers = User.createCriteria().list() {
                or{
                    'ilike'('name',dparam)
                    'ilike'('userID',dparam)
                    'ilike'('email',dparam)
                    'ilike'('lanid',dparam)
                }
                'eq'('isActive',true)
                maxResults(20)
            }
        }
        return render(contentType: "application/json"){
            users dusers.collect{ ['id':it.id,'value':it.id,'name':it.name] }
        }
    }


    def my_profile() {
        def curuser = User.get(session.userid)
        [user:curuser]
    }

    def my_profile_save(User user) {
        if (user == null) {
            notFound()
            return
        }

        try {
            user.lastInfoUpdate = new Date()
            userService.save(user)
        } catch (ValidationException e) {
            respond user.errors, view:'my_profile'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = "Your profile has been updated"
                redirect action:"my_profile", method:"GET"
            }
            '*' { respond user, [status: OK] }
        }
    }

    def api_list() {
        def usersdata = null
        def dparam = [max:params.max?:10]
        if(params.q) {
            def query = '%' + params.q?.trim().replace(' ','%') + '%'
            // Pickers only offer active accounts - an inactive one can no longer log in to act
            // on whatever it would be assigned.
            usersdata = userService.list_query(true,query,dparam)
        }
        else {
            usersdata = userService.listByIsActive(true,dparam)
        }
        def ul = []
        if(params.id) {
            def curdata = userService.get(params.id)
            if(curdata) {
                ul << [ 'id':curdata.id,'name':curdata.name,'userid':curdata.userID ]
            }
        }
        usersdata.each {
            ul << [ 'id':it.id,'name':it.name,'userid':it.userID ]
        }
        return render(contentType: "application/json") {
            users ul
        }
    }

    def index(Integer max) {
        // def curuser = User.get(session.userid)
        def curuser = session.curuser
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        def thelist = null
        def userCount = null
        def rolelist = PortalTreeNodeUser.executeQuery("select distinct role from PortalTreeNodeUser where role is not null and role not like 'roleadmin%'").sort()
        // 'all' means no isActive filtering at all; '0' means inactive-only;
        // absent or '1' defaults to active-only.
        def activeFilter = params.is_active=='0' ? false : true
        if(params.q) {
            def query = '%' + params.q?.trim().replace(' ','%') + '%'
            if(params.is_active && params.is_active=='all'){
                if(params.rolefilter && params.rolefilter!='All') {
                    thelist = userService.list(params.rolefilter,query,dparam)
                    userCount = userService.count(params.rolefilter,query)
                }
                else {
                    thelist = userService.list(query,dparam)
                    userCount = userService.count(query)
                }
            }
            else {
                if(params.rolefilter && params.rolefilter!='All') {
                    thelist = userService.list(params.rolefilter,activeFilter,query,dparam)
                    userCount = userService.count(params.rolefilter,activeFilter,query)
                }
                else {
                    thelist = userService.list(activeFilter,query,dparam)
                    userCount = userService.count(activeFilter,query)
                }
            }
            respond thelist, model:[curuser:curuser, userCount: userCount, params:params, rolelist:rolelist]
        }
        else {
            if(params.is_active && params.is_active=='all'){
                if(params.rolefilter && params.rolefilter!='All') {
                    thelist = userService.listByRole(params.rolefilter,dparam)
                    userCount = userService.countByRole(params.rolefilter)
                }
                else {
                    thelist = userService.list(dparam)
                    userCount = userService.count()
                }
            }
            else {
                if(params.rolefilter && params.rolefilter!='All') {
                    thelist = userService.listByIsActiveAndRole(activeFilter,params.rolefilter,dparam)
                    userCount = userService.countByIsActiveAndRole(activeFilter,params.rolefilter)
                }
                else {
                    thelist = userService.listByIsActive(activeFilter,dparam)
                    userCount = userService.countByIsActive(activeFilter)
                }
            }
            respond thelist, model:[curuser:curuser, userCount: userCount, params:params, rolelist:rolelist]
        }
    }

    def show(Long id) {
        // def curuser = User.get(session.userid)
        respond userService.get(id),model:[curuser:session.curuser]
    }

    def create() {
        respond new User(params)
    }

    def register() {
        respond new User(params)
    }

    def save(User user) {
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
            if (user == null) {
                notFound()
                return
            }
            if(params.password2 && params.password!=params.password2) {
                flash.message = "The repeated password is not the same"
                redirect(controller:"user",action:"register")
                return
            }

            // Decided BEFORE the password is replaced by its hash on the next line.
            // The two checks below used to ask `params.password == params.password2`,
            // which by then compares a bcrypt hash to the plaintext repeat and so is
            // never true: every registration fell past the "please login" branch into
            // the scaffold's redirect-to-user-detail, which an anonymous registrant
            // cannot see. They are already known to match - the guard above returns if
            // they do not - so all this needs to know is whether the register form was
            // the way in, and password2 only exists on that form.
            def viaRegisterForm = (params.password2 ? true : false)

            try {
                params.password = user.hashPassword(params.password)
                userService.save(user)
            } catch (ValidationException e) {
                println "Errors registering user: " + e
                respond user.errors, view:'create'
                return
            }

            if(user) {
                if(viaRegisterForm) {
                    flash.message = 'User registered. Please login to continue'
                    redirect(controller:"user",action:"login")
                    return 
                }
                request.withFormat {
                    form multipartForm {
                        flash.message = message(code: 'default.created.message', args: [message(code: 'user.label', default: 'User'), user.id])
                        redirect user
                    }
                    '*' { respond user, [status: CREATED] }
                }
            }
            else {
                if(viaRegisterForm) {
                  flash.message = 'User registered. Please login to continue'
                }
                redirect(controller:"user",action:"login")
            }
        }
    }

    def change_password() {
        def user = session.curuser
        if (!user) {
            flash.message = "Please login to change your password"
            flash.messageType = "warning"
            redirect(controller: "user", action: "login")
            return
        }
        ['user':user]
    }

    def update_password() {
        def user = User.get(session.userid)
        if (!user) {
            flash.message = "Please login to change your password"
            flash.messageType = "warning"
            redirect(controller: "user", action: "login")
            return
        }

        // Verify the current password
        if (!user.verifyPassword(params.currentPassword)) {
            flash.message = "Current password is incorrect"
            flash.messageType = "danger"
            redirect(action: "change_password")
            return
        }

        // Verify password confirmation
        if (params.newPassword != params.confirmPassword) {
            flash.message = "New passwords do not match"
            flash.messageType = "danger"
            redirect(action: "change_password")
            return
        }

        // Check password strength
        def passwordPattern = ~/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])[A-Za-z\d\W]{8,}$/
        if (!(params.newPassword ==~ passwordPattern)) {
            flash.message = "Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character"
            flash.messageType = "danger"
            redirect(action: "change_password")
            return
        }

        try {
            params.password = user.hashPassword(params.newPassword)
            User.withTransaction { sqltrans->
                user.lastInfoUpdate = new Date()
                user.save(flush:true)
            }
            flash.message = "Password successfully changed"
            flash.messageType = "success"
            redirect(controller: "portalPage", action: "home")
        } catch (Exception e) {
            log.error "Error changing password: ${e.message}", e
            flash.message = "An error occurred while changing your password"
            flash.messageType = "danger"
            redirect(action: "change_password")
        }
    }

    def edit(Long id) {
        respond userService.get(id)
    }

    def update(User user) {
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
            if (user == null) {
                notFound()
                return
            }

            // isAdmin and roletargetid are bindable:false on the domain, so the edit form's
            // values do not arrive by data binding - they are applied here, and only for a
            // superuser. A user editing their own profile cannot promote themselves, and the
            // anonymous register/save path cannot touch them at all. The hidden _<field>
            // marker Grails posts alongside each form field tells us the form really carried
            // it, so an API call that omits the field leaves the stored value alone.
            def actinguser = session.curuser
            if(actinguser?.isAdmin) {
                if(params.containsKey('_isAdmin')) {
                    user.isAdmin = params.isAdmin ? true : false
                }
                if(params.containsKey('roletargetid')) {
                    user.roletargetid = params.roletargetid ? params.int('roletargetid') : null
                }
            }

            try {
                userService.save(user)
            } catch (ValidationException e) {
                respond user.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'user.label', default: 'User'), user.id])
                    redirect user
                }
                '*'{ respond user, [status: OK] }
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

            def curuser = User.get(id)
            if(curuser) {
                User.withTransaction { sqltrans->
                    curuser.isActive = false
                    curuser.save(flush:true)
                    println "User deletion saved " + curuser + " to " + curuser.isActive
                }
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'user.label', default: 'User'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    def needlogin() {
    }

    def login() {
    }

    def logout = {

        // Only releases the single-session claim if this session owns it.

        try { User.get(session.userid)?.releaseSession(session.id) } catch(Exception e) {}

        if(session.userid){
            // def user = User.get(session.userid)
            def user = session.curuser
            def now = new Date()
            if(PortalSetting.namedefault("enable_loginlog",0)){
                def lastlogin = LoginLog.find {
                    user == user
                    year(clockin) == now.year + 1900
                    month(clockin) == now.month + 1
                    day(clockin) == now.date
                }
                if(lastlogin){
                    lastlogin.clockout = now
                    lastlogin.save()
                }
            }
            flash.message = "Goodbye ${user?.name}"
            try{
                session.userid = null
                session.realuserid = null
                session.painfo = null
                session.curuser = null
                session.realuser = null
                session.adminlink = null
                session.chosenrole = null
                if(session['redirectAfterLogin']){
                    session['redirectAfterLogin'].controller=null
                    session['redirectAfterLogin'].action=null
                    session['redirectAfterLogin'].params=null
                }
                session.invalidate()
            }
            catch(Exception e){
            }
            redirect(controller:"portalPage", action:"home")
        }
        else{
            redirect(controller:"portalPage", action:"home")
        }
    }

    def switchuser = {
        if(!session.userid){
            println("No userid found")
            flash.message = 'Need to login to switch users'
            redirect(controller:'portalPage',action:'home')
        }
        else{
            println("Got userid")
            if(params.id){
                println("Got id to switch")
                // def curuser = User.get(session.userid)
                def curuser = session.curuser
                if(curuser?.switchable() && !session.realuser){
                    println("Current user is an admin")
                    session.adminlink = curuser.userID
                    if(session.realuser) {
                        session.realuser = null
                    }
                    session.realuserid = session.userid
                    giverole(params.id)
                    redirect(controller:'portalPage',action:'home')
                    return
                }
                else{
                    println("Current user is normal")
                    flash.message = 'Need to be a SuperUser to switch users'
                    redirect(controller:'portalPage',action:'home')
                    return
                }
            }
        }
    }

    def restoreadmin = {
        if(!session.userid){
            flash.message = 'Need to login to switch users'
            redirect(controller:'portalPage',action:'home')
        }
        else{
            if(session.adminlink){
                giverole(session.adminlink)
                session.adminlink = null
                session.realuser = null
                session.realuserid = null
                if(session.painfo){
                    session.painfo = null
                }
                redirect(controller:'user',action:'index')
            }
        }
    }

    def connexion(){
        /* Will start to apply multiple roles */
        if(params.userid) {
            def user = User.findByUserID(params.userid,[cache:false])
            if(user && user.password5==params.secpass){
                /*
	    Remarks the PA parts until it is required

	    def pa = PA.findByPa(user)
                if(pa && pa.boss){
		user.lastlogin = new Date()
		user.save()
                    session.painfo = user
                    user = pa.boss
                }
	    */
                if(!user.claimSession(session.id)){
                    flash.message = "This account is already logged in from another browser or device."
                    return redirect(action:"login")
                }
                session['userid']=user.id
                session['realuserid']=user.id
                session['curuser']=user
                session['realuser']=null
                session['realuserid']=null
                session['rolestext']=[]
                session['role']=[]
                session['roletargetid']=[]
                def troles = user.treeroles(params)
                def firstone = true
                if(troles){
                    def curcount = 0
                    troles.each {
                        session['role'] << it.role
                        session['roletargetid'] << it.id
                        session['rolestext'] << it
                        if(!user.roletargetid && firstone && !user.isAdmin){
                            user.role = it.role
                            user.roletargetid = it.id
                            firstone = false
                        }
                        if(user.roletargetid==it.id) {
                            session['chosenrole'] = curcount
                        }
                        curcount++
                    }
                }
                user.lastlogin = new Date()
                user.save()
                return redirect(uri:params.finalurl)
            }
            flash.message = 'You need to login for access'
            return redirect(action:"login")
        }
        else {
            return redirect(uri:params.finalurl)
        }
    }

    def giverole(userid){
        /* Will start to apply multiple roles */
        def user = User.findByUserID(userid,[cache:false])
        if(user){
            user.clearModuleroleCache()
            /*
	    Remarks the PA parts until it is required

	    def pa = PA.findByPa(user)
            if(pa && pa.boss){
		user.lastlogin = new Date()
		user.save()
                session.painfo = user
                user = pa.boss
            }
	    */
            session['userid']=user.id
            session['curuser']=user
            session['profile']=user.load_profile()
            // session['realuserid']=user.id
            def troles = user.treeroles(params)
            session['rolestext']=[]
            session['role']=[]
            session['roletargetid']=[]
            if(troles){
                troles.each {
                    session['role'] << it.role
                    session['roletargetid'] << it.id
                    session['rolestext'] << it
                }
                if(!user.isAdmin){
                    // Prefer the role the user was last using (persisted via
                    // changerole, or from a previous login) if it's still
                    // valid, instead of always resetting to treeroles()'s
                    // first entry (an arbitrary node-id ordering that ignores
                    // what the user actually had active).
                    def existing = troles.find { it.id == user.roletargetid }
                    def chosen = existing ?: troles[0]
                    user.role = chosen.role
                    user.roletargetid = chosen.id
                    user.save()
                }
                return true
            }
            user.lastlogin = new Date()
            user.save()
        }
        return false
    }

    def changerole = {
        def chosenrole = params.chosenrole.toInteger()
        def fromtokens = params.frompage.tokenize('/')
        if(session.userid){
            User.withTransaction { ctrans->
                def duser = User.get(session.userid)
                if(duser) {
                    duser.clearModuleroleCache()
                    duser.role = session['role'][chosenrole]
                    duser.roletargetid = session['roletargetid'][chosenrole]
                    duser.save(flush:true)
                    session.curuser = duser
                    session['chosenrole'] = chosenrole
                }
            }
            /* if(fromtokens[1] in ['statement']){
                def optiontokens = fromtokens[2].tokenize('?')[1].tokenize('&')
                def slug = ''
                optiontokens.each { dtoken->
                    if(dtoken[0..4]=='slug='){
                        slug=dtoken
                    }
                }
                fromtokens[2]='criteria?'+slug
                params.frompage = '/' + fromtokens.join('/')
            }
            else if(fromtokens[1] in ['reports']){
                params.frompage = '/' + fromtokens[0..2].join('/')
            }
            else if(fromtokens[1] in ['branchPool']){
                params.frompage = '/' + fromtokens[0..1].join('/')
            } */
        }
        def finaluri = '/' + fromtokens.join('/')
        if(config.server.servlet['context-path']) {
            finaluri = '/' + (finaluri - config.server.servlet['context-path'])
        }
        redirect(uri:finaluri)
    }

    def verify = {
        // def curuser = User.get(session.userid)
        def curuser = session.curuser
        if(curuser){
            if(curuser.id==params.userId){
                // If the correct user is already logged in there is no
                // need to check for anything else thus just go
                redirect(controller:params.targetcontroller,action:"index")
            }
            else{
                if(giverole(params.userId)){
                    println "Redirecting user " + curuser.name + " with role " + curuser.role + " to target= " + curuser.roletargetid + " " + params.targetcontroller + " index"
                    redirect(controller:params.targetcontroller,action:"index")
                }else{
                    flash.message = "Sorry, ${params.userID}. Please try again."
                    redirect(action:"login")
                }
            }
        }
        else{
            flash.message = "Sorry, User not found."
            redirect(action:"login")
        }
    }

    def authenticate = {
        def encpass = ''
        def user = User.findByUserID(params.username,[cache:false])
        if(!user){
            user = User.findByLanid(params.username,[cache:false])
        }
        if(!user){
            println "User " + params.username + " not found"
            flash.message = "Invalid username or password."
            redirect(controller:"user",action:"login")
            return false
        }
        if(user && user.isActive==false){
            // Same generic message as "user not found" - a distinct message here would let
            // an attacker enumerate which usernames exist/are active (WA-33).
            flash.message = "Invalid username or password."
            redirect(controller:"user",action:"login")
            return false
        }
        def disable_lanid = config.server.disable_lanid
        // Claiming a password on first login only makes sense where local passwords are the
        // way in at all. With server.disable_lanid off, Active Directory is authoritative, and
        // an account that never set a local password has to keep not having one - otherwise
        // anyone who knows a username could set its password here and then log in with it.
        if(disable_lanid && user.password=="") {
            user.hashPassword(params.password)
            userService.save(user)
        } 
        // Active Directory first, then the local password - shared with PortalEndpoint
        // Basic auth, so whatever logs someone in here also lets their hg/git client in.
        if(Credentials.check(user, params.password)){
            if(session.logintry){
                session.removeAttribute('logintry')
                session.removeAttribute('previd')
            }
            // Takes the single-session lock (only when server.enforce_single_session is on).
            // A fresh login always wins, so this refuses nothing today; the branch is kept
            // for the day a refusal is wanted back.
            if(!user.claimSession(session.id)){
                UserLog.record(user, 'concurrent_login_blocked', 'Login blocked - account already active on another session', null)
                flash.message = "This account is already logged in from another browser or device."
                redirect(controller:"user",action:"login")
                return false
            }
            giverole(user.userID)
            def now = new Date()            
            user.treesdate = now
            user.lastlogin = now
            userService.save(user)
            if(session['redirectAfterLogin']) {
                redirect(
                controller: session['redirectAfterLogin'].controller,
                action: session['redirectAfterLogin'].action,
                params: session['redirectAfterLogin'].params
                )
                session.removeAttribute('redirectAfterLogin')
            }
            else if(session['urlAfterLogin']){
                def nexturl = session['urlAfterLogin']
                session.removeAttribute('urlAfterLogin')
                redirect(url:nexturl)
            }
            else{
                redirect(controller:"portalPage",action:"home")
            }
        }else{            
            if(session.logintry && session.previd==params.username){
                session.logintry+=1
            }
            else{
                session.logintry=1
                session.previd = params.username
            }
            if(session.logintry<4){
                if(!user.verifyPassword(params.password) && PortalSetting.namedefault("enforce_lanid",0)){
                    flash.message = "Sorry, ${user}. Please login using your LAN ID " + user.lanid + " password"
                }
                else{
                    flash.message = "Invalid username or password."
                }
            }
            else{
                if(user.lanid && PortalSetting.namedefault("enforce_lanid",0)){
                    flash.message = "Sorry, ${params.username}. You have tried to login more than 3 times. Do make sure that you use the correct password for your LAN ID ${user.lanid}. If you have forgotten it, please apply to reset it"
                }
                else {
                    // Previously this branch silently reset the account's password to a new
                    // random value and emailed it, triggered by nothing more than 3 wrong
                    // password guesses - an attacker who knew a valid username could lock a
                    // victim out of their own account at will, with no verification beyond
                    // "does this username exist" (WA-35). Just report the attempt count now;
                    // the account's current password is left untouched.
                    session.removeAttribute('logintry')
                    session.removeAttribute('previd')
                    flash.message = "Invalid username or password. You have tried to login more than 3 times."
                }
            }
            redirect(controller:"user",action:"login")
        }
        return
    }

    def updatelanid = {
        def user = User.get(params.id)
        def usersearch = "(employeeID=" + user.userID + ")"
        def gotupdate = false
        def gotupdate2 = false
        println "Update LAN ID for " + user.userID + " (" + user.name + "): attempting primary domain, filter " + usersearch
        def connection = null
        try{
            def adserver = PortalSetting.namedefault("adserver","defaultserver")
            def adport = PortalSetting.namedefault("adport",636)
            def adsecure = PortalSetting.namedefault("adsecure",true)
            def addn = PortalSetting.namedefault("addn","defaultdn")
            def topdn = PortalSetting.namedefault("topdn","defaulttopdn")
            println "Update LAN ID (primary): connecting to " + adserver + ":" + adport + " (ssl=" + adsecure + ")"
            connection = new LdapNetworkConnection(adserver, adport, adsecure)
            connection.bind(addn, PortalSetting.namedefault("adpassword","defaultpass"))
            println "Update LAN ID (primary): bound as service account " + addn
            println "Update LAN ID (primary): searching base " + topdn + " filter " + usersearch
            def cursor = connection.search(topdn,usersearch,SearchScope.SUBTREE,"*")
            def found = false
            while(cursor.next() && !gotupdate){
                found = true
                try{
                    def entry = cursor.get()
                    println "Update LAN ID (primary): found entry " + entry.dn + ", sAMAccountName=" + entry.sAMAccountName.toString()
                    def newlanid = entry.sAMAccountName.toString()[16..-1]
                    // A raw save() outside a transaction throws TransactionRequiredException on
                    // a packaged build - every other save() in this controller already goes
                    // through withTransaction, these two never did.
                    User.withTransaction { tstatus ->
                        user.lanid = newlanid
                        user.save(flush:true)
                    }
                    flash.message = "Updated user LAN ID to " + newlanid
                    println "Update LAN ID (primary): saved lanid=" + newlanid + " for " + user.userID
                    gotupdate = true
                }
                catch(Exception exp){
                    println "Update LAN ID (primary): could not read entry for " + user.userID + " - " + exp.class.simpleName + ": " + exp.message
                }
            }
            if(!found) {
                println "Update LAN ID (primary): no entry found for filter " + usersearch
            }
            if(!gotupdate){
                flash.message = "Fail to update the LAN ID for " + user
            }
        }
        catch(Exception exp){
            println "Update LAN ID (primary): error connecting/searching for " + user.userID + " - " + exp.class.name + ": " + exp.message
            exp.printStackTrace()
            PortalErrorLog.record(params,user,"user","updatelanid","Error connecting to ldap server:" + PortalErrorLog.describe(exp))
        }
        finally {
            if(connection) {
                try { connection.close() } catch(Exception ce) { println "Update LAN ID (primary): error closing connection - " + ce.message }
            }
        }

        def connection2 = null
        if(!gotupdate) {
            println "Update LAN ID for " + user.userID + ": attempting secondary domain"
            try{
                def adserver2 = PortalSetting.namedefault("adserver2","defaultserver2")
                def adport2 = PortalSetting.namedefault("adport2",636)
                def adsecure2 = PortalSetting.namedefault("adsecure2",true)
                def addn2 = PortalSetting.namedefault("addn2","defaultdn2")
                def topdn2 = PortalSetting.namedefault("topdn2","defaulttopdn2")
                println "Update LAN ID (secondary): connecting to " + adserver2 + ":" + adport2 + " (ssl=" + adsecure2 + ")"
                connection2 = new LdapNetworkConnection(adserver2, adport2, adsecure2)
                connection2.bind(addn2, PortalSetting.namedefault("adpassword2",'defaultpass'))
                println "Update LAN ID (secondary): bound as service account " + addn2
                println "Update LAN ID (secondary): searching base " + topdn2 + " filter " + usersearch
                def cursor2 = connection2.search(topdn2,usersearch,SearchScope.SUBTREE,"*")
                def found2 = false
                while(cursor2.next() && !gotupdate2){
                    found2 = true
                    try{
                        def entry = cursor2.get()
                        println "Update LAN ID (secondary): found entry " + entry.dn + ", sAMAccountName=" + entry.sAMAccountName.toString()
                        def newlanid = entry.sAMAccountName.toString()[16..-1]
                        User.withTransaction { tstatus ->
                            user.lanid = newlanid
                            user.save(flush:true)
                        }
                        flash.message = "Updated user LAN ID to " + newlanid
                        println "Update LAN ID (secondary): saved lanid=" + newlanid + " for " + user.userID
                        gotupdate2 = true
                    }
                    catch(Exception exp){
                        println "Update LAN ID (secondary): could not read entry for " + user.userID + " - " + exp.class.simpleName + ": " + exp.message
                    }
                }
                if(!found2) {
                    println "Update LAN ID (secondary): no entry found for filter " + usersearch
                }
            }
            catch(Exception exp){
                println "Update LAN ID (secondary): error connecting/searching for " + user.userID + " - " + exp.class.name + ": " + exp.message
                exp.printStackTrace()
                PortalErrorLog.record(params,user,"user","updatelanid","Error connecting to ldap server:" + PortalErrorLog.describe(exp))
            }
            finally {
                if(connection2) {
                    try { connection2.close() } catch(Exception ce) { println "Update LAN ID (secondary): error closing connection - " + ce.message }
                }
            }
        }
        else {
            println "Update LAN ID for " + user.userID + ": already resolved via the primary domain, skipping the secondary"
        }
        if(!gotupdate && !gotupdate2){
            flash.message = "Fail to update the LAN ID for " + user
        }
        println "Update LAN ID for " + user.userID + ": " + ((gotupdate || gotupdate2) ? "final result lanid=" + user.lanid : "final result - could not resolve a LAN ID")
        redirect(action: "show",id: user.id)
    }
    

    def clearsession = {
        if(!session.curuser?.isAdmin){
            flash.message = "Need to be admin"
            redirect(controller:'portalPage',action:'home')
            return
        }
        def user = User.get(params.id)
        if(user){
            user.activeSessionId = null
            user.activeSessionUpdated = null
            User.withTransaction { user.save(flush:true,validate:false) }
            flash.message = "Session lock cleared for ${user.name}"
        }
        redirect(controller:'user',action:'show',id:user?.id)
    }
}
