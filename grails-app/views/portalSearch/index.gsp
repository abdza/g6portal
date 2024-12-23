<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalSearch.label', default: 'PortalSearch')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Name"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalSearch" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
            </section>
            <section class="row">
                <div id="list-portalSearch" class="col-12 content scaffold-list" role="main">
                    <h1>Portal Search <g:if test='${params.query}'>- ${params.query}</g:if></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                </div>
            </section>
            <g:if test='${users.size()}'>
                <h2>Users</h2>
                <table class='table'>
                  <tr><th>#</th><th>User ID</th><th>Name</th><th>E-mail</th><th>Role</th></tr>
                  <g:each in='${users}' var='user' status='i'>
                  <tr>
                  <td>${i+1}</td>
                  <td>
                  <g:link controller='user' action='show' id='${user.id}'>
                  ${user.userID}
                  </g:link>
                  </td>
                  <td>
                  <g:if test='${curuser?.switchable() && !session.realuser}'>
                  <g:link controller='user' action='switchuser' id='${user.userID}'>
                  ${user.name}
                  </g:link>
                  </g:if>
                  <g:else>
                  ${user.name}
                  </g:else>
                  </td>
                  <td>${user.email}</td>
                  <td>${user.role}</td>
                  </tr>
                  </g:each>
                </table>
                <g:link class='btn btn-secondary' controller='user' action='index' params='[q:params.query]'>
                    More Users
                </g:link>
            </g:if>
            <g:if test='${pages.size() || trackers.size()}'>
                <h2>Pages/Modules</h2>
                <table class='table'>
                  <tr><th>Title</th><th>Module</th></tr>
                  <g:each in='${pages}' var='page'>
                  <tr>
                  <td>
                    <g:if test="${!page.runable}"><g:link controller='portalPage' action="display" params="['slug':page.slug,'module':page.module]">${page.title}</g:link></g:if>
                    <g:else>
                      <g:if test="${page.module in session['developermodules']}">
                        <g:link controller='portalPage' action="edit" id='${page.id}'>${page.title}</g:link>
                      </g:if>
                      <g:else>
                        ${page.title}
                      </g:else>
                    </g:else>
                  </td>
                  <td>${page.module}</td>
                  </tr>
                  </g:each>
                  <g:each in='${trackers}' var='tracker'>
                  <tr>
                  <td>
                    <g:link controller='portalTracker' action="list" params="['slug':tracker.slug,'module':tracker.module]">${tracker.name}</g:link>
                  </td>
                  <td>${tracker.module}</td>
                  </tr>
                  </g:each>
                </table>
            </g:if>
        </div>
    </div>
    </body>
</html>
