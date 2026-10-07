package g6portal

/**
 * One monthly access review of one module: its owners confirm who holds which role in it.
 *
 * Opened on the first run in a calendar month (period 'yyyy-MM') for every module in scope -
 * Active or status not recorded, with at least one owner and at least one reviewable role.
 * Owners are e-mailed a link to the review page, reminded on configurable days, and may add
 * or remove roles before approving. If it is still Pending when dueDate passes (opened + the
 * grace period), every reviewable role in the module is removed; what was removed is kept in
 * the log so an admin can restore it. See PortalAccessReviewService for the rules and settings.
 */
class PortalModuleAccessReview {

    static final List<String> STATUSES = ['Pending', 'Approved', 'Revoked', 'Cancelled']

    static belongsTo = [module: PortalModule]
    static hasMany = [logs: PortalModuleAccessReviewLog]

    String period               // 'yyyy-MM', the calendar month this review covers
    String status = 'Pending'
    Date openedDate
    Date dueDate                // roles are revoked once this passes unapproved
    Integer remindersSent = 0
    Date lastReminderDate
    User approvedBy
    Date approvedDate
    String approvalNote
    // JSON: the module's roles exactly as they stood when the owner approved - who (user id,
    // staff ID, name, e-mail as they were then), which role, whether it was exempt - plus the
    // approver. The live UserRole rows keep changing, and User rows get their staff ID or name
    // changed; this is the record of what was actually approved.
    String approvedSnapshot
    Date closedDate             // when it left Pending, whichever way

    Date dateCreated
    Date lastUpdated

    static constraints = {
        period maxSize: 7, unique: 'module'
        status inList: STATUSES, maxSize: 30
        lastReminderDate nullable: true
        approvedBy nullable: true
        approvedDate nullable: true
        approvalNote nullable: true, maxSize: 2000
        approvedSnapshot nullable: true
        closedDate nullable: true
    }

    static mapping = {
        approvedBy cascade: 'none'     // never save/validate a User through a review
        approvedSnapshot type: 'text'
        logs sort: 'id'
        module index: 'pm_access_review_module_idx'
        status index: 'pm_access_review_status_idx'
    }

    boolean isPending() { status == 'Pending' }
}
