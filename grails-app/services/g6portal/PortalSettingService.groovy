package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalSetting)
interface PortalSettingService {

    PortalSetting get(Serializable id)

    Long count()

    @Query("select count(*) from ${PortalSetting setting} where ${setting.name} like ${query} or ${setting.module} like ${query} or ${setting.text} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalSetting setting} where ${setting.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalSetting setting} where (${setting.name} like ${query} or ${setting.module} like ${query} or ${setting.text} like ${query}) and ${setting.module} in ${modules}")
    Long count(String query,ArrayList modules)

    @Query("select count(*) from ${PortalSetting setting} where (${setting.name} like ${query} or ${setting.module} like ${query} or ${setting.text} like ${query}) and ${setting.module}=${module}")
    Long count(String query, String module)

    List<PortalSetting> list(Map args)

    @Query("from ${PortalSetting setting} where ${setting.name} like ${query} or ${setting.module} like ${query} or ${setting.text} like ${query}")
    List<PortalSetting> list(String query, Map args)

    @Query("from ${PortalSetting setting} where ${setting.module} in ${modules}")
    List<PortalSetting> list(ArrayList modules, Map args)

    @Query("from ${PortalSetting setting} where (${setting.name} like ${query} or ${setting.module} like ${query} or ${setting.text} like ${query}) and ${setting.module} in ${modules}")
    List<PortalSetting> list(String query, ArrayList modules, Map args)

    @Query("from ${PortalSetting setting} where (${setting.name} like ${query} or ${setting.module} like ${query} or ${setting.text} like ${query}) and ${setting.module}=${module}")
    List<PortalSetting> list(String query, String module, Map args)

    void delete(Serializable id)

    PortalSetting save(PortalSetting portalSetting)

}
