<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTracker.label', default: 'PortalTracker')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#show-portalTracker" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                        <li><g:link class="list" action="list" params="['module':this.portalTracker.module,'slug':this.portalTracker.slug]">View Tracker</g:link></li>
                        <li><g:link class="create" action="create_defaults" id="${this.portalTracker.id}">Create Defaults</g:link></li>
                        <li><g:link class="create" action="export_data" id="${this.portalTracker.id}">Export Data</g:link></li>
                        <li><g:link class="create" action="fix_status" id="${this.portalTracker.id}">Fix Status</g:link></li>
                        <li><g:link class="create" action="delete_fields" id="${this.portalTracker.id}">Delete Fields</g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-portalTracker" class="col-12 content scaffold-show" role="main">
                    <h1><g:message code="default.show.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
		      <ul class="nav nav-tabs d-flex" id="trackertabs" role="tablist">
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100 active" id="details-tab" data-bs-toggle="tab" data-bs-target="#details" type="button" role="tab" aria-controls="details" aria-selected="true">Details</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="fields-tab" data-bs-toggle="tab" data-bs-target="#fields" type="button" role="tab" aria-controls="fields" aria-selected="false">Fields</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="roles-tab" data-bs-toggle="tab" data-bs-target="#roles" type="button" role="tab" aria-controls="roles" aria-selected="false">Roles</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="status-tab" data-bs-toggle="tab" data-bs-target="#status" type="button" role="tab" aria-controls="status" aria-selected="false">Status</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="transitions-tab" data-bs-toggle="tab" data-bs-target="#transitions" type="button" role="tab" aria-controls="transitions" aria-selected="false">Transitions</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="pages-tab" data-bs-toggle="tab" data-bs-target="#flow" type="button" role="tab" aria-controls="flow" aria-selected="false">Flows</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="pages-tab" data-bs-toggle="tab" data-bs-target="#index" type="button" role="tab" aria-controls="index" aria-selected="false">Index</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="dataupdates-tab" data-bs-toggle="tab" data-bs-target="#dataupdates" type="button" role="tab" aria-controls="dataupdates" aria-selected="false">Data Updates</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="pages-tab" data-bs-toggle="tab" data-bs-target="#pages" type="button" role="tab" aria-controls="pages" aria-selected="false">Pages</button>
			</li>
			<li class="nav-item flex-fill" role="presentation">
			  <button class="nav-link w-100" id="pages-tab" data-bs-toggle="tab" data-bs-target="#tools" type="button" role="tab" aria-controls="tools" aria-selected="false">Tools</button>
			</li>
		      </ul>
		      <div class="tab-content pt-2" id="trackercontents">
			<div class="tab-pane fade show active" id="details" role="tabpanel" aria-labelledby="details-tab">
			    <f:display except='fields,transitions,statuses,roles,datas,emails,flows,indexes' bean="portalTracker" />
			    <g:form useToken="true" resource="${this.portalTracker}" method="DELETE">
				<fieldset class="buttons">
				    <g:link class="edit" action="edit" resource="${this.portalTracker}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
				    <input class="delete" type="submit" value="${message(code: 'default.button.delete.label', default: 'Delete')}" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');" />
				</fieldset>
			    </g:form>
			</div>
			<div class="tab-pane fade" id="fields" role="tabpanel" aria-labelledby="fields-tab">
				<g:render template="fieldsList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="roles" role="tabpanel" aria-labelledby="roles-tab">
				<g:render template="rolesList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="status" role="tabpanel" aria-labelledby="status-tab">
				<g:render template="statusList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="transitions" role="tabpanel" aria-labelledby="transitions-tab">
				<g:render template="transitionsList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="flow" role="tabpanel" aria-labelledby="flow-tab">
				<g:render template="flowList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="index" role="tabpanel" aria-labelledby="index-tab">
				<g:render template="indexList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="dataupdates" role="tabpanel" aria-labelledby="dataupdates-tab">
				<g:render template="datasList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="pages" role="tabpanel" aria-labelledby="pages-tab">
				<g:render template="pagesList" model="[portalTracker:this.portalTracker]"/>
			</div>
			<div class="tab-pane fade" id="tools" role="tabpanel" aria-labelledby="tools-tab">
        <g:each in="${portalTracker.fields.sort{ it.name }*.name}" var='fieldname'>
        ${fieldname}<br/>
        </g:each>
			</div>
		     </div>
	      </div>
            </section>
        </div>
    </div>
    </body>
  <asset:script type='text/javascript' >
      var hash = window.location.hash;
      var someTabTriggerEl = document.querySelector(hash)
      var tab = new bootstrap.Tab(someTabTriggerEl)
      tab.show()
  </asset:script>
</html>
