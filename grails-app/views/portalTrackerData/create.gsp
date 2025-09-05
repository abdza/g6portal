<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerData.label', default: 'PortalTrackerData')}" />
        <title><g:message code="default.create.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#create-portalTrackerData" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index">Data Update List</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="create-portalTrackerData" class="col-12 content scaffold-create" role="main">
                    <h1>Data Update</h1>
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
                    <g:uploadForm resource="${this.portalTrackerData}" method="POST">
			<fieldset class="form">
				<div class="fieldcontain">
					<label for="name">File</label>
					<input type="file" name="fileupload" value="" id="fileupload">
				</div>
			</fieldset>
                        <fieldset class="form">
			    <g:if test='${params.tracker_id}'>
				<f:all except='isTrackerDeleting,tracker,module,path,date_created,uploaded,send_email,sent_email_date,messages,savedparams,file_link,uploadStatus' bean='portalTrackerData'/>
				<input type='hidden' name='tracker' id='tracker' value='${params.tracker_id}'/>
                <g:each in='${customdata}' var='custom'>
				<input type='hidden' name='${custom.name}' id='${custom.name}' value='${custom.value}'/>
                </g:each>
			    </g:if>
			    <g:else>
          <div class="fieldcontain">
            <label for="tracker">Tracker</label>
            <g:select name='tracker' from="${trackers}" optionKey="id" optionValue="name"/>
          </div>
                            <f:all except='isTrackerDeleting,tracker,module,path,date_created,uploaded,send_email,sent_email_date,messages,savedparams,file_link,uploadStatus' bean="portalTrackerData"/>
			    </g:else>
                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="create" class="save" value="${message(code: 'default.button.create.label', default: 'Create')}" />
                        </fieldset>
                    </g:uploadForm>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
