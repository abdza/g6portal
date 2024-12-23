package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalTrackerData)
interface PortalTrackerDataService {

    PortalTrackerData get(Serializable id)

    List<PortalTrackerData> list(Map args)

    Long count()

    @Query("from ${PortalTrackerData trackerdata} where ${trackerdata.tracker} in ${trackers}")
    List<PortalTrackerData> list(ArrayList trackers,Map args)

    @Query("select count(*) from ${PortalTrackerData trackerdata} where ${trackerdata.tracker} in ${trackers}")
    Long count(ArrayList trackers)

    void delete(Serializable id)

    PortalTrackerData save(PortalTrackerData portalTrackerData)

}
