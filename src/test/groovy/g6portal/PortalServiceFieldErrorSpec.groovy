package g6portal

import spock.lang.Specification

/**
 * field_error_messages runs every error check a tracker field carries, and two of its
 * assumptions used to be wrong in ways nobody could see from the screen:
 *
 *  - a Unique check queries the tracker for the submitted value, and an edit resubmits the
 *    record's own value, so the record clashed with itself and no record carrying a Unique
 *    field could be saved again - not even to change some other field.
 *  - val was assumed to be a String and had trim() called on it, so a Date, a number or a
 *    MultiSelect's List threw MissingMethodException out of validation.
 *
 * A plain unit spec: the method needs nothing from the container, only a field to read the
 * checks off and a tracker to ask for rows.
 */
class PortalServiceFieldErrorSpec extends Specification {

    PortalService service = new PortalService()

    /** A field carrying one error check, over a tracker returning the given rows. */
    private Expando field(String errorType, List rows, Map errorProps = [:]) {
        def check = new Expando([error_type: errorType, error_msg: null, allow_submission: false,
                                 format: null, error_function: null] + errorProps)
        new Expando(
            name: 'ic_no',
            label: 'IC No',
            error_checks: [check],
            tracker: new Expando(slug: 'member', module: 'ahli', rows: { qparams -> rows })
        )
    }

    void "a Unique value already held by another record is refused"() {
        given:
            def curfield = field('Unique', [[id: 7, ic_no: '880101015432']])

        when:
            def (errormsg, goterror) = service.field_error_messages(curfield, '880101015432', [id: '12'], null)

        then:
            goterror
            errormsg.join().contains('already exists')
    }

    void "editing a record does not clash with itself"() {
        given: "the only row holding this value is the record being edited"
            def curfield = field('Unique', [[id: 12, ic_no: '880101015432']])

        when:
            def (errormsg, goterror) = service.field_error_messages(curfield, '880101015432', [id: '12'], null)

        then:
            !goterror
            errormsg.isEmpty()
    }

    void "a new record still collides - there is no own id to excuse it"() {
        given:
            def curfield = field('Unique', [[id: 12, ic_no: '880101015432']])

        when: "a New transition posts no id"
            def (errormsg, goterror) = service.field_error_messages(curfield, '880101015432', [:], null)

        then:
            goterror
    }

    void "a Unique check on a non-String value does not blow up"() {
        given: "a Date field - what a Date or DateTime column hands back"
            def value = new Date()
            def curfield = field('Unique', [])

        when:
            def (errormsg, goterror) = service.field_error_messages(curfield, value, [id: '12'], null)

        then:
            notThrown(MissingMethodException)
            !goterror
    }

    void "a Not Empty check reads a non-String value as filled in"() {
        given:
            def curfield = field('Not Empty', [])

        when:
            def (errormsg, goterror) = service.field_error_messages(curfield, new Date(), [:], null)

        then:
            notThrown(MissingMethodException)
            !goterror
    }

    void "whitespace alone is still empty"() {
        given:
            def curfield = field('Not Empty', [])

        when:
            def (errormsg, goterror) = service.field_error_messages(curfield, '   ', [:], null)

        then:
            goterror
            errormsg.join().contains('cannot be empty')
    }

    void "a Unique check skips a value that is only whitespace"() {
        given: "rows would collide if the blank value were ever queried"
            def curfield = field('Unique', [[id: 7, ic_no: '']])

        when:
            def (errormsg, goterror) = service.field_error_messages(curfield, '  ', [id: '12'], null)

        then:
            !goterror
    }
}
