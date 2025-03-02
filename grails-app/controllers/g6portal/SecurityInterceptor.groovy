package g6portal

import static grails.util.Holders.config

class SecurityInterceptor {

    SecurityInterceptor() {
        matchAll()
        .except(controller:'user', action:'login')
        .except(controller:'user', action:'authenticate')
        .except(controller:'googleOAuth', action:'initiate')
        .except(controller:'googleOAuth', action:'callback')
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

    def whitelist = PortalSetting.namedefault('portal.whitelist',['portalPage.setup','portalTracker.data_dump','portalTrackerField.onchange','portalPage.home','user.register','user.save','user.connexion','portalScheduler.run','portalEmail.run','portalSearch.index'])
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
                        session['post_login'] = params
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
                    session['post_login'] = params
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
                session['post_login'] = params
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
            else if(controllerName=='userRole' && curuser?.isAdmin) {
                return true
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
            session['post_login'] = params
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

    boolean after() { 
	return true 
    }

    void afterView() {
        // no-op
    }
}
