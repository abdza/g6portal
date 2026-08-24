package g6portal

/**
 * A raw HTTP endpoint owned by a module and served by the portal at
 *
 *     /svc/{module}/{slug}/...
 *
 * Portal pages return a String, so they cannot carry a binary wire protocol.
 * This is the escape hatch for anything that has to stream: it hands the raw
 * request to a handler and streams the reply straight back, with the portal
 * doing authentication in front.
 *
 * It is deliberately not specific to any one protocol. The first user is the
 * scm module (Mercurial over hgweb, Git over git-http-backend), but any module
 * can register a row and get a URL - anything CGI-shaped, or anything already
 * listening on localhost that should not be exposed directly.
 *
 * Handlers:
 *   CGI    - exec `target` as a CGI program, per RFC 3875
 *   Proxy  - forward to `target` as an upstream base URL
 *
 * Auth modes:
 *   None    - open (use only for something genuinely public)
 *   Session - an existing portal login, for browser callers
 *   Basic   - HTTP Basic against portal users; what git and hg clients send
 *   Token   - a shared secret in ?token= or X-G5-Token, for machine callers
 */
class PortalEndpoint {

    static constraints = {
        module(blank: false)
        slug(blank: false)
        name(nullable: true)
        description(nullable: true, widget: 'textarea')
        handler_type(inList: ['CGI', 'Proxy'])
        target(blank: false)
        working_dir(nullable: true)
        env_json(nullable: true, widget: 'textarea')
        header_env_json(nullable: true, widget: 'textarea')
        auth_mode(inList: ['None', 'Session', 'Basic', 'Token'])
        auth_token(nullable: true)
        allowed_roles(nullable: true)
        enabled(nullable: true)
        timeout_seconds(nullable: true)
        max_body_mb(nullable: true)
        realm(nullable: true)
    }

    static mapping = {
        description type: 'text'
        env_json type: 'text'
        header_env_json type: 'text'
        cache false          // an endpoint edit must take effect at once
    }

    String  module
    String  slug
    String  name
    String  description
    String  handler_type    = 'CGI'
    String  target
    String  working_dir
    String  env_json
    /**
     * Extra request-header -> environment-variable mappings, as JSON.
     * The CGI convention turns `Foo-Bar` into HTTP_FOO_BAR, but some programs
     * insist on a bare name: git-http-backend reads protocol negotiation from
     * GIT_PROTOCOL, not HTTP_GIT_PROTOCOL, and silently answers with the wrong
     * protocol version if it is missing.
     *   {"Git-Protocol": "GIT_PROTOCOL"}
     */
    String  header_env_json
    String  auth_mode       = 'Basic'
    String  auth_token
    String  allowed_roles
    String  realm
    Boolean enabled         = true
    Integer timeout_seconds = 300
    Integer max_body_mb     = 512

    Date dateCreated
    Date lastUpdated

    static PortalEndpoint find(String module, String slug) {
        return PortalEndpoint.findByModuleAndSlugAndEnabled(module, slug, true)
    }

    /**
     * Splits a CGI `target` into argv on spaces, honouring double quotes.
     *
     * Splitting on bare spaces is what lets an interpreter be named ahead of a
     * script ("python.exe hgweb.cgi"), which is how a CGI runs where there is no
     * shebang - i.e. Windows. But it also shreds every stock Windows install path,
     * e.g. C:\Program Files\Git\...\git-http-backend.exe. Quote such a target and
     * it is passed through as one argument.
     */
    static List<String> argv(String target) {
        def out = []
        def cur = new StringBuilder()
        def quoted = false
        def has = false          // distinguishes "" (an empty argument) from no argument
        (target ?: '').trim().each { String c ->
            if(c == '"') {
                quoted = !quoted
                has = true
            }
            else if(c == ' ' && !quoted) {
                if(has) { out << cur.toString(); cur = new StringBuilder(); has = false }
            }
            else {
                cur.append(c)
                has = true
            }
        }
        if(has) out << cur.toString()
        return out
    }

    String toString() { "${module}/${slug}" }
}
