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
                    <g:set var="stillProcessing" value="${!portalTrackerData?.uploaded && !(portalTrackerData?.messages?.startsWith('Upload error')) && !(portalTrackerData?.messages?.startsWith('Upload failed'))}"/>
                    <g:if test="${stillProcessing}">
                        <div style="text-align:center;padding:40px 20px;">
                            <div style="display:inline-block;width:40px;height:40px;border:4px solid #ddd;border-top-color:#0078d4;border-radius:50%;animation:uploadspin .8s linear infinite;"></div>
                            <style>@keyframes uploadspin{to{transform:rotate(360deg);}}</style>
                            <h3 style="margin-top:16px;">Processing upload&hellip;</h3>
                            <p style="color:#666;">Your file is being processed in the background. This page refreshes automatically &mdash; large files may take a few minutes.</p>
                        </div>
                        <script>setTimeout(function(){ window.location.reload(); }, 3000);</script>
                    </g:if>
                    <g:else>
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
                    </g:else>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
