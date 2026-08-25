package g6portal

class BrandingTagLib {
    static defaultEncodeAs = [taglib:'html']
    // branding_style writes a <style> element, so it must not be HTML-escaped. What goes
    // inside it is not user text: BrandingService emits only its own property names and
    // colours it has matched against a hex pattern.
    static encodeAsForTags = [ branding_style: [taglib:'none'] ]

    def brandingService

    /**
     * The client's palette as :root custom properties. Emits nothing at all when the
     * portal still uses the stock colours, so an unbranded install carries no extra bytes.
     */
    def branding_style = { attrs ->
        String css = brandingService.cssVariables()
        if (css) {
            out << "<style>${css}</style>"
        }
    }

    /** A single branding value by key, e.g. <g:brand name='app_name'/>. */
    def brand = { attrs ->
        out << (brandingService.resolved()[attrs.name] ?: '')
    }

    /**
     * Where the header logo comes from: a file uploaded through the portal, an external
     * URL, or the shipped default - in that order.
     */
    def brand_logo_url = { attrs ->
        Map branding = brandingService.resolved()
        if (branding.logo_slug) {
            out << createLink(controller: 'fileLink', action: 'download',
                              params: [slug: branding.logo_slug, module: 'portal'])
        }
        else if (branding.logo_url) {
            out << branding.logo_url
        }
        else {
            out << resource(dir: 'images', file: 'logo.png')
        }
    }

    /** Favicon, falling back to the shipped one when the client has not uploaded any. */
    def brand_favicon_url = { attrs ->
        Map branding = brandingService.resolved()
        if (branding.favicon_slug) {
            out << createLink(controller: 'fileLink', action: 'download',
                              params: [slug: branding.favicon_slug, module: 'portal'])
        }
        else {
            out << resource(dir: 'images', file: 'favicon.ico')
        }
    }
}
