package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalErrorLog)
interface PortalErrorLogService {

    PortalErrorLog get(Serializable id)

    Long count()

    @Query("select count(*) from ${PortalErrorLog error} where ${error.controller} like ${query} or ${error.action} like ${query} or ${error.params} like ${query} or ${error.slug} like ${query} or ${error.errormsg} like ${query} or ${error.ipaddr} like ${query} or ${error.uri} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalErrorLog error} where ${error.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalErrorLog error} where (${error.controller} like ${query} or ${error.action} like ${query} or ${error.params} like ${query} or ${error.slug} like ${query} or ${error.errormsg} like ${query} or ${error.ipaddr} like ${query} or ${error.uri} like ${query}) and ${error.module} in ${modules}")
    Long count(String query,ArrayList modules)

    List<PortalErrorLog> list(Map args)

    @Query("from ${PortalErrorLog error} where ${error.controller} like ${query} or ${error.action} like ${query} or ${error.params} like ${query} or ${error.slug} like ${query} or ${error.errormsg} like ${query} or ${error.ipaddr} like ${query} or ${error.uri} like ${query} order by date desc")
    List<PortalErrorLog> list(String query,Map args)

    @Query("from ${PortalErrorLog error} where ${error.module} in ${modules} order by date desc")
    List<PortalErrorLog> list(ArrayList modules, Map args)

    @Query("from ${PortalErrorLog error} where (${error.controller} like ${query} or ${error.action} like ${query} or ${error.params} like ${query} or ${error.slug} like ${query} or ${error.errormsg} like ${query} or ${error.ipaddr} like ${query} or ${error.uri} like ${query}) and ${error.module} in ${modules} order by date desc")
    List<PortalErrorLog> list(String query,ArrayList modules, Map args)


    void delete(Serializable id)

    PortalErrorLog save(PortalErrorLog portalErrorLog)

}
