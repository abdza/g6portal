<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>New Endpoint</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index">Endpoint List</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="create-portalEndpoint" class="col-12 content scaffold-create" role="main">
                    <h1>New Endpoint</h1>
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
                    <g:form useToken="true" action="save" method="POST">
                        <g:render template="form"/>
                        <fieldset class="buttons">
                            <g:submitButton name="create" class="save" value="Create"/>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
