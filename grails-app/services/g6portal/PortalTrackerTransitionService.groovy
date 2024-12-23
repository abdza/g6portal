package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerTransition)
interface PortalTrackerTransitionService {

    PortalTrackerTransition get(Serializable id)

    List<PortalTrackerTransition> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerTransition save(PortalTrackerTransition portalTrackerTransition)

}
