<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerStatus.label', default: 'PortalTrackerStatus')}" />
        <title><g:message code="default.create.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#create-portalTrackerStatus" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="create-portalTrackerStatus" class="col-12 content scaffold-create" role="main">
                    <h1><g:message code="default.create.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.portalTrackerStatus}">
                    <ul class="errors" role="alert">
                        <g:eachError bean="${this.portalTrackerStatus}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                        </g:eachError>
                    </ul>
                    </g:hasErrors>
                    <g:form resource="${this.portalTrackerStatus}" method="POST">
                        <fieldset class="form">
			    <g:if test='${params.tracker_id}'>
                                <f:all except='tracker' bean="portalTrackerStatus"/>
				<input type='hidden' name='tracker' id='tracker' value='${params.tracker_id}'/>
			    </g:if>
			    <g:else>
                            <f:all bean="portalTrackerStatus"/>
			    </g:else>
                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="create" class="save" value="${message(code: 'default.button.create.label', default: 'Create')}" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>