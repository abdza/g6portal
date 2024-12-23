<!DOCTYPE html>
<html>
    <head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
	<g:if test="${pageInstance.fullpage}">
		<meta name="layout" content="empty" />
	</g:if>
	<g:else>
		<meta name="layout" content="main" />
	</g:else>
        <title>${pageInstance.title}</title>
    </head>
    <body>
    <div id="content" role="main">
	${raw(content)}
    </div>
    </body>
</html>
