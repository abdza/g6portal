<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerData" action="create" params="[tracker_id:params.id]">Create Data Update</g:link></li>
	<li><g:link class="delete" controller="portalTrackerData" action="cleardb" params="[tracker_id:params.id]" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure to clear DB?')}');" >Clear DB</g:link></li>
	<li><g:link class="delete" controller="portalTrackerData" action="cleandb" params="[tracker_id:params.id]" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure to clean DB?')}');" >Clean DB</g:link></li>
	<%-- Two dumps, because they are for two different readers.
	     forimport=1 is the one this tab exists for: it writes field names as headers,
	     the stored id behind every User/Branch/BelongsTo/File rather than the name, and
	     DateTime with seconds - the three things the plain dump gets wrong for a machine.
	     Without it an import dies on every row with "Error converting data type nvarchar
	     to numeric", or worse, succeeds with every DateTime silently null.
	     It also drops field_query columns: those are computed per row, so they dominate
	     the runtime and should not be frozen into an imported record anyway. --%>
	<li><g:link class="create" controller="portalTrackerData" action="datadump" params="[id:params.id, forimport:'1']" >Data Dump (for re-import)</g:link></li>
	<li><g:link class="create" controller="portalTrackerData" action="datadump" params="[id:params.id]" >Data Dump (readable)</g:link></li>
	<li><g:link class="create" controller="portalTrackerData" action="syncupload" params="[id:params.id]" >Sync Data</g:link></li>
    </ul>
</div>
</section>
<section class="row">
    <div class="col-12">
        <p class="text-muted" style="font-size:12px;margin:4px 0 10px">
            <strong>For re-import</strong> is the file to feed back through Create Data Update &mdash;
            headers are field names, references carry ids, computed columns are left out.
            <strong>Readable</strong> is the one to send to a person: names instead of ids,
            and every computed column included, which on a large tracker makes it much slower.
            Both honour any filter you pass in the URL, e.g.
            <code>&amp;record_status=Routed</code>.
        </p>
    </div>
</section>
<f:table except='tracker,date_created,uploaded,send_email,sent_email_date,messages,savedparams,file_link' collection="${portalTracker.datas}" />
