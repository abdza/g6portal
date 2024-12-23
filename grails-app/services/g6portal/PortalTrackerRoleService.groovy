package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerRole)
interface PortalTrackerRoleService {

    PortalTrackerRole get(Serializable id)

    List<PortalTrackerRole> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerRole save(PortalTrackerRole portalTrackerRole)

}
