<g:if test="${value}">
    <g:link controller='portalTreeNode' action='show' id='${value.id}'>${value.name}</g:link>
</g:if>
<g:else>
    <g:link controller='portalTree' action='create_root' id='${bean.id}'>Create root node</g:link>
</g:else>
