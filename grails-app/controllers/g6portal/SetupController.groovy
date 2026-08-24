package g6portal

import static grails.util.Holders.config

/**
 * First-run setup. Reachable without a session (SecurityInterceptor excepts this controller)
 * because on a new instance there is nobody who could log in - it guards itself instead:
 * every action refuses to do anything once a system administrator exists, and creating one
 * requires the token file written on the server at startup (see SetupService).
 */
class SetupController {

    static allowedMethods = [complete: "POST"]

    static final int MAX_ATTEMPTS = 5

    SetupService setupService

    private boolean alreadysetup() {
        if(setupService.needssetup()) {
            return false
        }
        flash.message = "This instance is already set up. Please log in."
        redirect(controller:'user', action:'login')
        return true
    }

    def index() {
        if(alreadysetup()) {
            return
        }
        // Make sure the token exists even if the instance was started before this feature,
        // so the page can always tell the operator where to look for it
        setupService.ensuretoken()
        render view:'index', model:[tokenpath: setupService.tokenfile().absolutePath, params:params]
    }

    def complete() {
        if(alreadysetup()) {
            return
        }
        // A wrong token is worth slowing down even though guessing a 48 character random
        // string is hopeless: it keeps a scripted attempt from filling the logs
        session.setupattempts = (session.setupattempts ?: 0) + 1
        if(session.setupattempts > MAX_ATTEMPTS) {
            println "Setup: too many failed attempts from " + request.remoteAddr
            flash.message = "Too many attempts. Restart the browser session and try again."
            redirect action:"index"
            return
        }
        if(!setupService.checktoken(params.token)) {
            println "Setup: wrong token from " + request.remoteAddr
            flash.message = "That setup token is not correct. It is in " + setupService.tokenfile().absolutePath
            redirect action:"index", params:[userid:params.userid, name:params.name, email:params.email]
            return
        }
        def problems = []
        def userid = params.userid?.trim()
        def name = params.name?.trim()
        def email = params.email?.trim()
        def password = params.password
        // User id is the login username (UserController.authenticate looks it up with
        // findByUserID), so it has to be something that can be typed into the login form
        if(!userid || userid ==~ /.*\s.*/) {
            problems << "User ID is required and cannot contain spaces"
        }
        if(!name) {
            problems << "Name is required"
        }
        if(!email || !(email ==~ /^[^@\s]+@[^@\s]+\.[^@\s]+$/)) {
            problems << "A valid email address is required"
        }
        if(!password || password.size() < 8) {
            problems << "Password must be at least 8 characters"
        }
        if(password != params.password2) {
            problems << "The repeated password is not the same"
        }
        if(problems) {
            flash.message = problems.join('. ')
            redirect action:"index", params:[userid:userid, name:name, email:email]
            return
        }
        def errors = []
        def user = setupService.createsuperuser(userid, name, email, password, errors)
        if(!user) {
            flash.message = "Could not create the administrator: " + (errors.join('. ') ?: 'unknown error')
            redirect action:"index", params:[userid:userid, name:name, email:email]
            return
        }
        if(params.enablesuperuser) {
            setupService.enablesuperuser()
        }
        // The token is spent the moment the account exists
        setupService.cleartoken()
        session.removeAttribute('setupattempts')
        println "Setup: created system administrator " + userid + " from " + request.remoteAddr
        // Bringing up a new instance is exactly when the login rules are least obvious, so
        // say which one this account falls under instead of leaving it to be discovered at the
        // login form. Without server.disable_lanid, accounts that HAVE a LAN ID authenticate
        // against Active Directory; this one has none, so it uses the password just set.
        def lanidnote = ''
        if(!config.server?.disable_lanid) {
            println "Setup: server.disable_lanid is not set - accounts with a LAN ID will authenticate against Active Directory; this administrator has none and signs in with its password"
            lanidnote = " Note: server.disable_lanid is not set, so accounts that have a LAN ID authenticate against Active Directory. This administrator has none and signs in with the password you just set."
        }
        flash.message = "Administrator ${userid} created. Please log in." + lanidnote
        redirect(controller:'user', action:'login')
    }
}
