<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerData" action="create" params="[tracker_id:params.id]">Create Data Update</g:link></li>
	<li><g:link class="delete" controller="portalTrackerData" action="cleardb" params="[tracker_id:params.id]" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure to clear DB?')}');" >Clear DB</g:link></li>
	<li><g:link class="delete" controller="portalTrackerData" action="cleandb" params="[tracker_id:params.id]" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure to clean DB?')}');" >Clean DB</g:link></li>
	<li><g:link class="create" controller="portalTrackerData" action="datadump" params="[id:params.id]" >Data Dump</g:link></li>
	<li><g:link class="create" controller="portalTrackerData" action="syncupload" params="[id:params.id]" >Sync Data</g:link></li>
    </ul>
</div>
</section>
<f:table except='tracker,date_created,uploaded,send_email,sent_email_date,messages,savedparams,file_link' collection="${portalTracker.datas}" />
