package g6portal

import grails.gorm.services.Query
import grails.gorm.services.Service

@Service(PortalTree)
interface PortalTreeService {

    PortalTree get(Serializable id)

    Long count()

    @Query("select count(*) from ${PortalTree tree} where ${tree.name} like ${query} or ${tree.module} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalTree tree} where ${tree.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalTree tree} where (${tree.name} like ${query} or ${tree.module} like ${query}) and ${tree.module} in ${modules}")
    Long count(String query,ArrayList modules)

    @Query("select count(*) from ${PortalTree tree} where (${tree.name} like ${query} or ${tree.module} like ${query}) and ${tree.module}=${module}")
    Long count(String query, String module)

    List<PortalTree> list(Map args)

    @Query("from ${PortalTree tree} where ${tree.name} like ${query} or ${tree.module} like ${query}")
    List<PortalTree> list(String query,Map args)

    @Query("from ${PortalTree tree} where ${tree.module} in ${modules}")
    List<PortalTree> list(ArrayList modules, Map args)

    @Query("from ${PortalTree tree} where (${tree.name} like ${query} or ${tree.module} like ${query}) and ${tree.module} in ${modules}")
    List<PortalTree> list(String query,ArrayList modules, Map args)

    @Query("from ${PortalTree tree} where (${tree.name} like ${query} or ${tree.module} like ${query}) and ${tree.module}=${module}")
    List<PortalTree> list(String query, String module, Map args)


    void delete(Serializable id)

    PortalTree save(PortalTree portalTree)

}
