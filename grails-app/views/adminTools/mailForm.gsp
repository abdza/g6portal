<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'fileLink.label', default: 'MailForm')}" />
        <title><g:message code="default.create.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div id="create-fileLink" class="col-12 content scaffold-create" role="main">
                    <h1>Admin Tools MailForm</h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:form action="mailForm" method="POST">
                        <fieldset class="form">
                          <div class="fieldcontain">
                            <label for="to">To</label>
                            <input type="text" name="to" value="" id="to">
                          </div>
                        </fieldset>
                        <fieldset class="form">
                          <div class="fieldcontain">
                            <label for="subject">Subject</label>
                            <input type="text" name="subject" value="" id="subject">
                          </div>
                        </fieldset>
                        <fieldset class="form">
                          <div class="fieldcontain">
                            <label for="content">Content</label>
                            <textarea name='content' id='content'>
                            </textarea>
                          </div>
                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="send" class="save" value="Send" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
