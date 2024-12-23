<g:if test='${curfield}'>
    <g:if test="${curfield.field_type=='BelongsTo'}">
      <g:select name="custom_${curfield.id}" from="${choices}" optionKey="id" optionValue="${otherfield}"/>
    </g:if>
    <g:else>
     <g:textField id="custom_${curfield.id}" name="custom_${curfield.id}" />
    </g:else>
</g:if>
<g:else>
  No field
</g:else>
