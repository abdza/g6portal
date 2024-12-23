<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalPage.label', default: 'PortalPage')}" />
        <title><g:message code="default.edit.label" args="[entityName]" /></title>
  <asset:stylesheet src="ace_editor/css/theme/github.css" rel="stylesheet"/>
    </head>
    <body>
    <div id="page-content" role="main">
        <div class="container">
            <section class="row">
                <a href="#edit-portalPage" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'portalPage',action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                        <g:if test="${this.portalPage.runable}"><li><g:link class="create" action="runpage" params="['slug':this.portalPage.slug,'module':this.portalPage.module]">Run</g:link></li></g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="edit-portalPage" class="col-12 content scaffold-edit" role="main">
                    <h1><g:message code="default.edit.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <g:hasErrors bean="${this.portalPage}">
                    <ul class="errors" role="alert">
                        <g:eachError bean="${this.portalPage}" var="error">
                        <li <g:if test="${error in org.springframework.validation.FieldError}">data-field-id="${error.field}"</g:if>><g:message error="${error}"/></li>
                        </g:eachError>
                    </ul>
                    </g:hasErrors>
                    <g:form useToken="true" resource="${this.portalPage}" method="PUT">
                        <g:hiddenField name="version" value="${this.portalPage?.version}" />
                        <fieldset class="form">
                            <f:all except="datasources" bean="portalPage"/>
                        </fieldset>
                        <fieldset class="buttons">
                            <input id='savebutton' class="save" type="submit" name="submitpage" value="Save" />
                            <input id='updatebutton' class="save" type="submit" name="submitpage" value="Update" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
  <asset:javascript src="ace_editor/js/ace.js"></asset:javascript>
  <asset:javascript src="ace_editor/js/mode-html.js"></asset:javascript>
  <asset:javascript src="ace_editor/js/mode-groovy.js"></asset:javascript>
    </body>
</html>
