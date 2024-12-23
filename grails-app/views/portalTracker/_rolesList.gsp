<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerRole" action="create" params="[tracker_id:params.id]">Create Role</g:link></li>
    </ul>
</div>
</section>
<f:table except='rolerule' collection="${portalTracker.roles.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }}" />
