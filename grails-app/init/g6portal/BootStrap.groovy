package g6portal

class BootStrap {

    def setupService
    def bundledModuleService
    def dataSource

    def init = { servletContext ->
        // On a brand new database nothing can be reached until a system administrator
        // exists, so write and announce the one-time /setup token when there is none.
        setupService.announce()

        // Import any module packages shipped alongside the application that are not
        // installed yet, so a preconfigured instance works on first boot without anyone
        // walking the import screens. Modules already present are left alone, and the
        // service swallows its own failures - a bad package must not stop startup.
        bundledModuleService.installBundled()

        // Attachments now find their tracker by module + tracker_slug; link the ones saved
        // with only a tracker_id before that change.
        FileLink.backfillTrackerSlug(dataSource)

        PortalAuditWriter.start()
    }
    def destroy = {
        // Writes out any audit rows still queued before the app goes down.
        PortalAuditWriter.shutdown()
    }
}
