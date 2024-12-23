<g:if test='${bean.runable}'>
<g:link controller='portalPage' action='runpage' params="['module':bean.module,'slug':bean.slug]">${value}</g:link>
</g:if>
<g:else>
<g:link controller='portalPage' action='display' params="['module':bean.module,'slug':bean.slug]">${value}</g:link>
</g:else>
