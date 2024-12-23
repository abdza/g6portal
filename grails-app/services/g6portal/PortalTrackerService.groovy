package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalTracker)
interface PortalTrackerService {

    PortalTracker get(Serializable id)


    Long count()

    @Query("select count(*) from ${PortalTracker tracker} where ${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalTracker tracker} where ${tracker.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalTracker tracker} where (${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}) and ${tracker.module} in ${modules}")
    Long count(String query,ArrayList modules)

    @Query("select count(*) from ${PortalTracker tracker} where (${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}) and ${tracker.module}=${module}")
    Long count(String query, String module)

    List<PortalTracker> list(Map args)

    @Query("from ${PortalTracker tracker} where ${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}")
    List<PortalTracker> list(String query, Map args)

    @Query("from ${PortalTracker tracker} where ${tracker.module} in ${modules}")
    List<PortalTracker> list(ArrayList modules, Map args)

    @Query("from ${PortalTracker tracker} where ${tracker.module} in ${modules}")
    List<PortalTracker> list(ArrayList modules)

    @Query("from ${PortalTracker tracker} where (${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}) and ${tracker.module} in ${modules}")
    List<PortalTracker> list(String query, ArrayList modules, Map args)

    @Query("from ${PortalTracker tracker} where (${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}) and ${tracker.module} in ${modules}")
    List<PortalTracker> list(String query, ArrayList modules)

    @Query("from ${PortalTracker tracker} where (${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}) and ${tracker.module}=${module}")
    List<PortalTracker> list(String query, String module, Map args)

    @Query("from ${PortalTracker tracker} where (${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}) and ${tracker.module}=${module}")
    List<PortalTracker> list(String query, String module)

    @Query("from ${PortalTracker tracker} where ${tracker.name} like ${query} or ${tracker.module} like ${query} or ${tracker.slug} like ${query}")
    List<PortalTracker> list(String query)

    void delete(Serializable id)

    PortalTracker save(PortalTracker portalTracker)

}
