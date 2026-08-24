<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <title>Import Tree Preview</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'portalTree',action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="create" action="importtreeform">Upload another file</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="import-tree-preview" class="col-12 content scaffold-show" role="main">
                    <h1>Import Tree Preview</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <p>
                        Lines starting with <span style="color:#cf222e;">-</span> show what this server
                        holds now, lines starting with <span style="color:#1a7f37;">+</span> show what
                        the file brings. Importing role holders: <strong>${staff_on ? 'Yes' : 'No'}</strong>.
                    </p>
                    <ul>
                        <g:each in="${trees}" var="t">
                            <li>${t.module ?: '(no module)'} / <strong>${t.name}</strong> &mdash;
                                ${t.known ? 'already here, will be updated in place' : 'not on this server yet, will be created'}</li>
                        </g:each>
                    </ul>
                    <g:if test="${diff}">
                        <g:render template="/portalModule/diffview" model="[diff:diff]"/>
                    </g:if>
                    <g:else>
                        <div class="message" role="status">No changes detected between this server's tree and the uploaded file.</div>
                    </g:else>
                    <g:form useToken="true" action="confirmimporttree" method="POST">
                        <g:hiddenField name="parked" value="${parked}"/>
                        <g:if test="${staff_on}"><g:hiddenField name="staff" value="on"/></g:if>
                        <fieldset class="buttons">
                            <g:link class="edit" action="index">Cancel</g:link>
                            <g:submitButton name="confirm" class="save" value="Import Tree"
                                onclick="return confirm('Import this tree?');"/>
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
