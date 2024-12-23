<g:select id='${property}' name='${property}' value='${portalTrackerTransition?.postprocess?.id}' noSelection="${['null':'No postprocess']}" from='${g6portal.PortalPage.findAllByModule(portalTrackerTransition?.tracker?.module)}' optionKey='id'>
</g:select>
