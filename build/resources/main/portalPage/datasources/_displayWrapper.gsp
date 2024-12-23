<a id='dslist'></a>
<ul>
<g:if test='${value}'>
  <g:each in='${value}' var='ds'>
      <li><g:link class="create" action="edit" controller="portalPageData" params="[page:bean.id,id:ds.id]" >${ds.name}</g:link> : ${ds.query}</li>
  </g:each>
</g:if>
<li><g:link class="create" action="create" controller="portalPageData" params="[page:bean.id]" >Add Datasource</g:link></li>
</ul>
