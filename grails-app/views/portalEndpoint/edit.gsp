<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Edit Endpoint</title>
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
                <div id="edit-portalEndpoint" class="col-12 content scaffold-edit" role="main">
                    <h1>Edit <code>/svc/${this.portalEndpoint?.module}/${this.portalEndpoint?.slug}</code></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.portalEndpoint}">
                        <ul class="errors" role="alert">
                            <g:eachError bean="${this.portalEndpoint}" var="error">
                            <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                            </g:eachError>
                        </ul>
                    </g:hasErrors>
                    <g:form useToken="true" resource="${this.portalEndpoint}" method="PUT">
                        <g:hiddenField name="id" value="${this.portalEndpoint?.id}"/>
                        <g:hiddenField name="version" value="${this.portalEndpoint?.version}"/>
                        <g:render template="form" model="[portalEndpoint: this.portalEndpoint]"/>
                        <fieldset class="buttons">
                            <input class="save" type="submit" value="Update"/>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
