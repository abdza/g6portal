<select id='${property}' name='${property}' class='user-selector' style='width: 40%;'>
<g:if test='${value}'>
<option value='${value.id}'>${value}</option>
</g:if>
</select>
<asset:script type='text/javascript'>
$('.user-selector').select2({
  ajax: {
    url: '<g:createLink controller='user' action='api_list'/>',
    dataType: 'json',
    data: function (params) {
      return {
        q: params.term, // search term
<g:if test='${value}'>
        id:${value.id},
</g:if>
        page: params.page
      };
    },
    processResults: function (data) {
      // Transforms the top-level key of the response object from 'items' to 'results'
      var toret = [];
      data.users.forEach(function(user) {
        toret.push( {'id':user.id,'text':user.name} );
      });
      return {
        results: toret
      };
    }
  }
});
</asset:script>
