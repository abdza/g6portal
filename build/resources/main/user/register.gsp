<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'user.label', default: 'User')}" />
        <title><g:message code="default.create.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#create-user" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
            </section>
            <section class="row">
                <div id="create-user" class="col-12 content scaffold-create" role="main">
                    <h1>Register</h1>
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
                    <g:form useToken="true" resource="${this.user}" method="POST">
                        <fieldset class="form">
                            <f:all except='isAdmin,isActive,resetPassword,lanidexception,role,roletargetid,lastlogin,date_joined,nodes,profilepic,resetexception,secretquestion,secretanswer,treesdate,lastUpdated,lastInfoUpdate,lastReminder,password5' bean="user"/>
                            <div class="fieldcontain required">
                              <label for="password2">Repeat Password
                                <span class="required-indicator">*</span>
                              </label><input type="password" name="password2" value="" required="" id="password2">
                            </div>

                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="register" class="save" value="Register" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
