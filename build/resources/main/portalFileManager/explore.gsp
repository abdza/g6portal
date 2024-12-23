<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalFileManager.label', default: 'PortalFileManager')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
        <style>
            #uploadform {
                width: 100%;
                height: 50px;
                border: 1px dashed green;
            }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#show-portalFileManager" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(action:'index')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <g:if test="${session['developermodules']}">
                          <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-portalFileManager" class="col-12 content scaffold-show" role="main">
                    <h1>Explore ${this.portalFileManager.name}</h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>

                    <!-- Button trigger modal -->
                    <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#createfolder">
                      Create Folder
                    </button>

                    <label for='unzip'>Unzip zip files:</label>
                    <input type='checkbox' name='unzip' id='unzip'/>

                    <!-- Modal -->
                    <div class="modal fade" id="createfolder" tabindex="-1" aria-labelledby="createfolder" aria-hidden="true">
                      <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content">
                          <form>
                          <div class="modal-header">
                            <h1 class="modal-title fs-5" id="exampleModalLabel">Create Folder</h1>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                          </div>
                          <div class="modal-body">
                                <label>New Folder Name:</label>
                                <input type='text' name='foldername' id='foldername'/>
                                <input type='hidden' name='mdfname' id='mdfname'/>
                          </div>
                          <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                            <button type="button" class="btn btn-primary" data-bs-dismiss="modal" hx-target='#explorepage' hx-post="<g:createLink action="createfolder" id="${this.portalFileManager.id}" params="[fname:params.fname]"/>">Create Folder</button>
                          </div>
                          </form>
                        </div>
                      </div>
                    </div>


                    <div id='uploadform' hx-target='#explorepage' hx-trigger="drop" hx-encoding='multipart/form-data' hx-post="<g:createLink action="upload" id="${this.portalFileManager.id}" params="[fname:params.fname]"/>">
                    </div>
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
                              <td id="td_${i+1}" hx-target="#explorepage" hx-get="<g:createLink action="explorepage" id="${this.portalFileManager.id}" params="[fname:params.fname + curfile.name]"/>">
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
                            <button hx-target="#td_${i+1}" hx-swap="outerHTML" hx-get="<g:createLink action="rename" id="${this.portalFileManager.id}" params="[fname:params.fname?params.fname + curfile.name:curfile.name]"/>" class="btn btn-secondary">Rename</button>
                            <button hx-target="#explorepage" hx-get="<g:createLink action="deletefm" id="${this.portalFileManager.id}" params="[fname:params.fname?params.fname + '/' + curfile.name:curfile.name]"/>" class="btn btn-danger" hx-confirm="Confirm delete this file?">Delete</button>
                          </td>
                        </tr>
                      </g:each>
                    </tbody>
                    </table>
                    </div>
                </div>
            </section>
        </div>
    </div>
    </body>
    <asset:script>

    function filedownload(target) {
        var link = document.createElement("a");
        link.setAttribute('download', '');
        link.href = $(target).data("url");
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    var dropZone = document.getElementById("uploadform");

    dropZone.addEventListener("dragover", function(event) {
      event.preventDefault();
      event.dataTransfer.dropEffect = "copy";
    });

    dropZone.addEventListener("drop", function(event) {
      event.preventDefault();
      window.files = event.dataTransfer.files;
    });

    htmx.on("htmx:configRequest", function(event) {
      if(event.detail.elt.id==="uploadform") {
        var formData = new FormData();
        var xhr = new XMLHttpRequest();
        if(!window.curpath) {
            window.curpath = event.detail.path;
        }
        xhr.open('POST', window.curpath, true);
        for (var i = 0; i < window.files.length; i++) {
          formData.append("file_" + i, files[i]);
        }
        if($('#unzip').prop('checked')) {
            formData.append("unzip",1);
        }
        var sent = xhr.send(formData);
        event.detail.path = window.curpath;
      }
    });

    htmx.on("htmx:afterSettle", function(event) {
        window.curpath = event.detail.pathInfo.responsePath.replace("explorepage","upload");
        const urlParams = new URLSearchParams(window.curpath);
        window.fname = urlParams.values().next().value;
        $('#mdfname').val(window.fname);
        $('#foldername').val('');
    });
    </asset:script>
</html>
