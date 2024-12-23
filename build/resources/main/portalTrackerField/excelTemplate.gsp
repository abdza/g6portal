<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerField.label', default: 'PortalTrackerField')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-portalTrackerField" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-portalTrackerField" class="col-12 content scaffold-list" role="main">
                    <h1>Excel Template - Upload File</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:form action='excelTemplateFields' id='${tracker.id}' enctype="multipart/form-data">
                        <fieldset class="form">
                            <div class="fieldcontain">
                                <label for="name">Template File</label>
                                <input type="file" name="fileupload" value="" id="fileupload">
                            </div>
                        </fieldset>
                        <fieldset class="form">
                            <div class="fieldcontain">
                                <label for="header_start">Header Start</label>
                                <input type="number" name="header_start" value="1" id="header_start">
                            </div>
                        </fieldset>
                        <fieldset class="form">
                            <div class="fieldcontain">
                                <label for="header_end">Header End</label>
                                <input type="number" name="header_end" value="1" id="header_end">
                            </div>
                        </fieldset>
                        <fieldset class="buttons">
                            <input type="submit" name="upload" class="save" value="Upload" id="upload">
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>