<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerField.label', default: 'PortalTrackerField')}" />
        <title><g:message code="default.create.label" args="[entityName]" /></title>
        <asset:script>
        $('#addfield').on('click',function(){
             $("#addbuttonrow").before('<tr><td><g:textField name='field_name'/></td><td><g:textField name='field_label'/></td><td><select id="field_type" name="field_type"><g:each in="${trackerField.constrainedProperties.field_type.inList}" var="opt"><option value="${opt}" <g:if test="${opt=='Text'}">selected</g:if> >${opt}</option></g:each></select></td></tr>');
                return false;
                });
        </asset:script>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#create-portalTrackerField" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="create-portalTrackerField" class="col-12 content scaffold-create" role="main">
                    <h1>Create Multiple Fields</h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.portalTrackerField}">
                    <ul class="errors" role="alert">
                        <g:eachError bean="${this.portalTrackerField}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                        </g:eachError>
                    </ul>
                    </g:hasErrors>
                    <g:form method="POST" action='multifields' id='${tracker.id}'>
                        <fieldset class="form">
                            <table>
                                <tr><th>Name</th><th>Label</th><th>Field Type</th></tr>

                                <tr>
                                <td>
                                    <input type='text' name='field_name' />
                                </td>
                                <td>
                                    <input type='text' name='field_label' />
                                </td>
                                <td>
                                    <g:select name='field_type' from='${trackerField.constrainedProperties.field_type.inList}' /> 
                                </td>
                                </tr>
                                <tr id='addbuttonrow'>
                                <td colspan='3'>
                                <button id='addfield'>Add</button>
                                </td>
                                </tr>
                            </table>
                        </fieldset>
                        <fieldset class="buttons">
                            <g:submitButton name="create" class="save" value="${message(code: 'default.button.create.label', default: 'Create')}" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
