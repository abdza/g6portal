<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerStatus" action="create" params="[tracker_id:params.id]">Create Status</g:link></li>
	<li><g:link class="create" controller="portalTracker" action="create_default_pages" params="[id:params.id,category:'display']">Create Default Display Page</g:link></li>
    </ul>
</div>
</section>
<f:table except='displayfields,editroles,editfields,updateable,attachable,emailonupdate,updateallowedroles,suppressupdatebutton' collection="${portalTracker.statuses.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }}" />
