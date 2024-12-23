<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalModule.label', default: 'PortalModule')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Name"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalModule" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <g:if test='${curuser?.isAdmin}'>
                            <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                            <li><g:link class="create" action="updatelist">Update</g:link></li>
                        </g:if>
                        <g:if test="${session['developermodules']}">
                            <li><g:link class="create" action="importform">Import Zip</g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalModule" class="col-12 content scaffold-list" role="main">
                    <h1><g:message code="default.list.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
		    <g:form method='get'>
			<fieldset class='form'>
                        <div class='fieldcontain' id='listsearch'>
                        <label>Search:</label><input type='text' name='q' id='q' value='${params.q.encodeAsHTML()}'/><input id='dosearch' name='dosearch' type='submit' value='Search'/>
                        </div>
			</fieldset>
		    </g:form>
                    <f:table collection="${portalModuleList}" />

                    <g:if test="${portalModuleCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${portalModuleCount ?: 0}" params="${params}" />
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
