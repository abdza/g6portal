<div id='explorepage'>
<g:fmbreadcrumbs id="${this.portalFileManager.id}"/>
<div id='filedownload'></div>
<table id="filetable">
<thead>
    <tr>
      <th>#</th>
      <g:sortableColumn property="filename" title="File Name" />
      <th>Last Update</th>
      <th>Action</th>
    </tr>
  </thead>
  <tbody>
  <g:each in="${filelist}" status="i" var="curfile">
      <tr class="${(i % 2) == 0 ? 'even' : 'odd'}">
      <td>${i+1}</td>
    <g:if test="${params.fname}">
      <g:if test="${curfile.isFile()}">
        <td id="td_${i+1}" data-url="<g:createLink action="download" id="${this.portalFileManager.id}" params="[fname:params.fname + '/' + curfile.name]"/>" onclick="filedownload(this)">
      </g:if>
      <g:else>
        <td id="td_${i+1}" hx-target="#explorepage" hx-get="<g:createLink action="explorepage" id="${this.portalFileManager.id}" params="[fname:params.fname + '/' + curfile.name]"/>">
      </g:else>
    </g:if>
    <g:else>
      <g:if test="${curfile.isFile()}">
        <td id="td_${i+1}" data-url="<g:createLink action="download" id="${this.portalFileManager.id}" params="[fname:curfile.name]"/>" onclick="filedownload(this)">
      </g:if>
      <g:else>
        <td id="td_${i+1}" hx-target="#explorepage" hx-get="<g:createLink action="explorepage" id="${this.portalFileManager.id}" params="[fname:curfile.name]"/>">
      </g:else>
    </g:else>
        <g:if test="${curfile.isFile()}">
            <span class="bi bi-file-richtext"></span>
        </g:if>
        <g:else>
            <span class="bi bi-folder"></span>    
        </g:else>
        &nbsp; 
        ${curfile.name}
      </td>
      <td>
        <g:formatDate date="${curfile.lastModified()}" format="dd/MM/yyyy HH:mm:ss"/>
      </td>
      <td>
          <button hx-target="#td_${i+1}" hx-swap="outerHTML" hx-get="<g:createLink action="rename" id="${this.portalFileManager.id}" params="[fname:params.fname?params.fname + '/' + curfile.name:curfile.name]"/>" class="btn btn-secondary">Rename</button>
          <button hx-target="#explorepage" hx-get="<g:createLink action="deletefm" id="${this.portalFileManager.id}" params="[fname:params.fname?params.fname + '/' + curfile.name:curfile.name]"/>" class="btn btn-danger" hx-confirm="Confirm delete this file?">Delete</button>
      </td>
    </tr>
  </g:each>
</tbody>
</table>
</div>
