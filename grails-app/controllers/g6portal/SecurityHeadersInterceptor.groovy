package g6portal

/**
 * Adds standard HTTP security response headers to every response, including
 * the login page - unlike SecurityInterceptor, this one has no exceptions,
 * since these headers are defense-in-depth and should never be skipped.
 */
class SecurityHeadersInterceptor {

    SecurityHeadersInterceptor() {
        matchAll()
    }

    boolean before() {
        response.setHeader('X-Frame-Options', 'SAMEORIGIN')
        response.setHeader('X-Content-Type-Options', 'nosniff')
        response.setHeader('X-XSS-Protection', '1; mode=block')
        response.setHeader('Content-Security-Policy',
            "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'self'")
        // Browsers ignore this header entirely unless it arrives over HTTPS (RFC 6797),
        // so it's safe to always set - only meaningful once TLS is confirmed in front.
        if(request.isSecure()) {
            response.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains')
        }
        return true
    }

    boolean after() { true }

    void afterView() {
    }
}
