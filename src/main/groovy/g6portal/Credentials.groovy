package g6portal

import org.apache.directory.ldap.client.api.*
import org.apache.directory.api.ldap.model.message.*
import static grails.util.Holders.config

/**
 * The one password check. The login form and PortalEndpoint Basic auth both come
 * through here, so a password that logs someone into the portal also lets their
 * hg/git client through - before this was shared, endpoints only ever compared the
 * local hash, and on an instance where Active Directory is authoritative every push
 * was refused with the user's (correct) AD password.
 *
 * `via` only labels the console lines, so a failed push is as visible as a failed login.
 */
class Credentials {

    static boolean check(User user, String password, String via = 'login') {
        if(!user || !password) return false
        def tag = via == 'login' ? 'AD login' : "AD check (${via})"
        def disable_lanid = config.server.disable_lanid
        def adenabled = PortalSetting.namedefault("adenable",0)
        def loggedin = false
        // Explain up front whether AD will even be tried, and why not, since a silent skip
        // here is the single most common reason "AD login doesn't work" reports turn out to
        // be nothing to do with AD at all - disable_lanid true means it is never attempted.
        if(disable_lanid) {
            println tag + " for " + user.userID + ": skipped - server.disable_lanid is true, local password only"
        }
        else if(!user.lanid) {
            println tag + " for " + user.userID + ": skipped - no lanid on record (use Update LAN ID first)"
        }
        else if(!adenabled) {
            println tag + " for " + user.userID + ": skipped - the adenable setting is off"
        }
        if(!disable_lanid && user.lanid && adenabled) {
            println tag + " for " + user.userID + " (lanid " + user.lanid + "): attempting primary domain"
            loggedin = bindAs(user, password, tag, 'primary',
                PortalSetting.namedefault("adserver","defaultadserver"),
                PortalSetting.namedefault("adport",636),
                PortalSetting.namedefault("adsecure",true),
                PortalSetting.namedefault("addn","defaultdn"),
                PortalSetting.namedefault("adpassword","defaultpass"),
                PortalSetting.namedefault("topdn","defaulttopdn"))
            if(!loggedin) {
                println tag + " for " + user.userID + " (lanid " + user.lanid + "): primary did not succeed, attempting secondary domain"
                loggedin = bindAs(user, password, tag, 'secondary',
                    PortalSetting.namedefault("adserver2","defaultserver2"),
                    PortalSetting.namedefault("adport2",636),
                    PortalSetting.namedefault("adsecure2",true),
                    PortalSetting.namedefault("addn2","defaultad2"),
                    PortalSetting.namedefault("adpassword2",'defaultpass2'),
                    PortalSetting.namedefault("topdn2","defaulttopdn2"))
            }
        }
        println tag + " for " + user.userID + ": " + (loggedin ? "succeeded via Active Directory" : "did not succeed via Active Directory, falling back to local password check")
        if(loggedin) return true
        // An account with no LAN ID has nothing to authenticate against Active Directory
        // with, so its own password is the only credential it has: checking it must not depend
        // on server.disable_lanid, or an instance where that key is unset cannot be logged into
        // at all - including the administrator /setup has just created. Deliberately narrow:
        // only accounts that ALREADY had a stored password qualify, so the legacy "empty
        // password is claimed on first login" path stays behind disable_lanid and an account
        // that never set one still cannot be claimed through here. The blank-hash test matters
        // for the disable_lanid case too: verifyPassword() returns TRUE for an empty hash, so an
        // account that never set a password would otherwise accept anything.
        def had_password = user.password ? true : false
        def localpassword = disable_lanid || (!user.lanid && had_password)
        def enforce_lanid = PortalSetting.namedefault("enforce_lanid",0)
        return localpassword && had_password && !(user.lanid && enforce_lanid) && user.verifyPassword(password)
    }

    private static boolean bindAs(User user, String password, String tag, String domain,
                                  adserver, adport, adsecure, addn, adpassword, topdn) {
        def loggedin = false
        def connection = null
        try {
            println tag + " (" + domain + "): connecting to " + adserver + ":" + adport + " (ssl=" + adsecure + ")"
            connection = new LdapNetworkConnection(adserver, adport, adsecure)
            connection.bind(addn, adpassword)
            println tag + " (" + domain + "): bound as service account " + addn
            def usersearch = "(sAMAccountName=" + user.lanid + ")"
            println tag + " (" + domain + "): searching base " + topdn + " filter " + usersearch
            def cursor = connection.search(topdn, usersearch, SearchScope.SUBTREE, "*")
            def found = false
            while(cursor.next() && !loggedin) {
                found = true
                def entry = cursor.get()
                println tag + " (" + domain + "): found entry " + entry.dn + ", attempting user bind"
                try {
                    connection.bind(entry.dn, password)
                    loggedin = true
                    println tag + " (" + domain + "): succeeded for " + user.lanid
                }
                catch(Exception exp) {
                    println tag + " (" + domain + "): user bind failed for " + user.lanid + " - " + exp.class.simpleName + ": " + exp.message
                }
            }
            if(!found) {
                println tag + " (" + domain + "): no entry found for filter " + usersearch
            }
        }
        catch(Exception exp) {
            println tag + " (" + domain + "): error connecting/searching - " + exp.class.name + ": " + exp.message
            exp.printStackTrace()
            PortalErrorLog.record([username: user.userID], user, "user", "authenticate", "Error connecting to ldap server " + domain + ":" + PortalErrorLog.describe(exp))
        }
        finally {
            if(connection) {
                try { connection.close() } catch(Exception ce) { println tag + " (" + domain + "): error closing connection - " + ce.message }
            }
        }
        return loggedin
    }
}
