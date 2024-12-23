<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'user.label', default: 'User')}" />
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  {
  td:nth-of-type(1):before { content: "Id"; }
  td:nth-of-type(2):before { content: "User ID"; }
  td:nth-of-type(3):before { content: "Name"; }
  td:nth-of-type(4):before { content: "Email"; }
  td:nth-of-type(5):before { content: "Role"; }
  td:nth-of-type(6):before { content: "Roletargetid"; }
  td:nth-of-type(7):before { content: "Lastlogin"; }
    }
        </style>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#list-user" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(controller:'user',action:'index')}"><g:message code="default.home.label"/></a></li>
                        <g:if test='${curuser?.isAdmin}'>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                        </g:if>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="list-user" class="col-12 content scaffold-list" role="main">
                    <h1><g:message code="default.list.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                        <div class="message" role="status">${flash.message}</div>
                    </g:if>
		    <g:form name='searchform' method='get'>
			<fieldset class='form'>
                        <div class='fieldcontain' id='listrole'>
                        <label>Role:</label>
                        <g:select name='rolefilter' value='${params.rolefilter}' from='${rolelist}' noSelection="['':'All']"/>
                        </div>
                        <div class='fieldcontain' id='isactivediv'>
                        <label>Is Active:</label><select name='is_active' id='is_active'>
                        <option value='all' <g:if test="${params.is_active=='all'}">selected</g:if> >All</option>
                        <option value='1' <g:if test="${params.is_active=='1'}">selected</g:if> >True</option>
                        <option value='0' <g:if test="${params.is_active=='0'}">selected</g:if> >False</option>
                        </select>
                        </div>
                        <div class='fieldcontain' id='listsearch'>
                        <label>Search:</label><input type='text' name='q' id='q' value='${params.q.encodeAsHTML()}'/><input id='dosearch' name='dosearch' type='submit' value='Search'/>
                        </div>
			</fieldset>
		    </g:form>
                    <f:table collection="${userList}" properties="['userID','name','email','role']"/>

                    <g:if test="${userCount > params.int('max')}">
                    <div class="pagination">
                        <g:paginate total="${userCount ?: 0}" params="${params}" />
                    </div>
                    </g:if>
                </div>
            </section>
        </div>
    </div>
    </body>
  <asset:script type='text/javascript' > 
  $('#is_active,#rolefilter').on('change',function() {
    $('#searchform').submit()
  })
  </asset:script>
</html>
