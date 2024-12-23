<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalTrackerTransition" action="create" params="[tracker_id:params.id]">Create Transition</g:link></li>
	<li><g:link class="create" controller="portalTracker" action="create_default_pages" params="[id:params.id,category:'form']">Create Default Transition Pages</g:link></li>
    </ul>
</div>
</section>
<f:table properties="id,name,roles,prev_status,next_status,postprocess,displayfields,editfields" 
         collection="${portalTracker.transitions.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }}">
</f:table>
