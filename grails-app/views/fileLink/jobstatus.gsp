<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>File Size Job Status</title>
        <%-- Only poll while something is actually running, so a settled page stays put. --%>
        <g:if test="${running}">
            <meta http-equiv="refresh" content="5" />
        </g:if>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'portalPage',action:'home')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index">File List</g:link></li>
                        <li><g:link class="create" action="jobstatus">Refresh</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content scaffold-list" role="main">
                    <h1>File Size Job Status</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>

                    <g:if test="${running}">
                        <div class="message" role="status">
                            A job is currently running. This page refreshes every 5 seconds.
                        </div>
                    </g:if>

                    <h3>Overall Coverage</h3>
                    <table class='table'>
                        <tr><th>Total File Records</th><th>With Size Recorded</th><th>Still Missing</th><th>Complete</th></tr>
                        <tr>
                            <td>${progress?.total ?: 0}</td>
                            <td>${progress?.updated ?: 0}</td>
                            <td>${progress?.missing ?: 0}</td>
                            <td>${progress?.percentComplete ?: 0}%</td>
                        </tr>
                    </table>
                    <br/>

                    <h3>Recent Jobs</h3>
                    <g:if test="${!jobs}">
                        <div class="message" role="status">
                            No file size jobs have been run yet.
                            Start one from any module's Files section, or from the file list.
                        </div>
                    </g:if>
                    <g:else>
                        <table class='table'>
                        <tr>
                            <th>#</th><th>Scope</th><th>Status</th><th>Started</th><th>Ended</th>
                            <th>Duration</th><th>Sized</th><th>Could Not Size</th>
                            <th>Missing at Start</th><th>Error</th><th>Action</th>
                        </tr>
                        <g:each in='${jobs}' var='job' status='i'>
                            <tr>
                                <td>${job.id}</td>
                                <td>${job.moduleName ?: 'All modules'}</td>
                                <td>
                                    <g:if test="${job.status == 'RUNNING'}"><strong>${job.status}</strong></g:if>
                                    <g:elseif test="${job.status == 'FAILED'}"><span style="color:#b00;"><strong>${job.status}</strong></span></g:elseif>
                                    <g:else>${job.status}</g:else>
                                    <g:if test="${job.cancelRequested && job.status == 'RUNNING'}">
                                        <br/><small>cancelling&hellip;</small>
                                    </g:if>
                                </td>
                                <td><g:formatDate date="${job.startTime}" format="yyyy-MM-dd HH:mm:ss"/></td>
                                <td><g:formatDate date="${job.endTime}" format="yyyy-MM-dd HH:mm:ss"/></td>
                                <td>
                                    <%-- endTime is null while running, so measure against now instead --%>
                                    <g:set var="endedAt" value="${job.endTime ?: new Date()}"/>
                                    ${(long)((endedAt.time - job.startTime.time) / 1000)}s
                                </td>
                                <td>${job.processedRecords ?: 0}</td>
                                <td>${job.skippedRecords ?: 0}</td>
                                <td>${job.totalRecords ?: 0}</td>
                                <td>${job.errorMessage}</td>
                                <td>
                                    <g:if test="${job.status == 'RUNNING' && !job.cancelRequested}">
                                        <g:link action="cancelSizeJob" id="${job.id}"
                                            onclick="return confirm('Cancel job #${job.id}? Work already done is kept.');">Cancel</g:link>
                                    </g:if>
                                    <g:elseif test="${job.status == 'RUNNING' && job.cancelRequested}">
                                        <g:link action="cancelSizeJob" id="${job.id}"
                                            onclick="return confirm('Force-stop job #${job.id}? Use this only if the worker is gone, e.g. after a restart.');">Force stop</g:link>
                                    </g:elseif>
                                </td>
                            </tr>
                        </g:each>
                        </table>
                    </g:else>

                    <p class="text-muted">
                        <em>Sized</em> counts records this run successfully measured on disk.
                        <em>Could Not Size</em> counts records whose file is no longer present
                        or which have no path recorded &mdash; rerunning will not change those.
                        Batch size and the pause between batches are tunable via the
                        <code>filesize_job_batch_size</code> and
                        <code>filesize_job_batch_delay_ms</code> portal settings.
                    </p>

                </div>
            </section>
        </div>
    </div>
    </body>
</html>
