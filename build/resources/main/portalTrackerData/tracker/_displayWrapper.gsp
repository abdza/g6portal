<g:if test="${'Developer' in curuser.modulerole(bean.module)}">
<g:link controller='portalTracker' action='show' params="['id':bean.tracker.id]">${value}</g:link>
</g:if>
<g:else>
${value}
</g:else>
