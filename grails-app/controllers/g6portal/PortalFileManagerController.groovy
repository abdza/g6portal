package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import java.util.zip.*

class PortalFileManagerController {

    PortalPageService portalPageService
    PortalService portalService
    PortalFileManagerService portalFileManagerService
    UserService userService
    def sessionFactory
    def mailService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
      def dparam = [max:params.max?:10,offset:params.offset?:0]
      params.max = dparam.max
      if(params.q) {
        def query = '%' + params.q + '%'
        if(session.enablesuperuser) {
            def thelist = portalFileManagerService.list(query,dparam)
            respond thelist, model:[portalFileManagerCount: portalFileManagerService.count(query), params:params]
        }
        else {
            def thelist = portalFileManagerService.list(query,session.adminmodules,dparam)
            respond thelist, model:[portalFileManagerCount: portalFileManagerService.count(query,session.adminmodules), params:params]
        }
      }
      else {
        if(session.enablesuperuser) {
            def thelist = portalFileManagerService.list(dparam)
            respond thelist, model:[portalFileManagerCount: portalFileManagerService.count(), params:params]
        }
        else {
            def thelist = portalFileManagerService.list(session.adminmodules,dparam)
            respond thelist, model:[portalFileManagerCount: portalFileManagerService.count(session.adminmodules), params:params]
        }
      }
    }

    def show(Long id) {
        respond portalFileManagerService.get(id)
    }
    
    def createfolder = {
        //PortalTracker.decodeparams(params) 
        def wantedfile = null
        def filemanager = PortalFileManager.get(params.id)
        params.fname = params.fname?.replace("\\","/")
        if(filemanager){
            try {
                def folderbase = filemanager.path
                if(params.mdfname){
                    def newname = folderbase + "/" + params.mdfname + "/" + params.foldername
                    def newfolder = new File(newname).mkdirs()
                    params.fname = params.mdfname
                    params.remove('mdfname')
                }
                else {
                    def newname = folderbase + "/" + params.foldername
                    def newfolder = new File(newname).mkdirs()
                    params.fname = params.mdfname
                    params.remove('mdfname')
                }
            }
            catch(Exception e) {
                // def curuser = User.get(session.userid)
                PortalErrorLog.record(params,session.curuser,controllerName,actionName,e.toString(),filemanager?.name,filemanager?.module)
            }
        }
        redirect(action:'explorepage',params:params)
    }

    def rename = {
        //PortalTracker.decodeparams(params) 
        def filemanager = PortalFileManager.get(params.id)
        try {
            def wantedfile = null
            params.fname = params.fname?.replace("\\","/")
            if(filemanager){
                def folderbase = filemanager.path
                if(params.fname){
                    def curfull = folderbase + params.fname
                    if(new File(curfull).exists()){
                        wantedfile = curfull
                    }
                }
                def fbobj = new File(folderbase)
                if(wantedfile){            
                    def thefile = new File(wantedfile)
                    if(thefile.exists()){
                        try{
                            if(params.newname) {
                                def tokens = params.fname.tokenize("/")
                                def dirfname = tokens.take(tokens.size()-1).join("/")
                                def newname = folderbase + "/" + dirfname + "/" + params.newname
                                thefile.renameTo(newname)
                                params.fname = dirfname
                                params.remove('newname')
                                redirect(action:'explorepage',params:params)
                                return
                            }
                        }
                        catch(Exception exp) {
                            // def curuser = User.get(session.userid)
                            println "Error renaming file: " + exp
                            PortalErrorLog.record(params,session.curuser,controllerName,actionName,exp.toString(),filemanager?.name,filemanager?.module)
                        }
                    }
                }
            }
            def tokens = params.fname.tokenize("/")
            def filename = ""
            if(params.newname) {
                filename = params.newname
            }
            else {
                if(tokens.size()) {
                    filename = tokens[-1]
                }
            }
            respond filemanager, model:[filename:filename]
        }
        catch(Exception e) {
            // def curuser = User.get(session.userid)
            println "Error renaming :" + e
            PortalErrorLog.record(params,session.curuser,controllerName,actionName,e.toString(),filemanager?.name,filemanager?.module)
        }
    }

    def deletefm = {
        //PortalTracker.decodeparams(params) 
        def wantedfile = null
        def filemanager = PortalFileManager.get(params.id)
        if(filemanager){
            def folderbase = filemanager.path
            if(params.fname){
                def curfull = folderbase + '/' + params.fname
                def thefile = new File(curfull)
                if(thefile.exists()){
                    try{
                      if(thefile.isFile()) {
                        thefile.delete()
                      }
                      else {
                        thefile.deleteDir()
                      }
                    }
                    catch(Exception exp) {
                      println "Error deleting file: " + exp
                    }
                }
            }
        }
        params.fname = params.fname?.replace("\\","/")
        def tokens = params.fname?.tokenize("/")
        params.fname = "/" + tokens.take(tokens.size()-1).join("/")
        def tparams = PortalTracker.encodeparams(params)
        redirect(action:'explorepage',id:params.id,params:tparams)
    }

    def upload = {
        //PortalTracker.decodeparams(params) 
        def filemanager = PortalFileManager.get(params.id)
        if(filemanager){
            def folderbase = filemanager.path
            if(params.fname){
                folderbase = folderbase + '/' + params.fname
            }

            params.each { pkey,pval ->
                if(pkey.size()>4) {
                    def ckey = pkey[0..4]
                    if(ckey=='file_') {
                        def f = request.getFile(pkey)
                        if(f) {
                            if (f?.empty) {
                                flash.message = 'file cannot be empty'
                                render(view: 'explore')
                                return
                            }
                            // Validate file security before processing
                            def filemanagermax = PortalSetting.namedefault('filemanager_max_' + session.curuser?.staffID,50000)
                            def validationResult = FileSecurityValidator.validateFile(f,null,filemanagermax)
                            if (!validationResult.valid) {
                                println "FileManager upload error:" + validationResult.errors.join(', ')
                                PortalErrorLog.record(params,session.curuser,controllerName,actionName,validationResult.errors.join(', '),filemanager?.name,filemanager?.module)
                                flash.message = "File upload failed: ${validationResult.errors.join(', ')}"
                                render(view: 'explore')
                                return
                            }
                            def fileName = f?.originalFilename
                            if(!(new File(folderbase).exists())){
                                new File(folderbase).mkdirs()
                            }
                            if(new File(folderbase).exists()){
                                def copytarget = folderbase+'/'+fileName
                                f.transferTo(new File(copytarget))
                                if(copytarget[-4..-1]=='.zip') {
                                   if(params.unzip) {
                                      byte[] buffer = new byte[1024];
                                      ZipInputStream zis = new ZipInputStream(new FileInputStream(copytarget));
                                      ZipEntry zipEntry = zis.getNextEntry();
                                      def destfolder = ''
                                      while (zipEntry != null) {
                                          def zipstr = zipEntry.toString().replace('\\','/')
                                          if(zipEntry.toString()[0] != '/' && zipEntry.toString()[0] != '\\') {
                                              destfolder = folderbase + '/'
                                          }
                                          File newFile = new File(destfolder + zipstr);
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
                                   }
                                }
                            } 
                        }
                    }
                }
            }
        }
        redirect(action:'explorepage',id:params.id,params:PortalTracker.encodeparams(params))
    }

    def download = {
        //PortalTracker.decodeparams(params) 
        def wantedfile = null
        def filemanager = PortalFileManager.get(params.id)
        if(filemanager){
            def folderbase = filemanager.path
            if(params.fname){
                def curfull = folderbase + '/' + params.fname
                if(new File(curfull).isFile()){
                    wantedfile = curfull
                }
            }
            def fbobj = new File(folderbase)
            if(wantedfile){            
                def thefile = new File(wantedfile)
                if(thefile.exists()){
                    try{
                        response.setContentType("application/octet-stream")
                        response.setHeader("Content-disposition", "attachment;filename=${thefile.getName().replace(' ','_')}")
                        def bis = null
                        try{
                            bis = thefile.newInputStream()
                            response.outputStream << bis
                        }
                        finally {
                            if(bis){
                                bis.close()
                            }
                        }
                        return
                    }
                    catch(Exception ex){
                        println "Got error download file " + thefile + ":" + ex
                    }
                }
                else{
                    flash.message = "Sorry but the file no longer exists on the server"
                    redirect(action:'explore')
                }
            }
        }
    }

    def explorepage(Long id) {
        //PortalTracker.decodeparams(params) 
        def filelist = []
        def filemanager = PortalFileManager.get(id)
        if(filemanager){
            def folderbase = filemanager.path
            if(params.fname){
                def curfull = folderbase + '/' + params.fname
                if(new File(curfull).isFile()){
                    redirect(action:'download',params:[id:filemanager.id,fname:params.fname])
                }
                else {
                    folderbase = curfull
                }
            }
            else {
                params.fname = '/'
            }
            def fbobj = new File(folderbase)
            if(fbobj.exists()){
                fbobj.eachFile { curfile->
                    filelist << curfile
                }
            }
        }
        respond filemanager, model:[filelist:filelist]
    }

    def explore(Long id) {
        //PortalTracker.decodeparams(params) 
        def filelist = []
        def filemanager = PortalFileManager.get(id)
        if(filemanager){
            def folderbase = filemanager.path
            if(params.fname){
                def curfull = folderbase + '/' + params.fname
                if(new File(curfull).isFile()){
                    redirect(action:'download',params:[id:filemanager.id,fname:params.fname])
                }
                else {
                    folderbase = curfull
                }
            }
            else {
                params.fname = '/'
            }
            def fbobj = new File(folderbase)
            if(fbobj.exists()){
                fbobj.eachFile { curfile->
                    filelist << curfile
                }
            }
        }
        respond filemanager, model:[filelist:filelist]
    }

    def create() {
        respond new PortalFileManager(params)
    }

    def save(PortalFileManager portalFileManager) {
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
            if (portalFileManager == null) {
                notFound()
                return
            }

            try {
                portalFileManagerService.save(portalFileManager)
            } catch (ValidationException e) {
                respond portalFileManager.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'portalFileManager.label', default: 'PortalFileManager'), portalFileManager.id])
                    redirect portalFileManager
                }
                '*' { respond portalFileManager, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond portalFileManagerService.get(id)
    }

    def update(PortalFileManager portalFileManager) {
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
            if (portalFileManager == null) {
                notFound()
                return
            }

            try {
                portalFileManagerService.save(portalFileManager)
            } catch (ValidationException e) {
                respond portalFileManager.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'portalFileManager.label', default: 'PortalFileManager'), portalFileManager.id])
                    redirect portalFileManager
                }
                '*'{ respond portalFileManager, [status: OK] }
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

            portalFileManagerService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalFileManager.label', default: 'PortalFileManager'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalFileManager.label', default: 'PortalFileManager'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
