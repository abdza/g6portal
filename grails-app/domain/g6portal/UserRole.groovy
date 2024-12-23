package g6portal

class UserRole {

    static constraints = {
        module()
        role()
        user()
    }

    static mapping = {
        cache true
    }

    User user
    String module
    String role

    String toString() {
        return this.role + '-::-::-'
    }

}
