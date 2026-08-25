package g6portal

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Deliberately NOT @Rollback.
 *
 * @Rollback wraps each feature method in a transaction, which would supply the very thing
 * this spec exists to check for. BrandingService is called from controller actions, and
 * controller actions are not transactional - so its write methods have to open their own
 * transaction. Without @Transactional on them these tests fail with
 * "javax.persistence.TransactionRequiredException: no transaction is in progress",
 * which is exactly what the branding screen hit in the browser.
 *
 * Because nothing rolls back, each test cleans up after itself.
 *
 * Reads go through PortalSetting.withNewSession. In the running portal the read path is
 * called while rendering a page, where Grails' OpenSessionInView filter has already put a
 * Hibernate session on the thread; a plain integration test has no such filter, so the
 * session has to be opened explicitly. The writes under test open their own.
 */
@Integration
class BrandingServiceSpec extends Specification {

    BrandingService brandingService

    def cleanup() {
        brandingService.clearAll()
    }

    void "put writes a setting without an ambient transaction"() {
        when:
            brandingService.put('color_primary', '#e30613')

        then:
            PortalSetting.withNewSession { brandingService.resolved().primary } == '#e30613'
    }

    void "put removes the setting when handed a blank value"() {
        given:
            brandingService.put('color_primary', '#e30613')

        when:
            brandingService.put('color_primary', null)

        then: "falls back to the stock theme colour"
            PortalSetting.withNewSession { brandingService.resolved().primary } ==
                BrandingService.COLOR_DEFAULTS.primary
    }

    void "clearAll wipes branding back to defaults"() {
        given:
            brandingService.put('color_primary', '#e30613')
            brandingService.put('app_name', 'Acme Operations')

        when:
            brandingService.clearAll()

        then:
            PortalSetting.withNewSession { brandingService.resolved().primary } ==
                BrandingService.COLOR_DEFAULTS.primary
            PortalSetting.withNewSession { brandingService.cssVariables() } == ''
    }

    void "a colour that is not a hex literal is rejected at the point of writing"() {
        when: "a value that would close the declaration and inject rules of its own"
            brandingService.put('color_primary', 'red; } body { display:none')

        then:
            thrown(IllegalArgumentException)

        and: "nothing was stored"
            PortalSetting.withNewSession { brandingService.resolved().primary } ==
                BrandingService.COLOR_DEFAULTS.primary
    }

    void "only colours that differ from the stock theme are emitted"() {
        when: "one colour is changed and another is set to the value it already had"
            brandingService.put('color_primary', '#e30613')
            brandingService.put('color_page_bg', BrandingService.COLOR_DEFAULTS.page_bg)

        then:
            String css = PortalSetting.withNewSession { brandingService.cssVariables() }
            css.contains('--brand-primary:#e30613')
            !css.contains('--brand-page-bg')

        and: "the icon tint is derived from the new primary rather than left NiceAdmin blue"
            css.contains('--brand-primary-tint:#fdebec')
    }
}
