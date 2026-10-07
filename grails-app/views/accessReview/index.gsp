<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <title>Access review dashboard</title>
</head>
<body>
<div id="content" role="main">
    <div class="container">
        <section class="row">
            <div class="nav" role="navigation">
                <ul>
                    <li><g:link class="list" controller="portalModule" action="index">Modules</g:link></li>
                    <li><g:link class="list" action="mine">My access reviews</g:link></li>
                </ul>
            </div>
        </section>
        <section class="row">
            <div class="col-12 content ar-page">
                <g:if test="${flash.message}"><div class="alert alert-info">${flash.message}</div></g:if>
                <div class="ar-head">
                    <div>
                        <h1>Access reviews &middot; ${periodLabel}</h1>
                        <div class="ar-sub">
                            <g:if test="${enabled}"><span class="ar-badge ar-approved">Scheduled run on</span></g:if>
                            <g:else><span class="ar-badge ar-cancelled">Scheduled run off</span> (setting <code>portal.access_review_enabled</code>)</g:else>
                            &middot; grace ${graceDays} days &middot; reminders on day ${reminderDays.join(', ')}
                            &middot; never reviewed: ${exemptRoles.join(', ')}<g:if test="${cc}"> &middot; cc ${cc.join(', ')}</g:if>
                        </div>
                    </div>
                    <div class="d-flex gap-2 align-items-center">
                        <g:form action="index" method="get" class="d-inline">
                            <select name="period" class="form-select form-select-sm" onchange="this.form.submit()">
                                <g:if test="${!(period in periods)}"><option value="${period}" selected>${periodLabel}</option></g:if>
                                <g:each in="${periods}" var="p"><option value="${p}" ${p == period ? 'selected' : ''}>${g6portal.PortalAccessReviewService.periodLabel(p)}</option></g:each>
                            </select>
                        </g:form>
                        <g:form action="runNow" useToken="true" class="d-inline"
                                onsubmit="return confirm('Run the access review now? It opens the current month reviews, sends any reminders that are due and revokes overdue reviews - e-mails go out for real.');">
                            <button type="submit" class="btn btn-sm btn-outline-danger">Run now</button>
                        </g:form>
                        <g:if test="${reviews}">
                            <%-- deletes the review records only; roles already added/removed stay as they are --%>
                            <g:form action="clearPeriod" useToken="true" class="d-inline ar-clear"
                                    onsubmit="return confirm('Delete all ${reviews.size()} review(s) for ${periodLabel} and their history? Roles are not changed. The next Run now opens the period again and e-mails the owners again.');">
                                <input type="hidden" name="period" value="${period}"/>
                                <g:if test="${counts.Revoked}">
                                    <label class="ar-muted" title="A revoked review's history is the only record of the roles it removed; clearing it means they can no longer be restored from here.">
                                        <input type="checkbox" name="includeRevoked" value="yes"/> include ${counts.Revoked} revoked
                                    </label>
                                </g:if>
                                <button type="submit" class="btn btn-sm btn-outline-secondary">Clear this period</button>
                            </g:form>
                        </g:if>
                    </div>
                </div>

                <div class="ar-stats">
                    <div class="ar-stat"><div class="v">${inScopeCount}</div><div class="l">Modules in scope today</div></div>
                    <div class="ar-stat"><div class="v">${reviews.size()}</div><div class="l">Reviews this period</div></div>
                    <div class="ar-stat"><div class="v">${counts.Approved}</div><div class="l">Approved</div></div>
                    <div class="ar-stat"><div class="v">${counts.Pending}</div><div class="l">Pending</div></div>
                    <div class="ar-stat"><div class="v" style="${counts.Revoked ? 'color:#c0392b' : ''}">${counts.Revoked}</div><div class="l">Revoked</div></div>
                    <div class="ar-stat"><div class="v" style="${noOwner ? 'color:#b7791f' : ''}">${noOwner.size()}</div><div class="l">Need an owner</div></div>
                </div>

                <div class="ar-card">
                    <h3>Reviews for ${periodLabel}</h3>
                    <table class="table table-sm ar-table">
                        <thead><tr><th>No.</th><th>Module</th><th>Owners</th><th>Status</th><th>Due</th><th>Reminders</th><th>Approved by</th></tr></thead>
                        <tbody>
                            <g:each in="${reviews}" var="r" status="vi">
                                <tr>
                                    <td class="ar-muted">${vi + 1}</td>
                                    <td><g:link action="show" id="${r.id}">${r.module.displayName()}</g:link> <span class="ar-muted">${r.module.name}</span></td>
                                    <td>${r.module.owners()*.name.join(', ')}</td>
                                    <td><span class="ar-badge ar-${r.status.toLowerCase()}">${r.status}</span></td>
                                    <td class="text-nowrap"><g:formatDate date="${r.dueDate}" format="d MMM yyyy"/></td>
                                    <td>${r.remindersSent}</td>
                                    <td>${r.approvedBy?.name}</td>
                                </tr>
                            </g:each>
                            <g:if test="${!reviews}"><tr><td colspan="7" class="ar-muted">No reviews opened for this period yet.</td></tr></g:if>
                        </tbody>
                    </table>
                </div>

                <g:if test="${noOwner}">
                    <div class="ar-card ar-card-quiet">
                        <h3>Need an owner <span class="ar-muted">${noOwner.size()}</span></h3>
                        <p class="ar-muted">These modules have roles but no owner, so they are not reviewed - and never revoked. Set an owner on the module and it joins the next run.</p>
                        <table class="table table-sm ar-table">
                            <thead><tr><th>No.</th><th>Module</th><th>Reviewable roles</th><th>Status</th></tr></thead>
                            <tbody>
                                <g:each in="${noOwner}" var="m" status="oi">
                                    <tr>
                                        <td class="ar-muted">${oi + 1}</td>
                                        <td><g:link controller="portalModule" action="show" id="${m.id}">${m.displayName()}</g:link> <span class="ar-muted">${m.name}</span></td>
                                        <td>${roleCounts[m.name]}</td>
                                        <td>${m.status ?: 'Not recorded'}</td>
                                    </tr>
                                </g:each>
                            </tbody>
                        </table>
                    </div>
                </g:if>
            </div>
        </section>
    </div>
</div>
<g:render template="styles"/>
</body>
</html>
