package g6portal

import grails.validation.ValidationException
import groovy.json.JsonSlurper
import java.util.concurrent.TimeUnit
import static org.springframework.http.HttpStatus.*

/**
 * Serves PortalEndpoint rows at /svc/{module}/{slug}/...
 *
 * Everything here streams. Nothing buffers a whole request or reply in memory,
 * because the first caller is a version control client pushing a packfile.
 */
class PortalEndpointController {

    // serve() is deliberately absent: a wire protocol picks its own verbs.
    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def serve() {
        // NOTE: do not touch `params` anywhere on this path. Reading it makes
        // Grails parse the request body, which consumes the input stream - the
        // CGI then gets an empty stdin and a push silently sends nothing. The
        // module and slug are taken from the URI for exactly that reason.
        def base = "${request.contextPath}/svc/"
        def uri = request.forwardURI ?: ''
        if(!uri.startsWith(base)) return fail(404, "Not an endpoint URL")
        def rest = uri.substring(base.length())
        def bits = rest.tokenize('/')
        if(bits.size() < 2) return fail(404, "Endpoint URL needs a module and a slug")
        def modName = bits[0], slugName = bits[1]

        def endpoint = PortalEndpoint.find(modName, slugName)
        if(!endpoint) {
            return fail(404, "No such endpoint: ${modName}/${slugName}")
        }

        // Everything after /svc/{module}/{slug} is the handler's own path.
        def prefix = base + endpoint.module + '/' + endpoint.slug
        def pathInfo = uri.startsWith(prefix) ? uri.substring(prefix.length()) : ''

        def who = authenticate(endpoint)
        if(who == null) return          // authenticate() has already replied

        try {
            if(endpoint.handler_type == 'Proxy') return runProxy(endpoint, pathInfo, who)
            return runCgi(endpoint, pathInfo, who)
        }
        catch(Exception e) {
            log.error("endpoint ${endpoint} failed", e)
            PortalErrorLog.record([uri: uri], null, controllerName, actionName, e,
                                  endpoint.slug, endpoint.module)
            if(!response.committed) return fail(500, "Endpoint error")
        }
        return null
    }

    // ------------------------------------------------------- maintenance UI
    // Superuser-only, enforced in SecurityInterceptor (see the portalEndpoint branch).

    def index(Integer max) {
        params.max = Math.min(max ?: 25, 100)
        def endpoints = PortalEndpoint.list(params)
        respond endpoints, model:[curuser: session.curuser,
                                  portalEndpointCount: PortalEndpoint.count(),
                                  params: params]
    }

    def show(Long id) {
        def endpoint = PortalEndpoint.get(id)
        if(!endpoint) { notFound(); return }
        respond endpoint, model:[curuser: session.curuser, checks: checkTarget(endpoint)]
    }

    def create() {
        respond new PortalEndpoint(params), model:[curuser: session.curuser]
    }

    def save(PortalEndpoint portalEndpoint) {
        if(portalEndpoint == null) { notFound(); return }
        try {
            PortalEndpoint.withTransaction { tstatus ->
                portalEndpoint.save(flush:true, failOnError:true)
            }
        }
        catch(ValidationException e) {
            respond portalEndpoint.errors, view:'create', model:[curuser: session.curuser]
            return
        }
        println "portalEndpoint: ${session.curuser?.userID} created ${portalEndpoint}"
        flash.message = "Endpoint ${portalEndpoint} created"
        redirect action:"show", id:portalEndpoint.id
    }

    def edit(Long id) {
        def endpoint = PortalEndpoint.get(id)
        if(!endpoint) { notFound(); return }
        respond endpoint, model:[curuser: session.curuser]
    }

    def update(PortalEndpoint portalEndpoint) {
        if(portalEndpoint == null) { notFound(); return }
        try {
            PortalEndpoint.withTransaction { tstatus ->
                portalEndpoint.save(flush:true, failOnError:true)
            }
        }
        catch(ValidationException e) {
            respond portalEndpoint.errors, view:'edit', model:[curuser: session.curuser]
            return
        }
        println "portalEndpoint: ${session.curuser?.userID} updated ${portalEndpoint}"
        flash.message = "Endpoint ${portalEndpoint} updated"
        redirect action:"show", id:portalEndpoint.id
    }

    def delete(Long id) {
        if(id == null) { notFound(); return }
        def endpoint = PortalEndpoint.get(id)
        if(!endpoint) { notFound(); return }
        def label = endpoint.toString()
        PortalEndpoint.withTransaction { tstatus ->
            endpoint.delete(flush:true)
        }
        println "portalEndpoint: ${session.curuser?.userID} deleted ${label}"
        flash.message = "Endpoint ${label} deleted"
        redirect action:"index", method:"GET"
    }

    /**
     * What this row would actually do if a request arrived now - the thing that is
     * invisible in a form full of paths. A module package can only carry the shape of
     * an endpoint, never one machine's paths, so "the target is not on this server" is
     * the normal state of a freshly imported row rather than an exotic failure.
     */
    private Map checkTarget(PortalEndpoint endpoint) {
        def out = [problems: [], notes: []]
        if(endpoint.handler_type == 'CGI') {
            def argv = PortalEndpoint.argv(endpoint.target)
            if(!argv) {
                out.problems << "No target set."
            }
            else {
                out.notes << "Runs: " + argv.collect { "[" + it + "]" }.join(' ')
                def prog = new File(argv[0])
                if(!prog.exists()) {
                    out.problems << "Program not found on this server: ${argv[0]}" +
                        (endpoint.target.contains(' ') && !endpoint.target.contains('"')
                            ? " - the target contains a space and is split into arguments; quote the path if it is one program."
                            : "")
                }
                else if(!prog.canExecute()) {
                    out.problems << "Program is not executable by the portal's account: ${argv[0]}"
                }
                else {
                    out.notes << "Program exists and is executable."
                }
            }
            if(endpoint.working_dir && !(new File(endpoint.working_dir).isDirectory())) {
                out.problems << "Working directory does not exist: ${endpoint.working_dir}"
            }
        }
        else if(endpoint.handler_type == 'Proxy') {
            out.notes << "Forwards to ${endpoint.target}"
        }
        ['env_json', 'header_env_json'].each { String f ->
            def v = endpoint."${f}"
            if(v?.trim()) {
                try { new JsonSlurper().parseText(v) }
                catch(Exception e) { out.problems << "${f} is not valid JSON: ${e.message}" }
            }
        }
        if(endpoint.auth_mode == 'Token' && !endpoint.auth_token) {
            out.problems << "Auth mode is Token but no token is set - every request will be refused."
        }
        if(endpoint.auth_mode == 'None') {
            out.notes << "Anyone who can reach the URL can use this endpoint (auth mode None)."
        }
        if(!endpoint.enabled) {
            out.notes << "Disabled - PortalEndpoint.find() ignores it, so nothing is served."
        }
        return out
    }

    protected void notFound() {
        flash.message = "Endpoint not found"
        redirect action:"index", method:"GET"
    }

    // ---------------------------------------------------------------- auth
    /**
     * Returns the resolved username on success, or null after having written a
     * 401/403. An empty string means "allowed, no identity".
     */
    private String authenticate(PortalEndpoint endpoint) {
        def mode = endpoint.auth_mode ?: 'Basic'

        if(mode == 'None') return ''

        if(mode == 'Token') {
            def given = (queryParam('token') ?: request.getHeader('X-G5-Token'))?.toString()
            def expected = endpoint.auth_token
            if(!expected) { fail(403, "Endpoint has no token configured"); return null }
            if(!given || !java.security.MessageDigest.isEqual(
                    expected.getBytes('UTF-8'), given.getBytes('UTF-8'))) {
                fail(403, "Bad token"); return null
            }
            return ''
        }

        if(mode == 'Session') {
            def u = session?.curuser ?: (session?.userid ? User.get(session.userid) : null)
            if(!u) { fail(401, "Not logged in"); return null }
            if(!roleOk(endpoint, u)) { fail(403, "Not authorised for this endpoint"); return null }
            return u.userID
        }

        // Basic - what git and hg send
        def header = request.getHeader('Authorization')
        if(!header || !header.toLowerCase().startsWith('basic ')) return challenge(endpoint)
        def decoded
        try { decoded = new String(header.substring(6).trim().decodeBase64(), 'UTF-8') }
        catch(Exception e) { return challenge(endpoint) }
        def idx = decoded.indexOf(':')
        if(idx < 0) return challenge(endpoint)
        def username = decoded.substring(0, idx)
        def password = decoded.substring(idx + 1)

        def user = User.findByUserID(username, [cache: false]) ?:
                   User.findByLanid(username, [cache: false])
        // verifyPassword() returns TRUE when the stored hash is blank, which
        // would let an unset account through on any password. Refuse those.
        if(!user || !user.password || !user.verifyPassword(password)) {
            log.warn("endpoint ${endpoint}: failed basic auth for '${username}'")
            return challenge(endpoint)
        }
        if(user.isActive == false) { fail(403, "Account is not active"); return null }
        if(!roleOk(endpoint, user)) { fail(403, "Not authorised for this endpoint"); return null }
        return user.userID
    }

    private boolean roleOk(PortalEndpoint endpoint, User user) {
        def needed = endpoint.allowed_roles?.tokenize(',')*.trim()?.findAll { it }
        if(!needed) return true
        if(user.isAdmin) return true
        def held = []
        try { held = user.modulerole(endpoint.module) ?: [] } catch(Exception e) { held = [] }
        return needed.any { it in held }
    }

    private String challenge(PortalEndpoint endpoint) {
        response.setHeader('WWW-Authenticate',
            "Basic realm=\"${endpoint.realm ?: (endpoint.module + '/' + endpoint.slug)}\"")
        fail(401, "Authentication required")
        return null
    }

    /** Query string parsing that does not touch `params` (see serve()). */
    private String queryParam(String name) {
        def qs = request.queryString
        if(!qs) return null
        for(pair in qs.tokenize('&')) {
            int i = pair.indexOf('=')
            if(i > 0 && java.net.URLDecoder.decode(pair.substring(0, i), 'UTF-8') == name) {
                return java.net.URLDecoder.decode(pair.substring(i + 1), 'UTF-8')
            }
        }
        return null
    }

    private fail(int code, String message) {
        response.status = code
        response.contentType = 'text/plain; charset=utf-8'
        response.outputStream.withStream { it.write((message + "\n").getBytes('UTF-8')) }
        return null
    }

    // ----------------------------------------------------------------- CGI
    /**
     * Runs `target` as a CGI program (RFC 3875). Request body goes to stdin on
     * its own thread, the reply's headers are parsed off stdout and the rest is
     * pumped straight to the client, so a large clone or push never lands in
     * memory.
     */
    private runCgi(PortalEndpoint endpoint, String pathInfo, String who) {
        def argv = PortalEndpoint.argv(endpoint.target)
        def pb = new ProcessBuilder(argv)
        if(endpoint.working_dir) {
            def d = new File(endpoint.working_dir)
            if(d.isDirectory()) pb.directory(d)
        }

        def env = pb.environment()
        env.put('GATEWAY_INTERFACE', 'CGI/1.1')
        env.put('SERVER_PROTOCOL', request.protocol ?: 'HTTP/1.1')
        env.put('SERVER_SOFTWARE', 'g6portal')
        env.put('SERVER_NAME', request.serverName ?: 'localhost')
        env.put('SERVER_PORT', String.valueOf(request.serverPort ?: 80))
        env.put('REQUEST_METHOD', request.method)
        env.put('PATH_INFO', pathInfo ?: '/')
        env.put('QUERY_STRING', request.queryString ?: '')
        env.put('SCRIPT_NAME', "${request.contextPath}/svc/${endpoint.module}/${endpoint.slug}".toString())
        env.put('REMOTE_ADDR', request.remoteAddr ?: '')
        env.put('REMOTE_USER', who ?: '')
        env.put('CONTENT_TYPE', request.contentType ?: '')
        def clen = request.getHeader('Content-Length')
        if(clen) env.put('CONTENT_LENGTH', clen)

        // Pass the request headers through the usual HTTP_* convention.
        request.headerNames?.each { String h ->
            if(h.equalsIgnoreCase('authorization')) return   // never leak the credentials on
            def key = 'HTTP_' + h.toUpperCase().replace('-', '_')
            env.put(key, request.getHeader(h) ?: '')
        }

        // Headers some CGI programs expect under a bare name rather than HTTP_*.
        parseJsonMap(endpoint.header_env_json, endpoint).each { header, varname ->
            def v = request.getHeader(header.toString())
            if(v) env.put(varname.toString(), v)
        }

        extraEnv(endpoint).each { k, v -> env.put(k.toString(), v?.toString() ?: '') }

        // The request body MUST be read on the request thread. A Tomcat request is
        // thread-confined and recycled, so reading its input stream from a worker
        // thread quietly yields nothing - the CGI then sees an empty stdin and the
        // client reports "the remote end hung up unexpectedly". Spool it to a temp
        // file first, bounded by max_body_mb, then feed the CGI from that.
        File spool = null
        if(request.method in ['POST', 'PUT', 'PATCH']) {
            spool = File.createTempFile('g5svc-', '.body')
            spool.deleteOnExit()
            long cap = (long)(endpoint.max_body_mb ?: 512) * 1024L * 1024L
            long spooled = 0
            spool.withOutputStream { os ->
                def ins = request.inputStream
                byte[] buf = new byte[65536]
                int r
                while((r = ins.read(buf)) != -1) {
                    spooled += r
                    if(spooled > cap) throw new IOException("Request body exceeds ${endpoint.max_body_mb}MB")
                    os.write(buf, 0, r)
                }
            }
        }

        def proc = pb.start()

        def feeder = Thread.start {
            try {
                proc.outputStream.withStream { os ->
                    if(spool) spool.withInputStream { it.transferTo(os) }
                }
            }
            catch(Exception e) { log.warn("endpoint ${endpoint}: feeding stdin failed: ${e}") }
        }
        def errbuf = new StringBuffer()
        def errpump = proc.consumeProcessErrorStream(errbuf)

        def ok = pumpCgiResponse(proc.inputStream)

        def finished = proc.waitFor(endpoint.timeout_seconds ?: 300, TimeUnit.SECONDS)
        if(!finished) { proc.destroyForcibly(); log.error("endpoint ${endpoint}: CGI timed out") }
        try { feeder.join(5000) } catch(Exception e) { }
        try { errpump.join(5000) } catch(Exception e) { }
        if(errbuf.length()) log.warn("endpoint ${endpoint} stderr: ${errbuf.toString().take(2000)}")
        try { spool?.delete() } catch(Exception e) { }
        if(!ok && !response.committed) fail(502, "Endpoint produced no response")
        return null
    }

    /** Parse CGI headers off the stream, then stream the body through. */
    private boolean pumpCgiResponse(InputStream ins) {
        def head = new ByteArrayOutputStream()
        int prev = -1, cur
        boolean sawHeaders = false
        while((cur = ins.read()) != -1) {
            head.write(cur)
            // header block ends at a blank line
            if(cur == 10) {
                def bytes = head.toByteArray()
                int n = bytes.length
                if((n >= 2 && bytes[n-2] == (byte)10) ||
                   (n >= 4 && bytes[n-4] == (byte)13 && bytes[n-3] == (byte)10 && bytes[n-2] == (byte)13)) {
                    sawHeaders = true
                    break
                }
            }
            prev = cur
            if(head.size() > 65536) break        // a sane cap on a header block
        }
        if(!sawHeaders && head.size() == 0) return false

        def out = response.outputStream
        new String(head.toByteArray(), 'UTF-8').readLines().each { line ->
            if(!line?.trim()) return
            int c = line.indexOf(':')
            if(c < 0) return
            def key = line.substring(0, c).trim()
            def val = line.substring(c + 1).trim()
            if(key.equalsIgnoreCase('Status')) {
                // Swallowing this hands the client a 200 for what the CGI called a failure -
                // a git or hg client would report success on an error.
                try { response.status = val.tokenize(' ')[0] as int }
                catch(Exception e) {
                    PortalErrorLog.capture(e, "endpoint ${endpoint.module}/${endpoint.slug} returned an unreadable Status header: ${val}",
                                           [controller: 'portalEndpoint', action: 'cgi status header',
                                            module: endpoint.module, slug: endpoint.slug])
                }
            }
            else if(key.equalsIgnoreCase('Content-Type')) response.contentType = val
            else response.setHeader(key, val)
        }
        response.flushBuffer()      // commit now, so Grails does not look for a view
        ins.transferTo(out)
        out.flush()
        return true
    }

    // --------------------------------------------------------------- Proxy
    private runProxy(PortalEndpoint endpoint, String pathInfo, String who) {
        def url = endpoint.target.replaceAll('/+$', '') + (pathInfo ?: '') +
                  (request.queryString ? ('?' + request.queryString) : '')
        def conn = new URL(url).openConnection()
        conn.setRequestMethod(request.method)
        conn.setInstanceFollowRedirects(false)
        conn.setConnectTimeout(15000)
        conn.setReadTimeout((endpoint.timeout_seconds ?: 300) * 1000)
        request.headerNames?.each { String h ->
            if(h.toLowerCase() in ['host', 'authorization', 'connection', 'content-length']) return
            conn.setRequestProperty(h, request.getHeader(h))
        }
        if(who) conn.setRequestProperty('X-G5-User', who)
        if(request.method in ['POST', 'PUT', 'PATCH']) {
            conn.setDoOutput(true)
            conn.outputStream.withStream { os -> request.inputStream.transferTo(os) }
        }
        def status = conn.responseCode
        response.status = status
        conn.headerFields?.each { k, v ->
            if(k && !(k.toLowerCase() in ['transfer-encoding', 'connection'])) {
                response.setHeader(k, v.join(','))
            }
        }
        response.flushBuffer()
        def src = (status >= 400) ? (conn.errorStream ?: conn.inputStream) : conn.inputStream
        if(src) src.withStream { it.transferTo(response.outputStream) }
        response.outputStream.flush()
        return null
    }

    private Map extraEnv(PortalEndpoint endpoint) {
        return parseJsonMap(endpoint.env_json, endpoint)
    }

    private Map parseJsonMap(String json, PortalEndpoint endpoint) {
        if(!json?.trim()) return [:]
        try { return (new JsonSlurper().parseText(json) as Map) ?: [:] }
        catch(Exception e) {
            log.error("endpoint ${endpoint}: not valid JSON: ${json.take(120)}")
            return [:]
        }
    }
}
