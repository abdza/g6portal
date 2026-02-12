package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

import org.apache.directory.ldap.client.api.*
import org.apache.directory.api.ldap.model.message.*
import static grails.util.Holders.config

class UserController {

    UserService userService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def completelist = {
        def dparam = '%' + params.q?.trim().replace(' ','%') + '%'
        def cusers = []
        if(params.value){
          cusers << csdportal.User.get(params.value)
        }
        if(params.role){
            def usernodes = TreeNodeUser.createCriteria().list() {
                'in'('role',params.role.decodeURL().tokenize(','))
                if(params.module) {
                'eq'('module',params.module)
                }
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
            if(params.module) {
                'eq'('module',params.module)
            }
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
        def dparam = '%' + params.q?.trim().replace(' ','%') + '%'
        def dusers = null
        if(params.role){
            def usernodes = TreeNodeUser.createCriteria().list() {
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
            users = User.createCriteria().list() {
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
            usersdata = userService.list_query(query,dparam)
        }
        else {
            usersdata = userService.list(dparam)
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
                    thelist = userService.list(params.rolefilter,true,query,dparam)
                    userCount = userService.count(params.rolefilter,true,query)
                }
                else {
                    thelist = userService.list(true,query,dparam)
                    userCount = userService.count(true,query)
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
                    thelist = userService.listByIsActiveAndRole(true,params.rolefilter,dparam)
                    userCount = userService.countByIsActiveAndRole(true,params.rolefilter)
                }
                else {
                    thelist = userService.listByIsActive(true,dparam)
                    userCount = userService.countByIsActive(true)
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

            try {
                params.password = user.hashPassword(params.password)
                userService.save(user)
            } catch (ValidationException e) {
                println "Errors registering user: " + e
                respond user.errors, view:'create'
                return
            }

            if(user) {
                if(params.password2 && params.password==params.password2) {
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
                if(params.password2 && params.password==params.password2) {
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

    def giverole(userid){
        /* Will start to apply multiple roles */
        def user = User.findByUserID(userid,[cache:false])
        if(user){
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
            def firstone = true
            if(troles){
                troles.each {
                    session['role'] << it.role
                    session['roletargetid'] << it.id
                    session['rolestext'] << it
                    if(firstone && !user.isAdmin){
                        user.role = it.role
                        user.roletargetid = it.id
                        firstone = false
                    }
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
            flash.message = "Sorry, Username ${params.username} does not exist."
            redirect(controller:"user",action:"login")
            return false
        }
        if(user && user.isActive==false){
            flash.message = "Sorry, Username ${params.username} is not an active user anymore."
            redirect(controller:"user",action:"login")
            return false
        }
        if(user.password=="") {
            user.hashPassword(params.password)
            userService.save(user)
        } 
        def loggedin = false
        def disable_lanid = config.server.disable_lanid
        println "Disable lan_id:" + disable_lanid
        if(disable_lanid) {
          println "Enforcer"
        }
        if(!disable_lanid && !loggedin && user.lanid && PortalSetting.namedefault("adenable",0)){
           	println "Trying ad"
            try{
                LdapConnection connection = new LdapNetworkConnection(PortalSetting.namedefault("adserver","defaultadserver"), PortalSetting.namedefault("adport",636), PortalSetting.namedefault("adsecure",true) )
                connection.bind(PortalSetting.namedefault("addn","defaultdn"),PortalSetting.namedefault("adpassword","defaultpass"))
                def usersearch = "(sAMAccountName=" + user.lanid + ")"
                def cursor = connection.search(PortalSetting.namedefault("topdn","defaulttopdn"),usersearch,SearchScope.SUBTREE,"*")
                println "Trying to login ad"
                while(cursor.next() && !loggedin){
                    try{
                        def entry = cursor.get()
                        println entry
                        connection.bind(entry.dn,params.password)
                        loggedin = true
                        user.lanhash = lanhash
                        user.save()
                    }
                    catch(Exception exp){
                        println "Wrong password for the user:" + user.lanid
                    }
                }	
            }
            catch(Exception exp){
                println "Error connecting to ldap server :" + exp.toString()
                PortalErrorLog.record(params,user,"user","authenticate","Error connecting to ldap server :" + exp.toString())
            }
            if(!loggedin){
                try{
                    LdapConnection connection2 = new LdapNetworkConnection(PortalSetting.namedefault("adserver2","defaultserver2"), PortalSetting.namedefault("adport2",636), PortalSetting.namedefault("adsecure2",true))
                    connection2.bind(PortalSetting.namedefault("addn2","defaultad2"),PortalSetting.namedefault("adpassword2",'defaultpass2'))
                    def usersearch2 = "(sAMAccountName=" + user.lanid + ")"
                    def cursor2 = connection2.search(PortalSetting.namedefault("topdn2","defaulttopdn2"),usersearch2,SearchScope.SUBTREE,"*")
                    while(cursor2.next() && !loggedin){
                        try{
                            def entry = cursor2.get()
                            connection2.bind(entry.dn,params.password)
                            loggedin = true
                            user.lanhash = lanhash
                            user.save()
                        }
                        catch(Exception exp){
                            println "Wrong password for the user:" + user.lanid
                        }
                    }	
                }
                catch(Exception exp){
                    println "Error connecting to ldap server:" + exp.toString()
                    PortalErrorLog.record(params,user,"user","authenticate","Error connecting to ldap server:" + exp.toString())
                }
            }
        }
        if(loggedin || (disable_lanid && (!(user.lanid && PortalSetting.namedefault("enforce_lanid",0)) || !PortalSetting.namedefault("enforce_lanid",0)) && user.verifyPassword(params.password))){
            if(session.logintry){
                session.removeAttribute('logintry')
                session.removeAttribute('previd')
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
            else if(session['post_login']){
                def togo = session['post_login']
                session.removeAttribute('post_login')
                redirect(togo)
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
                    flash.message = "Sorry, ${params.username}. Please try again."
                }
            }
            else{
                if(user.lanid && PortalSetting.namedefault("enforce_lanid",0)){
                    flash.message = "Sorry, ${params.username}. You have tried to login more than 3 times. Do make sure that you use the correct password for your LAN ID ${user.lanid}. If you have forgotten it, please apply to reset it"
                }
                else {
                    session.removeAttribute('logintry')
                    session.removeAttribute('previd')
                    flash.message = "Sorry, ${params.username}. You have tried to login more than 3 times. Your password has been reset and e-mail notification of your new password has been sent"
                    def newPw = UUID.randomUUID().toString().replace('-','').substring(0,8);
                    user.hashPassword(newPw)
                    user.resetPassword=true
                    if(user.save()){
                        def reset_email_password = PortalPage.findByModuleAndSlug('portal','reset_email_password')
                        if(reset_email_password) {
                            sendMail {
                            to user.email
                            subject reset_email_password.title
                            text reset_email_password.evalcontent([user:user])
                            }
                        }
                    }
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
        try{
            LdapConnection connection = new LdapNetworkConnection(PortalSetting.namedefault("adserver","defaultserver"), PortalSetting.namedefault("adport",636), PortalSetting.namedefault("adsecure",true) )
            connection.bind(PortalSetting.namedefault("addn","defaultdn"),PortalSetting.namedefault("adpassword","defaultpass"))
            def cursor = connection.search(PortalSetting.namedefault("topdn","defaulttopdn"),usersearch,SearchScope.SUBTREE,"*")
            while(cursor.next() && !gotupdate){
                try{
                    def entry = cursor.get()
                    println "User LAN ID is :" + entry.sAMAccountName.toString()[16..-1]
                    user.lanid = entry.sAMAccountName.toString()[16..-1]
                    user.save(flush:true)
                    flash.message = "Updated user LAN ID to " + entry.sAMAccountName.toString()[16..-1]
                    gotupdate = true
                }
                catch(Exception exp){
                    println "Can't get info for user:" + user + " exp:" + exp
                }
            }	
            if(!gotupdate){
                flash.message = "Fail to update the LAN ID for " + user
            }
        }
        catch(Exception exp){
            println "Error connecting to ldap server: " + exp.toString()
            PortalErrorLog.record(params,user,"user","authenticate","Error connecting to ldap server:" + exp.toString())
        }

        try{
            println "Will now try with Server 2"
            LdapConnection connection2 = new LdapNetworkConnection(PortalSetting.namedefault("adserver2","defaultserver2"), PortalSetting.namedefault("adport2",636), PortalSetting.namedefault("adsecure2",true))
            connection2.bind(PortalSetting.namedefault("addn2","defaultdn2"),PortalSetting.namedefault("adpassword2",'defaultpass'))
            def cursor2 = connection2.search(PortalSetting.namedefault("topdn2","defaulttopdn2"),usersearch,SearchScope.SUBTREE,"*")
            while(cursor2.next() && !gotupdate2){
                try{
                    println "Looking in server 2 for:" + usersearch
                    def entry = cursor2.get()
                    println "User LAN ID is :" + entry.sAMAccountName.toString()[16..-1]
                    user.lanid = entry.sAMAccountName.toString()[16..-1]
                    user.save(flush:true)
                    flash.message = "Updated user LAN ID to " + entry.sAMAccountName.toString()[16..-1]
                    gotupdate2 = true
                }
                catch(Exception exp){
                    println "Can't get info for user:" + user + " exp:" + exp
                }
            }	
        }
        catch(Exception exp){
            println "Error connecting to ldap server: " + exp.toString()
            PortalErrorLog.record(params,user,"user","authenticate","Error connecting to ldap server:" + exp.toString())
        }
        if(!gotupdate && !gotupdate2){
            flash.message = "Fail to update the LAN ID for " + user
        }
        redirect(action: "show",id: user.id)
    }
    
}
