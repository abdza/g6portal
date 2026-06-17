<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Upload Summary</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <g:if test="${portalTrackerData?.tracker}">
                            <li><g:link class="list" controller="portalTracker" action="list" params="['module':portalTrackerData.tracker.module,'slug':portalTrackerData.tracker.slug]">Back to ${portalTrackerData.tracker.name} List</g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <h1>Upload Summary</h1>
                    <g:set var="msgs" value="${portalTrackerData?.messages?.split('\n')}"/>
                    <table class="table">
                        <g:each in="${msgs}" var="line">
                            <g:set var="parts" value="${line?.split(':',2)}"/>
                            <g:if test="${parts?.size() == 2}">
                                <tr>
                                    <th style="width:220px;">${parts[0].trim()}</th>
                                    <td>${parts[1].trim()}</td>
                                </tr>
                            </g:if>
                        </g:each>
                    </table>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
