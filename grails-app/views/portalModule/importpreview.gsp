<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Import Preview - ${portalModule.name}</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="show" id="${portalModule.id}">Back to Module</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div class="col-12 content" role="main">
                    <h1>Import Preview: ${portalModule.name}</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <p>
                        Review the changes below before importing. Lines starting with
                        <span style="color:#cf222e;">-</span> show the current state that will be replaced,
                        lines starting with <span style="color:#1a7f37;">+</span> show the incoming changes.
                        <br/>Importing files: <strong>${file_on ? 'Yes' : 'No'}</strong>,
                        staff roles: <strong>${staff_on ? 'Yes' : 'No'}</strong>
                    </p>
                    <g:if test="${diff}">
                        <g:render template="diffview" model="[diff:diff]"/>
                    </g:if>
                    <g:else>
                        <div class="message" role="status">No changes detected between the current module and the migration files.</div>
                    </g:else>
                    <g:form useToken="true" action="confirmimport" id="${portalModule.id}" method="POST">
                        <g:if test="${file_on}"><g:hiddenField name="files" value="on"/></g:if>
                        <g:if test="${staff_on}"><g:hiddenField name="staff" value="on"/></g:if>
                        <fieldset class="form">
                            <div class="fieldcontain required">
                                <label for="remarks">Import Remarks<span class="required-indicator">*</span></label>
                                <g:textArea name="remarks" rows="4" cols="80" required="required" style="width:100%;" placeholder="Describe the changes being imported"/>
                            </div>
                        </fieldset>
                        <fieldset class="buttons">
                            <input class="delete" type="submit" value="Confirm Import" onclick="return confirm('Import module ${portalModule.name}? The changes shown above will be applied.');" />
                            <g:link class="list" action="show" id="${portalModule.id}">Cancel</g:link>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
