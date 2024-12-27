<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerField" action="create" params="[tracker_id:params.id]">Create Field</g:link></li>
	<li><g:link class="create" controller="portalTrackerField" action="excelTemplate" id="${params.id}">Excel Template</g:link></li>
	<li><g:link class="create" controller="portalTrackerField" action="multifields" id="${params.id}">Multi Fields</g:link></li>
	<li><g:link class="create" controller="portalTrackerField" action="fromTable" id="${params.id}" onclick="return confirm('Import from table?');">From Table</g:link></li>
	<li><g:link class="create" controller="portalTrackerField" action="updateDb" id="${params.id}">Update DB</g:link></li>
	<li><g:link class="create" controller="portalTracker" action="fix_file_links" id="${params.id}">Fix FileLinks</g:link></li>
    </ul>
</div>
</section>
<f:table except='tracker,hyperscript,url_value,is_encrypted,role_query,encode_exception,suppress_follow_link,error_checks,field_format,hide_heading,classes,params_override,field_display,field_query' collection="${portalTracker.fields.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }}" />
