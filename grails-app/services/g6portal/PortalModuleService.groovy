package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalModule)
interface PortalModuleService {

    PortalModule get(Serializable id)


    Long count()

    @Query("select count(*) from ${PortalModule module} where ${module.name} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalModule module} where ${module.name} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalModule module} where ${module.name} like ${query} and ${module.name} in ${modules}")
    Long count(String query,ArrayList modules)

    List<PortalModule> list(Map args)

    @Query("from ${PortalModule module} where ${module.name} like ${query}")
    List<PortalModule> list(String query, Map args)

    @Query("from ${PortalModule module} where ${module.name} in ${modules}")
    List<PortalModule> list(ArrayList modules,Map args)

    @Query("from ${PortalModule module} where ${module.name} like ${query} and ${module.name} in ${modules}")
    List<PortalModule> list(String query, ArrayList modules, Map args)

    void delete(Serializable id)

    PortalModule save(PortalModule portalModule)

}
