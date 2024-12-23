package g6portal

import grails.gorm.services.Service

@Service(PortalTreeNodeUser)
interface PortalTreeNodeUserService {

    PortalTreeNodeUser get(Serializable id)

    List<PortalTreeNodeUser> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTreeNodeUser save(PortalTreeNodeUser portalTreeNodeUser)

}
