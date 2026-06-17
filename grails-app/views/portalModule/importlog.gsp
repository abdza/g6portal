<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Import Log - ${importlog.module}</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <g:if test="${module}">
                            <li><g:link class="list" action="show" id="${module.id}">Back to Module</g:link></li>
                        </g:if>
                        <li><g:link class="list" action="importlogs" params="[module:importlog.module]">Module Import Logs</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <h1>Import Log: ${importlog.module}</h1>
                    <table class='table'>
                        <tr><th>Date</th><td><g:formatDate date="${importlog.dateCreated}" format="yyyy-MM-dd HH:mm:ss"/></td></tr>
                        <tr><th>Imported By</th><td>${importlog.staffname} (${importlog.staffid})</td></tr>
                        <tr><th>Remarks</th><td>${importlog.remarks}</td></tr>
                    </table>
                    <h3>Changes</h3>
                    <g:if test="${importlog.diff}">
                        <g:render template="diffview" model="[diff:importlog.diff]"/>
                    </g:if>
                    <g:else>
                        <div class="message" role="status">No changes were detected for this import.</div>
                    </g:else>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
