<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="layout" content="main" />
        <title>Save Data Update</title>
    </head>
    <body>
	<div class="nav" role="navigation">
	    <ul>
		<li><a class="home" href="${createLink(uri: '/')}"><g:message code="default.home.label"/></a></li>
		<li><g:link class="list" action="index">Data Update List</g:link></li>
	    </ul>
	</div>
        <div class="body">
            <h1>Data Update Fields</h1>
            <g:if test="${flash.message}">
            <div class="message">${flash.message}</div>
            </g:if>            
            <g:form action="doupload" method="post">
              <g:hiddenField name="update_id" value="${portalTrackerData.id}"/>
            <table>
              <tr>
                <th>Database Field</th>
                <th>Excel Position</th>
                <th>Manual Entry</th>
                <th style="width:20px;">Key Update</th>
              </tr>
              <g:each in="${portalTrackerData.tracker.fields.sort { it.name }}" var="field">
                <tr>
                  <td>
                    ${field.name}
                  </td>
                  <td>                    
                    <g:if test="${setfields[field.id]=='ignore' && field.field_default}">
                      <g:select data-field-id="${field.id}" class="fieldpos" name="datasource_${field.id}" from="${excelfields}" value="custom" optionKey="id" optionValue="name"/>
                    </g:if>
                    <g:else>
                      <g:select data-field-id="${field.id}" class="fieldpos" name="datasource_${field.id}" from="${excelfields}" value="${setfields[field.id]}" optionKey="id" optionValue="name"/>
                    </g:else>
                  </td>
                  <td>
                    <g:if test="${field.field_default}">
                      <g:textField id="custom_${field.id}" name="custom_${field.id}" value="${customdata[field.id]}"/>
                    </g:if>
                    <g:else>                          
                       <g:textField id="custom_${field.id}" name="custom_${field.id}" />
                    </g:else>
                  </td>
                  <td style="width:20px;">                    
                    <g:checkBox style="width: auto;" name="update_${field.id}"/>                  
                  </td>
                </tr>
              </g:each>
            </table>
              <g:submitButton name="Upload"/>
            </g:form>            
        </div>
    </body>
</html>
  <asset:script type='text/javascript'>
    $('.fieldpos').on('change',function(event) {
      var field_id = $('#' + event.currentTarget.id).data('field-id');
      htmx.ajax('GET', "<g:createLink controller='portalTrackerData' action='customfield'/>/" + field_id, {target:'#custom_' + field_id, swap:'outerHTML'});
    });
  </asset:script>
$(document).ready(function(){
