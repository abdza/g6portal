package g6portal

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import grails.util.Holders
import org.springframework.beans.factory.InitializingBean

class GoogleOAuthController implements InitializingBean {
    
    def userService
    
    private static final List<String> SCOPES = ['email', 'profile']
    private static final String AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth'
    private static final String TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token'
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport()
    private static final def JSON_FACTORY = GsonFactory.defaultInstance
    
    private String clientId
    private String clientSecret
    private String callbackUrl
    
    void afterPropertiesSet() throws Exception {
        clientId = Holders.config.google?.oauth?.clientId
        clientSecret = Holders.config.google?.oauth?.clientSecret
        callbackUrl = Holders.config.google?.oauth?.callbackUrl
        
        if (!clientId || !clientSecret || !callbackUrl) {
            log.error """
                Google OAuth configuration is incomplete:
                clientId: ${clientId ? 'present' : 'missing'}
                clientSecret: ${clientSecret ? 'present' : 'missing'}
                callbackUrl: ${callbackUrl ? 'present' : 'missing'}
            """
        }
    }

    private void validateConfig() {
        if (!clientId || !clientSecret || !callbackUrl) {
            throw new IllegalStateException("Google OAuth configuration is missing")
        }
    }

    def initiate() {
        try {
            validateConfig()
            
            def scopeString = SCOPES.join(' ')
            def authUrl = "${AUTH_ENDPOINT}?" + [
                client_id: clientId,
                response_type: 'code',
                scope: scopeString,
                redirect_uri: callbackUrl,
                access_type: 'offline',
                prompt: 'consent',
                state: session.id
            ].collect { k, v -> "$k=${URLEncoder.encode(v, 'UTF-8')}" }.join('&')
            
            redirect(url: authUrl)
        } catch (Exception e) {
            log.error("Error initiating OAuth flow", e)
            flash.message = "Configuration error: ${e.message}"
            redirect(controller: "user", action: "login")
        }
    }

    def callback() {
        try {
            validateConfig()
            
            String code = params.code
            if (!code) {
                flash.message = "Authorization code not received"
                redirect(controller: "user", action: "login")
                return
            }

            // Exchange code for tokens
            def tokenUrl = new URL(TOKEN_ENDPOINT)
            def conn = tokenUrl.openConnection()
            conn.setRequestMethod('POST')
            conn.setDoOutput(true)
            conn.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
            
            def postData = [
                code: code,
                client_id: clientId,
                client_secret: clientSecret,
                redirect_uri: callbackUrl,
                grant_type: 'authorization_code'
            ].collect { k, v -> "$k=${URLEncoder.encode(v, 'UTF-8')}" }.join('&')
            
            conn.getOutputStream().write(postData.getBytes('UTF-8'))
            def tokenResponse = new groovy.json.JsonSlurper().parse(conn.inputStream)

            // Get user info
            def userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo"
            def userInfoConn = new URL(userInfoUrl).openConnection()
            userInfoConn.setRequestProperty("Authorization", "Bearer ${tokenResponse.access_token}")
            def userInfo = new groovy.json.JsonSlurper().parse(userInfoConn.inputStream)

            // Find or create user
            def user = User.findByEmail(userInfo.email)
            if (!user) {
                user = new User(
                    email: userInfo.email,
                    name: userInfo.name,
                    userID: userInfo.email.tokenize('@')[0],
                    isActive: true,
                    password: UUID.randomUUID().toString()
                )
                user.hashPassword(user.password)
                userService.save(user)
            }

            // Login the user
            session['userid'] = user.id
            session['curuser'] = user
            session['realuser'] = null
            session['realuserid'] = null
            session['rolestext'] = []
            session['role'] = []
            session['roletargetid'] = []

            def troles = user.treeroles(params)
            def firstone = true
            if (troles) {
                troles.each { role ->
                    session['role'] << role.role
                    session['roletargetid'] << role.id
                    session['rolestext'] << role
                    if (!user.roletargetid && firstone && !user.isAdmin) {
                        user.role = role.role
                        user.roletargetid = role.id
                        firstone = false
                    }
                }
            }

            user.lastlogin = new Date()
            userService.save(user)

            if (session['post_login']) {
                def togo = session['post_login']
                session.removeAttribute('post_login')
                redirect(togo)
            } else {
                redirect(controller: "portalPage", action: "home")
            }

        } catch (Exception e) {
            log.error("Error during OAuth callback", e)
            flash.message = "Authentication error: ${e.message}"
            redirect(controller: "user", action: "login")
        }
    }
}
