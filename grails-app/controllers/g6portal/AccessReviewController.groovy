package g6portal

/**
 * Monthly module access reviews - pages for owners and admins. All the rules live in
 * PortalAccessReviewService; this controller only checks who is asking and renders.
 *
 *   index       admin dashboard for one period: progress, every review, modules needing an owner
 *   mine        the reviews waiting for the current user (the landing page if they own several)
 *   show/{id}   one review: its roles, add/remove, approve, history (the link in the e-mails)
 *   addRole / removeRole / approve        owner (or portal admin) actions, POST + form token
 *   restore / runNow                      portal admin only
 *
 * SecurityInterceptor lets any logged-in user reach this controller (owners are usually not
 * admins of anything); every action below checks ownership or admin itself.
 */
class AccessReviewController {

    def portalAccessReviewService

    static allowedMethods = [addRole: 'POST', removeRole: 'POST', approve: 'POST', restore: 'POST', runNow: 'POST', clearPeriod: 'POST']

    private User currentUser() {
        session.curuser?.id ? User.get(session.curuser.id) : null
    }

    private boolean tokenOk() {
        def ok = true
        withForm { }.invalidToken {
            flash.message = 'Your session for that form expired - please try again.'
            ok = false
        }
        return ok
    }

    def index() {
        def curuser = currentUser()
        if(!curuser?.isAdmin) {
            redirect(action: 'mine')
            return
        }
        def svc = portalAccessReviewService
        def period = (params.period ==~ /\d{4}-\d{2}/) ? params.period : PortalAccessReviewService.periodOf(new Date())
        def reviews = PortalModuleAccessReview.findAllByPeriod(period).sort { it.module.name.toLowerCase() }
        def periods = PortalModuleAccessReview.executeQuery(
            'select distinct r.period from PortalModuleAccessReview r order by r.period desc')
        def sc = svc.scope()
        def counts = PortalModuleAccessReview.STATUSES.collectEntries { s -> [(s): reviews.count { it.status == s }] }
        [curuser: curuser, period: period, periodLabel: PortalAccessReviewService.periodLabel(period), periods: periods,
         reviews: reviews, counts: counts, noOwner: sc.noOwner, inScopeCount: sc.inScope.size(), roleCounts: sc.roleCounts,
         enabled: svc.enabled(), graceDays: svc.graceDays(), reminderDays: svc.reminderDays(),
         exemptRoles: svc.exemptRoles(), cc: svc.ccAddresses()]
    }

    def mine() {
        def curuser = currentUser()
        if(!curuser) { redirect(controller: 'user', action: 'login'); return }
        def ownedIds = PortalModuleContact.executeQuery(
            "select c.module.id from PortalModuleContact c where c.role = 'Owner' and c.user.id = :u", [u: curuser.id])
        def reviews = ownedIds ? PortalModuleAccessReview.executeQuery(
            'from PortalModuleAccessReview r where r.module.id in (:ids) order by r.period desc, r.id desc',
            [ids: ownedIds], [max: 200]) : []
        [curuser: curuser, pending: reviews.findAll { it.isPending() }.sort { it.dueDate },
         recent: reviews.findAll { !it.isPending() }.take(30)]
    }

    def show(Long id) {
        def curuser = currentUser()
        def review = id ? PortalModuleAccessReview.get(id) : null
        if(!review) {
            flash.message = 'That access review does not exist.'
            redirect(action: 'mine')
            return
        }
        def svc = portalAccessReviewService
        def canAct = svc.canAct(review, curuser)
        // module admins may look, read-only; anyone else is turned away
        def canView = canAct || curuser?.modulerole(review.module.name)?.contains('Admin')
        if(!canView) {
            flash.message = 'Only the owners of this module can open its access review.'
            redirect(action: 'mine')
            return
        }
        def roles = svc.reviewableRoles(review.module).sort { a, b -> (a.user?.name ?: '') <=> (b.user?.name ?: '') ?: a.role <=> b.role }
        // What was approved, and how the module has moved on since (keyed user id + role).
        def snapshot = svc.approvedSnapshot(review)
        def sinceAdded = [], sinceRemoved = []
        if(snapshot) {
            def key = { uid, role -> "${uid}|${role}".toString() }
            def then = (snapshot.roles ?: []).collectEntries { [(key(it.uid, it.role)): it] }
            def now = UserRole.findAllByModule(review.module.name).collectEntries { [(key(it.user?.id, it.role)): it] }
            sinceRemoved = then.findAll { k, v -> !now.containsKey(k) }.values().toList()
            sinceAdded = now.findAll { k, v -> !then.containsKey(k) }.values().toList()
        }
        [curuser: curuser, review: review, module: review.module, canAct: canAct, editable: canAct && review.isPending(),
         roles: roles, exempt: svc.exemptRolesOf(review.module), grantable: svc.grantableRoles(review.module),
         snapshot: snapshot, sinceAdded: sinceAdded, sinceRemoved: sinceRemoved,
         logs: PortalModuleAccessReviewLog.findAllByReview(review, [sort: 'id', order: 'desc']),
         periodLabel: PortalAccessReviewService.periodLabel(review.period)]
    }

    private void act(Long id, Closure work) {
        def review = PortalModuleAccessReview.get(id)
        if(!review) { redirect(action: 'mine'); return }
        if(tokenOk()) {
            try { flash.message = work(review, currentUser()) }
            catch(IllegalArgumentException e) { flash.error = e.message }
        }
        redirect(action: 'show', id: review.id)
    }

    def addRole(Long id) {
        act(id) { review, user ->
            def target = params.userId?.toString()?.isLong() ? User.get(params.userId as Long) : null
            def ur = portalAccessReviewService.addRole(review, user, target, params.role)
            "Added ${ur.role} for ${target.name}."
        }
    }

    def removeRole(Long id) {
        act(id) { review, user ->
            def ur = params.userRoleId?.toString()?.isLong() ? UserRole.get(params.userRoleId as Long) : null
            def what = ur ? "${ur.role} for ${ur.user?.name}" : ''
            portalAccessReviewService.removeRole(review, user, ur)
            "Removed ${what}."
        }
    }

    def approve(Long id) {
        act(id) { review, user ->
            if(params.confirm != 'yes') throw new IllegalArgumentException('Tick the confirmation box to approve.')
            portalAccessReviewService.approve(review, user, params.note)
            "Approved. Thank you - the ${PortalAccessReviewService.periodLabel(review.period)} review is complete."
        }
    }

    def restore(Long id) {
        act(id) { review, user ->
            def n = portalAccessReviewService.restore(review, user)
            "${n} role(s) restored."
        }
    }

    /** Admin: delete a period's reviews so the next run opens it again (see the service). */
    def clearPeriod() {
        def curuser = currentUser()
        def period = params.period?.toString()
        if(curuser?.isAdmin && tokenOk()) {
            try {
                def n = portalAccessReviewService.clearPeriod(period, curuser, params.includeRevoked == 'yes')
                flash.message = "Cleared ${n} review(s) for ${PortalAccessReviewService.periodLabel(period)}. " +
                                "Press Run now to open the period again."
            }
            catch(IllegalArgumentException e) {
                flash.message = e.message
            }
        }
        redirect(action: 'index', params: period ? [period: period] : [:])
    }

    def runNow() {
        def curuser = currentUser()
        if(curuser?.isAdmin && tokenOk()) {
            // force: an admin pressing the button means it, even while the schedule is switched off
            def s = portalAccessReviewService.run(new Date(), true)
            flash.message = "Run complete: ${s.opened} opened, ${s.reminded} reminded, ${s.revoked} revoked " +
                            "(${s.rolesRevoked} roles), ${s.cancelled} cancelled; ${s.noOwner} module(s) need an owner." +
                            (s.errors ? " Errors: ${s.errors.join('; ')}" : '')
        }
        redirect(action: 'index')
    }
}
