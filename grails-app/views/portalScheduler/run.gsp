<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalScheduler.label', default: 'PortalScheduler')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div id="list-portalScheduler" class="col-12 content scaffold-list" role="main">
                <h1>Running Scheduler</h1>
                <g:if test="${summary}">
                    <p>${summary}</p>
                </g:if>
                %{-- run is whitelisted for the anonymous cron caller: error details only for a logged-in user --}%
                <g:if test="${session.curuser}">
                    <g:if test="${problems}">
                        <pre style="white-space:pre-wrap;">${problems.join('\n\n')}</pre>
                    </g:if>
                    <p><g:link action="status">Scheduler status</g:link></p>
                </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
