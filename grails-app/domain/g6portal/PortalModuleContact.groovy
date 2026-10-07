package g6portal

/**
 * A person attached to a PortalModule in a named capacity - who owns it, who maintains it.
 *
 * Deliberately separate from UserRole: a UserRole in a module is an access grant (any role in
 * a module lifts the per-record filter on its trackers), whereas being the owner of a module
 * says who is accountable for it and must not quietly widen what they can see.
 */
class PortalModuleContact {

    static final List<String> ROLES = ['Owner', 'Maintainer']

    static belongsTo = [module: PortalModule]

    User user
    String role

    Date dateCreated
    Date lastUpdated

    static constraints = {
        role inList: ROLES, maxSize: 30   // see PortalModule.status: sized for roles added later
        user unique: ['module', 'role']
    }

    static mapping = {
        module index: 'portal_module_contact_module_idx'
        // Never cascade into User. Saving a module otherwise deep-validates every contact's
        // User, and legacy accounts that fail User's own constraints (e.g. a null isAdmin) then
        // block the module save - 104 of 290 modules in the first register load did exactly that.
        user cascade: 'none'
    }

    String toString() { "${role}: ${user?.name}" }
}
