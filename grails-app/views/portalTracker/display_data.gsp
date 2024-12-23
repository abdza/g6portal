<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTracker.label', default: 'PortalTracker')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalTracker" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'portalTracker',action:'list',params:['module':params.module,'slug':params.slug])}"><g:message code="default.home.label"/></a></li>
                        <g:if test="${(tracker.transitionallowed('New',curuser))}">
                            <li><g:link class="create" controller='portalTracker' action="create_data" params="['slug':params.slug,'module':params.module]">New ${tracker.name}</g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalTracker" class="col-12 content scaffold-list" role="main">
                    <h1>Display ${tracker.name}</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:trackerDisplay tracker="${tracker}" record_id="${params.id}"/>
                    <g:trackerUpdates tracker="${tracker}" record_id="${params.id}"/>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
