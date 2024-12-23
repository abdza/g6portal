package g6portal

import grails.gorm.services.Service

@Service(UserLog)
interface UserLogService {

    UserLog get(Serializable id)

    List<UserLog> list(Map args)

    Long count()

    void delete(Serializable id)

    UserLog save(UserLog userLog)

}
