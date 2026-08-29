<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalModule.label', default: 'PortalModule')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#show-portalModule" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <g:if test='${curuser?.isAdmin}'>
                            <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-portalModule" class="col-12 content scaffold-show" role="main">
                    <g:form useToken="true" resource="${this.portalModule}" method="DELETE">
                        <fieldset class="buttons">
                            <g:if test="${curuser?.isAdmin || curuser?.modulerole(this.portalModule.name)?.contains('Developer')}">
                                <label for='files'>Files:</label><input type="checkbox" name="files" id="files" />
                                <label for='trees'>Trees:</label><input type="checkbox" name="trees" id="trees" title="Include this module's trees in the export. On import trees are taken whenever the package has them." />
                                <label for='menus'>Menus:</label><input type="checkbox" name="menus" id="menus" title="Include this module's menu entries in the export, so importing it elsewhere can wire up the megamenu and sidebar." />
                                <label for='user'>user:</label><input type="checkbox" name="user" id="user" />
                                <g:link class="edit" action="edit" resource="${this.portalModule}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
                                <input class="delete" type="submit" name="op" value="Export" onclick="return confirm('Export module ${this.portalModule.name}?');" />
                            </g:if>
                            <g:if test='${curuser?.isAdmin}'>
                                <input class="delete" type="submit" name="op" value="Delete" onclick="return confirm('Delete module ${this.portalModule.name}?');" />
                                <input class="delete" type="submit" name="op" value="Import" onclick="return confirm('Import module ${this.portalModule.name}?');" />
                            </g:if>
                            <g:link class="save" controller="userRole" action="create" params="[module:this.portalModule.name,role:'Admin']">Add Admin</g:link>
                            <g:link class="save" controller="userRole" action="create" params="[module:this.portalModule.name,role:'Developer']">Add Developer</g:link>
                        </fieldset>
                    </g:form>
                    <h1><g:message code="default.show.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <f:display bean="portalModule" />
                    <h3>Files</h3>
                    <div class="nav" role="navigation">
                        <ul>
                            <%-- q= is passed deliberately: FileLinkController.index only applies the
                                 module filter inside its search branch, which needs the q param present.
                                 Shown only to admins of THIS module: listing a module's files legitimately
                                 requires that role, so for anyone else the link would just bounce. --%>
                            <g:if test="${this.portalModule.name in session['adminmodules']}">
                                <li><g:link class="list" controller="fileLink" action="index" params="[module:this.portalModule.name,q:'']">View Files for this Module</g:link></li>
                            </g:if>
                            <g:if test='${curuser?.isAdmin}'>
                                <li><g:link class="list" controller="fileLink" action="jobstatus">File Size Job Status</g:link></li>
                            </g:if>
                        </ul>
                    </div>
                    <table class='table'>
                    <tr><th>Total Size on Disk</th><th>Files</th><th>Size Not Recorded</th></tr>
                    <tr>
                        <td><strong>${g6portal.FileLink.humanSize(filesize)}</strong></td>
                        <td>${filecount ?: 0}</td>
                        <td>
                            <g:if test="${fileunsized}">
                                <span title="These file records have no size stored, so the total above understates actual disk usage.">${fileunsized}</span>
                                <g:if test='${curuser?.isAdmin}'>
                                    <%-- scopemodule, not module: params.module makes SecurityInterceptor
                                         demand an Admin/Developer role on that specific module --%>
                                    &nbsp;<g:link controller="fileLink" action="updateMissingSizes" params="[scopemodule:this.portalModule.name]"
                                        onclick="return confirm('Scan disk and backfill sizes for the ${fileunsized} unsized file records in module ${this.portalModule.name}?');">Backfill</g:link>
                                </g:if>
                            </g:if>
                            <g:else>0</g:else>
                        </td>
                    </tr>
                    </table>
                    <br/>
                    <h3>Trackers</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <g:if test="${this.portalModule.name in session['developermodules']}">
                            <li><g:link controller='portalTracker' class="create" action="create" params="['module':this.portalModule.name]">Add Tracker</g:link></li>
                        </g:if>
                        </ul>
                    </div>
                    <table class='table'>
                    <tr><th>#</th><th>Title</th><th>Slug</th><th>Type</th><th>Fields</th><th>Transitions</th><th>Action</th></tr>
                    <g:each in='${trackers}' var='tracker' status='i'>
                        <tr>
                        <td>${i+1}</td>
                        <td>${tracker.name}</td>
                        <td>${tracker.slug}</td>
                        <td>${tracker.tracker_type}</td>
                        <td>${tracker.fields.size()}</td>
                        <td>${tracker.transitions.size()}</td>
                        <td>
                        <g:link controller='portalTracker' action='list' params="['slug':tracker.slug,'module':tracker.module]">List</g:link>
                        &nbsp;&nbsp;
                        <g:if test="${tracker.module in session['developermodules']}">
                          <g:link controller='portalTracker' action='show' id='${tracker.id}'>Edit</g:link>
                        </g:if>
                        </td>
                        </tr>
                    </g:each>
                    </table>
                    <br/>
                    <h3>Pages</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <g:if test="${this.portalModule.name in session['developermodules']}">
                            <li><g:link controller='portalPage' class="create" action="create" params="['module':this.portalModule.name]">Add Page</g:link></li>
                        </g:if>
                        </ul>
                    </div>
                    <table class='table'>
                    <tr><th>#</th><th>Title</th><th>Slug</th><th>Action</th></tr>
                    <g:each in='${pages}' var='page' status="i">
                        <tr>
                        <td>${i+1}</td>
                        <td>${page.title}</td>
                        <td>${page.slug}</td>
                        <td>
                        <g:if test="${!page.runable}"><g:link class="create" controller='portalPage' action="display" params="['slug':page.slug,'module':page.module]">View</g:link></g:if>
                        <g:if test="${page.runable}"><g:link class="create" controller='portalPage' action="runpage" params="['slug':page.slug,'module':page.module]">Run</g:link></g:if>
                        &nbsp;&nbsp;
                        <g:if test="${page.module in session['developermodules']}">
                          <g:link class="edit" controller='portalPage' action="edit" id="${page.id}">Edit</g:link>
                          &nbsp;&nbsp;
                          <g:link class="display" controller='portalPage' action="show" id="${page.id}">Details</g:link>
                        </g:if>
                        </td>
                        </tr>
                    </g:each>
                    </table>
                    <br/>
                    <h3>Roles</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <g:if test="${this.portalModule.name in session['developermodules']}">
                            <li><g:link controller='userRole' class="create" action="create" params="['module':this.portalModule.name]">Add User Role</g:link></li>
                            <li><g:link class="list" action="exportUserRoles" id="${this.portalModule.id}">Export User Roles</g:link></li>
                        </g:if>
                        </ul>
                    </div>
                    <g:if test="${this.portalModule.name in session['developermodules']}">
                        <div style="margin: 10px 0; padding: 10px; border: 1px solid #ccc; border-radius: 4px; display: flex; align-items: center; gap: 20px; flex-wrap: wrap;">
                            <g:uploadForm action="importUserRoles" id="${this.portalModule.id}" style="display:inline-flex; align-items:center; gap:8px; margin:0;">
                                <label>Import User Roles (Excel): </label>
                                <input type="file" name="userRoleFile" accept=".xlsx" required />
                                <input type="submit" class="save" value="Import" />
                            </g:uploadForm>
                            <g:form action="deleteAllUserRoles" id="${this.portalModule.id}" method="post" style="display:inline; margin:0;">
                                <input type="submit" class="delete" value="Delete All Roles"
                                    onclick="return confirm('Delete ALL user roles for module \'${this.portalModule.name}\'?') &amp;&amp; confirm('This cannot be undone. Are you absolutely sure?');" />
                            </g:form>
                        </div>
                    </g:if>
                    <g:if test="${flash.errors}">
                        <div class="errors" role="status">
                            <ul>
                                <g:each in="${flash.errors}" var="error">
                                    <li>${error}</li>
                                </g:each>
                            </ul>
                        </div>
                    </g:if>
                    <table class='table'>
                    <tr><th>#</th><th>Name</th><th>User ID</th><th>Role</th><th>Action</th></tr>
                    <g:each in='${roles}' var='role' status='i'>
                        <tr>
                        <td>${i+1}</td>
                        <td>
                  <g:if test='${curuser?.switchable() && !session.realuser}'>
                  <g:link controller='user' action='switchuser' id='${role.user.userID}'>
${role.user.name}
                  </g:link>
                  </g:if>
                  <g:else>
${role.user.name}
                  </g:else>
</td>
                        <td>${role.user.userID}</td>
                        <td>${role.role}</td>
                        <td>
                        <g:if test="${role.module in session['developermodules']}">
                          <g:link controller='userRole' action='edit' id='${role.id}'>Edit</g:link>
                        </g:if>
                        </td>
                        </tr>
                    </g:each>
                    </table>
                    <br/>
                    <h3>Settings</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <g:if test="${this.portalModule.name in session['developermodules']}">
                            <li><g:link controller='portalSetting' class="create" action="create" params="['module':this.portalModule.name]">Add Setting</g:link></li>
                            <li><g:link class="list" action="exportSettings" id="${this.portalModule.id}">Export Settings</g:link></li>
                        </g:if>
                        </ul>
                    </div>
                    <g:if test="${this.portalModule.name in session['developermodules']}">
                        <div style="margin: 10px 0; padding: 10px; border: 1px solid #ccc; border-radius: 4px; display: flex; align-items: center; gap: 20px; flex-wrap: wrap;">
                            <g:uploadForm action="importSettings" id="${this.portalModule.id}" style="display:inline-flex; align-items:center; gap:8px; margin:0;">
                                <label>Import Settings (Excel): </label>
                                <input type="file" name="settingsFile" accept=".xlsx" required />
                                <input type="submit" class="save" value="Import" />
                            </g:uploadForm>
                            <g:form action="deleteAllSettings" id="${this.portalModule.id}" method="post" style="display:inline; margin:0;">
                                <input type="submit" class="delete" value="Delete All Settings"
                                    onclick="return confirm('Delete ALL settings for module \'${this.portalModule.name}\'?') &amp;&amp; confirm('This cannot be undone. Are you absolutely sure?');" />
                            </g:form>
                        </div>
                    </g:if>
                    <table class='table'>
                    <tr><th>#</th><th>Name</th><th>Type</th><th>Value</th><th>Action</th></tr>
                    <g:each in='${settings}' var='setting' status='i'>
                        <tr>
                        <td>${i+1}</td>
                        <td>${setting.name}</td>
                        <td>${setting.type}</td>
                        <td>${setting.value()}</td>
                        <td>
                        <g:if test="${setting.module in session['developermodules']}">
                          <g:link controller='portalSetting' action='edit' id='${setting.id}'>Edit</g:link>
                        </g:if>
                        </td>
                        </tr>
                    </g:each>
                    </table>
                    <br/>
                    <h3>Trees</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <%-- Matches what SecurityInterceptor actually allows for tree writes:
                             admin of the tree's own module, or a superuser anywhere. --%>
                        <g:if test="${curuser?.isAdmin || this.portalModule.name in session['adminmodules']}">
                            <li><g:link controller='portalTree' class="create" action="create" params="['module':this.portalModule.name]">Add Tree</g:link></li>
                            <li><g:link controller='portalTree' class="create" action="importtreeform">Import Tree</g:link></li>
                        </g:if>
                        </ul>
                    </div>
                    <table class='table js-collapse' data-collapse-rows='20' data-collapse-label='trees'>
                    <tr><th>#</th><th>Name</th><th>Root</th><th>Nodes</th><th>Valid From</th><th>Expires</th><th>Action</th></tr>
                    <g:each in='${trees}' var='tree' status="i">
                        <tr>
                        <td>${i+1}</td>
                        <td>${tree.name}</td>
                        <td>${tree.root?.name}</td>
                        <td>${treenodecounts ? treenodecounts[tree.id] : ''}</td>
                        <td><g:formatDate date="${tree.valid}" format="yyyy-MM-dd"/></td>
                        <td><g:formatDate date="${tree.expire}" format="yyyy-MM-dd"/></td>
                        <td>
                        <g:link controller='portalTree' action='show' id='${tree.id}'>View</g:link>
                        &nbsp;&nbsp;
                        <g:link controller='portalTree' action='exporttree' id='${tree.id}'>Export</g:link>
                        </td>
                        </tr>
                    </g:each>
                    <g:if test="${!trees}">
                        <tr><td colspan="7">No trees in this module.</td></tr>
                    </g:if>
                    </table>
                    <br/>
                    <h3>Endpoints</h3>
                    <g:if test="${curuser?.isAdmin}">
                        <%-- Superusers only, matching the sidebar and SecurityInterceptor: an
                             endpoint's target is a program the server runs. Everyone else does
                             not see the section at all, since they could not open it anyway. --%>
                        <div class="nav" role="navigation">
                            <ul>
                                <li><g:link controller='portalEndpoint' class="create" action="create" params="['module':this.portalModule.name]">Add Endpoint</g:link></li>
                            </ul>
                        </div>
                        <table class='table js-collapse' data-collapse-rows='20' data-collapse-label='endpoints'>
                        <tr><th>#</th><th>URL</th><th>Handler</th><th>Target</th><th>Auth</th><th>Enabled</th><th>Action</th></tr>
                        <g:each in='${endpoints}' var='endpoint' status="i">
                            <tr>
                            <td>${i+1}</td>
                            <td><code>/svc/${endpoint.module}/${endpoint.slug}</code></td>
                            <td>${endpoint.handler_type}</td>
                            <td><code>${endpoint.target}</code></td>
                            <td>${endpoint.auth_mode}</td>
                            <td>
                                <g:if test="${endpoint.enabled}"><span class="badge bg-success">Yes</span></g:if>
                                <g:else><span class="badge bg-secondary">No</span></g:else>
                            </td>
                            <td><g:link controller='portalEndpoint' action='show' id='${endpoint.id}'>View</g:link></td>
                            </tr>
                        </g:each>
                        <g:if test="${!endpoints}">
                            <tr><td colspan="7">No endpoints in this module.</td></tr>
                        </g:if>
                        </table>
                    </g:if>
                    <g:else>
                        <p class="small">Endpoints are managed by system administrators.</p>
                    </g:else>
                    <br/>
                    <g:if test="${curuser?.isAdmin || this.portalModule.name in session['developermodules']}">
                        <h3>Import History</h3>
                        <div class="nav" role="navigation">
                            <ul>
                                <li><g:link class="list" action="importlogs" params="[module:this.portalModule.name]">View All Import Logs</g:link></li>
                            </ul>
                        </div>
                        <table class='table'>
                        <tr><th>#</th><th>Date</th><th>Imported By</th><th>Remarks</th><th>Action</th></tr>
                        <g:each in='${importlogs}' var='importlog' status='i'>
                            <tr>
                            <td>${i+1}</td>
                            <td><g:formatDate date="${importlog.dateCreated}" format="yyyy-MM-dd HH:mm:ss"/></td>
                            <td>${importlog.staffname} (${importlog.staffid})</td>
                            <td>${importlog.remarks}</td>
                            <td><g:link action='importlog' id='${importlog.id}'>View Changes</g:link></td>
                            </tr>
                        </g:each>
                        </table>
                        <br/>
                    </g:if>
                    <h3>Admins</h3>
                    <g:each in='${admins}' var='admin'>
                        <li><g:link controller='userRole' action='show' id='${admin.id}'>${admin.user.name}</g:link></li>
                    </g:each>
                    <h3>Developers</h3>
                    <g:each in='${developers}' var='developer'>
                        <li><g:link controller='userRole' action='show' id='${developer.id}'>${developer.user.name}</g:link></li>
                    </g:each>
                    <br/>
                    <g:form useToken="true" resource="${this.portalModule}" method="DELETE">
                        <fieldset class="buttons">
                            <g:if test="${curuser?.isAdmin || curuser?.modulerole(this.portalModule.name)?.contains('Developer')}">
                                <label for='files'>Files:</label><input type="checkbox" name="files" id="files" />
                                <label for='trees'>Trees:</label><input type="checkbox" name="trees" id="trees" title="Include this module's trees in the export. On import trees are taken whenever the package has them." />
                                <label for='menus'>Menus:</label><input type="checkbox" name="menus" id="menus" title="Include this module's menu entries in the export, so importing it elsewhere can wire up the megamenu and sidebar." />
                                <label for='user'>User:</label><input type="checkbox" name="user" id="user" />
                                <g:link class="edit" action="edit" resource="${this.portalModule}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
                                <input class="delete" type="submit" name="op" value="Export" onclick="return confirm('Export module ${this.portalModule.name}?');" />
                            </g:if>
                            <g:if test='${curuser?.isAdmin}'>
                                <input class="delete" type="submit" name="op" value="Delete" onclick="return confirm('Delete module ${this.portalModule.name}?');" />
                                <input class="delete" type="submit" name="op" value="Import" onclick="return confirm('Import module ${this.portalModule.name}?');" />
                            </g:if>
                            <g:link class="save" controller="userRole" action="create" params="[module:this.portalModule.name,role:'Admin']">Add Admin</g:link>
                            <g:link class="save" controller="userRole" action="create" params="[module:this.portalModule.name,role:'Developer']">Add Developer</g:link>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
