<textarea id='${property}' name='${property}' style='width: 40%;'>
<g:if test='${value}'>
${value}
</g:if>
</textarea>
<asset:script type='text/javascript'>

var input_${property} = document.querySelector('textarea[name=${property}]')
var tagify_${property} = new Tagify(input_${property}, {
        enforceWhitelist : true,
	originalInputValueFormat: valuesArr => valuesArr.map(item => item.value).join(','),
        delimiters       : ',',
        whitelist        : ["${raw((this.portalTracker?.fields*.name)?.join('","'))}"],
        callbacks        : {
            add    : console.log,  // callback when adding a tag
            remove : console.log   // callback when removing a tag
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
