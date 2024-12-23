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
                            <g:if test='${curuser?.isAdmin}'>
                                <label for='files'>Files:</label><input type="checkbox" name="files" id="files" />
                                <label for='user'>user:</label><input type="checkbox" name="user" id="user" />
                                <g:link class="edit" action="edit" resource="${this.portalModule}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
                                <input class="delete" type="submit" name="op" value="Delete" onclick="return confirm('Delete module ${this.portalModule.name}?');" />
                                <input class="delete" type="submit" name="op" value="Export" onclick="return confirm('Export module ${this.portalModule.name}?');" />
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
                    <h3>Settings</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <g:if test="${this.portalModule.name in session['developermodules']}">
                            <li><g:link controller='portalSetting' class="create" action="create" params="['module':this.portalModule.name]">Add Setting</g:link></li>
                        </g:if>
                        </ul>
                    </div>
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
                    <h3>Roles</h3>
                    <div class="nav" role="navigation">
                        <ul>
                        <g:if test="${this.portalModule.name in session['developermodules']}">
                            <li><g:link controller='userRole' class="create" action="create" params="['module':this.portalModule.name]">Add User Role</g:link></li>
                        </g:if>
                        </ul>
                    </div>
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
                            <g:if test='${curuser?.isAdmin}'>
                                <label for='files'>Files:</label><input type="checkbox" name="files" id="files" />
                                <label for='user'>User:</label><input type="checkbox" name="user" id="user" />
                                <g:link class="edit" action="edit" resource="${this.portalModule}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
                                <input class="delete" type="submit" name="op" value="Delete" onclick="return confirm('Delete module ${this.portalModule.name}?');" />
                                <input class="delete" type="submit" name="op" value="Export" onclick="return confirm('Export module ${this.portalModule.name}?');" />
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
