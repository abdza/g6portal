<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Stuck Uploads</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index">Data Update List</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <h1>Stuck Uploads</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <p style="color:#666;">
                        These uploads are still marked <strong>"Upload is currently in queue"</strong> — their background
                        processing never finished, usually because the server restarted mid-upload.
                        Restarting removes any partial rows the interrupted run loaded, then re-runs the upload from the saved file.
                    </p>
                    <p style="color:#a60;">
                        <strong>Note:</strong> an upload started only moments ago may still be processing normally —
                        give recent uploads a few minutes before restarting them.
                    </p>
                    <g:if test="${stuckUploads}">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Module</th>
                                    <th>Tracker</th>
                                    <th>File</th>
                                    <th>Uploader</th>
                                    <th>State</th>
                                    <th></th>
                                </tr>
                            </thead>
                            <tbody>
                                <g:each in="${stuckUploads}" var="upd">
                                    <tr>
                                        <td><g:link action="uploadsummary" id="${upd.id}">${upd.id}</g:link></td>
                                        <td>${upd.module}</td>
                                        <td>${upd.tracker?.slug}</td>
                                        <td>${upd.path ? upd.path.tokenize('/')[-1] : '-'}</td>
                                        <td>${upd.uploader?.staffID ?: '-'}</td>
                                        <td>
                                            <g:if test="${upd.uploadStatus == -1}">Interrupted mid-processing</g:if>
                                            <g:else>Never started</g:else>
                                        </td>
                                        <td>
                                            <g:link action="requeueupload" id="${upd.id}"
                                                onclick="return confirm('Restart upload ${upd.id}? Partial rows from the interrupted run will be removed first.');">Restart</g:link>
                                        </td>
                                    </tr>
                                </g:each>
                            </tbody>
                        </table>
                        <g:link action="requeueall" class="btn btn-primary"
                            onclick="return confirm('Restart ALL ${stuckUploads.size()} stuck upload(s)? Partial rows from the interrupted runs will be removed first.');">Restart All</g:link>
                    </g:if>
                    <g:else>
                        <p><strong>No stuck uploads — everything has been processed.</strong></p>
                    </g:else>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
