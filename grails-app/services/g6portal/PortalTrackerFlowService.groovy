package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerFlow)
interface PortalTrackerFlowService {

    PortalTrackerFlow get(Serializable id)

    List<PortalTrackerFlow> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerFlow save(PortalTrackerFlow portalTrackerFlow)

}