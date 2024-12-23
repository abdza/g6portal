package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalPage)
interface PortalPageService {

    PortalPage get(Serializable id)

    List<PortalPage> list(Map args)

    Long count()

    @Query("select count(*) from ${PortalPage page} where ${page.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("from ${PortalPage page} where ${page.title} like ${query} or ${page.slug} like ${query} or ${page.content} like ${query} or ${page.module} like ${query}")
    List<PortalPage> list(String query, Map args)

    @Query("from ${PortalPage page} where ${page.module} in ${modules}")
    List<PortalPage> list(ArrayList modules, Map args)

    @Query("select count(*) from ${PortalPage page} where ${page.title} like ${query} or ${page.slug} like ${query} or ${page.content} like ${query} or ${page.module} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalPage page} where (${page.title} like ${query} or ${page.slug} like ${query} or ${page.content} like ${query} or ${page.module} like ${query}) and ${page.module} in ${modules}")
    Long count(String query,ArrayList modules)

    @Query("from ${PortalPage page} where (${page.title} like ${query} or ${page.slug} like ${query} or ${page.content} like ${query} or ${page.module} like ${query}) and ${page.module}=${module}")
    List<PortalPage> list(String query, String module, Map args)

    @Query("from ${PortalPage page} where (${page.title} like ${query} or ${page.slug} like ${query} or ${page.content} like ${query} or ${page.module} like ${query}) and ${page.module} in ${modules}")
    List<PortalPage> list(String query, ArrayList modules, Map args)

    @Query("select count(*) from ${PortalPage page} where (${page.title} like ${query} or ${page.slug} like ${query} or ${page.content} like ${query} or ${page.module} like ${query}) and ${page.module}=${module}")
    Long count(String query, String module)

    void delete(Serializable id)

    PortalPage save(PortalPage portalPage)

}
