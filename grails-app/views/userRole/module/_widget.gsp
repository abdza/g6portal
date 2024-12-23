<g:if test='${curuser?.isAdmin}'>
    <input type="text" id="${property}" name="${property}" value="${value.encodeAsHTML()}"/>
</g:if>
<g:else>
    <g:select id='${propery}' name='${property}' value="${value.encodeAsHTML()}" from="${session['adminmodules']}"/>
</g:else>
