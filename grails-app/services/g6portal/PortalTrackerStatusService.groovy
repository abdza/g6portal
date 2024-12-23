package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerStatus)
interface PortalTrackerStatusService {

    PortalTrackerStatus get(Serializable id)

    List<PortalTrackerStatus> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerStatus save(PortalTrackerStatus portalTrackerStatus)

}
