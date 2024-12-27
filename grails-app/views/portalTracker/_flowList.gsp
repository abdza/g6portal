<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerFlow" action="create" params="[tracker_id:params.id]">Create Flow</g:link></li>
    </ul>
</div>
</section>
<f:table collection="${portalTracker.flows.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }}" />
