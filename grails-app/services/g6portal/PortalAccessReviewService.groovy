package g6portal

import grails.gorm.transactions.Transactional
import groovy.sql.Sql

/**
 * Monthly module access reviews.
 *
 * Each calendar month every module IN SCOPE gets a PortalModuleAccessReview. Its owners are
 * e-mailed a link to a page listing the module's roles, where they can remove roles, add roles
 * (role names come from the module's trackers' 'User Role' roles, including trackers of a
 * previous portal generation that share the database) and approve. Reminders go
 * out on configured days; if the review is still unapproved when the grace period ends, every
 * reviewable role in the module is removed and logged, so an admin can restore it.
 *
 * In scope: status Active or not recorded, at least one Owner contact, and at least one
 * reviewable role. Modules with roles but no owner are never revoked - they are reported as
 * needing an owner instead, so an incomplete register cannot take anyone's access away.
 *
 * Settings (module 'portal'; all optional):
 *   access_review_enabled        'true' to let the scheduled run open/remind/revoke. Default
 *                                off, so deploying the code sends nothing until switched on.
 *   access_review_grace_days     days from opening until unapproved roles are revoked (30)
 *   access_review_reminder_days  days after opening to send reminders, comma list ('10,20,27')
 *   access_review_exempt_roles   roles never reviewed or revoked, comma list ('Developer'),
 *                                matched case-insensitively. Developer is exempt so a lapsed
 *                                review cannot lock maintainers out of their own module.
 *   access_review_cc             extra addresses copied on every review e-mail
 *   server_url                   base of the links in the e-mails (shared portal setting)
 *
 * The run is idempotent: it can be called any number of times a day and only acts on what is
 * due. Nothing here is specific to one organisation.
 */
@Transactional
class PortalAccessReviewService {

    def mailService
    def groovyPageRenderer
    def grailsLinkGenerator
    def dataSource

    // null until first checked; whether the old portal's tracker/tracker_role tables are here
    private static volatile Boolean legacyTrackerTables = null

    // ------------------------------------------------------------------ settings

    boolean enabled() {
        def v = PortalSetting.namedefault('portal.access_review_enabled', false)
        return v?.toString()?.trim()?.toLowerCase() in ['true', '1', 'yes', 'on']
    }

    int graceDays() {
        def v = PortalSetting.namedefault('portal.access_review_grace_days', 30)
        try { return Math.max(1, v.toString().trim().toInteger()) } catch(Exception e) { return 30 }
    }

    List<Integer> reminderDays() {
        def v = PortalSetting.namedefault('portal.access_review_reminder_days', '10,20,27')
        def items = (v instanceof Collection) ? v : v?.toString()?.tokenize(',')
        def days = (items ?: []).collect { it?.toString()?.trim() }.findAll { it?.isInteger() }.collect { it.toInteger() }
        // a reminder on or after the due date would arrive with (or after) the revocation
        return days.findAll { it > 0 && it < graceDays() }.unique().sort()
    }

    List<String> exemptRoles() {
        def v = PortalSetting.namedefault('portal.access_review_exempt_roles', 'Developer')
        def items = (v instanceof Collection) ? v : v?.toString()?.tokenize(',')
        return (items ?: []).collect { it?.toString()?.trim() }.findAll { it }
    }

    boolean isExempt(String role) {
        role && exemptRoles().any { it.equalsIgnoreCase(role.trim()) }
    }

    List<String> ccAddresses() {
        def v = PortalSetting.namedefault('portal.access_review_cc', '')
        def items = (v instanceof Collection) ? v : v?.toString()?.tokenize(',;')
        return (items ?: []).collect { it?.toString()?.trim() }.findAll { it?.contains('@') }
    }

    static String periodOf(Date d) { d.format('yyyy-MM') }

    static String periodLabel(String period) {
        try { return Date.parse('yyyy-MM', period).format('MMMM yyyy') } catch(Exception e) { return period }
    }

    // ------------------------------------------------------------------ scope

    /** Roles in a module that the review covers (everything but the exempt roles). */
    List<UserRole> reviewableRoles(PortalModule module) {
        UserRole.findAllByModule(module.name).findAll { !isExempt(it.role) }
    }

    /** Roles in a module the review leaves alone, shown read-only on the review page. */
    List<UserRole> exemptRolesOf(PortalModule module) {
        UserRole.findAllByModule(module.name).findAll { isExempt(it.role) }
    }

    /**
     * Which modules the review covers today.
     *   inScope  - reviewed
     *   noOwner  - would be reviewed but have no owner: reported, never revoked
     * Built from three grouped queries rather than one per module - there are hundreds.
     */
    Map scope() {
        def exemptLower = exemptRoles()*.toLowerCase()
        def roleCounts = [:]
        UserRole.executeQuery('select r.module, r.role, count(r.id) from UserRole r group by r.module, r.role').each { row ->
            if(!(row[1]?.toString()?.toLowerCase() in exemptLower)) {
                roleCounts[row[0]] = (roleCounts[row[0]] ?: 0) + (row[2] as Long)
            }
        }
        def ownedIds = PortalModuleContact.executeQuery(
            "select distinct c.module.id from PortalModuleContact c where c.role = 'Owner'") as Set
        def candidates = PortalModule.executeQuery(
            "from PortalModule m where m.status is null or m.status = 'Active' order by m.name")
        def inScope = [], noOwner = []
        candidates.each { m ->
            if(!roleCounts[m.name]) return
            if(m.id in ownedIds) inScope << m else noOwner << m
        }
        return [inScope: inScope, noOwner: noOwner, roleCounts: roleCounts]
    }

    /**
     * Role names an owner may grant: every 'User Role' role defined on the module's trackers,
     * those defined on the old portal's trackers when that portal shares this database (modules
     * not migrated yet have no trackers here), plus names already in use in the module.
     * Exempt roles are never offered.
     */
    List<String> grantableRoles(PortalModule module) {
        def names = PortalTrackerRole.executeQuery(
            "select distinct r.name from PortalTrackerRole r where r.tracker.module = :m and r.role_type = 'User Role'",
            [m: module.name])
        names += legacyTrackerRoles(module.name)
        names += UserRole.executeQuery('select distinct r.role from UserRole r where r.module = :m', [m: module.name])
        return names.collect { it?.toString()?.trim() }.findAll { it && !isExempt(it) }.unique().sort { it.toLowerCase() }
    }

    /**
     * 'User Role' names on the old portal's trackers (tables tracker + tracker_role) for a module.
     * Empty where those tables are absent. Their presence is checked through metadata first and
     * remembered, so a missing table never fails a statement (which would abort the surrounding
     * transaction on PostgreSQL).
     */
    List<String> legacyTrackerRoles(String moduleName) {
        if(legacyTrackerTables == null) {
            def found = false
            try {
                dataSource.connection.withCloseable { conn ->
                    def md = conn.metaData
                    def hasColumns = { String table, List<String> cols ->
                        def seen = [] as Set
                        [table, table.toUpperCase()].each { t ->
                            def rs = md.getColumns(null, null, t, null)
                            try { while(rs.next()) seen << rs.getString('COLUMN_NAME')?.toLowerCase() } finally { rs.close() }
                        }
                        cols.every { it in seen }
                    }
                    found = hasColumns('tracker', ['id', 'module']) &&
                            hasColumns('tracker_role', ['name', 'role_type', 'tracker_id'])
                }
            } catch(Exception e) {
                log.warn("Could not check for old-portal tracker tables: ${e.message}")
            }
            legacyTrackerTables = found
        }
        if(!legacyTrackerTables) return []
        return new Sql(dataSource).rows(
            "select distinct r.name from tracker_role r join tracker t on t.id = r.tracker_id where t.module = ? and r.role_type = 'User Role'",
            [moduleName]).collect { it.name }
    }

    boolean canAct(PortalModuleAccessReview review, User user) {
        if(!user || !review) return false
        if(user.isAdmin) return true
        return review.module.owners().any { it.id == user.id }
    }

    // ------------------------------------------------------------------ the scheduled run

    /**
     * Everything that is due, in order: settle pending reviews (cancel / revoke / remind),
     * then open this month's. Settling first matters when a new month starts on the same day
     * an older review falls due. Returns counts for the caller to show or log.
     */
    Map run(Date now = new Date(), boolean force = false) {
        def summary = [enabled: enabled(), opened: 0, reminded: 0, revoked: 0, cancelled: 0, rolesRevoked: 0,
                       noOwner: 0, errors: []]
        if(!summary.enabled && !force) return summary
        def sc = scope()
        summary.noOwner = sc.noOwner.size()
        def inScopeIds = sc.inScope*.id as Set

        PortalModuleAccessReview.findAllByStatus('Pending').each { review ->
            try {
                if(!(review.module.id in inScopeIds)) {
                    close(review, 'Cancelled', null, 'Module left the review scope (status changed, no owner or no reviewable roles).')
                    summary.cancelled++
                }
                else if(now >= review.dueDate) {
                    summary.rolesRevoked += revoke(review, now)
                    summary.revoked++
                }
                else if(remind(review, now)) {
                    summary.reminded++
                }
            }
            catch(Exception e) {
                summary.errors << "review ${review.id} (${review.module?.name}): ${e.message}"
                PortalErrorLog.capture(e, "Access review ${review.id} failed during the scheduled run", [:])
            }
        }

        def period = periodOf(now)
        def existing = PortalModuleAccessReview.executeQuery(
            'select r.module.id from PortalModuleAccessReview r where r.period = :p', [p: period]) as Set
        sc.inScope.findAll { !(it.id in existing) }.each { module ->
            try {
                open(module, period, now)
                summary.opened++
            }
            catch(Exception e) {
                summary.errors << "open ${module.name}: ${e.message}"
                PortalErrorLog.capture(e, "Access review could not be opened for ${module.name}", [:])
            }
        }
        return summary
    }

    PortalModuleAccessReview open(PortalModule module, String period, Date now) {
        def start = new Date(now.time).clearTime()
        def review = new PortalModuleAccessReview(period: period, status: 'Pending', openedDate: now,
                                                  dueDate: start + graceDays(), remindersSent: 0)
        module.addToReviews(review)
        review.save(flush: true, failOnError: true)
        log(review, 'Opened', null, null, null,
            "${reviewableRoles(module).size()} reviewable role(s); due ${review.dueDate.format('d MMM yyyy')}")
        email(review, 'invite')
        return review
    }

    /** Sends at most one reminder per run - the latest one that is due - and says whether it did. */
    boolean remind(PortalModuleAccessReview review, Date now) {
        def due = reminderDays().count { day -> now >= new Date(review.openedDate.time).clearTime() + day }
        if(due <= (review.remindersSent ?: 0)) return false
        review.remindersSent = due
        review.lastReminderDate = now
        review.save(flush: true, failOnError: true)
        log(review, 'Reminder', null, null, null, "Reminder ${due} of ${reminderDays().size()}")
        email(review, 'reminder')
        return true
    }

    /** Removes every reviewable role in the module, logging each so it can be restored. */
    int revoke(PortalModuleAccessReview review, Date now) {
        def roles = reviewableRoles(review.module)
        roles.each { ur ->
            log(review, 'RoleRevoked', ur.user, ur.role, null, 'Review not approved by ' + review.dueDate.format('d MMM yyyy'))
            ur.delete(flush: true)
        }
        close(review, 'Revoked', null, "${roles.size()} role(s) removed")
        email(review, 'revoked')
        return roles.size()
    }

    private void close(PortalModuleAccessReview review, String status, User actor, String detail) {
        review.status = status
        review.closedDate = new Date()
        review.save(flush: true, failOnError: true)
        log(review, status == 'Revoked' ? 'Revoked' : (status == 'Approved' ? 'Approved' : 'Cancelled'), null, null, actor, detail)
    }

    // ------------------------------------------------------------------ owner actions

    /**
     * Every role in the module as it stands right now, with each person's details as they are
     * right now, as JSON. Stored on the review when it is approved - the record of what the
     * owner actually approved, which the live UserRole and User rows cannot give back later.
     */
    String rolesSnapshot(PortalModule module, User actor) {
        def roles = UserRole.findAllByModule(module.name).collect { ur ->
            [uid: ur.user?.id, userID: ur.user?.userID, name: ur.user?.name, email: ur.user?.email,
             active: (ur.user?.isActive != false), role: ur.role, exempt: isExempt(ur.role)]
        }.sort { a, b -> (a.name ?: '').toLowerCase() <=> (b.name ?: '').toLowerCase() ?: (a.role ?: '') <=> (b.role ?: '') }
        return groovy.json.JsonOutput.toJson([
            takenAt : new Date().format("yyyy-MM-dd'T'HH:mm:ss"),
            module  : module.name,
            approver: [id: actor?.id, userID: actor?.userID, name: actor?.name],
            roles   : roles])
    }

    /** The stored approval snapshot, parsed; null for reviews approved before snapshots existed. */
    Map approvedSnapshot(PortalModuleAccessReview review) {
        if(!review?.approvedSnapshot) return null
        try { return new groovy.json.JsonSlurper().parseText(review.approvedSnapshot) as Map }
        catch(Exception e) { log.warn("Unreadable approval snapshot on review ${review.id}: ${e.message}"); return null }
    }

    /** Approves the review, and any older review of the same module still pending. */
    void approve(PortalModuleAccessReview review, User actor, String note) {
        requirePending(review, actor)
        review.approvedBy = actor
        review.approvedDate = new Date()
        review.approvalNote = note?.trim() ? note.trim().take(2000) : null
        review.approvedSnapshot = rolesSnapshot(review.module, actor)
        def changes = PortalModuleAccessReviewLog.countByReviewAndActionInList(review, ['RoleAdded', 'RoleRemoved'])
        close(review, 'Approved', actor, changes ? "Approved with ${changes} change(s)" : 'Approved without changes')
        PortalModuleAccessReview.findAllByModuleAndStatus(review.module, 'Pending').each { older ->
            if(older.id != review.id && older.period < review.period) {
                older.approvedBy = actor
                older.approvedDate = review.approvedDate
                older.approvedSnapshot = review.approvedSnapshot
                close(older, 'Approved', actor, "Covered by the approval of ${periodLabel(review.period)}")
            }
        }
    }

    UserRole addRole(PortalModuleAccessReview review, User actor, User user, String role) {
        requirePending(review, actor)
        role = role?.trim()
        if(!user) throw new IllegalArgumentException('Choose a user.')
        if(!role || !(role in grantableRoles(review.module))) throw new IllegalArgumentException("'${role}' is not a role this module offers.")
        if(UserRole.findByUserAndModuleAndRole(user, review.module.name, role)) {
            throw new IllegalArgumentException("${user.name} already has ${role}.")
        }
        def ur = new UserRole(user: user, module: review.module.name, role: role)
        ur.save(flush: true, failOnError: true)
        log(review, 'RoleAdded', user, role, actor, null)
        return ur
    }

    void removeRole(PortalModuleAccessReview review, User actor, UserRole ur) {
        requirePending(review, actor)
        if(!ur || ur.module != review.module.name) throw new IllegalArgumentException('That role is not in this module.')
        if(isExempt(ur.role)) throw new IllegalArgumentException("${ur.role} is not part of the review.")
        log(review, 'RoleRemoved', ur.user, ur.role, actor, null)
        ur.delete(flush: true)
    }

    /** Admin undo of a revocation: puts back every role the revocation removed. */
    int restore(PortalModuleAccessReview review, User actor) {
        if(!actor?.isAdmin) throw new IllegalArgumentException('Only a portal admin can restore roles.')
        if(review.status != 'Revoked') throw new IllegalArgumentException('Only a revoked review can be restored.')
        def put = 0
        PortalModuleAccessReviewLog.findAllByReviewAndAction(review, 'RoleRevoked').each { entry ->
            if(entry.user && entry.role && !UserRole.findByUserAndModuleAndRole(entry.user, review.module.name, entry.role)) {
                new UserRole(user: entry.user, module: review.module.name, role: entry.role).save(flush: true, failOnError: true)
                put++
            }
        }
        log(review, 'Restored', null, null, actor, "${put} role(s) restored")
        return put
    }

    /**
     * Admin: deletes every review of a period (and its history) so the next run opens that
     * month again - for re-running while testing, or after fixing owners/settings.
     *
     * Roles already removed or added are NOT touched; only the review records go. A Revoked
     * review's history is the only record of what it removed (restore replays it), so a period
     * holding revoked reviews that were never restored is refused unless includeRevoked.
     * Returns how many reviews were deleted.
     */
    int clearPeriod(String period, User actor, boolean includeRevoked = false) {
        if(!actor?.isAdmin) throw new IllegalArgumentException('Only a portal admin can clear a review period.')
        if(!(period ==~ /\d{4}-\d{2}/)) throw new IllegalArgumentException("'${period}' is not a period.")
        def reviews = PortalModuleAccessReview.findAllByPeriod(period)
        if(!reviews) return 0
        def unrestored = reviews.findAll { r ->
            r.status == 'Revoked' && !PortalModuleAccessReviewLog.countByReviewAndAction(r, 'Restored')
        }
        if(unrestored && !includeRevoked) {
            throw new IllegalArgumentException("${unrestored.size()} revoked review(s) in ${periodLabel(period)} " +
                "(${unrestored*.module*.name.take(10).join(', ')}${unrestored.size() > 10 ? ', ...' : ''}) still hold the " +
                "only record of the roles they removed. Restore them first, or tick the box to clear them anyway.")
        }
        def ids = reviews*.id
        // bulk deletes: a period is hundreds of reviews and thousands of log rows
        ids.collate(1000).each { chunk ->
            PortalModuleAccessReviewLog.executeUpdate('delete from PortalModuleAccessReviewLog l where l.review.id in (:ids)', [ids: chunk])
            PortalModuleAccessReview.executeUpdate('delete from PortalModuleAccessReview r where r.id in (:ids)', [ids: chunk])
        }
        log.info "Access review period ${period} cleared by ${actor.name}: ${ids.size()} review(s)" +
                 (unrestored ? ", including ${unrestored.size()} unrestored revocation(s)" : '')
        return ids.size()
    }

    private void requirePending(PortalModuleAccessReview review, User actor) {
        if(!canAct(review, actor)) throw new IllegalArgumentException('Only an owner of this module can do that.')
        if(!review.isPending()) throw new IllegalArgumentException("This review is already ${review.status.toLowerCase()}.")
    }

    private void log(PortalModuleAccessReview review, String action, User user, String role, User actor, String detail) {
        def entry = new PortalModuleAccessReviewLog(action: action, user: user, role: role, actor: actor,
                                                    detail: detail?.take(2000))
        review.addToLogs(entry)
        entry.save(flush: true, failOnError: true)
    }

    // ------------------------------------------------------------------ e-mail

    String reviewLink(PortalModuleAccessReview review) {
        def base = PortalSetting.namedefault('portal.server_url', '')?.toString()?.trim()
        if(base) return base.replaceAll('/+$', '') + '/accessReview/show/' + review.id
        return grailsLinkGenerator.link(controller: 'accessReview', action: 'show', id: review.id, absolute: true)
    }

    /** kind: invite | reminder | revoked. One e-mail to all owners; failures are logged, not thrown. */
    void email(PortalModuleAccessReview review, String kind) {
        def owners = review.module.owners().findAll { it.email }
        if(!owners) {
            log(review, 'EmailFailed', null, null, null, 'No owner with an e-mail address')
            return
        }
        def module = review.module
        def label = periodLabel(review.period)
        def subject = [
            invite  : "Access review for ${module.displayName()} - ${label}",
            reminder: "Reminder ${review.remindersSent}: access review for ${module.displayName()} due ${review.dueDate.format('d MMM yyyy')}",
            revoked : "Access removed: ${module.displayName()} - ${label} review was not approved"
        ][kind]
        def body = groovyPageRenderer.render(template: '/accessReview/email', model: [
            review: review, module: module, kind: kind, periodLabel: label, link: reviewLink(review),
            roles: reviewableRoles(module), graceDays: graceDays(),
            revokedCount: PortalModuleAccessReviewLog.countByReviewAndAction(review, 'RoleRevoked')])
        def pe = new PortalEmail(emailto: owners*.email.join(','), emailcc: ccAddresses().join(',') ?: null,
                                 title: subject.toString().take(250), body: body, module: module.name,
                                 deliveryTime: new Date())
        pe.send(mailService)
        if(pe.emailSent) log(review, 'Emailed', null, null, null, "${kind} to ${owners*.email.join(', ')}")
        else log(review, 'EmailFailed', null, null, null, "${kind} to ${owners*.email.join(', ')} could not be sent")
    }
}
