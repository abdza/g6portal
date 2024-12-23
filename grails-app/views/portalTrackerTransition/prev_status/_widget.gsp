<select id='${property}' name='${property}' class='status-selector' multiple style='width: 40%;'>
<g:if test='${value}'>
<g:each in='${value}' var='status'>
<option value='${status.id}' selected>${status.name}</option>
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
	item: 'status',
        q: params.term, // search term
<g:if test='${params.tracker_id}'>
        tracker_id:${params.tracker_id},
</g:if>
<g:else>
<g:if test="${portalTrackerTransition}">
        tracker_id:${portalTrackerTransition.tracker?.id},
</g:if>
</g:else>
        page: params.page
      };
    },
    processResults: function (data) {
      // Transforms the top-level key of the response object from 'items' to 'results'
      var toret = [];
      data.statuses.forEach(function(status) {
	toret.push( {'id':status.id,'text':status.name} );
      });
      return {
        results: toret
      };
    }
  }
});
</asset:script>
