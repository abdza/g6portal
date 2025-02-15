<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalPage.label', default: 'PortalPage')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
<g:if test="${content}">
	${raw(content)}
</g:if>
<g:else>
	<h1>Welcome to IKRAM Serdang Portal</h1>
	<p>Ukhuwwah Teras Kegemilangan</p>
</g:else>
    </div>
    </body>
</html>
