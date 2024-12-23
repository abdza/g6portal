package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import java.util.zip.*

class PortalModuleController {

  PortalModuleService portalModuleService
  PortalService portalService

  static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

  def updatelist() {
    def curuser = null
    if(session.curuser) {
        // curuser = User.get(session.userid)
        curuser = session.curuser
    }
    portalService.update_module_list(params,curuser)
    redirect action:"index", method:"GET"
  }

  def index(Integer max) {
      def curuser = null
      if(session.curuser) {
          // curuser = User.get(session.userid)
          curuser = session.curuser
      }
      def dparam = [max:params.max?:10,offset:params.offset?:0]
      params.max = dparam.max
      if(params.q) {
          def query = '%' + params.q + '%'
          if(curuser?.isAdmin) {
              def thelist = portalModuleService.list(query,dparam)
              respond thelist, model:[curuser:curuser, portalModuleCount: portalModuleService.count(query), params:params]
          }
          else{
              def thelist = portalModuleService.list(query,session.adminmodules,dparam)
              respond thelist, model:[curuser:curuser, portalModuleCount: portalModuleService.count(query,session.adminmodules), params:params]
          }
      }
      else {
          if(curuser?.isAdmin) {
              def thelist = portalModuleService.list(dparam)
              respond thelist, model:[curuser:curuser, portalModuleCount: portalModuleService.count(), params:params]
          }
          else {
              def thelist = portalModuleService.list(session.adminmodules,dparam)
              respond thelist, model:[curuser:curuser, portalModuleCount: portalModuleService.count(session.adminmodules), params:params]
          }
      }
  }

  def show(Long id) {
    def curuser = null
    if(session.curuser) {
        // curuser = User.get(session.userid)
        curuser = session.curuser
    }
    def module = portalModuleService.get(id)
    def admins = UserRole.findAllByModuleAndRole(module.name,'Admin')
    def developers = UserRole.findAllByModuleAndRole(module.name,'Developer')
    def pages = PortalPage.findAllByModule(module.name,[sort:'slug'])
    def trackers = PortalTracker.findAllByModule(module.name,[sort:'slug'])
    def settings = PortalSetting.findAllByModule(module.name,[sort:'name'])
    def roles = UserRole.findAllByModule(module.name)
    respond module,model:[curuser:curuser,admins:admins,developers:developers,pages:pages,trackers:trackers,settings:settings,roles:roles]
  }

  def create() {
    def curuser = null
    if(session.curuser) {
        // curuser = User.get(session.userid)
        curuser = session.curuser
    }
    respond new PortalModule(params), model:[curuser:curuser]
  }

  def save(PortalModule portalModule) {
    def abandon = false
    withForm {
    }.invalidToken {
        flash.message = "Invalid session for the forms"
        redirect(controller:'portalPage',action:'index')
        abandon = true
    }
    if(abandon) {
        return true
    }
    else {
        if (portalModule == null) {
          notFound()
            return
        }

        try {
          portalModuleService.save(portalModule)
        } catch (ValidationException e) {
          respond portalModule.errors, view:'create'
            return
        }

        request.withFormat {
          form multipartForm {
            flash.message = message(code: 'default.created.message', args: [message(code: 'portalModule.label', default: 'PortalModule'), portalModule.id])
              redirect portalModule
          }
          '*' { respond portalModule, [status: CREATED] }
        }
    }
  }

  def edit(Long id) {
    def curuser = null
    if(session.curuser) {
        // curuser = User.get(session.userid)
        curuser = session.curuser
    }
    respond portalModuleService.get(id), model:[curuser:curuser]
  }

  def update(PortalModule portalModule) {
    def abandon = false
    withForm {
    }.invalidToken {
        flash.message = "Invalid session for the forms"
        redirect(controller:'portalPage',action:'index')
        abandon = true
    }
    if(abandon) {
        return true
    }
    else {
        if (portalModule == null) {
          notFound()
            return
        }

        try {
          portalModuleService.save(portalModule)
        } catch (ValidationException e) {
          respond portalModule.errors, view:'edit'
            return
        }

        request.withFormat {
          form multipartForm {
            flash.message = message(code: 'default.updated.message', args: [message(code: 'portalModule.label', default: 'PortalModule'), portalModule.id])
              redirect portalModule
          }
          '*'{ respond portalModule, [status: OK] }
        }
    }
  }

  def importform() {
      if(request.method == 'POST') {
          try {
              // def curuser = User.get(session.userid)
              def curuser = session.curuser
              def f = request.getFile('fileupload')
              if (!f.empty) {
                  def fileName = f.originalFilename
                  def curfolder = System.getProperty("user.dir")
                  def migrationfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration')
                  def modulename = fileName[0..-5]
                  println "Importing from " + fileName + " for module " + modulename
                  def destfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + modulename
                  println "Dest folder:" + destfolder
                  def existingmodule = PortalModule.findByName(modulename)
                  def doimport = true
                  if(existingmodule) {
                      if(!('Admin' in curuser.modulerole(modulename))) {
                          doimport = false
                          flash.message = 'You are not authorized to update that module'
                      }
                  }
                  if(doimport) {
                      if(!(new File(migrationfolder).exists())){
                          new File(migrationfolder).mkdirs()
                      }
                      try {
                          def df = new File(destfolder)
                          if(df.exists()) {
                              def deleteold = df.deleteDir()
                              println "Results of deleting old folder: " + deleteold
                          }
                      }
                      catch(Exception exp) {
                          println "Error deleting old folder: " + exp
                      }
                      if(new File(migrationfolder).exists()){
                          def copytarget = migrationfolder+'/'+fileName
                          println "Copy target:" + copytarget
                          f.transferTo(new File(copytarget))
                          byte[] buffer = new byte[1024];
                          ZipInputStream zis = new ZipInputStream(new FileInputStream(copytarget));
                          ZipEntry zipEntry = zis.getNextEntry();
                          while (zipEntry != null) {
                              def zipstr = zipEntry.toString().replace('\\','/')
                              if(zipEntry.toString()[0] != '/' && zipEntry.toString()[0] != '\\') {
                                  destfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/modulemigration') + '/' + modulename + '/'
                              }
                              println "File destfolder:" + destfolder
                              File newFile = new File(destfolder + zipstr);
                              println "New File:" + newFile
                              if (zipEntry.isDirectory()) {
                                  if (!newFile.isDirectory() && !newFile.mkdirs()) {
                                      throw new IOException("Failed to create directory " + newFile);
                                  }
                              } else {
                                  // fix for Windows-created archives
                                  File parent = newFile.getParentFile();
                                  if (!parent.isDirectory() && !parent.mkdirs()) {
                                      throw new IOException("Failed to create directory " + parent);
                                  }

                                  // write file content
                                  FileOutputStream fos = new FileOutputStream(newFile);
                                  int len;
                                  while ((len = zis.read(buffer)) > 0) {
                                      fos.write(buffer, 0, len);
                                  }
                                  fos.close();
                              }
                              zipEntry = zis.getNextEntry();
                          }
                          zis.closeEntry();
                          zis.close();
                          PortalModule.withTransaction { sqltrans-> 
                              def module = PortalModule.findByName(modulename)
                              if(!module) {
                                  module = new PortalModule()
                                  module.name = modulename
                                  module.save(flush:true)
                              }
                              portalService.import_module(module.id,true,true)
                              flash.message = "Module imported"
                              redirect action:"show", method:"GET", id:module.id
                          }
                          return
                      } 
                  }
              }
          } catch (ValidationException e) {
              println "Got error importing module. " + e
              return
          }
      }
  }

  def delete(Long id) {
    def abandon = false
    withForm {
    }.invalidToken {
        flash.message = "Invalid session for the forms"
        redirect(controller:'portalPage',action:'index')
        abandon = true
    }
    if(abandon) {
        return true
    }
    else {
        if (id == null) {
          notFound()
            return
        }

        def file_on = false
        def user_on = false
        if(params.files) {
            file_on = true
        }
        if(params.user) {
            user_on = true
        }
        if(params.op=="Delete") {
          portalModuleService.delete(id)
            flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalModule.label', default: 'PortalModule'), id])
        }
        else if(params.op=="Export") {
            flash.message = "Module exported"
            def module = portalModuleService.get(id)
            if(module) {
                portalService.export_module(id,file_on,user_on)
                def curfolder = System.getProperty("user.dir")
                def migrationfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + module.name
                migrationfolder = migrationfolder.replaceAll('//','/')
                def thefile = new File(migrationfolder + '.zip')
                def zipfile = portalService.compress(new File(migrationfolder),thefile)
                try{
                    response.setContentType("application/octet-stream")
                    response.setHeader("Content-disposition", "attachment;filename=${zipfile.getName().replace(' ','_')}")
                    def bis = null
                    try{
                        bis = zipfile.newInputStream()
                        response.outputStream << bis
                    }
                    finally {
                        bis.close()
                        response.outputStream.flush()
                    }
                    return
                }
                catch(Exception ex){
                    println "Got error download file " + thefile + ":" + ex
                }
            }
        }
        else if(params.op=="Import") {
            flash.message = "Module imported"
            def module = portalModuleService.get(id)
            if(module) {
              portalService.import_module(id,file_on,user_on)
            }
        }

        request.withFormat {
          form multipartForm {
            redirect action:"index", method:"GET"
          }
          '*'{ render status: NO_CONTENT }
        }
    }
  }

  protected void notFound() {
    request.withFormat {
      form multipartForm {
        flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalModule.label', default: 'PortalModule'), params.id])
          redirect action: "index", method: "GET"
      }
      '*'{ render status: NOT_FOUND }
    }
  }
}
