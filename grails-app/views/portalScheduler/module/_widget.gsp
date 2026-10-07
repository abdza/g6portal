<%-- Searchable module picker (g:moduleSelect). Superusers can pick any module or type a new
     name, as the free-text box they had allowed; everyone else chooses from their own developermodules. --%>
<g:if test='${session['enablesuperuser']}'>
    <g:moduleSelect name="${property}" value="${value}" allowNew="true"/>
</g:if>
<g:else>
    <g:moduleSelect name="${property}" value="${value}" from="${session['developermodules'] ?: []}"/>
</g:else>
