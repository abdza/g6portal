<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Scheduler Status</title>
        <style>
            .sched-ok { color:#1a7f37; font-weight:600; }
            .sched-bad { color:#c62828; font-weight:600; }
            .sched-warn { color:#b26a00; font-weight:600; }
            .sched-muted { color:#888; }
            .sched-banner { padding:10px 14px; border-radius:4px; margin:10px 0 16px; }
            .sched-banner.ok { background:#e8f5e9; border:1px solid #a5d6a7; }
            .sched-banner.bad { background:#ffebee; border:1px solid #ef9a9a; }
            .sched-err { white-space:pre-wrap; font-size:12px; max-width:520px; margin:0; }
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
                        <li><g:link class="list" action="index">Scheduler List</g:link></li>
                        <li><g:link class="list" action="runs">Scheduler Ticks</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <h1>Scheduler Status</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>

                    <g:if test="${!lasttick}">
                        <div class="sched-banner bad">
                            <strong>No scheduler run has been logged yet.</strong>
                            Scheduled pages run when something calls <code>/portalScheduler/run</code> (normally an hourly cron).
                        </div>
                    </g:if>
                    <g:elseif test="${tickgap > 90}">
                        <div class="sched-banner bad">
                            <strong>The scheduler has not been called for ${tickgap >= 120 ? ((tickgap / 60) as int) + ' hours' : tickgap + ' minutes'}.</strong>
                            Last call: <g:formatDate date="${lasttick.started}" format="yyyy-MM-dd HH:mm"/>${lasttick.host ? ' on ' + lasttick.host : ''}.
                            Nothing below is running until the cron that calls <code>/portalScheduler/run</code> is back.
                        </div>
                    </g:elseif>
                    <g:else>
                        <div class="sched-banner ok">
                            Scheduler last called <g:formatDate date="${lasttick.started}" format="yyyy-MM-dd HH:mm"/>
                            (${tickgap} min ago${lasttick.host ? ', ' + lasttick.host : ''}) &middot; ${ticks24} calls in the last 24 hours.
                        </div>
                    </g:else>

                    <table class="table">
                        <thead>
                            <tr>
                                <th>Schedule</th>
                                <th>Runs at</th>
                                <th>Last result</th>
                                <th>Last success</th>
                                <th>Last 7 days</th>
                                <th>Next due</th>
                            </tr>
                        </thead>
                        <tbody>
                        <g:each in="${schedules}" var="s">
                            <g:set var="lj" value="${lastjob[s.id]}"/>
                            <g:set var="st" value="${stats[s.id]}"/>
                            <tr>
                                <td>
                                    <g:link action="runs" id="${s.id}">${s.name}</g:link><br/>
                                    <span class="sched-muted">${s.module}: ${s.slugs}</span>
                                    <g:if test="${!s.enabled}"><br/><span class="sched-muted">(disabled)</span></g:if>
                                </td>
                                <td>
                                    hour ${s.hour_of_day ?: '-'}<br/>
                                    <span class="sched-muted">weekday ${s.day_of_week ?: '-'} &middot; date ${s.day_of_month ?: '-'}</span>
                                </td>
                                <td>
                                    <g:if test="${lj}">
                                        %{-- lj is every page of the schedule's latest run; headline is the worst of them --}%
                                        <g:set var="ljworst" value="${g6portal.PortalSchedulerRun.worst(lj*.status)}"/>
                                        <span class="${ljworst == 'Success' ? 'sched-ok' : (ljworst == 'Running' ? 'sched-warn' : 'sched-bad')}">${ljworst}</span>
                                        <span class="sched-muted"><g:formatDate date="${lj[0].due_slot ?: lj[0].started}" format="yyyy-MM-dd HH:mm"/></span>
                                        <g:each in="${lj}" var="r">
                                            <g:if test="${r.error}">
                                                <g:if test="${lj.size() > 1}"><br/><span class="sched-muted">${r.slug}:</span></g:if>
                                                <pre class="sched-err">${r.error.size() > 400 ? r.error.substring(0,400) + ' ...' : r.error}</pre>
                                            </g:if>
                                        </g:each>
                                        <g:if test="${lj.size() == 1 && !lj[0].error && lj[0].result}"><br/><span class="sched-muted">${lj[0].result.size() > 200 ? lj[0].result.substring(0,200) + ' ...' : lj[0].result}</span></g:if>
                                    </g:if>
                                    <g:else><span class="sched-muted">not run since logging began</span></g:else>
                                </td>
                                <td>
                                    <g:if test="${lastok[s.id]}"><g:formatDate date="${lastok[s.id]}" format="yyyy-MM-dd HH:mm"/></g:if>
                                    <g:elseif test="${s.lastrun}"><g:formatDate date="${s.lastrun}" format="yyyy-MM-dd HH:mm"/> <span class="sched-muted">(before logging)</span></g:elseif>
                                    <g:else><span class="sched-muted">never</span></g:else>
                                </td>
                                <td>
                                    <g:if test="${st}">
                                        <g:each in="${st.sort()}" var="kv">
                                            <span class="${kv.key == 'Success' ? 'sched-ok' : 'sched-bad'}">${kv.value} ${kv.key}</span><br/>
                                        </g:each>
                                    </g:if>
                                    <g:else><span class="sched-muted">-</span></g:else>
                                </td>
                                <td>
                                    <g:set var="nd" value="${s.nextDue(now)}"/>
                                    <g:if test="${nd}"><g:formatDate date="${nd}" format="yyyy-MM-dd HH:00"/></g:if>
                                    <g:else><span class="sched-muted">${s.enabled ? 'not within 2 months' : '-'}</span></g:else>
                                </td>
                            </tr>
                        </g:each>
                        </tbody>
                    </table>

                    <h3>Recent problems</h3>
                    <g:if test="${recent}">
                        <table class="table">
                            <thead><tr><th>Due</th><th>Schedule</th><th>Status</th><th>Detail</th></tr></thead>
                            <tbody>
                            <g:each in="${recent}" var="r">
                                <tr>
                                    <td><g:formatDate date="${r.due_slot ?: r.started}" format="yyyy-MM-dd HH:mm"/></td>
                                    <td>${r.name}<br/><span class="sched-muted">${r.module}: ${r.slug}</span></td>
                                    <td class="sched-bad">${r.status}</td>
                                    <td><pre class="sched-err">${r.error}</pre></td>
                                </tr>
                            </g:each>
                            </tbody>
                        </table>
                    </g:if>
                    <g:else><p class="sched-muted">None logged.</p></g:else>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
