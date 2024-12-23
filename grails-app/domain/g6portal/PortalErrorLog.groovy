package g6portal

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils

class PortalErrorLog {

    static constraints = {
        date(nullable:true)
        uri(nullable:true)
        user(nullable:true)
        module(nullable:true)
        slug(nullable:true)
        errormsg(nullable:true)
        params(nullable:true)
        controller(nullable:true)
        action(nullable:true)
        resolved(nullable:true)
        dateresolved(nullable:true)
        ipaddr(nullable:true)
    }

    static mapping = {
        errormsg type: 'text'
        params type: 'text'
        sort "date": 'desc'
        cache true
    }

    String controller
    String action
    String params
    String slug
    String module
    String errormsg
    User user
    Date date
    Boolean resolved
    Date dateresolved
    String ipaddr
    String uri

    static PortalErrorLog record(params,curuser,controllerName,actionName,errormsg,slug=null,module=null) {
        PortalErrorLog.withTransaction { sqltrans->
            def errorlog = new PortalErrorLog()
            def storeparams = [:]
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
            try {
                if(slug){
                    errorlog.slug = slug
                }
                else if(params.slug){
                    errorlog.slug = params.slug
                }
            }
            catch(Exception e){
                println "Error recording error log slug"
            }
            try {
                if(module){
                    errorlog.module = module
                }
                else if(params.module){
                    errorlog.module = params.module
                }
            }
            catch(Exception e){
                println "Error recording error log module"
            }
            errorlog.controller = controllerName
            errorlog.action = actionName
            errorlog.params = storeparams
            errorlog.date = new Date()
            errorlog.user = curuser
            errorlog.errormsg = errormsg
            GrailsWebRequest webUtils = WebUtils.retrieveGrailsWebRequest()
            def request = webUtils.getCurrentRequest()
            errorlog.ipaddr = request.getRemoteAddr()
            errorlog.uri = request.forwardURI
            println "Errormsg:" + errormsg
            try {
                errorlog.save(flush:true)
            }
            catch(Exception e) {
                println "Error saving the error:" + e
                println "For error:" + errormsg
            }
            return errorlog
        }
    }
}
