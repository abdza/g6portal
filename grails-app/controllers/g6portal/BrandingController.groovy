package g6portal

/**
 * The screen a client's administrator uses to make the portal look like theirs.
 *
 * Everything here writes PortalSetting rows under the 'branding' module and clears
 * BrandingService's cache, so a change is live on the next page load - no restart and no
 * config file, which is the whole point: a business user should be able to do this.
 *
 * Superuser only. Branding is not scoped to a module, so the generic "admin of some
 * module" fallback in SecurityInterceptor would be too broad - a module admin would be
 * able to restyle the entire portal for everyone. The interceptor enforces this; the
 * check is not repeated here.
 */
class BrandingController {

    static allowedMethods = [save: 'POST', reset: 'POST']

    // Bitmap formats only, and only the ones FileSecurityValidator can confirm by magic
    // number. .ico and .svg are deliberately absent: the validator cannot verify an .ico,
    // and an .svg is a script-bearing document served from the portal's own origin.
    // Browsers have taken PNG favicons for years, so nothing is lost.
    static final List<String> IMAGE_TYPES = ['png', 'jpg', 'jpeg', 'gif']
    static final long MAX_IMAGE_BYTES = 2L * 1024L * 1024L

    BrandingService brandingService

    def index() {
        [ branding: brandingService.resolved(),
          colorKeys: BrandingService.COLOR_DEFAULTS.keySet() as List,
          colorDefaults: BrandingService.COLOR_DEFAULTS ]
    }

    def save() {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(action: 'index')
            abandon = true
        }
        if (abandon) { return }

        try {
            ['app_name', 'copyright', 'team', 'homepage', 'logo_url'].each { key ->
                brandingService.put(key, params[key]?.toString()?.trim())
            }
            BrandingService.COLOR_DEFAULTS.keySet().each { key ->
                // "color_${key}" is a GString, and a GString does not hash equal to the
                // String it prints as - looking a params entry up with one silently misses.
                String field = 'color_' + key
                brandingService.put(field, params[field]?.toString()?.trim())
            }

            // An upload replaces whatever is there; ticking "remove" clears it. Doing
            // nothing to either control leaves the current image alone.
            handleImage('logo', 'branding_logo', 'logo_slug')
            handleImage('favicon', 'branding_favicon', 'favicon_slug')

            flash.message = "Branding updated"
        }
        catch (IllegalArgumentException e) {
            flash.message = e.message
        }
        redirect(action: 'index')
    }

    /** Put the palette and text back to the shipped defaults. */
    def reset() {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(action: 'index')
            abandon = true
        }
        if (abandon) { return }

        brandingService.clearAll()
        flash.message = "Branding reset to defaults"
        redirect(action: 'index')
    }

    /**
     * Store one uploaded image as a FileLink and point a branding setting at it.
     *
     * The FileLink is created with allowedroles 'All' on purpose: the logo and favicon are
     * on the login page, which nobody has authenticated for yet. Saying so explicitly
     * beats relying on 'portal' happening to sit in download_module_whitelist, which an
     * administrator is free to narrow.
     */
    private void handleImage(String field, String slug, String settingKey) {
        if (params['remove_' + field]) {
            brandingService.removeImageLink(slug, settingKey)
            return
        }

        def upload = request.getFile(field)
        if (!upload || upload.empty) { return }

        def check = FileSecurityValidator.validateFile(upload, IMAGE_TYPES, MAX_IMAGE_BYTES)
        if (!check.valid) {
            throw new IllegalArgumentException("${field.capitalize()} rejected: ${check.errors.join(', ')}")
        }

        def base = PortalSetting.namedefault('uploadfolder', System.getProperty('user.dir') + '/uploads') + '/portal'
        def dir = new File(base)
        if (!dir.exists()) { dir.mkdirs() }

        // Named after the slug rather than the uploaded filename, so re-uploading replaces
        // the image instead of leaving orphans behind in the folder.
        def target = new File(dir, "${slug}.${check.detectedType}")

        // A logo replaced by one in a different format would otherwise leave the old file
        // behind, since the name is derived from the extension.
        def previous = brandingService.imagePath(slug)
        if (previous && previous != target.absolutePath) {
            def stale = new File(previous)
            if (stale.exists()) { stale.delete() }
        }

        upload.transferTo(target)

        brandingService.saveImageLink(slug, settingKey, check.sanitizedFilename,
                                      target.absolutePath, (int) target.length())
    }
}
