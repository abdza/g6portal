package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerIndex)
interface PortalTrackerIndexService {

    PortalTrackerIndex get(Serializable id)

    List<PortalTrackerIndex> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerIndex save(PortalTrackerIndex portalTrackerIndex)

}
