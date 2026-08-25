package g6portal

import grails.testing.mixin.integration.Integration
import grails.util.Holders
import spock.lang.Specification
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Not @Rollback, for the same reason as BrandingServiceSpec: installBundled() runs from
 * BootStrap with no ambient transaction and has to manage its own, so wrapping the test in
 * one would hide whether it does.
 *
 * The packages here are produced by the real exporter and zipped with the real
 * PortalService.compress, so what gets imported is the same shape the module export screen
 * produces rather than hand-written JSON that might drift from it.
 */
@Integration
class BundledModuleServiceSpec extends Specification {

    static final String MODULE = 'testbundle'

    BundledModuleService bundledModuleService

    File bundleFolder

    def setup() {
        bundleFolder = File.createTempDir('bundled_', '_spec')
        Holders.config.merge([portal: [bundled_modules_folder: bundleFolder.absolutePath]])
    }

    def cleanup() {
        bundleFolder?.deleteDir()
        // The service extracts into the real migration folder under user.dir, so the test
        // has to take its own unpacked copy away again.
        new File(System.getProperty('user.dir'), "uploads/modulemigration/${MODULE}").deleteDir()
        PortalSetting.withNewTransaction {
            PortalSetting.findAllByModule(MODULE)*.delete(flush: true)
            PortalModuleImportLog.findAllByModule(MODULE)*.delete(flush: true)
            PortalModule.findAllByName(MODULE)*.delete(flush: true)
        }
    }

    /** Build a real export package for a module carrying one setting, then remove it. */
    private void buildPackageAndRemoveModule(String settingValue) {
        def exportDir = File.createTempDir('export_', '_spec')
        PortalSetting.withNewTransaction {
            def module = new PortalModule(name: MODULE).save(flush: true, failOnError: true)
            new PortalSetting(module: MODULE, name: 'greeting', type: 'Text', text: settingValue)
                .save(flush: true, failOnError: true)
            module.exportmodule(false, false, false, exportDir.path)
        }
        PortalService.compress(exportDir, new File(bundleFolder, "${MODULE}.zip"))
        exportDir.deleteDir()

        PortalSetting.withNewTransaction {
            PortalSetting.findAllByModule(MODULE)*.delete(flush: true)
            PortalModule.findAllByName(MODULE)*.delete(flush: true)
        }
    }

    void "a bundled package is imported when the module is not installed"() {
        given:
            buildPackageAndRemoveModule('hello from the package')

        when:
            bundledModuleService.installBundled()

        then: "the module and the setting it carried are both present"
            PortalSetting.withNewSession {
                PortalModule.findByName(MODULE) != null &&
                PortalSetting.findByModuleAndName(MODULE, 'greeting')?.text == 'hello from the package'
            }

        and: "the import is on the audit trail"
            PortalSetting.withNewSession {
                PortalModuleImportLog.findByModule(MODULE)?.staffname == 'Bundled package'
            }
    }

    void "an already installed module is left completely alone"() {
        given: "the package is installed, then edited the way a client would"
            buildPackageAndRemoveModule('hello from the package')
            bundledModuleService.installBundled()
            PortalSetting.withNewTransaction {
                def s = PortalSetting.findByModuleAndName(MODULE, 'greeting')
                s.text = 'edited by the client'
                s.save(flush: true, failOnError: true)
            }

        when: "the portal restarts with the same package still in place"
            bundledModuleService.installBundled()

        then: "the client's edit survives - the package did not overwrite it"
            PortalSetting.withNewSession {
                PortalSetting.findByModuleAndName(MODULE, 'greeting').text
            } == 'edited by the client'

        and: "and it was not imported a second time"
            PortalSetting.withNewSession {
                PortalModuleImportLog.countByModule(MODULE)
            } == 1
    }

    void "an entry pointing outside the module folder is refused"() {
        given: "a package carrying a path traversal entry"
            File escapee = new File(bundleFolder.parentFile, 'bundled-escaped.txt')
            escapee.delete()
            new ZipOutputStream(new FileOutputStream(new File(bundleFolder, "${MODULE}.zip"))).withCloseable { zos ->
                zos.putNextEntry(new ZipEntry('../bundled-escaped.txt'))
                zos.write('owned'.bytes)
                zos.closeEntry()
            }

        when:
            bundledModuleService.installBundled()

        then: "nothing was written outside the folder"
            !escapee.exists()

        and: "startup was not brought down by it"
            noExceptionThrown()

        cleanup:
            escapee.delete()
    }

    void "a package whose module cannot be named is skipped"() {
        given:
            new File(bundleFolder, '../evil.zip').createNewFile()

        when:
            bundledModuleService.installBundled()

        then:
            noExceptionThrown()
    }
}
