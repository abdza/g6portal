<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'user.label', default: 'User')}" />
        <title><g:message code="default.edit.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#edit-user" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="edit-user" class="col-12 content scaffold-edit" role="main">
                    <h1>My Profile</h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.user}">
                    <ul class="errors" role="alert">
                        <g:eachError bean="${this.user}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                        </g:eachError>
                    </ul>
                    </g:hasErrors>
                    <g:form useToken="true" action="my_profile_save" resource="${this.user}" method="PUT">
                        <g:hiddenField name="version" value="${this.user?.version}" />
                        <fieldset class="form">
                            <f:all except='profile_id,role,roletargetid,lastlogin,nodes,profilepic,lastUpdated,lastInfoUpdate,lastReminder,password,resetexception,secretquestion,secretanswer,date_joined,lanid,treesdate,lanidexception,isActive,isAdmin,resetPassword,password5,activeSessionId,activeSessionUpdated' bean="user"/>
                        </fieldset>
                        <fieldset class="buttons">
                            <input class="save" type="submit" value="${message(code: 'default.button.update.label', default: 'Update')}" />
                        </fieldset>
                    </g:form>
                    <g:if test="${this.user.moduleroles()}">
                    <h3>My Module Roles</h3>
                    <p>These module roles apply automatically at all times and don't require switching, so they won't show up next to your name above.</p>
                    <table class='table'>
                    <tr><th>Module</th><th>Role</th></tr>
                    <g:each in="${this.user.moduleroles()}" var="mrole">
                        <tr>
                            <td>${mrole.module}</td>
                            <td><g:link controller='userRole' action='show' params="[id:mrole.id]">${mrole.role}</g:link></td>
                        </tr>
                    </g:each>
                    </table>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
