package g6portal

import grails.gorm.services.Service
import grails.gorm.services.Query

@Service(FileLink)
interface FileLinkService {

    FileLink get(Serializable id)

    Long count()

    @Query("select count(*) from ${FileLink file} where ${file.module} in ${modules}")
    Long count(ArrayList modules)

    @Query("select count(*) from ${FileLink file} where ${file.name} like ${query} or ${file.module} like ${query} or ${file.slug} like ${query}")
    Long count(String query)

    @Query("select count(*) from ${FileLink file} where (${file.name} like ${query} or ${file.module} like ${query} or ${file.slug} like ${query}) and ${file.module} in ${modules}")
    Long count(String query,ArrayList modules)

    @Query("select count(*) from ${FileLink file} where (${file.name} like ${query} or ${file.module} like ${query} or ${file.slug} like ${query}) and ${file.module}=${module}")
    Long count(String query,String module)

    List<FileLink> list(Map args)

    @Query("from ${FileLink file} where ${file.module} in ${modules}")
    List<FileLink> list(ArrayList modules,Map args)

    @Query("from ${FileLink file} where ${file.name} like ${query} or ${file.module} like ${query} or ${file.slug} like ${query}")
    List<FileLink> list(String query,Map args)

    @Query("from ${FileLink file} where (${file.name} like ${query} or ${file.module} like ${query} or ${file.slug} like ${query}) and ${file.module}=${module}")
    List<FileLink> list(String query,String module,Map args)

    @Query("from ${FileLink file} where (${file.name} like ${query} or ${file.module} like ${query} or ${file.slug} like ${query}) and ${file.module} in ${modules}")
    List<FileLink> list(String query,ArrayList modules,Map args)


    void delete(Serializable id)

    FileLink save(FileLink fileLink)

}
