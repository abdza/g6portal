package g6portal

import grails.gorm.services.Service

@Service(PortalTreeNode)
interface PortalTreeNodeService {

    PortalTreeNode get(Serializable id)

    List<PortalTreeNode> list(Map args)

    Long count()

    void delete(Serializable id)

    PortalTreeNode save(PortalTreeNode portalTreeNode)

}
