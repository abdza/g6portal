<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'userRole.label', default: 'UserRole')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Module"; }
	td:nth-of-type(2):before { content: "Role"; }
	td:nth-of-type(3):before { content: "User"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-userRole" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-userRole" class="col-12 content scaffold-list" role="main">
                    <h1><g:message code="default.list.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
		    <g:form name='searchform' method='get'>
			<fieldset class='form'>
                        <div class='fieldcontain' id='listsearch'>
                        <label>Search:</label><input type='text' name='q' id='q' value='${params.q.encodeAsHTML()}'/><input id='dosearch' name='dosearch' type='submit' value='Search'/>
                        </div>
                        <div class='fieldcontain' id='listmodulesearch'>
                        <label>Module:</label>
                        <g:if test='${session.enablesuperuser}'>
                          <g:select class='searchfilter' name='module' id='module' from='${['All'] + g6portal.PortalModule.findAll()*.name}' value='${params.module?.encodeAsHTML()}'/>
                        </g:if>
                        <g:else>
                          <g:select class='searchfilter' name='module' id='module' from='${['All'] + session.adminmodules}' value='${params.module?.encodeAsHTML()}'/>
                        </g:else>
                        </div>
                        <div class='fieldcontain' id='listrolesearch'>
                        <label>Role:</label><g:select class='searchfilter' name='role' id='role' from='${['All'] + (g6portal.UserRole.findAllByModule(params.module?.encodeAsHTML())*.role).unique()}' value='${params.role?.encodeAsHTML()}'/>
                        </div>
			</fieldset>
		    </g:form>
                    <f:table collection="${userRoleList}" properties="id,module,role,user" />

                    <g:if test="${userRoleCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${userRoleCount ?: 0}" params="${params}" />
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
  <asset:script type='text/javascript' >
  $('.searchfilter').on('change',function() {
    $('#searchform').submit()
  })
  </asset:script>
</html>
