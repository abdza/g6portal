<%-- Body of every access-review e-mail. kind: invite | reminder | revoked.
     Rendered by PortalAccessReviewService.email; inline styles only, as mail clients drop <style>. --%>
<div style="font-family: Arial, sans-serif; font-size: 14px; color: #222; max-width: 680px;">
    <p>Dear owner of <strong>${module.displayName()}</strong>,</p>

    <g:if test="${kind == 'revoked'}">
        <p>The <strong>${periodLabel}</strong> access review for this module was not approved by
        <strong><g:formatDate date="${review.dueDate}" format="d MMM yyyy"/></strong>, so
        <strong>${revokedCount}</strong> role(s) have been removed from the module. Users who relied on them
        no longer have access.</p>
        <p>To have access restored, contact the portal administrators. The review record, including
        everyone who was removed, is here: <a href="${link}">${link}</a></p>
    </g:if>
    <g:else>
        <g:if test="${kind == 'reminder'}">
            <p style="color: #b7791f;"><strong>Reminder ${review.remindersSent}:</strong> the review below has not been approved yet.</p>
        </g:if>
        <p>Please review who has access to this module for <strong>${periodLabel}</strong>. On the review page
        you can remove anyone who should no longer have access, add missing roles, and then approve.</p>
        <p style="margin: 20px 0;">
            <a href="${link}" style="background: #2a78d6; color: #fff; padding: 10px 18px; border-radius: 6px; text-decoration: none;">Review access</a>
        </p>
        <p><strong>Please approve by <g:formatDate date="${review.dueDate}" format="d MMM yyyy"/>.</strong>
        If the review is not approved by then, all ${roles.size()} role(s) below will be removed from the module
        automatically.</p>

        <p style="margin: 16px 0 4px;"><strong>${roles.size()} role(s) to review:</strong></p>
        <table cellpadding="6" cellspacing="0" style="border-collapse: collapse; font-size: 13px; margin-top: 10px;">
            <tr style="background: #f3f4f6;">
                <th align="right" style="border-bottom: 1px solid #ddd;">No.</th>
                <th align="left" style="border-bottom: 1px solid #ddd;">Name</th>
                <th align="left" style="border-bottom: 1px solid #ddd;">User ID</th>
                <th align="left" style="border-bottom: 1px solid #ddd;">Role</th>
            </tr>
            <g:each in="${roles.sort { a, b -> (a.user?.name ?: '') <=> (b.user?.name ?: '') ?: a.role <=> b.role }}" var="ur" status="ri">
                <tr>
                    <td align="right" style="border-bottom: 1px solid #eee; color: #777;">${ri + 1}</td>
                    <td style="border-bottom: 1px solid #eee;">${ur.user?.name}</td>
                    <td style="border-bottom: 1px solid #eee;">${ur.user?.userID}</td>
                    <td style="border-bottom: 1px solid #eee;">${ur.role}</td>
                </tr>
            </g:each>
        </table>
    </g:else>

    <p style="color: #777; font-size: 12px; margin-top: 24px;">You receive this because you are recorded as an owner of this module.</p>
</div>
