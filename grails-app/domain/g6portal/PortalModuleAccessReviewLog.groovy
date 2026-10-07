package g6portal

/**
 * Audit trail of an access review: when it opened, every e-mail, every role an owner added or
 * removed, the approval, and - on revocation - each role taken away. RoleRevoked rows keep the
 * user and role name, which is what PortalAccessReviewService.restore puts back.
 */
class PortalModuleAccessReviewLog {

    static final List<String> ACTIONS = ['Opened', 'Emailed', 'EmailFailed', 'Reminder', 'RoleAdded', 'RoleRemoved',
                                         'Approved', 'RoleRevoked', 'Revoked', 'Restored', 'Cancelled']

    static belongsTo = [review: PortalModuleAccessReview]

    String action
    User user           // the person a role was added/removed for, when there is one
    String role         // the role name involved, when there is one
    User actor          // who did it; null = the scheduled run
    String detail
    Date dateCreated

    static constraints = {
        action inList: ACTIONS, maxSize: 30
        user nullable: true
        role nullable: true, maxSize: 255
        actor nullable: true
        detail nullable: true, maxSize: 2000
    }

    static mapping = {
        user cascade: 'none'
        actor cascade: 'none'
        review index: 'pm_access_review_log_review_idx'
    }
}
