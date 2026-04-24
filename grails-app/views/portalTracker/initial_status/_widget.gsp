<g:select id='${property}' name='${property}' value='${portalTracker?.initial_status?.id}' from='${portalTracker.statuses?.findAll { !it.compositeStatuses }}' optionKey='id'>
</g:select>
