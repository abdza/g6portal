package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalTrackerError)
interface PortalTrackerErrorService {

    PortalTrackerError get(Serializable id)

    Long count()

    List<PortalTrackerError> list(Map args)

    void delete(Serializable id)

    PortalTrackerError save(PortalTrackerError portalTrackerError)

}
