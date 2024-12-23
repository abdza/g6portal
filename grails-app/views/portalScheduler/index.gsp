<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalScheduler.label', default: 'PortalScheduler')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Id"; }
  td:nth-of-type(2):before { content: "Name"; }
  td:nth-of-type(3):before { content: "Module"; }
  td:nth-of-type(4):before { content: "Slugs"; }
  td:nth-of-type(5):before { content: "Hour"; }
  td:nth-of-type(6):before { content: "Day Of Week"; }
  td:nth-of-type(7):before { content: "Day Of Month"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalScheduler" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <g:if test="${session['developermodules']}">
                          <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                          <g:if test='${curuser?.isAdmin}'>
                              <li><g:link class="create" action="run">Run</g:link></li>
                          </g:if>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalScheduler" class="col-12 content scaffold-list" role="main">
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
                    <f:table collection="${portalSchedulerList}" except="params" />

                    <g:if test="${portalSchedulerCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${portalSchedulerCount ?: 0}" params="${params}"/>
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
