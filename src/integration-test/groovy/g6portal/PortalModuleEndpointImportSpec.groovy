package g6portal

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import groovy.json.JsonOutput

/**
 * An endpoint's target is a program this server execs, which is why the endpoint admin
 * screens are restricted to superusers. Importing a module must not be a way around that:
 * a module Developer could otherwise ship endpointlist.json in a package and get a CGI row
 * they are not allowed to create through the UI.
 *
 * Not @Rollback - importmodule manages its own transactions.
 */
@Integration
class PortalModuleEndpointImportSpec extends Specification {

    static final String MODULE = 'endpointspec'

    File migrationFolder

    def setup() {
        def root = PortalSetting.withNewSession {
            PortalSetting.namedefault('migrationfolder',
                System.getProperty('user.dir') + '/uploads/modulemigration')
        }
        migrationFolder = new File(root, MODULE)
        migrationFolder.deleteDir()
        migrationFolder.mkdirs()

        // A package carrying one CGI endpoint whose target exists on this machine, so
        // nothing else would hold it back from being created enabled.
        new File(migrationFolder, 'endpointlist.json').write(JsonOutput.toJson([[
            module: MODULE, slug: 'shell', name: 'shell', description: null,
            handler_type: 'CGI', target: '/bin/sh', working_dir: null,
            env_json: null, header_env_json: null, auth_mode: 'None',
            allowed_roles: null, realm: null, timeout_seconds: 30, max_body_mb: 1,
            enabled: true, had_auth_token: false
        ]]))
    }

    def cleanup() {
        migrationFolder?.deleteDir()
        PortalSetting.withNewTransaction {
            PortalEndpoint.findAllByModule(MODULE)*.delete(flush: true)
            PortalModule.findAllByName(MODULE)*.delete(flush: true)
        }
    }

    private PortalModule makeModule() {
        PortalModule.withNewTransaction {
            new PortalModule(name: MODULE).save(flush: true, failOnError: true)
        }
    }

    void "an import that does not ask for endpoints creates none"() {
        given:
            def module = makeModule()

        when: "the default - what a module Developer's import passes"
            PortalModule.withNewSession { module.importmodule(false, false, false) }

        then:
            PortalEndpoint.withNewSession { PortalEndpoint.findAllByModule(MODULE) } == []
    }

    void "an import that asks for endpoints creates them"() {
        given:
            def module = makeModule()

        when: "what a superuser's import, and the bundled-package importer, pass"
            PortalModule.withNewSession { module.importmodule(false, false, false, null, true) }

        then: "and it arrives live, which is what the gate above is protecting"
            def created = PortalEndpoint.withNewSession {
                PortalEndpoint.findByModuleAndSlug(MODULE, 'shell')
            }
            created.target == '/bin/sh'
            // importendpoints only disables a CGI endpoint whose target is missing from
            // this machine. /bin/sh is not missing, so nothing holds it back - and with
            // auth_mode None, serve() answers before it checks anybody.
            created.enabled
            created.auth_mode == 'None'
    }
}
