<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalPage.label', default: 'PortalPage')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Id"; }
	td:nth-of-type(2):before { content: "Title"; }
	td:nth-of-type(3):before { content: "Slug"; }
	td:nth-of-type(4):before { content: "Module"; }
	td:nth-of-type(5):before { content: "Published"; }
	td:nth-of-type(6):before { content: "Requirelogin"; }
	td:nth-of-type(7):before { content: "Allowedroles"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalPage" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller: 'portalPage',action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalPage" class="col-12 content scaffold-list" role="main">
                    <h1><g:message code="default.list.label" args="[entityName]" /></h1>
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
                    <f:table except="content" collection="${portalPageList}" class="responsive" />

                    <g:if test="${portalPageCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${portalPageCount ?: 0}" params="${params}" />
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
  <asset:script type='text/javascript' > 
  $('#module').on('change',function() {
    $('#searchform').submit()
  })
  </asset:script>
</html>
