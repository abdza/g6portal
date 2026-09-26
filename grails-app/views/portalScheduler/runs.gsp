<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>${schedule ? 'Runs: ' + schedule.name : 'Scheduler Ticks'}</title>
        <style>
            .sched-ok { color:#1a7f37; font-weight:600; }
            .sched-bad { color:#c62828; font-weight:600; }
            .sched-warn { color:#b26a00; font-weight:600; }
            .sched-muted { color:#888; }
            .sched-err { white-space:pre-wrap; font-size:12px; max-width:600px; margin:0; }
            table.table td { vertical-align:top; }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="status">Scheduler Status</g:link></li>
                        <g:if test="${schedule}"><li><g:link class="list" action="show" id="${schedule.id}">Schedule</g:link></li></g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <g:if test="${schedule}">
                        <h1>Runs: ${schedule.name}</h1>
                        <p class="sched-muted">${schedule.module}: ${schedule.slugs} &middot; hour ${schedule.hour_of_day} &middot; weekday ${schedule.day_of_week} &middot; date ${schedule.day_of_month}</p>
                    </g:if>
                    <g:else>
                        <h1>Scheduler Ticks</h1>
                        <p class="sched-muted">Every call to /portalScheduler/run, whether or not anything was due.</p>
                    </g:else>

                    <g:if test="${rows}">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>Due</th>
                                    <g:if test="${schedule}"><th>Page</th></g:if>
                                    <th>Status</th>
                                    <th>Started</th>
                                    <th>Took</th>
                                    <th>By</th>
                                    <th>Detail</th>
                                </tr>
                            </thead>
                            <tbody>
                            <g:each in="${rows}" var="r">
                                <tr>
                                    <td><g:formatDate date="${r.due_slot}" format="yyyy-MM-dd HH:00"/></td>
                                    <g:if test="${schedule}"><td>${r.slug}</td></g:if>
                                    <td class="${r.status == 'Success' ? 'sched-ok' : (r.status == 'Running' ? 'sched-warn' : 'sched-bad')}">${r.status}</td>
                                    <td><g:formatDate date="${r.started}" format="yyyy-MM-dd HH:mm:ss"/></td>
                                    <td>${r.duration_ms != null ? (r.duration_ms < 1000 ? r.duration_ms + ' ms' : String.format('%.1f s', r.duration_ms / 1000.0)) : ''}</td>
                                    <td>${r.run_type == 'Missed' ? '' : r.triggered_by}<g:if test="${r.host}"><br/><span class="sched-muted">${r.host}</span></g:if></td>
                                    <td>
                                        <g:if test="${r.error}"><pre class="sched-err">${r.error}</pre></g:if>
                                        <g:if test="${r.result}"><span class="sched-muted">${r.result}</span></g:if>
                                    </td>
                                </tr>
                            </g:each>
                            </tbody>
                        </table>
                        <g:if test="${total > max}">
                            <div class="pagination">
                                <g:paginate total="${total}" max="${max}" offset="${offset}" id="${schedule?.id}"/>
                            </div>
                        </g:if>
                    </g:if>
                    <g:else><p class="sched-muted">Nothing logged yet.</p></g:else>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
