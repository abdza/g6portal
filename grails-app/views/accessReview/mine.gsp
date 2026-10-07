<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <title>My access reviews</title>
</head>
<body>
<div id="content" role="main">
    <div class="container">
        <section class="row">
            <div class="col-12 content ar-page">
                <g:if test="${flash.message}"><div class="alert alert-info">${flash.message}</div></g:if>
                <div class="ar-head">
                    <div>
                        <h1>My access reviews</h1>
                        <div class="ar-sub">Modules you own. Each month, confirm who has access to each of them.</div>
                    </div>
                    <g:if test="${curuser?.isAdmin}"><g:link action="index" class="btn btn-sm btn-outline-primary">Review dashboard</g:link></g:if>
                </div>

                <div class="ar-card">
                    <h3>Waiting for you <span class="ar-muted">${pending.size()}</span></h3>
                    <table class="table table-sm ar-table">
                        <thead><tr><th>No.</th><th>Module</th><th>Period</th><th>Due</th><th>Reminders</th><th></th></tr></thead>
                        <tbody>
                            <g:each in="${pending}" var="r" status="pi">
                                <tr>
                                    <td class="ar-muted">${pi + 1}</td>
                                    <td>${r.module.displayName()}</td>
                                    <td>${g6portal.PortalAccessReviewService.periodLabel(r.period)}</td>
                                    <td class="${(r.dueDate - new Date().clearTime()) <= 7 ? 'text-danger fw-bold' : ''}"><g:formatDate date="${r.dueDate}" format="d MMM yyyy"/></td>
                                    <td>${r.remindersSent}</td>
                                    <td class="text-end"><g:link action="show" id="${r.id}" class="btn btn-sm btn-primary">Review</g:link></td>
                                </tr>
                            </g:each>
                            <g:if test="${!pending}"><tr><td colspan="6" class="ar-muted">Nothing waiting - all your reviews are done.</td></tr></g:if>
                        </tbody>
                    </table>
                </div>

                <g:if test="${recent}">
                    <div class="ar-card ar-card-quiet">
                        <h3>Recent</h3>
                        <table class="table table-sm ar-table">
                            <thead><tr><th>No.</th><th>Module</th><th>Period</th><th>Result</th><th>When</th></tr></thead>
                            <tbody>
                                <g:each in="${recent}" var="r" status="ci">
                                    <tr>
                                        <td class="ar-muted">${ci + 1}</td>
                                        <td><g:link action="show" id="${r.id}">${r.module.displayName()}</g:link></td>
                                        <td>${g6portal.PortalAccessReviewService.periodLabel(r.period)}</td>
                                        <td><span class="ar-badge ar-${r.status.toLowerCase()}">${r.status}</span></td>
                                        <td><g:formatDate date="${r.closedDate}" format="d MMM yyyy"/></td>
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
