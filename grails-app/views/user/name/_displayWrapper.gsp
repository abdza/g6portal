<g:if test="${curuser.switchable() && !session.realuser}">
<g:link controller='user' action='switchuser' id='${bean.userID}'>
${value}
</g:link>
</g:if>
<g:else>
${value}
</g:else>
