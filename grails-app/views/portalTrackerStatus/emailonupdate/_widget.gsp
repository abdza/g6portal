<g:select name="${property}" from="${g6portal.PortalTrackerEmail.findAllByTracker(this.portalTrackerStatus?.tracker)}" optionKey="id" value="${value?.id}" noSelection="['':'Please select']"/>
