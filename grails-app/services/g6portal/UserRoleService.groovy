package g6portal

import grails.gorm.services.Query
import grails.gorm.services.Service

@Service(UserRole)
interface UserRoleService {

    UserRole get(Serializable id)

    Long count()

    @Query("select count(*) from ${UserRole role} where ${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})")
    Long count(String query)

    @Query("select count(*) from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module}=${module} and ${role.role}=${role}")
    Long count(String query,String module,String role)

    @Query("select count(*) from ${UserRole role} where ${role.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module} in ${modules}")
    Long count(String query,ArrayList modules)

    @Query("select count(*) from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module} in ${modules} and ${role.role}=${role}")
    Long count(String query,ArrayList modules,String role)

    @Query("select count(*) from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module}=${module}")
    Long count(String query,String module)

    List<UserRole> list(Map args)

    @Query("from ${UserRole role} where ${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})")
    List<UserRole> list(String query, Map args)

    @Query("from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module}=${module} and ${role.role}=${role}")
    List<UserRole> list(String query, String module, String role, Map args)

    @Query("from ${UserRole role} where ${role.module} in ${modules}")
    List<UserRole> list(ArrayList modules, Map args)

    @Query("from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module} in ${modules}")
    List<UserRole> list(String query, ArrayList modules, Map args)

    @Query("from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module} in ${modules} and ${role.role}=${role}")
    List<UserRole> list(String query, ArrayList modules, String role, Map args)

    @Query("from ${UserRole role} where (${role.role} like ${query} or ${role.module} like ${query} or ${role.user} in (select ${user} from ${User user} where ${user.name} like ${query})) and ${role.module}=${module}")
    List<UserRole> list(String query, String module, Map args)



    void delete(Serializable id)

    UserRole save(UserRole userRole)

}
