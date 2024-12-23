<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerData.label', default: 'PortalTrackerData')}" />
        <title><g:message code="default.edit.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#edit-portalTrackerData" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="edit-portalTrackerData" class="col-12 content scaffold-edit" role="main">
                    <h1><g:message code="default.edit.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.portalTrackerData}">
                    <ul class="errors" role="alert">
                        <g:eachError bean="${this.portalTrackerData}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                        </g:eachError>
                    </ul>
                    </g:hasErrors>
                    <g:uploadForm resource="${this.portalTrackerData}" method="PUT">
                        <g:hiddenField name="version" value="${this.portalTrackerData?.version}" />
			<fieldset class="form">
				<div class="fieldcontain">
					<label for="name">File</label>
					<input type="file" name="fileupload" value="" id="fileupload">
				</div>
			</fieldset>
                        <fieldset class="form">
                            <f:all except='tracker,path,date_created,uploaded,send_email,sent_email_date,messages,savedparams,file_link' bean="portalTrackerData"/>
			    <input type='hidden' name='tracker' id='tracker' value='${this.portalTrackerData?.tracker?.id}'/>
                        </fieldset>
                        <fieldset class="buttons">
                            <input class="save" type="submit" value="${message(code: 'default.button.update.label', default: 'Update')}" />
                        </fieldset>
                    </g:uploadForm>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
