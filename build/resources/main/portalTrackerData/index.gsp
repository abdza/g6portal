<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerData.label', default: 'PortalTrackerData')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalTrackerData" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="create" action="create">New Data Update</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalTrackerData" class="col-12 content scaffold-list" role="main">
                    <h1>Data Update</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
		    <g:form name='searchform' method='get'>
			<fieldset class='form'>
                        <div class='fieldcontain' id='listsearch'>
                        <label>Module:</label>
                        <g:if test='${session.enablesuperuser}'>
                          <g:select name='module' id='module' from='${['All'] + g6portal.PortalModule.findAll()*.name}' value='${params.module?.encodeAsHTML()}'/>
                        </g:if>
                        <g:else>
                          <g:select name='module' id='module' from='${['All'] + session.adminmodules}' value='${params.module?.encodeAsHTML()}'/>
                        </g:else>
                        </div>
                        <div class='fieldcontain' id='listsearch'>
                        <label>Search:</label><input type='text' name='q' id='q' value='${params.q.encodeAsHTML()}'/><input id='dosearch' name='dosearch' type='submit' value='Search'/>
                        </div>
			</fieldset>
		    </g:form>
                    <f:table except='date_created,uploaded,send_email,sent_email_date,messages,savedparams,file_link' collection="${portalTrackerDataList}" />

                    <g:if test="${portalTrackerDataCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${portalTrackerDataCount ?: 0}" />
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
  <asset:script type='text/javascript' > 
  $('#module').on('change',function() {
    $('#searchform').submit()
  })
  </asset:script>
    </body>
</html>
