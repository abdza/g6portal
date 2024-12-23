<section class="row">
<div class="nav" role="navigation">
    <ul>
	<li><g:link class="create" controller="portalPage" action="create" params="[tracker_id:params.id]">Create Page</g:link></li>
	<li><g:link class="create" controller="portalTracker" action="create_default_pages" params="[id:params.id]">Create Default Pages</g:link></li>
    </ul>
</div>
<f:table except='content' collection="${g6portal.PortalPage.findAllByModule(portalTracker.module).sort{ a, b -> a.slug.compareToIgnoreCase(b.slug) }}" />
</section>
