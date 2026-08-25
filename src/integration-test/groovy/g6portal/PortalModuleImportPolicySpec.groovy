package g6portal

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Who may import over a module that already exists.
 *
 * The rule is deliberately "Developer of that module", not "Admin of that module":
 * User.modulerole() hands Admin to every Developer, and to superusers when enablesuperuser
 * is set, so an Admin test would be satisfied by roles that do not imply ownership.
 *
 * This matters because a module package is executable content - GSP page bodies, tracker
 * scripts run through GroovyShell, and CGI endpoints - so importing over someone's module
 * replaces code they own.
 */
@Integration
@Rollback
class PortalModuleImportPolicySpec extends Specification {

    PortalModuleController controller = new PortalModuleController()

    private User makeUser(String id, boolean superuser = false) {
        def u = new User(userID: id, name: id, email: "${id}@example.com")
        u.isAdmin = superuser
        u.save(flush: true, failOnError: true)
        u
    }

    private void grant(User u, String module, String role) {
        new UserRole(user: u, module: module, role: role).save(flush: true, failOnError: true)
    }

    @Unroll
    void "#who #outcome import over an existing module"() {
        given:
            def module = new PortalModule(name: 'ownedmod').save(flush: true, failOnError: true)
            def user = makeUser(who, superuser)
            if (role) {
                grant(user, roleModule, role)
            }

        expect:
            controller.mayImportInto(module, user) == allowed

        where:
            who          | superuser | roleModule  | role        || allowed
            'owner'      | false     | 'ownedmod'  | 'Developer' || true
            'superuser'  | true      | null        | null        || true
            'moduleadmin'| false     | 'ownedmod'  | 'Admin'     || false
            'otherdev'   | false     | 'someother' | 'Developer' || false
            'bystander'  | false     | null        | null        || false

            outcome = allowed ? 'may' : 'may not'
    }

    void "an anonymous caller may not import over anything"() {
        given:
            def module = new PortalModule(name: 'ownedmod').save(flush: true, failOnError: true)

        expect:
            !controller.mayImportInto(module, null)
    }

    void "a Developer of one module may not import over another"() {
        given: "the case that used to be allowed anywhere via confirmimport"
            new PortalModule(name: 'mine').save(flush: true, failOnError: true)
            def theirs = new PortalModule(name: 'theirs').save(flush: true, failOnError: true)
            def dev = makeUser('dev')
            grant(dev, 'mine', 'Developer')

        expect:
            !controller.mayImportInto(theirs, dev)
    }
}
