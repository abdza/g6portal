package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(PortalEmail)
interface PortalEmailService {

    PortalEmail get(Serializable id)

    Long count()

    @Query("select count(*) from ${PortalEmail email} where ${email.title} like ${query} or ${email.emailto} like ${query} or ${email.emailcc} like ${query} or ${email.body} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${PortalEmail email} where ${email.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${PortalEmail email} where (${email.title} like ${query} or ${email.emailto} like ${query} or ${email.emailcc} like ${query} or ${email.body} like ${query}) and ${email.module} in ${modules}")
    Long count(String query,ArrayList modules)

    List<PortalEmail> list(Map args)

    @Query("from ${PortalEmail email} where ${email.title} like ${query} or ${email.emailto} like ${query} or ${email.emailcc} like ${query} or ${email.body} like ${query}")
    List<PortalEmail> list(String query,Map args)

    @Query("from ${PortalEmail email} where ${email.module} in ${modules}")
    List<PortalEmail> list(ArrayList modules, Map args)

    @Query("from ${PortalEmail email} where (${email.title} like ${query} or ${email.emailto} like ${query} or ${email.emailcc} like ${query} or ${email.body} like ${query}) and ${email.module} in ${modules}")
    List<PortalEmail> list(String query,ArrayList modules, Map args)

    @Query("from ${PortalEmail email} where ${email.emailSent}='0' and (${email.deliveryTime} is null or ${email.deliveryTime}<=CURRENT_TIMESTAMP)")
    List<PortalEmail> tosend()

    @Query("from ${PortalEmail email} where ${email.emailSent}=false and (${email.deliveryTime} is null or ${email.deliveryTime}<=CURRENT_TIMESTAMP)")
    List<PortalEmail> h2tosend()

    void delete(Serializable id)

    PortalEmail save(PortalEmail portalEmail)

}
