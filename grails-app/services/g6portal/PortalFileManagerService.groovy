package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalFileManager)
interface PortalFileManagerService {

    PortalFileManager get(Serializable id)

    Long count()

    @Query("select count(*) from ${PortalFileManager FileManager} where ${FileManager.name} like ${query} or ${FileManager.path} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalFileManager FileManager} where ${FileManager.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalFileManager FileManager} where (${FileManager.name} like ${query} or ${FileManager.path} like ${query}) and ${FileManager.module} in ${modules}")
    Long count(String query,ArrayList modules)

    List<PortalFileManager> list(Map args)

    @Query("from ${PortalFileManager FileManager} where ${FileManager.name} like ${query} or ${FileManager.path} like ${query}")
    List<PortalFileManager> list(String query,Map args)

    @Query("from ${PortalFileManager FileManager} where ${FileManager.module} in ${modules}")
    List<PortalFileManager> list(ArrayList modules, Map args)

    @Query("from ${PortalFileManager FileManager} where (${FileManager.name} like ${query} or ${FileManager.path} like ${query}) and ${FileManager.module} in ${modules}")
    List<PortalFileManager> list(String query,ArrayList modules, Map args)

    void delete(Serializable id)

    PortalFileManager save(PortalFileManager portalFileManager)

}
