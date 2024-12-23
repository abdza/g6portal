<select id='${property}' name='${property}' class='roles-selector' multiple style='width: 40%;'>
<g:if test='${value}'>
<g:each in='${value}' var='roles'>
<option value='${roles.id}' selected>${roles.name}</option>
</g:each>
</g:if>
</select>
<asset:script type='text/javascript'>
$('#${property}').select2({
  multiple: true,
  placeholder: 'None',
  allowClear: true,
  ajax: {
    url: '<g:createLink controller='portalTracker' action='api_list'/>',
    dataType: 'json',
    data: function (params) {
      return {
	item: 'roles',
        q: params.term, // search term
<g:if test='${params.tracker_id}'>
        tracker_id:${params.tracker_id},
</g:if>
<g:else>
<g:if test="${portalTrackerStatus}">
        tracker_id:${portalTrackerStatus.tracker?.id},
</g:if>
</g:else>
        page: params.page
      };
    },
    processResults: function (data) {
      // Transforms the top-level key of the response object from 'items' to 'results'
      var toret = [];
      data.roles.forEach(function(role) {
	toret.push( {'id':role.id,'text':role.name} );
      });
      return {
        results: toret
      };
    }
  }
});
</asset:script>
