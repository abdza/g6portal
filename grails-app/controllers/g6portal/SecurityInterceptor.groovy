package g6portal

import static grails.util.Holders.config

class SecurityInterceptor {

    SecurityInterceptor() {
        matchAll()
        .except(controller:'user', action:'login')
        .except(controller:'user', action:'authenticate')
        // First-run setup runs before any account exists, so there is nobody this
        // interceptor could authorize. SetupController guards itself instead: it does
        // nothing once the instance has a system administrator, and creating the first one
        // needs the token file written on the server.
        .except(controller:'setup')
        .except(controller:'googleOAuth', action:'initiate')
        .except(controller:'googleOAuth', action:'callback')
        // Module-owned raw endpoints authenticate themselves, per PortalEndpoint
        // row (Basic / Token / Session / None). A protocol client cannot follow a
        // redirect to the login form, so this interceptor must not see them.
        //
        // ONLY serve() is exempt. The maintenance screens live in the same controller
        // and must NOT be: an endpoint's `target` is a program this server executes, so
        // an unauthenticated edit is remote code execution. They are handled by the
        // superuser-only branch in before().
        .except(controller:'portalEndpoint', action:'serve')
    }

    boolean before() { 
    // println "Date:" + new Date()
		// println "Params:" + params
		// println "Session:" + session
		// println "ControllerName:" + controllerName
		// println "ActionName:" + actionName
		def object = null
		def slug = null
		def module = null
		def curuser = null
    def realuser = null

    if(session) {
        if(session.curuser) {
            curuser = session.curuser
            realuser = session.realuser
            if(config.server?.user_profile && !session['profile']){
                session['profile'] = curuser.load_profile()
            }
        }
        else if(session.userid) {
            curuser = User.get(session.userid)
            session['curuser'] = curuser
            if(config.server?.user_profile){
                session['profile'] = curuser.load_profile()
            }
            else {
                session['profile'] = null
            }
        }
        if(session.realuser) {
            realuser = session.realuser
        }
        else if(session.realuserid) {
            realuser = User.get(session?.realuserid)
            session['realuser'] = realuser
        }
    }

    // Single-session-per-account enforcement, ported from g5portal. Skipped
    // entirely while an admin is impersonating a user (session.adminlink set) -
    // during impersonation curuser is the *target* user, so this must not
    // touch/steal the target's claim, and the admin's own impersonation session
    // must never be treated as a concurrent login.
    //
    // Inert unless `server.enforce_single_session: true` is set (see
    // User.allowConcurrentSessions).
    if(curuser && !session.adminlink) {
        if(!curuser.validateSession(session.id)) {
            flash.message = "You have been logged out because this account was logged in from another browser or device."
            session.userid = null
            session.curuser = null
            session.realuser = null
            session.realuserid = null
            session.adminlink = null
            session.chosenrole = null
            try { session.invalidate() } catch(Exception e) {}
            redirect(controller:'user', action:'login')
            return false
        }
    }
		if(params.slug) {
        slug = params.slug
		}
		if(params.module) {
        module = params.module
		}

    def storeparams = [:]
    if(request.method=='GET'){
		    def neverstore = ['password']
		    params.each { pkey,pval->
            try {
                if(pkey!=pkey.toUpperCase() && !(pkey.toLowerCase() in neverstore)){
                    storeparams[pkey]=pval
                }
            }
            catch(Exception e){
                println 'error interceptor:' + e
            }
        }
    }

    def trail = new PortalAuditTrail()
    trail.controller=controllerName
    trail.action=actionName
    if(request.method=='GET'){
      if(storeparams.toString().size()<256){
          trail.params=storeparams
        }
        else{
          trail.params=storeparams[0..255]
        }
    }
    else{
        trail.params = 'It was a post request'
    }
    trail.ipaddr = request.getRemoteAddr()
    trail.uri = request.forwardURI
    trail.useragent=request.getHeader("User-Agent")
    trail.date=new Date()
    if(module) {
        if(config.grails?.domainlimit){
            def url_domain = request.getRequestURL() - request.getRequestURI()
            session['url_domain'] = url_domain
            def module_limit = PortalSetting.namedefault(url_domain + '_modules',[])
            if(module_limit.size()) {
                if(!(module in module_limit + ['portal'])) {
                    flash.message = "The module you are looking for is unavailable" 
                    redirect(controller: "portalPage", action: "home")
                    return false
                }
            }
        }
    }
    if(session){
        try {
            if(curuser && !('enablesuperuser' in session)) {
                session['enablesuperuser'] = PortalSetting.namedefault('enablesuperuser',false) && curuser?.isAdmin
                if(realuser) {
                    session['adminmodules'] = realuser.adminlist()
                    session['developermodules'] = realuser.developerlist()
                }
                else {
                    session['adminmodules'] = curuser.adminlist()
                    session['developermodules'] = curuser.developerlist()
                }
                if(session['enablesuperuser']) {
                    return true
                }
            }
            if(session?.userid){
                trail.user_id = session.userid
            }
            if(session?.realuserid){
                trail.realuser_id = session.realuserid
                def module_whitelist = PortalSetting.namedefault('portal.module_whitelist',[]) + ['portal']
                if(module && !(module in realuser.adminlist() + module_whitelist)) {
                    flash.message = "You are not authorized to view that module" 
                    redirect(controller: "portalPage", action: "home")
                    return false
                }
            }
        }
        catch(Exception e){
            // This block both populates session['adminmodules']/['developermodules'] and
            // performs the impersonation module check that can return false. Swallowing an
            // exception here means that authorization check silently did not happen, and the
            // UI gates that read those session keys quietly hide links instead. It runs once
            // per session, so recording it cannot flood.
            PortalErrorLog.capture(e, "session setup / impersonation module check failed for ${curuser?.userID ?: 'anonymous'} on ${controllerName}.${actionName}",
                                   [params: params, user: curuser, controller: controllerName,
                                    action: 'securityinterceptor session setup', module: module, slug: slug])
        }
    }
    try {
        PortalAuditTrail.withTransaction { atrans-> 
            trail.save(flush:true)
        }
    }
    catch(Exception e){
        println "Error with audit trail:" + e
    }

    def whitelist = PortalSetting.namedefault('portal.whitelist',['portalPage.setup','portalTracker.data_dump','portalTrackerField.onchange','portalPage.home','user.register','user.save','user.connexion','portalScheduler.run','portalEmail.run','portalSearch.index','portalTrackerData.customfield','portalTrackerData.create','portalTrackerData.save','portalTrackerData.doupload'])
    def modtest = module + '.' + slug
    def contest = controllerName + '.' + actionName
    if(modtest in whitelist || contest in whitelist) {
        return true
    }

		if(controllerName=='portalPage') {
        if(actionName=='home' || (actionName=='display' && slug=='home')) {
            return true
        }
        if(curuser){
            if((!module || module=='All') && curuser.developerlist()?.size()>0) {
                return true
            }
            else if(module in curuser.developerlist()) {
                return true
            }
        }
        object = PortalPage.findByModuleAndSlug(module,slug)
        if(object) {
            if(object.published) {
                if(object.requirelogin) {
                    if(curuser) {
                        if(object.allowedroles) {
                            def testroles = object.allowedroles.tokenize(',')*.trim()
                            if(testroles.size()==0) {
                                return true
                            }
                            if('All' in testroles) {
                                return true
                            }
                            if(testroles.any { tr -> tr in curuser.modulerole(module)}){
                                return true
                            }
                            else if(curuser.currentrole()?.role in testroles){
                                return true
                            }
                        }
                        else {
                            return true
                        }
                    }
                    else {
                        rememberForLogin()
                        flash.message = "You need to login to view that page"
                        redirect(controller: "user", action: "login")
                        return false
                    }
                }
                else {
                    return true
                }
            }
        }
        flash.message = "The page you are looking for is unavailable" 
        redirect(controller: "portalPage", action: "home")
        return false
		}
		else if(controllerName=='fileLink') {
        if(curuser){
            if((!module || module=='All') && curuser.adminlist()?.size()>0) {
              return true
            }
            else if(module in curuser.adminlist()) {
              return true
            }
        }
        if(params.id) {
            object = FileLink.get(params.id)
        }
        else {
            object = FileLink.findByModuleAndSlug(module,slug)
        }
        if(object) {
            // Page documents (file group, no tracker) follow FileLink.readable(), which keeps the
            // old portal's rule for them; the controller applies the same check.
            if(object.filegroup && !object.tracker_id && FileLink.readable(object, session)) {
                return true
            }
            if(object.allowedroles){
                def testroles = object.allowedroles.tokenize(',')*.trim()
                if('All' in testroles) {
                    return true
                }
                if(curuser) {
                    if('Authenticated' in testroles) {
                        return true
                    }
                    if(testroles.any { tr -> tr in curuser.modulerole(module)}){
                        return true
                    }
                    else if(curuser.currentrole()?.role in testroles){
                        return true
                    }
                }
            }
            else if(object.module_roles(curuser)) {
                return true
            }
            else {
                def whitelist_modules = PortalSetting.namedefault('download_module_whitelist',['portal'])
                if(object.module in whitelist_modules) {
                    return true
                }
                if(object.tracker_id && object.tracker_data_id) {
                    return true
                }
            }
        }
        flash.message = "The file you are looking for is unavailable" 
        redirect(controller: "portalPage", action: "home")
        return false
		}
		else if(controllerName=='portalTracker') {
        if(actionName in ['userlist','objectlist','nodeslist','dropdownlist','dropdowndata']){
            return true
        }
        if(curuser){
            if((!module || module=='All') && curuser.developerlist()?.size()>0) {
                return true
            }
            else if(module in curuser.developerlist()) {
                return true
            }
        }
        object = PortalTracker.findByModuleAndSlug(module,slug)
        if(object) {
            if(object.require_login){
                if(curuser){
                    if(object.allowedroles) {
                        def testroles = object.allowedroles.tokenize(',')*.trim()
                        if(testroles.size()==0) {
                            return true
                        }
                        if('All' in testroles) {
                            return true
                        }
                        else if(curuser.currentrole()?.role in testroles){
                            return true
                        }
                    }
                    else {
                        return true
                    }
                    if(object.module_roles(curuser)) {
                        return true
                    }
                    else if(object.user_roles(curuser).size()){
                        return true
                    }
                    rememberForLogin()
                    flash.message = "Sorry but you do not have the credentials to view the system" 
                    redirect(controller: "portalPage", action: "home")
                    return false
                }
                else {
                    if(object.anonymous_list && actionName=='list'){
                        return true
                    }
                    else if(object.anonymous_view && actionName=='display_data'){
                        return true
                    }
                }
                rememberForLogin()
                flash.message = "Sorry but you need to login to view the system" 
                redirect(controller: "user", action: "login")
                return false
            }
            else {
                return true
            }
        }
        flash.message = "The item you are looking for is unavailable" 
        redirect(controller: "portalPage", action: "home")
        return false
		}
		else if(controllerName=='branding') {
        // Branding restyles the whole portal for every user and is not scoped to any
        // module, so the generic "admin of some module" fallback below would be far too
        // broad - one module's admin could rebrand the entire install. Superusers only.
        if(curuser?.isAdmin) {
            return true
        }
        if(curuser) {
            flash.message = "Branding can only be changed by a system administrator"
            redirect(controller: "portalPage", action: "home")
            return false
        }
        rememberForLogin()
        flash.message = "You need to login to access that functionality"
        redirect(controller: "user", action: "login")
        return false
		}
		else if(controllerName=='portalEndpoint') {
        // serve() is exempt from this interceptor entirely (it authenticates itself per
        // PortalEndpoint row); everything else here is the maintenance UI. An endpoint's
        // `target` is a program the server execs and `env_json` its environment, so being
        // able to edit one is being able to run anything as the portal's account. That is
        // strictly above "admin of some module" - the generic fallback below would grant
        // it to any module admin - so it is superusers only, deliberately.
        if(curuser?.isAdmin) {
            return true
        }
        if(curuser) {
            flash.message = "Endpoints can only be managed by a system administrator"
            redirect(controller: "portalPage", action: "home")
            return false
        }
        rememberForLogin()
        flash.message = "You need to login to access that functionality"
        redirect(controller: "user", action: "login")
        return false
		}
		else if(controllerName=='userRole') {
        // A UserRole grants a user Admin/Developer/etc access to a whole module, so
        // this needs its own object-aware check rather than falling into the generic
        // "admin of any module" fallback below - otherwise being Admin/Developer of
        // ANY one module would let someone view/edit/delete/create UserRole rows for
        // every module, including granting themselves new roles anywhere.
        if(curuser?.isAdmin) {
            return true
        }
        if(curuser) {
            if(actionName=='index' && curuser.adminlist()?.size()>0) {
                return true
            }
            object = params.id ? UserRole.get(params.id) : null
            if(actionName=='show' && object?.user?.id==curuser.id) {
                // users may always view (read-only) their own role assignments
                return true
            }
            def targetmodule = object?.module ?: params.module
            if(targetmodule && targetmodule in curuser.adminlist()) {
                // only Admin/Developer of the role's OWN module - not any module
                return true
            }
            flash.message = "You need admin rights to access that functionality"
            redirect(controller: "portalPage", action: "home")
            return false
        }
        else {
            rememberForLogin()
            flash.message = "You need to login to access that functionality"
            redirect(controller: "user", action: "login")
            return false
        }
		}
		else if(controllerName in ['portalTree','portalTreeNode','portalTreeNodeUser']) {
        // Trees hold the portal's global navigation (portal/main_menu) and the org
        // hierarchies other modules derive their permissions from. Without an
        // object-aware check these controllers fall into the generic branch below,
        // where being Admin/Developer of ANY single module grants write access to
        // EVERY tree - the same flaw already fixed for userRole above. Reads stay
        // open to any module admin; writes must be on the tree's OWN module.
        if(curuser?.isAdmin) {
            return true
        }
        if(curuser) {
            // movenode drags a node to a new parent; user_form adds, changes and removes the
            // users holding roles on a node. Both write, so both are scoped to the tree's module.
            def writeactions = ['create','save','edit','update','delete','fixnodes','movenode','user_form']
            // Importing a tree from a file also writes, but the module it writes to is named
            // INSIDE the uploaded file, which treemodule() cannot see. These actions are let
            // through to any module admin here and the per-module check is enforced in
            // PortalTreeController.unauthorizedmodules(), where the file has been parsed.
            def fileimportactions = ['importtreeform','importtree','importtreepreview','confirmimporttree']
            if(actionName in fileimportactions) {
                if(curuser.adminlist()?.size()>0) {
                    return true
                }
            }
            else if(!(actionName in writeactions)) {
                if(curuser.adminlist()?.size()>0) {
                    return true
                }
            }
            else if(treemodule() in curuser.adminlist()) {
                return true
            }
            flash.message = "You need admin rights on that tree's module to access that functionality"
            redirect(controller: "portalPage", action: "home")
            return false
        }
        else {
            rememberForLogin()
            flash.message = "You need to login to access that functionality"
            redirect(controller: "user", action: "login")
            return false
        }
		}
		else if(controllerName=='accessReview') {
        // Module owners are usually not admins of anything, so the default rule below would
        // turn them away from their own review. Any logged-in user may reach the controller;
        // AccessReviewController checks ownership / admin on every action itself. Not logged
        // in (e.g. straight from the e-mail link): log in first, then come back here.
        if(curuser) {
            return true
        }
        rememberForLogin()
        flash.message = "Please log in to open your access review"
        redirect(controller: "user", action: "login")
        return false
		}
		else {
        if(curuser) {
            if(actionName in ['api_list']) {
                return true
            }
            else if(controllerName=='user' && actionName in ['index','show','login','logout','restoreadmin','my_profile','my_profile_save','changerole']) {
                return true
            }
            else if(controllerName=='user' && actionName in ['edit','update'] && params.id!=curuser.id && !curuser?.isAdmin) {
                return false
            }
            else if((!module || module=='All') && curuser.adminlist()?.size()>0) {   // if module is not specified, then can access as long as they are admin of something
                return true
            }
            else if(module in curuser.adminlist()) {  // but if module is specified, they need to be the admin of that module
                return true
            }
            else {
                flash.message = "You need admin rights to access that functionality"
                redirect(controller: "portalPage", action: "home")
                return false
            }
        }
        else {
            rememberForLogin()
            flash.message = "You need to login to access that functionality"
            redirect(controller: "user", action: "login")
            return false
        }
		}

	/*
		if(controllerName=='portalPage' && actionName=='display') {
			object = PortalPage.findByModuleAndSlug(module,slug)
		}
		if(controllerName=='fileLink' && actionName=='download') {
			object = FileLink.findByModuleAndSlug(module,slug)
		}
		if(controllerName=='portalTracker' && actionName in ['transition','display_data','create_data']) {
			object = PortalTracker.findByModuleAndSlug(module,slug)
		}
		if(object) {
			
		} */
		flash.message = "You lack the rights to access that functionality"
		redirect(controller: "portalPage", action: "home")
		return false
    }

    /**
     * Resolves which tree a portalTree / portalTreeNode / portalTreeNodeUser request
     * targets, and returns that tree's module. Used to scope write access so that
     * being admin of one module cannot rewrite another module's tree (or the portal's
     * global navigation menu).
     *
     * Returns null when the target cannot be determined, which callers treat as "deny"
     * for write actions.
     */
    private String treemodule() {
        try {
            // Moving a node: o is the node, r its new parent. Both must sit in trees of the
            // same module, or a node could be dragged out of a tree the user may not touch.
            if(controllerName=='portalTreeNode' && actionName=='movenode') {
                def moved = params.o ? PortalTreeNode.get(params.o)?.tree?.module : null
                def target = params.r ? PortalTreeNode.get(params.r)?.tree?.module : null
                return (moved && moved == target) ? moved : null
            }
            // Node users: the node comes as hidenodeid, and an edit/delete names an existing
            // assignment by usernodeid, which must belong to a node of that same module - or
            // an assignment in another module's tree could be rewritten through a node here.
            if(controllerName=='portalTreeNode' && actionName=='user_form') {
                def nodemodule = params.hidenodeid ? PortalTreeNode.get(params.hidenodeid)?.tree?.module : null
                if(params.usernodeid) {
                    def assignmodule = PortalTreeNodeUser.get(params.usernodeid)?.node?.tree?.module
                    return (nodemodule && nodemodule == assignmodule) ? nodemodule : null
                }
                return nodemodule
            }
            // Creating a node: it inherits its tree from the parent (see
            // PortalTreeNodeController.save), so parentid identifies the target tree.
            if(params.parentid) {
                return PortalTreeNode.get(params.parentid)?.tree?.module
            }
            if(controllerName=='portalTree') {
                if(params.id) {
                    return PortalTree.get(params.id)?.module
                }
                return params.module   // creating a brand new tree
            }
            if(controllerName=='portalTreeNode' && params.id) {
                return PortalTreeNode.get(params.id)?.tree?.module
            }
            if(controllerName=='portalTreeNodeUser' && params.id) {
                return PortalTreeNodeUser.get(params.id)?.node?.tree?.module
            }
        }
        catch(Exception e) {
            println "Error resolving tree module for ${controllerName}.${actionName}: ${e}"
        }
        return null
    }

    boolean after() { 
	return true 
    }

    void afterView() {
        // no-op
    }

    /**
     * Stash where to come back to after logging in - but only if this request was a
     * page somebody was actually looking at.
     *
     * A view with a RELATIVE asset URL makes the browser ask a controller for an
     * image. login.gsp did exactly that: `src="assets/img/logo.png"` on a page served
     * at /user/login had the browser fetch /user/assets/img/logo.png, which no asset
     * handler serves, so it arrived here as controller `user`, action `assets` - and
     * was duly remembered as the place to go after login. UserController has no such
     * action, so Grails fell back to the controller's index and every login landed on
     * the user list.
     *
     * The interceptor cannot tell a navigation from a sub-resource by controller and
     * action alone, so it asks two independent questions. Sec-Fetch-Dest is the
     * browser saying what it wants the bytes FOR, which is authoritative but absent on
     * older browsers and on anything scripted. The path extension is a weaker signal
     * that works everywhere. Either one saying "not a page" is enough.
     */
    private void rememberForLogin() {
        def dest = request.getHeader('Sec-Fetch-Dest')
        if(dest && dest != 'document') { return }
        def uri = (request.forwardURI ?: '').toLowerCase()
        if(uri ==~ /.*\.(png|jpe?g|gif|svg|ico|webp|avif|bmp|css|js|mjs|map|json|woff2?|ttf|otf|eot)$/) { return }
        session['redirectAfterLogin'] = [ controller: controllerName, action: actionName, params: params ]
    }

}
