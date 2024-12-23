<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="Import Module" />
        <title><g:message code="default.create.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#create-importmodule" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'portalModule',action:'index')}"><g:message code="default.home.label"/></a></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="create-importmodule" class="col-12 content scaffold-create" role="main">
                    <h1>Import Module</h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:uploadForm method="POST" action="importform">
                        <fieldset class="form">
                          <div class="fieldcontain">
                            <label for="name">Module File</label>
                            <input type="file" name="fileupload" value="" id="fileupload">
                          </div>
                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="create" class="save" value="${message(code: 'default.button.create.label', default: 'Create')}" />
                        </fieldset>
                    </g:uploadForm>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
