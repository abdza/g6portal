<textarea id='${property}' name='${property}' style='width: 40%;'><g:if test='${value}'>${value}</g:if></textarea>
<asset:script type='text/javascript'>
<%
def nonCompositeStatuses = portalTrackerStatus?.tracker?.statuses?.findAll {
    !it.compositeStatuses && it.id != portalTrackerStatus?.id
}*.name ?: []
%>
var input_${property} = document.querySelector('textarea[name=${property}]')
var tagify_${property} = new Tagify(input_${property}, {
    enforceWhitelist : true,
    originalInputValueFormat: valuesArr => valuesArr.map(item => item.value).join(','),
    delimiters       : ',',
    whitelist        : [${raw(nonCompositeStatuses.collect { '"' + it.replace('"', '\\"') + '"' }.join(','))}],
    callbacks        : {
        add    : console.log,
        remove : console.log
    }
});
var dragsort_${property} = new DragSort(tagify_${property}.DOM.scope, {
    selector: '.'+tagify_${property}.settings.classNames.tag,
    callbacks: {
        dragEnd: onDragEnd_${property}
    }
});
function onDragEnd_${property}(elm){
    tagify_${property}.updateValueByDOMTags()
}
</asset:script>
