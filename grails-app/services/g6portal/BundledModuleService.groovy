package g6portal

import grails.util.Holders

/**
 * Installs module packages shipped alongside the application, so a preconfigured instance
 * can be handed to a client as "unzip this and run it".
 *
 * Without this, standing up a configured portal is an interactive chore: boot, read the
 * one-time /setup token off the server's filesystem, create an administrator, log in, and
 * walk the module import screens for every package. Every step needs a human at a browser.
 *
 * Point portal.bundled_modules_folder at a directory of <modulename>.zip files - the same
 * archives the module export screen produces - and each one that is not installed yet is
 * imported on startup:
 *
 *     dist/
 *       g6portal-h2.jar
 *       application.yml
 *       bundled-modules/
 *         claims.zip
 *
 * A module that already exists is left completely alone. Upgrading one stays a deliberate
 * act through the admin UI, where a human sees the diff first - an unattended overwrite
 * would silently discard whatever the client had changed since.
 */
class BundledModuleService {

    static final String DEFAULT_FOLDER = 'bundled-modules'

    /** A module name has to survive being used as a folder name and a DB lookup key. */
    static final java.util.regex.Pattern SAFE_NAME = ~/^[A-Za-z0-9][A-Za-z0-9 ._-]{0,63}$/

    /**
     * Import every bundled package whose module is not installed yet.
     *
     * Called from BootStrap, so nothing here may throw: a malformed package must not stop
     * the portal from starting. Each package is handled independently and its failure is
     * recorded, then the next one is tried.
     */
    void installBundled() {
        File folder = resolveFolder()
        if (!folder?.isDirectory()) {
            return
        }

        def packages = folder.listFiles()?.findAll {
            it.isFile() && it.name.toLowerCase().endsWith('.zip')
        }?.sort { it.name }

        if (!packages) {
            return
        }

        // println rather than log.info: logback.xml sets the root level to error, so an
        // info line would be invisible, and this reports an unattended change to the
        // database that whoever is watching the console needs to see. The module import
        // code this calls into reports its progress the same way.
        println "Bundled modules: found ${packages.size()} package(s) in ${folder.absolutePath}"
        packages.each { File pkg ->
            try {
                // BootStrap runs outside a request, so there is no OpenSessionInView to put
                // a Hibernate session on the thread. importmodule() reads the migrationfolder
                // setting before opening its own transaction, and PortalModule.namedefault
                // would fail with "No Session found for current thread" without this.
                // One session per package, so a failure cannot poison the next.
                PortalModule.withNewSession {
                    install(pkg)
                }
            }
            catch (Throwable e) {
                // Deliberately catching Throwable: this runs during startup, and one bad
                // archive must not take the whole portal down with it.
                println "Bundled modules: ${pkg.name} FAILED to import - ${e}"
                log.error "Bundled modules: ${pkg.name} failed to import", e
                recordFailure(pkg, e)
            }
        }
    }

    /** The configured folder, resolved against the working directory when relative. */
    private File resolveFolder() {
        String configured = Holders.config.getProperty('portal.bundled_modules_folder') ?: DEFAULT_FOLDER
        File folder = new File(configured)
        folder.isAbsolute() ? folder : new File(System.getProperty('user.dir'), configured)
    }

    private void install(File pkg) {
        String moduleName = pkg.name[0..-5]   // drop '.zip'

        if (!SAFE_NAME.matcher(moduleName).matches()) {
            println "Bundled modules: skipping ${pkg.name}, '${moduleName}' is not a usable module name"
            return
        }

        boolean exists = PortalModule.withTransaction { PortalModule.findByName(moduleName) != null }
        if (exists) {
            println "Bundled modules: ${moduleName} is already installed, leaving it untouched"
            return
        }

        File migrationFolder = new File(migrationRoot(), moduleName)
        if (migrationFolder.exists()) {
            migrationFolder.deleteDir()
        }
        PortalService.extract(pkg, migrationFolder)

        // The export screen only writes these files when the exporter ticked the matching
        // box, so their presence is what says whether the package carries them.
        boolean fileOn = new File(migrationFolder, 'filelinklist.json').exists()
        boolean treeOn = new File(migrationFolder, 'treelist.json').exists()

        // staff is always off. userrolelist.json names people by id on the machine the
        // package was exported from; on a fresh install those accounts do not exist, and
        // on any other install they are the wrong people. Roles are assigned per
        // deployment, so an unattended import must not guess at them.
        def module = PortalModule.withTransaction {
            def m = new PortalModule(name: moduleName)
            m.save(flush: true, failOnError: true)
            m
        }

        module.importmodule(fileOn, false, treeOn)

        println "Bundled modules: imported ${moduleName} (files=${fileOn}, trees=${treeOn})"
        recordImport(moduleName, pkg, fileOn, treeOn)
    }

    private String migrationRoot() {
        def curfolder = System.getProperty('user.dir')
        PortalSetting.namedefault('migrationfolder', curfolder + '/uploads/modulemigration')
    }

    /** Same audit trail the interactive import writes, attributed to the package. */
    private void recordImport(String moduleName, File pkg, boolean fileOn, boolean treeOn) {
        PortalModuleImportLog.withTransaction {
            new PortalModuleImportLog(
                module: moduleName,
                staffname: 'Bundled package',
                remarks: "Imported automatically at startup from ${pkg.name} " +
                         "(files=${fileOn}, trees=${treeOn}, staff roles not imported)."
            ).save(flush: true, failOnError: true)
        }
    }

    private void recordFailure(File pkg, Throwable e) {
        try {
            PortalErrorLog.withTransaction {
                PortalErrorLog.record([package: pkg.name], null, 'portal', 'bundledModuleImport', e)
            }
        }
        catch (Throwable ignored) {
            // The log is a convenience; never let recording a failure become one.
        }
    }
}
