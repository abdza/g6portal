package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerField)
interface PortalTrackerFieldService {

    PortalTrackerField get(Serializable id)

    PortalTrackerField findByTrackerAndName(PortalTracker tracker,String name)

    List<PortalTrackerField> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerField save(PortalTrackerField portalTrackerField)

}
