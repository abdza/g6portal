<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="Import Tree" />
        <title>Import Tree</title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'portalTree',action:'index')}"><g:message code="default.home.label"/></a></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="import-tree" class="col-12 content scaffold-create" role="main">
                    <h1>Import Tree</h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <p>
                        Upload a tree file exported from another server (or one tree taken out of a
                        module's <code>treelist.json</code>). The tree is matched by module and name:
                        an existing one is updated in place, a new one is created. Nothing is deleted -
                        nodes this server has that the file does not are left alone.
                    </p>
                    <g:uploadForm method="POST" action="importtree">
                        <fieldset class="form">
                          <div class="fieldcontain">
                            <label for="fileupload">Tree File</label>
                            <input type="file" name="fileupload" id="fileupload" accept=".json"/>
                          </div>
                          <div class="fieldcontain">
                            <label for="staff">Include role holders</label>
                            <input type="checkbox" name="staff" id="staff"/>
                            <span class="property-value">Staff assigned to each node, matched by staff id. Leave unticked to move the structure only.</span>
                          </div>
                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="upload" class="save" value="Review Changes" />
                        </fieldset>
                    </g:uploadForm>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
