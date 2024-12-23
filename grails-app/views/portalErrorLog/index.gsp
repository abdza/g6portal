<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalErrorLog.label', default: 'PortalErrorLog')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Id"; }
  td:nth-of-type(2):before { content: "Date"; }
  td:nth-of-type(3):before { content: "Uri"; }
  td:nth-of-type(4):before { content: "User"; }
  td:nth-of-type(5):before { content: "Module"; }
  td:nth-of-type(6):before { content: "Slug"; }
  td:nth-of-type(7):before { content: "Errormsg"; }
  td:nth-of-type(8):before { content: "Params"; }
  td:nth-of-type(9):before { content: "Controller"; }
  td:nth-of-type(10):before { content: "Action"; }
  td:nth-of-type(11):before { content: "Resolved"; }
  td:nth-of-type(12):before { content: "Date Resolved"; }
  td:nth-of-type(13):before { content: "Ip Addr"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalErrorLog" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="delete" action="clear_log">Clear Log</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalErrorLog" class="col-12 content scaffold-list" role="main">
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
                    <f:table collection="${portalErrorLogList}" except="params" />

                    <g:if test="${portalErrorLogCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${portalErrorLogCount ?: 0}" params="${params}"/>
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
