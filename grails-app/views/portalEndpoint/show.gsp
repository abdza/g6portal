<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Endpoint</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index">Endpoint List</g:link></li>
                        <li><g:link class="create" action="create">New Endpoint</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-portalEndpoint" class="col-12 content scaffold-show" role="main">
                    <h1><code>${request.contextPath}/svc/${this.portalEndpoint?.module}/${this.portalEndpoint?.slug}</code></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>

                    <g:if test="${checks?.problems}">
                        <div class="alert alert-warning">
                            <strong>Not ready:</strong>
                            <ul>
                                <g:each in="${checks.problems}" var="p"><li>${p}</li></g:each>
                            </ul>
                        </div>
                    </g:if>
                    <g:elseif test="${this.portalEndpoint?.enabled}">
                        <div class="alert alert-success">This endpoint looks ready to serve.</div>
                    </g:elseif>
                    <g:if test="${checks?.notes}">
                        <ul class="small">
                            <g:each in="${checks.notes}" var="n"><li>${n}</li></g:each>
                        </ul>
                    </g:if>

                    <table class="table">
                        <tbody>
                            <tr><th>Module</th><td>${this.portalEndpoint?.module}</td></tr>
                            <tr><th>Slug</th><td>${this.portalEndpoint?.slug}</td></tr>
                            <tr><th>Name</th><td>${this.portalEndpoint?.name}</td></tr>
                            <tr><th>Description</th><td>${this.portalEndpoint?.description}</td></tr>
                            <tr><th>Handler</th><td>${this.portalEndpoint?.handler_type}</td></tr>
                            <tr><th>Target</th><td><code>${this.portalEndpoint?.target}</code></td></tr>
                            <tr><th>Working directory</th><td><code>${this.portalEndpoint?.working_dir}</code></td></tr>
                            <tr><th>Environment</th><td><code>${this.portalEndpoint?.env_json}</code></td></tr>
                            <tr><th>Header → environment</th><td><code>${this.portalEndpoint?.header_env_json}</code></td></tr>
                            <tr><th>Auth mode</th><td>${this.portalEndpoint?.auth_mode}</td></tr>
                            <tr><th>Token</th><td><g:if test="${this.portalEndpoint?.auth_token}">(set)</g:if><g:else>—</g:else></td></tr>
                            <tr><th>Basic realm</th><td>${this.portalEndpoint?.realm}</td></tr>
                            <tr><th>Allowed roles</th><td>${this.portalEndpoint?.allowed_roles ?: 'any authenticated user'}</td></tr>
                            <tr><th>Timeout</th><td>${this.portalEndpoint?.timeout_seconds} s</td></tr>
                            <tr><th>Max body</th><td>${this.portalEndpoint?.max_body_mb} MB</td></tr>
                            <tr><th>Enabled</th><td>
                                <g:if test="${this.portalEndpoint?.enabled}"><span class="badge bg-success">Yes</span></g:if>
                                <g:else><span class="badge bg-secondary">No</span></g:else>
                            </td></tr>
                        </tbody>
                    </table>

                    <g:form useToken="true" resource="${this.portalEndpoint}" method="DELETE">
                        <fieldset class="buttons">
                            <g:link class="edit" action="edit" resource="${this.portalEndpoint}">Edit</g:link>
                            <input class="delete" type="submit" value="Delete"
                                   onclick="return confirm('Delete /svc/${this.portalEndpoint?.module}/${this.portalEndpoint?.slug}? Anything relying on this URL stops working.');"/>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
