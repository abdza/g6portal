<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Import Logs<g:if test="${module}"> - ${module.name}</g:if></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index">Module List</g:link></li>
                        <g:if test="${module}">
                            <li><g:link class="list" action="show" id="${module.id}">Back to Module</g:link></li>
                            <li><g:link class="list" action="importlogs">All Import Logs</g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content scaffold-list" role="main">
                    <h1>Import Logs<g:if test="${module}">: ${module.name}</g:if></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <table class='table'>
                    <tr><th>#</th><th>Date</th><th>Module</th><th>Imported By</th><th>Remarks</th><th>Action</th></tr>
                    <g:each in='${importlogs}' var='importlog' status='i'>
                        <tr>
                        <td>${(params.int('offset') ?: 0) + i + 1}</td>
                        <td><g:formatDate date="${importlog.dateCreated}" format="yyyy-MM-dd HH:mm:ss"/></td>
                        <td><g:link action="importlogs" params="[module:importlog.module]">${importlog.module}</g:link></td>
                        <td>${importlog.staffname} (${importlog.staffid})</td>
                        <td>${importlog.remarks}</td>
                        <td><g:link action='importlog' id='${importlog.id}'>View Changes</g:link></td>
                        </tr>
                    </g:each>
                    </table>
                    <g:if test="${!importlogs}">
                        <div class="message" role="status">No import logs found<g:if test="${module}"> for module ${module.name}</g:if>.</div>
                    </g:if>
                    <g:if test="${importlogCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${importlogCount ?: 0}" action="importlogs" params="${[module:params.module]}" />
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
