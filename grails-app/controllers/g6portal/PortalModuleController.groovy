package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import java.util.zip.*

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.*
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.streaming.SXSSFSheet

class PortalModuleController {

  PortalModuleService portalModuleService
  PortalService portalService

  static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE", importUserRoles: "POST", importSettings: "POST", deleteAllUserRoles: "POST", deleteAllSettings: "POST", confirmimport: "POST"]

  def exportUserRoles(Long id) {
      def module = portalModuleService.get(id)
      if (!module) {
          notFound()
          return
      }

      def roles = UserRole.findAllByModule(module.name)

      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setHeader("Content-disposition", "attachment;filename=user_roles_${module.name}.xlsx")

      def wb = new SXSSFWorkbook(100)
      def sheet = wb.createSheet("User Roles")

      def headerRow = sheet.createRow(0)
      headerRow.createCell(0).setCellValue("Staff ID")
      headerRow.createCell(1).setCellValue("Name")
      headerRow.createCell(2).setCellValue("Role")

      roles.eachWithIndex { ur, i ->
          def row = sheet.createRow(i + 1)
          row.createCell(0).setCellValue(ur.user.staffID)
          row.createCell(1).setCellValue(ur.user.name)
          row.createCell(2).setCellValue(ur.role)
      }

      wb.write(response.outputStream)
      wb.dispose()
      response.outputStream.flush()
      return
  }

  def importUserRoles(Long id) {
      def module = portalModuleService.get(id)
      if (!module) {
          notFound()
          return
      }

      def f = request.getFile('userRoleFile')
      if (f.empty) {
          flash.message = "Please select a file to import"
          redirect action: "show", id: id
          return
      }

      int successCount = 0
      int errorCount = 0
      List<String> errors = []

      try {
          def workbook = new XSSFWorkbook(f.inputStream)
          def sheet = workbook.getSheetAt(0)
          def dataFormatter = new DataFormatter()

          UserRole.withTransaction { status ->
              sheet.eachWithIndex { row, i ->
                  if (i == 0) return // Skip header

                  def staffID = dataFormatter.formatCellValue(row.getCell(0))?.trim()
                  def roleName = dataFormatter.formatCellValue(row.getCell(2))?.trim()

                  if (staffID && roleName) {
                      def user = User.findByStaffID(staffID)
                      if (user) {
                          def userRole = UserRole.findByUserAndModuleAndRole(user, module.name, roleName)
                          if (!userRole) {
                              userRole = new UserRole(user: user, module: module.name, role: roleName)
                              if (userRole.save(flush: true)) {
                                  successCount++
                              } else {
                                  errorCount++
                                  errors << "Row ${i+1}: Failed to save role ${roleName} for ${staffID}"
                              }
                          } else {
                              successCount++
                          }
                      } else {
                          errorCount++
                          errors << "Row ${i+1}: User with Staff ID ${staffID} not found"
                      }
                  }
              }
          }
          workbook.close()
      } catch (Exception e) {
          log.error("Error importing user roles", e)
          flash.message = "Error parsing Excel file: ${e.message}"
          redirect action: "show", id: id
          return
      }

      flash.message = "Import completed. Success: ${successCount}, Errors: ${errorCount}"
      if (errors) {
          flash.errors = errors
      }
      redirect action: "show", id: id
  }

  def exportSettings(Long id) {
      def module = portalModuleService.get(id)
      if (!module) {
          notFound()
          return
      }

      def settings = PortalSetting.findAllByModule(module.name, [sort: 'name'])

      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      response.setHeader("Content-disposition", "attachment;filename=settings_${module.name}.xlsx")

      def wb = new SXSSFWorkbook(100)
      def sheet = wb.createSheet("Settings")

      def headerRow = sheet.createRow(0)
      headerRow.createCell(0).setCellValue("Name")
      headerRow.createCell(1).setCellValue("Type")
      headerRow.createCell(2).setCellValue("Datum Type")
      headerRow.createCell(3).setCellValue("Text")
      headerRow.createCell(4).setCellValue("Date Value")
      headerRow.createCell(5).setCellValue("Number")

      settings.eachWithIndex { setting, i ->
          def row = sheet.createRow(i + 1)
          row.createCell(0).setCellValue(setting.name ?: '')
          row.createCell(1).setCellValue(setting.type ?: '')
          row.createCell(2).setCellValue(setting.datum_type ?: '')
          row.createCell(3).setCellValue(setting.text ?: '')
          row.createCell(4).setCellValue(setting.date_value ? setting.date_value.format('yyyy-MM-dd') : '')
          row.createCell(5).setCellValue(setting.number != null ? setting.number.toString() : '')
      }

      wb.write(response.outputStream)
      wb.dispose()
      response.outputStream.flush()
      return
  }

  def importSettings(Long id) {
      def module = portalModuleService.get(id)
      if (!module) {
          notFound()
          return
      }

      def f = request.getFile('settingsFile')
      if (f.empty) {
          flash.message = "Please select a file to import"
          redirect action: "show", id: id
          return
      }

      int successCount = 0
      int errorCount = 0
      List<String> errors = []

      try {
          def workbook = new XSSFWorkbook(f.inputStream)
          def sheet = workbook.getSheetAt(0)
          def dataFormatter = new DataFormatter()

          PortalSetting.withTransaction { status ->
              sheet.eachWithIndex { row, i ->
                  if (i == 0) return // Skip header

                  def name       = dataFormatter.formatCellValue(row.getCell(0))?.trim()
                  def type       = dataFormatter.formatCellValue(row.getCell(1))?.trim() ?: null
                  def datumType  = dataFormatter.formatCellValue(row.getCell(2))?.trim() ?: null
                  def text       = dataFormatter.formatCellValue(row.getCell(3))?.trim() ?: null
                  def dateStr    = dataFormatter.formatCellValue(row.getCell(4))?.trim() ?: null
                  def numberStr  = dataFormatter.formatCellValue(row.getCell(5))?.trim() ?: null

                  if (!name) return

                  def setting = PortalSetting.findByModuleAndName(module.name, name)
                  if (!setting) {
                      setting = new PortalSetting(module: module.name, name: name)
                  }

                  setting.type       = type
                  setting.datum_type = datumType
                  setting.text       = text
                  setting.number     = numberStr ? numberStr.toInteger() : null
                  if (dateStr) {
                      try { setting.date_value = Date.parse('yyyy-MM-dd', dateStr) } catch (e) { setting.date_value = null }
                  } else {
                      setting.date_value = null
                  }

                  if (setting.save(flush: true)) {
                      successCount++
                  } else {
                      errorCount++
                      errors << "Row ${i+1}: Failed to save setting '${name}': ${setting.errors}"
                  }
              }
          }
          workbook.close()
      } catch (Exception e) {
          log.error("Error importing settings", e)
          flash.message = "Error parsing Excel file: ${e.message}"
          redirect action: "show", id: id
          return
      }

      flash.message = "Settings import completed. Success: ${successCount}, Errors: ${errorCount}"
      if (errors) {
          flash.errors = errors
      }
      redirect action: "show", id: id
  }

  def deleteAllSettings(Long id) {
      def module = portalModuleService.get(id)
      if (!module) {
          notFound()
          return
      }
      def deleted = 0
      PortalSetting.withTransaction {
          def settings = PortalSetting.findAllByModule(module.name)
          deleted = settings.size()
          settings.each { it.delete(flush: true) }
      }
      flash.message = "Deleted ${deleted} setting(s) for module '${module.name}'"
      redirect action: "show", id: id
  }

  def deleteAllUserRoles(Long id) {
      def module = portalModuleService.get(id)
      if (!module) {
          notFound()
          return
      }
      def deleted = 0
      UserRole.withTransaction {
          def roles = UserRole.findAllByModuleAndRoleNotEqual(module.name, 'Developer')
          deleted = roles.size()
          roles.each { it.delete(flush: true) }
      }
      flash.message = "Deleted ${deleted} user role(s) for module '${module.name}'"
      redirect action: "show", id: id
  }

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

    // Sort roles by user name
    def roles = UserRole.executeQuery("select ur from UserRole ur join ur.user u where ur.module = :moduleName order by u.name asc", [moduleName: module.name])
    def admins = roles.findAll { it.role == 'Admin' }
    def developers = roles.findAll { it.role == 'Developer' }

    def pages = PortalPage.findAllByModule(module.name,[sort:'slug'])
    def trackers = PortalTracker.findAllByModule(module.name,[sort:'slug'])
    def settings = PortalSetting.findAllByModule(module.name,[sort:'name'])
    def importlogs = PortalModuleImportLog.findAllByModule(module.name,[sort:'dateCreated',order:'desc',max:20])

    respond module,model:[curuser:curuser,admins:admins,developers:developers,pages:pages,trackers:trackers,settings:settings,roles:roles,importlogs:importlogs]
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
              println "Updated import module"
              // def curuser = User.get(session.userid)
              def curuser = session.curuser
              def f = request.getFile('fileupload')
              if (!f.empty) {
                  def fileName = f.originalFilename
                  
                  // Enhanced security validation for file imports
                  if(!fileName.toLowerCase().endsWith('.zip')) {
                      flash.error = 'Only ZIP files are allowed for module imports'
                      return
                  }
                  
                  // Check file size limit (50MB for module imports)
                  def maxFileSize = 50 * 1024 * 1024 // 50MB
                  if(f.size > maxFileSize) {
                      flash.error = 'File too large. Maximum size is 50MB.'
                      return
                  }
                  
                  // Sanitize filename to prevent directory traversal
                  def sanitizedFileName = fileName.replaceAll(/[\/\\]/, '_')
                                                 .replaceAll(/[^a-zA-Z0-9.\_-]/, '_')
                  
                  def curfolder = System.getProperty("user.dir")
                  def migrationfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration')
                  def modulename = sanitizedFileName[0..-5] // Remove .zip extension
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
                  println "Before doing import"
                  if(doimport) {
                      println "Doing import"
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
                                  destfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + modulename + '/'
                              }
                              println "File destfolder:" + destfolder
                              def destfile = new File(destfolder)
                              if(!destfile.isDirectory() && !destfile.mkdirs()) {
                                  throw new IOException("Failed to create directory " + destfile);
                              }
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
                          println "Done extract zip file"
                          def module = null
                          PortalModule.withTransaction { sqltrans->
                              module = PortalModule.findByName(modulename)
                              if(!module) {
                                  module = new PortalModule()
                                  module.name = modulename
                                  module.save(flush:true)
                              }
                          }
                          // review the changes before applying the import
                          redirect action:"importpreview", id:module.id, params:[files:'on', staff:'on']
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

  private String modulemigrationfolder(module) {
      def curfolder = System.getProperty("user.dir")
      return PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + module.name
  }

  // Files and staff roles are only present in the migration files when the
  // exporter ticked them. On production, exporting every uploaded file for
  // the comparison is slow and the staff list will have diverged, so only
  // include them in the temporary export if the import package has them.
  private boolean migrationHasFiles(module) {
      return new File(modulemigrationfolder(module) + '/filelinklist.json').exists()
  }

  private boolean migrationHasStaff(module) {
      return new File(modulemigrationfolder(module) + '/userrolelist.json').exists()
  }

  // Export the module's current state to a temp folder and diff it against
  // the migration files about to be imported
  private String generateimportdiff(module, file_on, staff_on) {
      def migrationfolder = modulemigrationfolder(module)
      def tmpdir = File.createTempDir('g6export_', '_' + module.name)
      try {
          portalService.export_module(module.id, file_on, staff_on, tmpdir.path)
          return PortalService.diff_module_folders(tmpdir, new File(migrationfolder))
      } finally {
          tmpdir.deleteDir()
      }
  }

  def importpreview(Long id) {
      def curuser = session.curuser
      def module = portalModuleService.get(id)
      if (module == null) {
          notFound()
          return
      }
      if(!(new File(modulemigrationfolder(module)).exists())) {
          flash.message = "No migration files found for module ${module.name}"
          redirect action:"show", method:"GET", id:id
          return
      }
      def file_on = (params.files ? true : false) && migrationHasFiles(module)
      def staff_on = (params.staff ? true : false) && migrationHasStaff(module)
      def difftext = ''
      try {
          difftext = generateimportdiff(module, file_on, staff_on)
      } catch(Exception e) {
          println "Error generating import diff: " + e
          flash.message = "Error generating import diff: " + e.message
          redirect action:"show", method:"GET", id:id
          return
      }
      respond module, view:'importpreview', model:[curuser:curuser, diff:difftext, file_on:file_on, staff_on:staff_on]
  }

  def confirmimport(Long id) {
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
      if(request.method != 'POST') {
          redirect action:"show", method:"GET", id:id
          return
      }
      def curuser = session.curuser
      def module = portalModuleService.get(id)
      if (module == null) {
          notFound()
          return
      }
      def file_on = (params.files ? true : false) && migrationHasFiles(module)
      def staff_on = (params.staff ? true : false) && migrationHasStaff(module)
      try {
          // regenerate the diff at import time so the log records exactly
          // what changed even if the module was modified after the preview
          def difftext = generateimportdiff(module, file_on, staff_on)
          portalService.import_module(id, file_on, staff_on)
          PortalModuleImportLog.withTransaction {
              new PortalModuleImportLog(module:module.name, staffid:curuser?.staffID,
                  staffname:curuser?.name, remarks:params.remarks, diff:difftext).save(flush:true)
          }
          flash.message = "Module imported"
      } catch(Exception e) {
          println "Error importing module: " + e
          e.printStackTrace()
          flash.message = "Error importing module: " + e.message
      }
      redirect action:"show", method:"GET", id:id
  }

  def importlog(Long id) {
      def curuser = session.curuser
      def importlog = PortalModuleImportLog.get(id)
      if (importlog == null) {
          notFound()
          return
      }
      def module = PortalModule.findByName(importlog.module)
      respond importlog, view:'importlog', model:[curuser:curuser, importlog:importlog, module:module]
  }

  // List import logs, across all modules or filtered to one (?module=name)
  def importlogs(Integer max) {
      def curuser = session.curuser
      params.max = Math.min(max ?: 25, 100)
      if(!params.sort) {
          params.sort = 'dateCreated'
          params.order = 'desc'
      }
      def logs
      def logcount
      if(params.module) {
          logs = PortalModuleImportLog.findAllByModule(params.module, params)
          logcount = PortalModuleImportLog.countByModule(params.module)
      } else {
          logs = PortalModuleImportLog.list(params)
          logcount = PortalModuleImportLog.count()
      }
      def module = params.module ? PortalModule.findByName(params.module) : null
      render view:'importlogs', model:[curuser:curuser, importlogs:logs, importlogCount:logcount, module:module]
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
            // show a diff of the incoming changes for review before importing
            redirect action:"importpreview", id:id, params:[files:params.files, staff:params.user]
            return
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
