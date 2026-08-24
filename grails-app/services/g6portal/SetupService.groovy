package g6portal

import grails.util.Holders
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * First-run setup for a brand new instance.
 *
 * A fresh database has no system administrator, and nothing in the portal can be reached
 * without one - every controller is behind SecurityInterceptor. So on startup, when no
 * superuser exists, a one-time token is written to a file on the server and printed to the
 * console. Whoever can read that file (i.e. whoever has access to the machine the portal
 * runs on) can use /setup to create the first administrator, and the token file is deleted
 * the moment that account exists.
 *
 * The token proves filesystem access to the server, which is the only credential available
 * before any account exists.
 */
class SetupService {

    static transactional = false

    static final int TOKEN_BYTES = 24        // 48 hex characters
    static final String DEFAULT_TOKEN_FILE = 'setup-token.txt'

    /** True while this instance has no system administrator at all. */
    boolean needssetup() {
        return User.countByIsAdmin(true) == 0
    }

    File tokenfile() {
        def configured = null
        try {
            configured = Holders.config.server?.setup_token_file
        }
        catch(Exception e) {
            configured = null
        }
        if(configured) {
            return new File(configured.toString())
        }
        return new File(System.getProperty("user.dir"), DEFAULT_TOKEN_FILE)
    }

    /**
     * Returns the current setup token, creating the file on first call. An existing file is
     * reused so that restarting the app does not invalidate a token the operator has already
     * copied out.
     */
    String ensuretoken() {
        def file = tokenfile()
        if(file.exists()) {
            def existing = file.text?.trim()
            if(existing) {
                return existing
            }
        }
        def bytes = new byte[TOKEN_BYTES]
        new SecureRandom().nextBytes(bytes)
        def token = bytes.encodeHex().toString()
        file.setText(token + "\n", "UTF-8")
        // Best effort: keep it readable only by the account running the portal. Not every
        // filesystem supports POSIX permissions, and failing to lock it down is not a reason
        // to refuse to start.
        try {
            Files.setPosixFilePermissions(Paths.get(file.absolutePath),
                [PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE] as Set)
        }
        catch(Exception e) {
            println "Setup: could not restrict permissions on " + file.absolutePath + ": " + e.message
        }
        return token
    }

    /** Constant-time comparison, so a wrong token leaks nothing through timing. */
    boolean checktoken(String supplied) {
        if(!supplied) {
            return false
        }
        def expected = null
        def file = tokenfile()
        if(file.exists()) {
            expected = file.text?.trim()
        }
        if(!expected) {
            return false
        }
        return MessageDigest.isEqual(expected.getBytes("UTF-8"), supplied.trim().getBytes("UTF-8"))
    }

    def cleartoken() {
        def file = tokenfile()
        if(file.exists()) {
            if(file.delete()) {
                println "Setup: removed " + file.absolutePath
            }
            else {
                println "Setup: COULD NOT REMOVE " + file.absolutePath + " - delete it by hand"
            }
        }
    }

    /**
     * Creates the first system administrator. Re-checks that the instance still has no
     * superuser inside the transaction, so two people racing the form cannot both win.
     *
     * @return the new User, or null with the reason in errors
     */
    def createsuperuser(userid, name, email, password, errors = []) {
        def created = null
        User.withTransaction { tstatus ->
            if(!needssetup()) {
                errors << "This instance already has a system administrator"
                return
            }
            if(User.findByUserID(userid)) {
                errors << "A user with user id ${userid} already exists"
                return
            }
            def user = new User()
            user.userID = userid
            user.name = name
            user.email = email
            user.isAdmin = true
            user.isActive = true
            user.resetPassword = false
            user.hashPassword(password)
            if(!user.save(flush:true)) {
                user.errors.allErrors.each { errors << it.toString() }
                tstatus.setRollbackOnly()
                return
            }
            created = user
        }
        return created
    }

    /**
     * Makes system administrators count as Admin of every module. Without it isAdmin only
     * carries the checks that test it directly, and User.modulerole() reports no role for
     * modules the account has no explicit UserRole row on.
     */
    def enablesuperuser() {
        PortalSetting.withTransaction { tstatus ->
            def setting = PortalSetting.findByName('enablesuperuser')
            if(!setting) {
                setting = new PortalSetting(name:'enablesuperuser', module:'portal')
            }
            setting.type = 'Number'
            setting.number = 1
            if(!setting.save(flush:true)) {
                println "Setup: could not save enablesuperuser setting: " + setting.errors.allErrors
            }
        }
    }

    /**
     * Called at startup. Writes and announces the token on an instance that has no
     * administrator yet; on one that does, points out a token file left lying around rather
     * than deleting a file nobody asked us to touch.
     */
    def announce() {
        try {
            if(needssetup()) {
                def token = ensuretoken()
                println ""
                println "*".multiply(78)
                println "* This instance has no system administrator yet."
                println "*"
                println "* Open  /setup  and enter this token to create the first administrator:"
                println "*"
                println "*     " + token
                println "*"
                println "* It is also in:  " + tokenfile().absolutePath
                println "* That file is deleted as soon as the administrator account exists."
                println "*".multiply(78)
                println ""
            }
            else if(tokenfile().exists()) {
                println "Setup: " + tokenfile().absolutePath + " still exists but this instance " +
                        "already has an administrator - that token no longer works, delete the file."
            }
        }
        catch(Exception e) {
            println "Setup: could not check for a first-run administrator: " + e
        }
    }
}
