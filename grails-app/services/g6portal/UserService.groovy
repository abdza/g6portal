package g6portal

import grails.gorm.services.Query
import grails.gorm.services.Service

@Service(User)
interface UserService {

    User get(Serializable id)

    List<User> list(Map args)
    Long count()

    List<User> listByIsActive(Boolean isActive, Map args)
    Long countByIsActive(Boolean isActive)

    @Query("from ${User user} where ${user.isActive} = ${isActive} and ( ${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query})")
    List<User> list(Boolean isActive, String query, Map args)

    @Query("select count(*) from ${User user} where ${user.isActive} = ${isActive} and ( ${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query})")
    Long count(Boolean isActive, String query)

    @Query("from ${User user} where ${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query}")
    List<User> list(String query, Map args)

    @Query("select count(*) from ${User user} where ${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query}")
    Long count(String query)

    List<User> listByRole(String role,Map args)
    Long countByRole(String role)

    List<User> listByIsActiveAndRole(Boolean isActive, String role, Map args)
    Long countByIsActiveAndRole(Boolean isActive, String role)

    @Query("from ${User user} where ${user.role} = ${role} and ${user.isActive} = ${isActive} and ( ${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query})")
    List<User> list(String role, Boolean isActive, String query, Map args)

    @Query("select count(*) from ${User user} where ${user.role} = ${role} and ${user.isActive} = ${isActive} and ( ${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query})")
    Long count(String role, Boolean isActive, String query)

    @Query("from ${User user} where ${user.role} = ${role} and (${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query})")
    List<User> list(String role, String query, Map args)

    @Query("select count(*) from ${User user} where ${user.role} = ${role} and (${user.name} like ${query} or ${user.email} like ${query} or ${user.userID} like ${query})")
    Long count(String role, String query)


    void delete(Serializable id)

    User save(User user)

    @Query("from ${User user} where ${user.name} like ${query} or ${user.userID} like ${query} or ${user.email} like ${query} or ${user.lanid} like ${query}")
    List<User> list_query(String query, Map args)
}
