<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'user.label', default: 'User')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#show-user" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <g:if test='${curuser?.isAdmin}'>
                            <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                            <li><g:link class="list" action="updatelanid" id="${this.user.id}">Update LAN ID</g:link></li>
                        </g:if>
                        <g:if test='${curuser?.switchable() && !session.realuser}'>
                        <li><g:link class="list" action="switchuser" id="${this.user.userID}">Switch</g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-user" class="col-12 content scaffold-show" role="main">
                    <h1><g:message code="default.show.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <f:display except='nodes,profilepic,roletargetid,resetexception,lanid,treesdate,lanidexception,resetPassword,lastReminder,state,password,password5,emergency_contact,lastInfoUpdate,directline,handphone,company_handphone,address,secretquestion,secretanswer,emergency_name,isAdmin,profile_id' bean="user" />
                    <g:if test='${curuser?.isAdmin || curuser.id==this.user.id}'>
                      <f:display except='profile_id,userID,name,email,role,roletargetid,resetexception,date_joined,treesdate,lanidexception,isActive,resetPassword,lastlogin,nodes,profilepic,lastUpdated,lastInfoUpdate,lastReminder,password,password5,secretquestion,secretanswer,isAdmin' bean="user" />
                        <g:if test='${curuser?.isAdmin}'>
                            <f:display except='directline,handphone,company_handphone,emergency_contact,emergency_name,state,address,lanid,userID,name,email,role,roletargetid,resetexception,date_joined,treesdate,lanidexception,isActive,resetPassword,lastlogin,nodes,profilepic,lastUpdated,lastInfoUpdate,lastReminder,password,password5,secretquestion,secretanswer,isAdmin' bean="user" />
                        </g:if>
                    </g:if>
                    <g:if test='${curuser.switchable() && !session.realuser}'>
                    <h3>Tree Roles</h3>
                    <ol>
                    <g:each in="${this.user.treeroles()}" var="trole">
                        <li><g:link controller='portalTreeNode' action='show' params="[id:trole.node.tree.root.id,nodeid:trole.node.id]">${trole}</g:link> - ${trole.node.getfullpath()}</li>
                    </g:each>
                    </ol>
                    <h3>Module Roles</h3>
                    <ol>
                    <g:each in="${this.user.moduleroles()}" var="mrole">
                        <li><g:link controller='userRole' action='show' params="[id:mrole.id]">${mrole.module} - ${mrole.role}</g:link></li>
                    </g:each>
                    </ol>
                    </g:if>
                    <g:if test='${curuser?.isAdmin}'>
                        <g:form useToken="true" resource="${this.user}" method="DELETE">
                            <fieldset class="buttons">
                                <g:link class="edit" action="edit" resource="${this.user}"><g:message code="default.button.edit.label" default="Edit" /></g:link>
                                <input class="delete" type="submit" value="${message(code: 'default.button.delete.label', default: 'Delete')}" onclick="return confirm('${message(code: 'default.button.delete.confirm.message', default: 'Are you sure?')}');" />
                            </fieldset>
                        </g:form>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
</html>
