<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="Endpoint" />
        <title>Endpoints</title>
        <style>
            @media
      only screen
    and (max-width: 760px), (min-device-width: 768px)
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "URL"; }
  td:nth-of-type(2):before { content: "Name"; }
  td:nth-of-type(3):before { content: "Handler"; }
  td:nth-of-type(4):before { content: "Target"; }
  td:nth-of-type(5):before { content: "Auth"; }
  td:nth-of-type(6):before { content: "Enabled"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="create" action="create">New Endpoint</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalEndpoint" class="col-12 content scaffold-list" role="main">
                    <h1>Endpoints</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <p class="small">
                        Each row serves a raw HTTP endpoint at
                        <code>${request.contextPath}/svc/{module}/{slug}</code> — used for things a
                        portal page cannot carry, such as Mercurial and Git traffic for the scm
                        module. <strong>A CGI endpoint's target is a program this server runs</strong>,
                        which is why only system administrators can see this screen.
                    </p>
                    <table class="table">
                        <thead>
                            <tr>
                                <th>URL</th>
                                <th>Name</th>
                                <th>Handler</th>
                                <th>Target</th>
                                <th>Auth</th>
                                <th>Enabled</th>
                            </tr>
                        </thead>
                        <tbody>
                        <g:each in="${portalEndpointList}" var="endpoint">
                            <tr>
                                <td><g:link action="show" id="${endpoint.id}"><code>/svc/${endpoint.module}/${endpoint.slug}</code></g:link></td>
                                <td>${endpoint.name}</td>
                                <td>${endpoint.handler_type}</td>
                                <td><code>${endpoint.target}</code></td>
                                <td>${endpoint.auth_mode}</td>
                                <td>
                                    <g:if test="${endpoint.enabled}"><span class="badge bg-success">Yes</span></g:if>
                                    <g:else><span class="badge bg-secondary">No</span></g:else>
                                </td>
                            </tr>
                        </g:each>
                        <g:if test="${!portalEndpointList}">
                            <tr><td colspan="6">No endpoints yet.</td></tr>
                        </g:if>
                        </tbody>
                    </table>
                    <div class="pagination">
                        <g:paginate total="${portalEndpointCount ?: 0}" />
                    </div>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
