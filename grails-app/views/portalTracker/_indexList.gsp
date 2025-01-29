<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerIndex" action="create" params="[tracker_id:params.id]">Create Index</g:link></li>
    </ul>
</div>
</section>
<f:table except='tracker' collection="${portalTracker.indexes.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }}" />
