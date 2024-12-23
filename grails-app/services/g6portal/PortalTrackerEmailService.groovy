package g6portal

import grails.gorm.services.Service

@Service(PortalTrackerEmail)
interface PortalTrackerEmailService {

    PortalTrackerEmail get(Serializable id)

    List<PortalTrackerEmail> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTrackerEmail save(PortalTrackerEmail portalTrackerEmail)

}
