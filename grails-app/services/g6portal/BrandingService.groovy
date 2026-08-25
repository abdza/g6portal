package g6portal

import grails.util.Holders
import grails.gorm.transactions.Transactional

/**
 * Client branding: the small set of values a customer changes to make an install look
 * like theirs - name, logo, favicon, footer text, and the theme palette.
 *
 * Everything lives in PortalSetting rows under the 'branding' module, so a client's admin
 * changes it from the UI and sees the result on the next page load. No restart, no config
 * file, no rebuild.
 *
 * Resolution order for every key is:
 *   1. PortalSetting(module:'branding')  - what the client set in the UI
 *   2. grailsApplication.config info.app.*  - what a deployment set before this existed
 *   3. the built-in default
 * Step 2 is what keeps already-deployed instances looking exactly as they do today: their
 * application.yml still wins over the defaults until somebody sets a value in the UI.
 *
 * The resolved map is cached in memory because main.gsp reads it on *every* page render.
 * It is a handful of short strings and it changes maybe once in an install's lifetime, so
 * it is held until something writes to it (see reset()).
 */
class BrandingService {

    static final String MODULE = 'branding'

    /**
     * Palette keys and their stock NiceAdmin values. The defaults here must stay in step
     * with the var(--brand-*, #fallback) fallbacks in niceadmin/css/style.css - they are
     * the same colours, and this map is what the branding screen shows as "current".
     */
    static final Map<String,String> COLOR_DEFAULTS = [
        primary:    '#4154f1',   // links, buttons, active nav, chart accents
        accent:     '#717ff5',   // link hover
        heading:    '#012970',   // headings and the logo wordmark
        page_bg:    '#f6f9ff',   // body background and hover rows
        header_bg:  '#ffffff',   // top bar
        sidebar_bg: '#ffffff'    // side nav
    ].asImmutable()

    /** Text/identity keys, mapped to the config property that used to supply them. */
    static final Map<String,Map> TEXT_KEYS = [
        app_name:  [ config: 'info.app.name',      fallback: 'G6 Portal' ],
        copyright: [ config: 'info.app.copyright', fallback: 'G6 Portal' ],
        team:      [ config: 'info.app.team',      fallback: 'G6Portal Portal Team' ],
        homepage:  [ config: 'info.app.homepage',  fallback: 'https://g6portal.abdullahsolutions.com/' ],
        logo_slug: [ config: 'info.app.logo_slug', fallback: '' ],
        logo_url:  [ config: 'info.app.logo_url',  fallback: '' ],
        favicon_slug: [ config: null,              fallback: '' ]
    ].asImmutable()

    /**
     * A colour reaches the browser inside a <style> block, so it is never interpolated
     * without passing this first. Only a plain hex literal is allowed: that rules out
     * "red; } body { display:none" and every other attempt to close the declaration and
     * write rules of one's own. A stored value that fails is treated as unset, so the
     * theme falls back to its own default rather than emitting anything unchecked.
     */
    static boolean validColor(String value) {
        value ==~ /(?i)^#([0-9a-f]{3}|[0-9a-f]{6})$/
    }

    private static volatile Map cached = null

    /** Drop the cache. Called whenever a branding value is written. */
    static void reset() {
        cached = null
    }

    /** The resolved branding map, built once and reused until reset(). */
    Map resolved() {
        Map local = cached
        if (local == null) {
            local = build()
            cached = local
        }
        local
    }

    private Map build() {
        def config = Holders.config
        Map out = [:]

        TEXT_KEYS.each { key, spec ->
            String stored = storedText(key)
            if (stored) {
                out[key] = stored
            }
            else {
                String fromConfig = spec.config ? config.getProperty(spec.config) : null
                out[key] = fromConfig ?: spec.fallback
            }
        }

        COLOR_DEFAULTS.each { key, fallback ->
            String stored = storedText("color_${key}")
            out[key] = validColor(stored) ? stored.toLowerCase() : fallback
        }

        // The sidebar/dashboard icon tint is a wash of the primary colour. Deriving it
        // means a client who sets a red primary does not keep NiceAdmin's blue-tinted
        // icon chips, without having to understand what a "tint" is.
        out.primary_tint = tint(out.primary)

        out
    }

    private String storedText(String name) {
        PortalSetting.findByModuleAndName(MODULE, name)?.text?.trim()
    }

    /** Blend a hex colour 8% over white - the light chip background behind icons. */
    static String tint(String hex) {
        if (!validColor(hex)) return COLOR_DEFAULTS.primary
        String h = expand(hex)
        int r = Integer.parseInt(h[1..2], 16)
        int g = Integer.parseInt(h[3..4], 16)
        int b = Integer.parseInt(h[5..6], 16)
        def wash = { int c -> Math.round(255 - (255 - c) * 0.08d) as int }
        String.format('#%02x%02x%02x', wash(r), wash(g), wash(b))
    }

    /** #abc -> #aabbcc, so the arithmetic above only deals with one form. */
    private static String expand(String hex) {
        hex.size() == 4 ? "#${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}" : hex
    }

    /**
     * The :root declarations for the current palette, ready to drop into <head>.
     * Only values that actually differ from the stock theme are emitted, so a portal that
     * has not been branded pays nothing and the CSS fallbacks do the work.
     */
    String cssVariables() {
        Map brand = resolved()
        def parts = []
        COLOR_DEFAULTS.each { key, fallback ->
            String value = brand[key]
            if (value && value.toLowerCase() != fallback.toLowerCase()) {
                parts << "--brand-${key.replace('_','-')}:${value}"
            }
        }
        String stockTint = tint(COLOR_DEFAULTS.primary)
        if (brand.primary_tint && brand.primary_tint != stockTint) {
            parts << "--brand-primary-tint:${brand.primary_tint}"
        }
        parts ? ":root{${parts.join(';')}}" : ''
    }

    /**
     * Write a branding value. Colours are validated here as well as on render, so a bad
     * value is rejected at the point somebody can still see the error.
     */
    @Transactional
    void put(String name, String value) {
        boolean isColor = name.startsWith('color_')
        if (isColor && value && !validColor(value)) {
            throw new IllegalArgumentException("${value} is not a hex colour like #1a2b3c")
        }
        def setting = PortalSetting.findByModuleAndName(MODULE, name)
        if (!value) {
            setting?.delete(flush: true)   // cleared = fall back to config/default again
        }
        else {
            if (!setting) {
                setting = new PortalSetting(module: MODULE, name: name, type: 'Text')
            }
            setting.text = value
            setting.save(flush: true, failOnError: true)
        }
        reset()
    }

    /**
     * Point a branding setting at an uploaded image, creating or updating its FileLink.
     *
     * allowedroles is 'All' deliberately: the logo and favicon appear on the login page,
     * which nobody has authenticated for yet. Saying so explicitly beats relying on
     * 'portal' happening to sit in download_module_whitelist, which an admin may narrow.
     */
    @Transactional
    void saveImageLink(String slug, String settingKey, String name, String path, int size) {
        def link = FileLink.findBySlug(slug)
        if (!link) {
            link = new FileLink(slug: slug, module: 'portal')
        }
        link.name = name
        link.path = path
        link.size = size
        link.allowedroles = 'All'
        link.save(flush: true, failOnError: true)
        put(settingKey, slug)
    }

    /** Forget an uploaded logo/favicon. FileLink.beforeDelete removes the file too. */
    @Transactional
    void removeImageLink(String slug, String settingKey) {
        FileLink.findBySlug(slug)?.delete(flush: true)
        put(settingKey, null)
    }

    /** Path of the file currently backing a branding image, or null. */
    String imagePath(String slug) {
        FileLink.findBySlug(slug)?.path
    }

    /** Drop every branding value and the uploaded images, back to the shipped look. */
    @Transactional
    void clearAll() {
        PortalSetting.findAllByModule(MODULE)*.delete(flush: true)
        ['branding_logo', 'branding_favicon'].each { slug ->
            FileLink.findBySlug(slug)?.delete(flush: true)
        }
        reset()
    }
}
