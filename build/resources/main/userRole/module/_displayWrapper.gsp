<g:set var='moduleid' value='${g6portal.PortalModule.findByName(value)}'/>
<g:if test='${moduleid}'>
<g:link controller='portalModule' action='show' id='${moduleid?.id}'>${value}</g:link>
</g:if>
<g:else>
${value}
</g:else>
