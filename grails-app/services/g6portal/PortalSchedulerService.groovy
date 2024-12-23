package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalScheduler)
interface PortalSchedulerService {

    PortalScheduler get(Serializable id)

    Long count()

    @Query("select count(*) from ${PortalScheduler schedule} where ${schedule.name} like ${query} or ${schedule.slugs} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalScheduler schedule} where ${schedule.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalScheduler schedule} where (${schedule.name} like ${query} or ${schedule.slugs} like ${query}) and ${schedule.module} in ${modules}")
    Long count(String query,ArrayList modules)

    List<PortalScheduler> list(Map args)

    @Query("from ${PortalScheduler schedule} where ${schedule.name} like ${query} or ${schedule.slugs} like ${query}")
    List<PortalScheduler> list(String query,Map args)

    @Query("from ${PortalScheduler schedule} where ${schedule.module} in ${modules}")
    List<PortalScheduler> list(ArrayList modules, Map args)

    @Query("from ${PortalScheduler schedule} where (${schedule.name} like ${query} or ${schedule.slugs} like ${query}) and ${schedule.module} in ${modules}")
    List<PortalScheduler> list(String query,ArrayList modules, Map args)


    void delete(Serializable id)

    PortalScheduler save(PortalScheduler portalScheduler)

}
