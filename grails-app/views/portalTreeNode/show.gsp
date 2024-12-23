<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTreeNode.label', default: 'PortalTreeNode')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
        <asset:stylesheet src="jstree/themes/default/style.min.css" rel="stylesheet"/>
        <style>
#treebody {
            max-height: 400px;
            overflow-y: auto;
        }
        </style>
    </head>
<body>
<div id="userformdialog" class="modal" tabindex="-1">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title">Node User</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
      </div>
    <g:form name='user_form' controller='portalTreeNode' action='user_form' resource="${this.portalTreeNode}" method="POST">
        <input type='hidden' id='hidenodeid' name='hidenodeid' />
        <input type='hidden' id='usernodeid' name='usernodeid' />
        <input type='hidden' id='useruserid' name='useruserid' />
        <input type='hidden' id='nodeaction' name='nodeaction' value='new' />
      <div class="modal-body">
            <fieldset class="form">
                <div class="fieldcontain required">
                <label for="user">User
                <span class="required-indicator">*</span>
                </label>
                <select id='user' name='user' class='user-selector' style='width: 60%;'>
                </select>
                </div>
            </fieldset>
            <fieldset class="form">
                <div class="fieldcontain required">
                <label for="role">Role
                <span class="required-indicator">*</span>
                </label>
                <input type="text" name="role" value="" required="" id="role">
                </div>
            </fieldset>
            <fieldset class="form">
                <div class="fieldcontain">
                <label for="retain">Retain Current Role
                </label>
                <input type="checkbox" name="retain" value="" id="retain">
                </div>
            </fieldset>
      </div>
      <div class="modal-footer">
        <button type="submit" class="btn btn-warning" id='delete_user_button'>Delete User</button>
        <button type="submit" class="btn btn-primary" id='add_user_button'>Add User</button>
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
      </div>
    </g:form>
    </div>
  </div>
</div>

    <div id="content" role="main">
        <div class="container">
            <div class='card'>
                <div class='card-header'>
                    <g:form name='deleteform' resource="${this.portalTreeNode}" method="POST">
                    <g:link class="create createnode btn btn-outline-secondary" action="create" params="[parentid:this.portalTreeNode.id]">
                    <i class="bi bi-file-plus"></i>
                    New Node
                    </g:link>
                    <g:link class="edit editnode btn btn-outline-secondary" action="edit" resource="${this.portalTreeNode}">
                    <i class="bi bi-file-check"></i>
                    Edit
                    </g:link>
                    <button type="button" class="delete deletenode btn btn-outline-secondary" action="delete" resource="${this.portalTreeNode}" onclick='if(confirm("Confirm delete?")){ $("#deleteform").submit() }'>
                    <i class="bi bi-file-excel"></i>
                    Delete
                    </button>
                    <g:link class="create editdomain btn btn-outline-secondary" action="edit_domain" resource="${this.portalTreeNode}">
                    <i class="bi bi-bookmark-check"></i>
                    Edit Domain
                    </g:link>
                    <button type="button" class="adduser btn btn-outline-secondary" data-bs-toggle="modal" data-bs-target="#userformdialog" onclick="inituserform();return true;">
                    <i class="bi bi-person-plus"></i>
                    Add User
                    </button>
                    Move: <input type="checkbox" id="enablemove"/>
                    </g:form>
                </div>
                <div class='card-body'>
                <ol class='property-list'>
                    <li class='fieldcontain'>
                        <span class='property-label'>Id</span>
                        <div class='property-value' id='nodeid'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Name</span>
                        <div class='property-value' id='nodename'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Slug</span>
                        <div class='property-value' id='nodeslug'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Full Path</span>
                        <div class='property-value' id='nodepath'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Lft/Rgt</span>
                        <div class='property-value' id='nodelftrgt'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Domain</span>
                        <div class='property-value' id='nodedomain'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Domain ID</span>
                        <div class='property-value' id='nodedomainid'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Data</span>
                        <div class='property-value' id='nodedata'></div>
                    </li>
                    <li class='fieldcontain'>
                        <span class='property-label'>Users</span>
                        <div class='property-value' id='nodeusers'></div>
                    </li>
                </ol>
                </div>
            </div>
            <div class='card'>
                <div class='card-header'>
                    <form class='horizontal-form'>
                        <fieldset class="form">
                            <div class="fieldcontain required">
                            <label for="search">Search</label>
                            <input type="text" name="q" value="${params.q.encodeAsHTML()}" id="q">
                            <button type="submit" class="btn btn-primary">Search</button>
                            </div>
                        </fieldset>
                    </form>
                </div>
                <div id='treebody' class='card-body'>
                    <g:treenodes node='${this.portalTreeNode}'/>
                </div>
            </div>
        </div>
    </div>
</body>
<asset:javascript src="jstree/jstree.min.js" asset-defer="true"/>
<asset:script type="text/javascript">
$('div.jstree').jstree({
"core" : {
    "animation" : 0,
    "check_callback" : function() {
        if($('#enablemove').prop('checked')){
            return true
        }
        else {
            return false
        }
    },
    "themes" : { "stripes" : true },
    <g:if test="${params.nodepath}">
    "initially_open" : [${(params.nodepath.class.isArray()?params.nodepath.join(','):params.nodepath)}],
    </g:if>
},
"plugins" : [ "themes", "html_data", "search", "adv_search", "ui", "dnd", "crrm" ],
})
<g:if test="${params.q}">
$('div.jstree').jstree("search","${params.q.encodeAsHTML()}")
</g:if>

function inituserform() {
    $('#usernodeid').val("")
    $('#useruserid').val("")
    $('#nodeaction').val('new')
    $('#delete_user_button').hide()
    $('#role').val("")
    $('#user').empty()
    $('#add_user_button').text('Add User')
}

function userclick(data) {
    $('#usernodeid').val($(data).data('node_id'))
    $('#useruserid').val($(data).data('user_id'))
    $('#nodeaction').val('edit')
    $('#delete_user_button').show()
    $('#role').val($(data).data('user_role'))
    $('#user').empty()
    var newOption = new Option($(data).data('user_name'), $(data).data('user_id'), false, false)
    $('#user').append(newOption).trigger('change')
    var myModal = new bootstrap.Modal(document.getElementById("userformdialog"), {});
    $('#add_user_button').text('Update User')
    myModal.show();
}

function switchclick(data) {
    console.log('Swith to ' + data)
    window.location = data
}

$('#delete_user_button').bind("click",function(event){
    if(confirm('Delete user?')) {
        $('#nodeaction').val('delete')
        $('#user_form').submit()
        return true
    }
    return false
})
$('div.jstree')
.bind("move_node.jstree",function(event, mdata) {        
    if($("#enablemove").prop('checked')){        
        console.log(mdata);
        $.ajax({
        url: "<g:createLink controller="portalTreeNode" action="movenode"/>",
        data: "o=" + mdata.node.id + "&r=" + mdata.parent + "&p=" + mdata.position,
        });
    }
})
.bind("select_node.jstree",function(e,data){
    console.log(data);
    console.log(data.node.id);
    var selectedid = data.node.id; 
    $('a.createnode').attr('href','<g:createLink controller="portalTreeNode" action="create"/>?parentid=' + selectedid);
    $('a.editnode').attr('href','<g:createLink controller="portalTreeNode" action="edit"/>?id=' + selectedid);
    $('a.editdomain').attr('href','<g:createLink controller="portalTreeNode" action="edit_domain"/>?id=' + selectedid);
    $('a.deletenode').attr('href','<g:createLink controller="portalTreeNode" action="delete"/>?id=' + selectedid);
    $('#deleteform').attr('action','<g:createLink controller="portalTreeNode" action="delete"/>?id=' + selectedid);
    fetch('<g:createLink controller="portalTreeNode" action="json"/>/' + selectedid)
    .then(response => response.json())
    .then(data => {
        console.log(data)
        $('#delete_user_button').hide()
        $('div#nodeid').text(data.node.id)
        $('#hidenodeid').val(data.node.id)
        $('div#nodename').text(data.node.name)
        $('div#nodeslug').text(data.node.slug)
        $('div#nodepath').text(data.node.path)
        $('div#nodelftrgt').text(data.node.lft + ' - ' + data.node.rgt)
        $('div#nodedomain').text(data.node.domain)
        $('div#nodedomainid').text(data.node.domainid)
        $('div#nodedata').text(data.node.data)
        $('#add_user_button').text('Add User')
        $('#nodeaction').val('new')
        var amountusers = $("<span data-bs-toggle='collapse' data-bs-target='#userlist'>" + data.node.users.length + " users</span>")
        var ulist = $("<ul class='collapse' id='userlist'></ul>")
        data.node.users.forEach(function(it){
            var linktext = it.role + ' : ' + it.name
            var curlink = "<a onclick='userclick(this)' href='#' data-user_name='" + it.name + "' data-user_role='" + it.role + "' data-node_id='" + it.id + "' data-user_id='" + it.user_id + "'>" + linktext + "</a>"
            var toinfo = "<a onclick='switchclick(this)' href='<g:createLink controller='user' action='show'/>/" + it.user_id + "'>Info</a>"
            var toswitch = "<a onclick='switchclick(this)' href='<g:createLink controller='user' action='switchuser'/>/" + it.user_id + "'>Switch</a>"
            var theli = '<li>' + curlink + ' : ' + toinfo;
            <g:if test='${session.curuser?.switchable() && !session.realuser}'>
            theli += ' : ' + toswitch;
            </g:if>
            theli += '</li>';
            ulist.append($(theli));
        })
        amountusers.append(ulist)
        $('div#nodeusers').html(amountusers)
    })
})
<g:if test="${params.nodeid}">
    $("div.jstree").jstree("select_node", "#${params.nodeid}")
</g:if>
<g:else>
<g:if test="${params.id}">
    $("div.jstree").jstree("select_node", "#${params.id}")
</g:if>
</g:else>
$('.user-selector').select2({
  dropdownParent: "#userformdialog",
  ajax: {
    url: "<g:createLink controller='user' action='api_list'/>",
    dataType: 'json',
    data: function (params) {
      return {
        q: params.term, // search term
        id: $('#useruserid').val(),
        page: params.page
      }
    },
    processResults: function (data) {
      // Transforms the top-level key of the response object from 'items' to 'results'
      var toret = [];
      data.users.forEach(function(user) {
        toret.push( {'id':user.id,'text':user.name} );
      })
      return {
        results: toret
      }
    }
  }
})
</asset:script>
</html>
