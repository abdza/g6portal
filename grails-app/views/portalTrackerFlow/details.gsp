<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main" />
        <g:set var="entityName" value="${message(code: 'portalTrackerFlow.label', default: 'PortalTrackerFlow')}" />
        <title><g:message code="default.show.label" args="[entityName]" /></title>
    </head>
    <body>
    <div id="content" role="main">
        <div class="container">
            <section class="row">
                <a href="#show-portalTrackerFlow" class="skip" tabindex="-1"><g:message code="default.link.skip.label" default="Skip to content&hellip;"/></a>
                <div class="nav" role="navigation">
                    <ul>
                        <li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
                        <li><g:link class="list" action="index"><g:message code="default.list.label" args="[entityName]" /></g:link></li>
                        <li><g:link class="create" action="create"><g:message code="default.new.label" args="[entityName]" /></g:link></li>
                    </ul>
                </div>
            </section>
            <section class="row">
                <div id="show-portalTrackerFlow" class="col-12 content scaffold-show" role="main">
                    <h1><g:message code="default.show.label" args="[entityName]" /></h1>
                    <g:if test="${flash.message}">
                    <div class="message" role="status">${flash.message}</div>
                    </g:if>
                    <f:display bean="portalTrackerFlow" except="['fields','transitions']" />
                    <h2>All 
                    <button id='all'><i class="bi bi-check"></i></button>
                    <button id='undoall'><i class="bi bi-x"></i></button>
                    </h2>
                    <g:form action="details" id="${portalTrackerFlow.id}" method="POST">
                    <input type='hidden' id='update' name='update' value='1'/>
                    <table>
                    <tr>
                    <th colspan='2'>&nbsp;</th>
                    <g:each in='${transitions}' var='transition'>
                    <th>
                    ${transition.name}
                    <button id="t_${transition.id}" class="all_c" data-cid="t_${transition.id}"><i class="bi bi-check"></i></button>
                    <button id="ut_${transition.id}" class="undo_c" data-cid="t_${transition.id}"><i class="bi bi-x"></i></button>
                    </th>
                    <th>
                    ${transition.next_status?.name}
                    <button id="s_${transition.next_status?.id}" class="all_c" data-cid="s_${transition.next_status?.id}"><i class="bi bi-check"></i></button>
                    <button id="us_${transition.next_status?.id}" class="undo_c" data-cid="s_${transition.next_status?.id}"><i class="bi bi-x"></i></button>
                    </th>
                    </g:each>
                    </tr>
                    <g:each in='${fields}' var='field'>
                    <tr>
                    <th rowspan='2'>
                    ${field.name}
                    <button id="r_${field.id}" class="all_r" data-rid="r_${field.id}"><i class="bi bi-check"></i></button>
                    <button id="ur_${field.id}" class="undo_r" data-rid="r_${field.id}"><i class="bi bi-x"></i></button>
                    </th>
                    <td>Edit</td>
                    <g:each in='${transitions}' var='transition'>
                    <td>
                    <input <g:if test="${checkboxes['te_' + transition.id + '_' + field.id]}">checked</g:if> type='checkbox' id="te_${transition.id}_${field.id}" name="te_${transition.id}_${field.id}" title="Edit '${field.name}' for '${transition.name}'" class="fieldbox t_${transition.id} r_${field.id}"/>
                    </td>
                    <td>
                    &nbsp;
                    </td>
                    </g:each>
                    </tr>
                    <tr>
                    <td>Display</td>
                    <g:each in='${transitions}' var='transition'>
                    <td>
                    <input <g:if test="${checkboxes['td_' + transition.id + '_' + field.id]}">checked</g:if> type='checkbox' id="td_${transition.id}_${field.id}" name="td_${transition.id}_${field.id}" title="Display '${field.name}' for '${transition.name}'" class="fieldbox t_${transition.id} r_${field.id}"/>
                    </td>
                    <td>
                    <input <g:if test="${checkboxes['sd_' + transition.next_status?.id + '_' + field.id]}">checked</g:if> type='checkbox' id="sd_${transition.next_status?.id}_${field.id}" name="sd_${transition.next_status?.id}_${field.id}" title="Display '${field.name}' for '${transition.next_status?.name}'" class="fieldbox s_${transition.next_status?.id} r_${field.id}"/>
                    </td>
                    </g:each>

                    </tr>
                    </g:each>
                    </table>
                        <fieldset class="buttons">
                            <g:submitButton name="create" class="save" value="Update" />
                        </fieldset>
                    </g:form>
                </div>
            </section>
        </div>
    </div>
    </body>
    <asset:script>
    $('#all').on('click',function(item){
        $('.fieldbox').prop('checked',true);
    });
    $('#undoall').on('click',function(item){
        $('.fieldbox').prop('checked',false);
    });
    $('.all_c').on('click',function(item){
        $('.' + $(item.currentTarget).data('cid')).prop('checked',true);
        return false;
    });
    $('.all_r').on('click',function(item){
        $('.' + $(item.currentTarget).data('rid')).prop('checked',true);
        return false;
    });
    $('.undo_c').on('click',function(item){
        $('.' + $(item.currentTarget).data('cid')).prop('checked',false);
        return false;
    });
    $('.undo_r').on('click',function(item){
        $('.' + $(item.currentTarget).data('rid')).prop('checked',false);
        return false;
    });
    </asset:script>
</html>
