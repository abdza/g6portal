<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerTransition.label', default: 'PortalTrackerTransition')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#show-portalTrackerTransition" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" controller='portalTracker' action="show" id='${this.portalTrackerTransition.tracker.id}' fragment='transitions-tab'><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <li><g:link controller='portalTrackerEmail' action='create' class='create' params="[transition_id:this.portalTrackerTransition.id]">Create Email</g:link></li>
                        <li><g:link class="create" controller="portalTracker" action="create_default_pages" params="[id:params.id,category:'form',transition_id:params.id]">Create Default Transition Page</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-portalTrackerTransition" class="col-12 content scaffold-show" role="main">
                    <h1><g:message code="default.show.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <f:display bean="portalTrackerTransition" />
                    <g:form resource="${this.portalTrackerTransition}" method="DELETE">
                        <fieldset class="buttons">
                            <g:link class="edit" action="edit" resource="${this.portalTrackerTransition}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
                            <input class="delete" type="submit" value="${message(code: 'default.button.delete.label', default: 'Delete')}" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
