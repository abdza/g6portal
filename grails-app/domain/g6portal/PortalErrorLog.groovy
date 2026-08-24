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

    /**
     * Turns an exception into something a maintainer can act on: the type, the message, and
     * where it happened - preferring frames from the evaluated script (a GroovyShell script
     * compiles to Script<n>, a GSP to the template name it was given) over framework frames,
     * because "line 7 of your preprocess" is the answer and "AbstractCallSite.call" is not.
     *
     * Never throws; callers are catch blocks.
     */
    static String describe(Throwable e) {
        if(e == null) return '(no exception)'
        def msg = new StringBuilder()
        try {
            msg << e.getClass().name << ": " << (e.message ?: '(no message)')
            def frames = []
            try {
                e.stackTrace?.each { f ->
                    def cn = f.className ?: ''
                    def fn = f.fileName ?: ''
                    if(cn.startsWith('Script') || (fn.endsWith('.groovy') && cn.startsWith('page'))) {
                        frames << "  in the evaluated script at line ${f.lineNumber} (${cn})"
                    }
                }
            } catch(Throwable ignored) { }
            if(frames) {
                msg << "\n" << frames.unique().take(5).join("\n")
            }
            else {
                try { e.stackTrace?.take(5)?.each { f -> msg << "\n  at " << f.toString() } }
                catch(Throwable ignored) { }
            }
            def cause = e.cause
            if(cause && !cause.is(e)) {
                msg << "\ncaused by " << cause.getClass().name << ": " << (cause.message ?: '')
            }
        }
        catch(Throwable t) {
            return String.valueOf(e)
        }
        return msg.toString()
    }

    /**
     * Overload taking the exception itself rather than a string. Call sites that passed
     * e.toString() threw the stack trace away before it ever reached the log - which is the
     * difference between a maintainer knowing which line of their page failed and not.
     */
    static PortalErrorLog record(params,curuser,controllerName,actionName,Throwable e,slug=null,module=null) {
        return record(params,curuser,controllerName,actionName,describe(e),slug,module)
    }

    /**
     * Records a failure in code that came OUT OF THE DATABASE - a page's content or
     * preprocess, a field's options/default/display, a transition's postprocess or
     * enabledcondition, an email body, a status's runonupdate.
     *
     * Those are scripts a maintainer wrote and can fix, so what they need is which artefact
     * failed and WHERE INSIDE IT - which `e.toString()` alone never tells them. This pulls the
     * frames that belong to the evaluated script (a GroovyShell script compiles to Script<n>,
     * a GSP to the template name it was given) to the front of the message, then falls back to
     * the top few frames when nothing looks script-shaped.
     *
     * Never throws. It is called from catch blocks, and a logger that can fail while reporting
     * a failure loses the original error too.
     *
     * @param what  the artefact, e.g. "page ecdd2:dashboard preprocess" or
     *              "field ecdd2:ecdd.branch_code field_options"
     */
    static PortalErrorLog capture(Throwable e, String what, Map ctx = [:]) {
        try {
            return record(ctx.params ?: [:], ctx.user, ctx.controller ?: 'dynamic',
                          ctx.action ?: what, what + "\n" + describe(e), ctx.slug, ctx.module)
        }
        catch(Throwable t) {
            // last resort: never let the reporter mask what it was reporting
            println "PortalErrorLog.capture could not record '" + what + "': " + t
            println "  original error was: " + e
            return null
        }
    }

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
