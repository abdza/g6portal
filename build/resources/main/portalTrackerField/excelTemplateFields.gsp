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
                    <h1>Excel Template - Select Fields</h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:form action='submitExcelTemplate' id='${tracker.id}'>
                        <table class='table'>
                            <tr><th>Field</th><th>Name</th><th>Label</th><th>Type</th><th>Default</th><th>Options</th></tr>
                            <g:each in="${excelfields}" var="field">
                            <tr>
                                <td>${field.name}</td>
                                <td><g:textField name="${ 'fname_' + field.name }" value="${field.name}"/></td>
                                <td><g:textField name="${ 'flabel_' + field.name }" value="${field.name.replace('_',' ').capitalize()}"/></td>
                                <td><g:select name="${ 'ftype_' + field.name }" from='${fieldtypes}' value='${field.type}'/></td>
                                <td><g:textArea name="${ 'fdefault_' + field.name }" value=""/></td>
                                <td><g:textArea name="${ 'foptions_' + field.name }" value=""/></td>
                            </tr>
                            </g:each>
                        </table>
                        <fieldset class="buttons">
                            <input type="submit" name="process" class="save" value="Process" id="process">
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
