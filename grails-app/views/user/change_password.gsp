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
                <div id="edit-user" class="col-12 content scaffold-edit" role="main">
                    <h1>Change Password</h1>
                    <g:if test="${flash.message}">
                    <div class="alert alert-${flash.messageType ?: 'primary'}" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.user}">
                    <ul class="errors" role="alert">
                        <g:eachError bean="${this.user}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                        </g:eachError>
                    </ul>
                    </g:hasErrors>
                    <div class="card">
                        <div class="card-body">
                            <g:form useToken="true" action="update_password" method="POST" class="row g-3">
                                <div class="col-12">
                                    <label for="currentPassword" class="form-label">Current Password:</label>
                                    <g:passwordField name="currentPassword" class="form-control" required="true"/>
                                </div>
                                
                                <div class="col-12">
                                    <label for="newPassword" class="form-label">New Password:</label>
                                    <g:passwordField name="newPassword" class="form-control" required="true"/>
                                    <div class="form-text">Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one number, and one special character.</div>
                                </div>
                                
                                <div class="col-12">
                                    <label for="confirmPassword" class="form-label">Confirm New Password:</label>
                                    <g:passwordField name="confirmPassword" class="form-control" required="true"/>
                                </div>

                                <div class="col-12">
                                    <button type="submit" class="btn btn-primary">Change Password</button>
                                    <g:link controller="portalPage" action="home" class="btn btn-secondary">Cancel</g:link>
                                </div>
                            </g:form>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
