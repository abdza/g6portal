package g6portal

import grails.gorm.services.Service

@Service(PortalPageData)
interface PortalPageDataService {

    PortalPageData get(Serializable id)

    List<PortalPageData> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalPageData save(PortalPageData portalPageData)

}
