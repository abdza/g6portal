<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main" />
    <title>Access review - ${module.displayName()}</title>
</head>
<body>
<div id="content" role="main">
    <div class="container">
        <section class="row">
            <div class="nav" role="navigation">
                <ul>
                    <li><g:link class="list" action="mine">My access reviews</g:link></li>
                    <g:if test="${curuser?.isAdmin}"><li><g:link class="list" action="index" params="[period: review.period]">Review dashboard</g:link></li></g:if>
                </ul>
            </div>
        </section>
        <section class="row">
            <div class="col-12 content ar-page">
                <g:if test="${flash.message}"><div class="alert alert-success">${flash.message}</div></g:if>
                <g:if test="${flash.error}"><div class="alert alert-danger">${flash.error}</div></g:if>

                <div class="ar-head">
                    <div>
                        <h1>${module.displayName()} <span class="ar-badge ar-${review.status.toLowerCase()}">${review.status}</span></h1>
                        <div class="ar-sub">Access review for <strong>${periodLabel}</strong> &middot; <code>${module.name}</code>
                            &middot; owners: ${module.owners()*.name.join(', ')}</div>
                    </div>
                    <div class="ar-due">
                        <g:if test="${review.pending}">
                            <% def daysLeft = (review.dueDate - new Date().clearTime()) %>
                            <div class="ar-due-n ${daysLeft <= 7 ? 'ar-urgent' : ''}">${daysLeft}</div>
                            <div>day(s) left &middot; due <g:formatDate date="${review.dueDate}" format="d MMM yyyy"/></div>
                        </g:if>
                        <g:elseif test="${review.status == 'Approved'}">
                            Approved by ${review.approvedBy?.name}<br/><g:formatDate date="${review.approvedDate}" format="d MMM yyyy, HH:mm"/>
                        </g:elseif>
                        <g:else>
                            ${review.status} <g:formatDate date="${review.closedDate}" format="d MMM yyyy"/>
                        </g:else>
                    </div>
                </div>

                <g:if test="${editable}">
                    <div class="ar-steps">
                        <strong>What to do:</strong> check everyone below still needs their role. Remove anyone who
                        should not have access, add anyone missing, then confirm and approve at the bottom. If this
                        review is not approved by <g:formatDate date="${review.dueDate}" format="d MMM yyyy"/>, every role
                        listed here will be removed from the module automatically.
                    </div>
                </g:if>
                <g:elseif test="${review.pending && !canAct}">
                    <div class="ar-steps">You can view this review; only the module's owners can change or approve it.</div>
                </g:elseif>

                <%-- ---------- what was approved (stored at approval, never recomputed) ---------- --%>
                <g:if test="${snapshot}">
                    <div class="ar-card">
                        <h3>Roles as approved <span class="ar-muted">${snapshot.roles?.size() ?: 0}</span></h3>
                        <p class="ar-muted">Recorded when ${snapshot.approver?.name} (${snapshot.approver?.userID}) approved this review,
                            on ${snapshot.takenAt?.replace('T', ' ')}. Names and user IDs are as they were then.</p>
                        <table class="table table-sm ar-table">
                            <thead><tr><th>No.</th><th>Name</th><th>User ID</th><th>E-mail</th><th>Role</th></tr></thead>
                            <tbody>
                                <g:each in="${snapshot.roles}" var="r" status="si">
                                    <tr>
                                        <td class="ar-muted">${si + 1}</td>
                                        <td>${r.name}<g:if test="${r.active == false}"> <span class="ar-flag">inactive account</span></g:if></td>
                                        <td>${r.userID}</td>
                                        <td>${r.email}</td>
                                        <td>${r.role}<g:if test="${r.exempt}"> <span class="ar-muted">(not part of the review)</span></g:if></td>
                                    </tr>
                                </g:each>
                                <g:if test="${!snapshot.roles}"><tr><td colspan="5" class="ar-muted">The module had no roles when this was approved.</td></tr></g:if>
                            </tbody>
                        </table>
                        <g:if test="${sinceAdded || sinceRemoved}">
                            <div class="ar-steps" style="margin-top:8px">
                                <strong>Changed since approval:</strong>
                                <g:if test="${sinceAdded}"> ${sinceAdded.size()} role(s) added
                                    (<g:each in="${sinceAdded}" var="a" status="ai">${ai ? ', ' : ''}${a.user?.name} &ndash; ${a.role}</g:each>)</g:if><g:if test="${sinceAdded && sinceRemoved}">;</g:if>
                                <g:if test="${sinceRemoved}"> ${sinceRemoved.size()} role(s) removed
                                    (<g:each in="${sinceRemoved}" var="r" status="ri2">${ri2 ? ', ' : ''}${r.name} &ndash; ${r.role}</g:each>)</g:if>
                            </div>
                        </g:if>
                    </div>
                </g:if>
                <g:elseif test="${review.status == 'Approved'}">
                    <div class="ar-steps">This review was approved before the approved list was recorded on reviews, so only the
                        module's current roles can be shown below.</div>
                </g:elseif>

                <%-- ---------- roles under review (live) ---------- --%>
                <div class="ar-card">
                    <h3>${review.pending ? 'Roles under review' : 'Roles in the module now'} <span class="ar-muted">${roles.size()}</span></h3>
                    <table class="table table-sm ar-table">
                        <thead><tr><th>No.</th><th>Name</th><th>User ID</th><th>E-mail</th><th>Role</th><g:if test="${editable}"><th></th></g:if></tr></thead>
                        <tbody>
                            <g:each in="${roles}" var="ur" status="ri">
                                <tr class="${ur.user?.isActive == false ? 'ar-inactive' : ''}">
                                    <td class="ar-muted">${ri + 1}</td>
                                    <td>${ur.user?.name}<g:if test="${ur.user?.isActive == false}"> <span class="ar-flag">inactive account</span></g:if></td>
                                    <td>${ur.user?.userID}</td>
                                    <td>${ur.user?.email}</td>
                                    <td>${ur.role}</td>
                                    <g:if test="${editable}">
                                        <td class="text-end">
                                            <g:form action="removeRole" id="${review.id}" useToken="true" class="d-inline"
                                                    onsubmit="return confirm('Remove ${ur.role?.encodeAsJavaScript()} from ${ur.user?.name?.encodeAsJavaScript()}?');">
                                                <input type="hidden" name="userRoleId" value="${ur.id}"/>
                                                <button type="submit" class="btn btn-sm btn-outline-danger">Remove</button>
                                            </g:form>
                                        </td>
                                    </g:if>
                                </tr>
                            </g:each>
                            <g:if test="${!roles}"><tr><td colspan="6" class="ar-muted">No roles in this module.</td></tr></g:if>
                        </tbody>
                    </table>

                    <g:if test="${editable}">
                        <h4>Add a role</h4>
                        <g:if test="${grantable}">
                            <g:form action="addRole" id="${review.id}" useToken="true" class="ar-add">
                                <div id="addUser_div" class="ar-add-user">
                                    <select name="userId" id="addUser" style="width: 100%;"></select>
                                </div>
                                <select name="role" id="addRoleName" class="form-select form-select-sm">
                                    <g:each in="${grantable}" var="rn"><option value="${rn}">${rn}</option></g:each>
                                </select>
                                <button type="submit" class="btn btn-sm btn-primary">Add</button>
                            </g:form>
                        </g:if>
                        <g:else>
                            <p class="ar-muted">This module's trackers define no user roles, so there is nothing to grant from here.</p>
                        </g:else>
                    </g:if>
                </div>

                <g:if test="${exempt}">
                    <div class="ar-card ar-card-quiet">
                        <h3>Not part of the review <span class="ar-muted">${exempt.size()}</span></h3>
                        <p class="ar-muted">These roles are managed separately and are never removed by the review.</p>
                        <table class="table table-sm ar-table">
                            <tbody>
                                <g:each in="${exempt.sort { it.user?.name }}" var="ur" status="ei">
                                    <tr><td class="ar-muted">${ei + 1}</td><td>${ur.user?.name}</td><td>${ur.user?.userID}</td><td>${ur.role}</td></tr>
                                </g:each>
                            </tbody>
                        </table>
                    </div>
                </g:if>

                <%-- ---------- approve ---------- --%>
                <g:if test="${editable}">
                    <div class="ar-card ar-approve">
                        <h3>Approve</h3>
                        <g:form action="approve" id="${review.id}" useToken="true">
                            <label class="ar-confirm">
                                <input type="checkbox" name="confirm" value="yes" required/>
                                I have checked every user and role listed above and confirm they still need this access.
                            </label>
                            <textarea name="note" rows="2" class="form-control" maxlength="2000" placeholder="Note (optional)"></textarea>
                            <button type="submit" class="btn btn-success mt-2">Approve access for ${periodLabel}</button>
                        </g:form>
                    </div>
                </g:if>
                <g:if test="${review.approvalNote}">
                    <div class="ar-card"><h3>Approval note</h3><p class="ar-pre">${review.approvalNote}</p></div>
                </g:if>

                <g:if test="${curuser?.isAdmin && review.status == 'Revoked'}">
                    <div class="ar-card">
                        <h3>Restore</h3>
                        <p>Puts back every role this review removed.</p>
                        <g:form action="restore" id="${review.id}" useToken="true" onsubmit="return confirm('Restore all roles removed by this review?');">
                            <button type="submit" class="btn btn-outline-primary">Restore removed roles</button>
                        </g:form>
                    </div>
                </g:if>

                <%-- ---------- history ---------- --%>
                <div class="ar-card">
                    <h3>History</h3>
                    <table class="table table-sm ar-table">
                        <thead><tr><th>No.</th><th>When</th><th>What</th><th>User</th><th>Role</th><th>By</th><th>Detail</th></tr></thead>
                        <tbody>
                            <g:each in="${logs}" var="l" status="gi">
                                <tr>
                                    <td class="ar-muted">${gi + 1}</td>
                                    <td class="text-nowrap"><g:formatDate date="${l.dateCreated}" format="d MMM yyyy HH:mm"/></td>
                                    <td>${l.action}</td>
                                    <td>${l.user?.name}</td>
                                    <td>${l.role}</td>
                                    <td>${l.actor?.name ?: 'System'}</td>
                                    <td>${l.detail}</td>
                                </tr>
                            </g:each>
                        </tbody>
                    </table>
                </div>
            </div>
        </section>
    </div>
</div>
<g:render template="styles"/>
<g:if test="${editable && grantable}">
    <asset:script><g:user_selector property="addUser" parent="#addUser_div"/></asset:script>
</g:if>
</body>
</html>
