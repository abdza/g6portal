package g6portal

import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * The first Google sign-in for an email address is the only path that creates a User from
 * OAuth, and it broke twice over in ways that surfaced to the person signing in as nothing
 * but an "Authentication error" alert:
 *
 *  - password is bindable:false (so an anonymous POST to the whitelisted user.register
 *    cannot set one), which means the map constructor silently drops it. The old code
 *    passed password: in that map and then called hashPassword(user.password) on the null
 *    that came back out - "Cannot invoke method toCharArray() on null object".
 *  - userID is unique:true and was taken straight from the email local part, so the second
 *    person named ali to sign in, from a different mail host, collided with the first.
 *
 * Both are silent traps rather than anything the code reads as wrong, so they are pinned here.
 */
class GoogleOAuthUserCreationSpec extends Specification implements ControllerUnitTest<GoogleOAuthController>, DataTest {

    Class[] getDomainClassesToMock() { [User] as Class[] }

    /** uniqueUserID is private; Groovy's access rules around that are not worth relying on. */
    private String uniqueUserID(String email) {
        def method = GoogleOAuthController.getDeclaredMethod('uniqueUserID', String)
        method.setAccessible(true)
        return method.invoke(controller, email)
    }

    private User existing(String userID, String email) {
        def user = new User(userID: userID, name: userID, email: email)
        user.hashPassword('irrelevant')
        user.save(flush: true, failOnError: true)
    }

    void "the map constructor drops password, so it must not be set through it"() {
        when: "password is handed to the map constructor the way the old code did"
        def user = new User(
            email: 'someone@example.com',
            name: 'Someone',
            userID: 'someone',
            isActive: true,
            password: UUID.randomUUID().toString()
        )

        then: "the bindable properties arrive and password does not"
        user.email == 'someone@example.com'
        user.isActive
        user.password == null

        when: "the dropped value is then hashed, as the old code did"
        user.hashPassword(user.password)

        then: "the exact failure people saw on the login screen"
        def e = thrown(Exception)
        e.message.contains('toCharArray')
    }

    void "assigning the password directly is what actually sets it"() {
        given:
        def user = new User(userID: 'someone', name: 'Someone', email: 'someone@example.com')

        when:
        user.hashPassword(UUID.randomUUID().toString())

        then: "a real bcrypt hash, and no password anyone could have guessed"
        user.password.startsWith('$2')
        user.verifyPassword('') == false
    }

    void "a free userID is taken as-is"() {
        expect:
        uniqueUserID('ali@gmail.com') == 'ali'
    }

    void "a taken userID gets a numbered suffix"() {
        given:
        existing('ali', 'ali@gmail.com')

        expect: "the same name at a different mail host no longer collides"
        uniqueUserID('ali@yahoo.com') == 'ali2'
    }

    void "suffixes keep counting past the first collision"() {
        given:
        existing('ali', 'ali@gmail.com')
        existing('ali2', 'ali@yahoo.com')
        existing('ali3', 'ali@hotmail.com')

        expect:
        uniqueUserID('ali@outlook.com') == 'ali4'
    }

    void "characters that do not belong in a login name are stripped"() {
        expect:
        uniqueUserID(email) == expected

        where:
        email                        || expected
        'Ali.Bin@gmail.com'          || 'ali.bin'
        'ali+portal@gmail.com'       || 'aliportal'
        'ali_bin-abu@gmail.com'      || 'ali_bin-abu'
        "ali'quote@gmail.com"        || 'aliquote'
    }

    void "an email local part that sanitises away still yields a usable id"() {
        expect: "no empty userID, which would fail the not-nullable constraint"
        uniqueUserID('++++@gmail.com') == 'user'
    }

    void "the fallback id collides like any other"() {
        given:
        existing('user', 'plus@gmail.com')

        expect:
        uniqueUserID('++++@gmail.com') == 'user2'
    }

    void "a very long local part is truncated"() {
        when:
        def id = uniqueUserID(('a' * 100) + '@gmail.com')

        then:
        id == 'a' * 40
    }
}
