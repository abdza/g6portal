package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import groovy.sql.Sql
import groovy.text.Template
import org.springframework.transaction.annotation.Transactional
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import groovy.json.*

class PortalTrackerController {

    PortalTrackerService portalTrackerService

    PortalService portalService

    def mailService

    def groovyPagesTemplateEngine

    def sessionFactory

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    // Background Excel export jobs: token → [done, file, error, filename]
    static java.util.concurrent.ConcurrentHashMap excelJobs = new java.util.concurrent.ConcurrentHashMap()

    def index(Integer max) {
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All')) {
            def query = '%' + params.q + '%'
            if(params.module && params.module!='All') {
                def thelist = portalTrackerService.list(query,params.module,dparam)
                respond thelist, model:[portalTrackerCount: portalTrackerService.count(query,params.module), params:params]
            }
            else { 
                if(session.enablesuperuser) {
                    def thelist = portalTrackerService.list(query,dparam)
                    respond thelist, model:[portalTrackerCount: portalTrackerService.count(query), params:params]
                }
                else {
                    def thelist = portalTrackerService.list(query,session.developermodules,dparam)
                    respond thelist, model:[portalTrackerCount: portalTrackerService.count(query,session.developermodules), params:params]
                }
            }
        }
        else {
            if(session.enablesuperuser) {
                def thelist = portalTrackerService.list(dparam)
                respond thelist, model:[portalTrackerCount: portalTrackerService.count(), params:params]
            }
            else {
                def thelist = portalTrackerService.list(session.developermodules,dparam)
                respond thelist, model:[portalTrackerCount: portalTrackerService.count(session.developermodules), params:params]
            }
        }
    }

    def data_dump() {
        portalService.perform_data_dump()
        flash.message = "Data dump running in the background"
        redirect action:"index", method:"GET"
    }

    @Transactional
    def create_default_pages(Long id) {
        def tracker = portalTrackerService.get(id)
        def status = null
        def tt = null
        if(!tracker && params.status_id) {
            status = PortalTrackerStatus.get(params.status_id)
            if(status) {
                tracker = status.tracker
            }
        }
        if(!tracker && params.transition_id) {
            tt = PortalTrackerTransition.get(params.transition_id)
            if(tt) {
                tracker = tt.tracker
            }
        }
        if(tracker) {
            PortalTracker.withTransaction { transaction ->
                if(!params.category || params.category=='list') {
                    def listpage = PortalPage.findByModuleAndSlug('portal_migration','tracker_default_list')
                    if(listpage) {
                        def newpage = new PortalPage()
                        newpage.title = tracker.name + " List"
                        newpage.module = tracker.module
                        newpage.slug = tracker.slug + "_list"
                        newpage.content = listpage.content
                        newpage.save(flush:true)
                    }
                }
                if(!params.category || params.category=='display') {
                    def showpage = PortalPage.findByModuleAndSlug('portal_migration','tracker_default_display')
                    if(showpage) {
                        if(params.status_id) {
                            if(!status) {
                                status = PortalTrackerStatus.get(params.status_id)
                            }
                            if(status) {
                                def newpage = new PortalPage()
                                newpage.title = tracker.name + " Details"
                                newpage.module = tracker.module
                                newpage.slug = tracker.slug + "_show_" + status.name.replaceAll(' ','_').toLowerCase()
                                newpage.content = showpage.content
                                newpage.save(flush:true)
                            }
                        }
                        else {
                            def newpage = new PortalPage()
                            newpage.title = tracker.name + " Details"
                            newpage.module = tracker.module
                            newpage.slug = tracker.slug + "_show_default"
                            newpage.content = showpage.content
                            newpage.save(flush:true)
                        }
                    }
                }
                if(!params.category || params.category=='form') {
                    def formpage = PortalPage.findByModuleAndSlug('portal_migration','tracker_default_form')
                    if(formpage) {
                        if(params.transition_id) {
                            if(!tt) {
                                tt = PortalTrackerTransition.get(params.transition_id)
                            }
                            if(tt) {
                                def ttpage = new PortalPage()
                                ttpage.title = tracker.name + " " + tt.name + " Form"
                                ttpage.module = tracker.module
                                ttpage.slug = tracker.slug + "_" + tt.name.replaceAll(" ","_").toLowerCase()
                                ttpage.content = formpage.content
                                ttpage.save(flush:true)
                            }
                        }
                        else {
                            def ttpage = new PortalPage()
                            ttpage.title = tracker.name + " Form"
                            ttpage.module = tracker.module
                            ttpage.slug = tracker.slug + "_edit"
                            ttpage.content = formpage.content
                            ttpage.save(flush:true)
                        }
                    }
                }
                flash.message = "Done creating default pages"
            }
            return redirect(controller:"portalTracker",action:"show", id:tracker.id)
        }
        redirect(action:"index", method:"GET")
    }

    @Transactional
    def delete_fields(Long id) {
        def tracker = portalTrackerService.get(id)
        if(tracker) {
            PortalTracker.withTransaction { transaction ->
                tracker.fields.each { tfield->
                    tfield.tracker.discard()
                    tfield.delete(flush:true)
                }
                flash.message = "Fields deleted"
                redirect tracker
                return
            }
        }
    }

    @Transactional
    def fix_file_links(Long id) {
        def tracker = portalTrackerService.get(id)
        if(tracker) {
            def fixfields = []
            tracker.fields.each { tfield->
                if(tfield.field_type=='File') {
                    fixfields << tfield
                }
            }
            if(fixfields.size()>0) {
                PortalTracker.withTransaction { transaction ->
                    def rows = tracker.rows()
                    rows.each { row->
                        fixfields.each { ff->
                            def flink = FileLink.get(row[ff.name])
                            if(flink) {
                                flink.module = tracker.module
                                flink.tracker_id = tracker.id
                                flink.tracker_data_id = row['id']
                                flink.save(flush:true)
                            }
                        }
                    }
                }
            }
            flash.message = "File links fixed"
            redirect tracker
            return
        }
    }

    def fix_trail_file_links(Long id) {
        def tracker = portalTrackerService.get(id)
        if(tracker) {
            def fixed = 0
            def sql = new Sql(sessionFactory.currentSession.connection())
            def trailTable = tracker.trail_table()
            def rows = sql.rows("SELECT attachment_id, record_id FROM [${trailTable}] WHERE attachment_id IS NOT NULL".toString())
            rows.each { row ->
                def attachmentId = row['attachment_id']
                def recordId = row['record_id']
                if(attachmentId) {
                    def updated = sql.executeUpdate(
                        "UPDATE file_link SET module=:module, tracker_id=:tid, tracker_data_id=:rdid WHERE id=:id AND (tracker_id IS NULL OR tracker_data_id IS NULL)",
                        [module: tracker.module, tid: tracker.id as Integer, rdid: recordId as Long, id: attachmentId as Long]
                    )
                    fixed += updated
                }
            }
            flash.message = "Trail file links fixed: ${fixed} updated"
            redirect tracker
            return
        }
    }

    @Transactional
    def create_defaults(Long id) {
        def tracker = portalTrackerService.get(id)
        if(tracker) {
            PortalTracker.withTransaction { transaction ->
                if(!tracker.listfields) {
                    tracker.listfields = (tracker.fields*.name).join(',')
                    tracker.save(flush:true)
                }
                def adminrole = PortalTrackerRole.findByTrackerAndName(tracker,'Admin')
                if(!adminrole) {
                    adminrole = new PortalTrackerRole()
                    adminrole.tracker = tracker
                    adminrole.name = 'Admin'
                    adminrole.role_type = 'User Role'
                    adminrole.role_desc = 'Admin role for ' + tracker.name
                    adminrole.save(flush:true)
                    adminrole = PortalTrackerRole.findByTrackerAndName(tracker,'Admin')
                }
                def defstatus = PortalTrackerStatus.findByTrackerAndName(tracker,'New')
                if(!defstatus) {
                    defstatus = new PortalTrackerStatus()
                    defstatus.tracker = tracker
                    defstatus.name = 'New'
                    defstatus.save(flush:true)
                    defstatus = PortalTrackerStatus.findByTrackerAndName(tracker,'New')
                    tracker.initial_status = defstatus
                    tracker.save(flush:true)
                }
                tracker.initial_status = defstatus
                tracker.save(flush:true)
                def newtrans = PortalTrackerTransition.findByTrackerAndName(tracker,'New')
                if(!newtrans) {
                    newtrans = new PortalTrackerTransition()
                    newtrans.name = 'New'
                    newtrans.tracker = tracker
                    newtrans.next_status = defstatus
                    newtrans.editfields = tracker.listfields
                    newtrans.addToRoles(adminrole)
                    newtrans.save(flush:true)
                }
                def edittrans = PortalTrackerTransition.findByTrackerAndName(tracker,'Edit')
                if(!edittrans) {
                    edittrans = new PortalTrackerTransition()
                    edittrans.name = 'Edit'
                    edittrans.tracker = tracker
                    edittrans.next_status = defstatus
                    edittrans.editfields = tracker.listfields
                    edittrans.addToRoles(adminrole)
                    edittrans.addToPrev_status(defstatus)
                    edittrans.save(flush:true)
                }
                def delstatus = PortalTrackerStatus.findByTrackerAndName(tracker,'Delete')
                if(!delstatus) {
                    delstatus = new PortalTrackerStatus()
                    delstatus.tracker = tracker
                    delstatus.name = 'Delete'
                    delstatus.save(flush:true)
                    delstatus = PortalTrackerStatus.findByTrackerAndName(tracker,'Delete')
                }
                def deltrans = PortalTrackerTransition.findByTrackerAndName(tracker,'Delete')
                if(!deltrans) {
                    deltrans = new PortalTrackerTransition()
                    deltrans.name = 'Delete'
                    deltrans.tracker = tracker
                    deltrans.next_status = delstatus
                    deltrans.addToRoles(adminrole)
                    deltrans.addToPrev_status(defstatus)
                    deltrans.save(flush:true)
                }
            }
            redirect tracker
            return
        }
        redirect action:"index", method:"GET"
    }

    def show(Long id) {
        respond portalTrackerService.get(id)
    }

    def create() {
        respond new PortalTracker(params)
    }

    def save(PortalTracker portalTracker) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            if(portalTracker?.slug){
                redirect(action:"list",params:[slug:portalTracker?.slug,module:portalTracker?.module])
            }
            else{
                redirect(controller:'portalPage',action:'index')
            }
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (portalTracker == null) {
                notFound()
                return
            }

            try {
                portalTrackerService.save(portalTracker)
            } catch (ValidationException e) {
                respond portalTracker.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'portalTracker.label', default: 'PortalTracker'), portalTracker.id])
                    redirect portalTracker
                }
                '*' { respond portalTracker, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond portalTrackerService.get(id)
    }

    def update(PortalTracker portalTracker) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            if(portalTracker?.slug){
                redirect(action:"list",params:[slug:portalTracker?.slug,module:portalTracker?.module])
            }
            else{
                redirect(controller:'portalPage',action:'index')
            }
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            if (portalTracker == null) {
                notFound()
                return
            }

            try {
                portalTrackerService.save(portalTracker)
            } catch (ValidationException e) {
                respond portalTracker.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTracker.label', default: 'PortalTracker'), portalTracker.id])
                    redirect portalTracker
                }
                '*'{ respond portalTracker, [status: OK] }
            }
        }
    }

    def delete(Long id) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            redirect(controller:'portalTracker',action:'index')
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

            portalTrackerService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTracker.label', default: 'PortalTracker'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTracker.label', default: 'PortalTracker'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    def api_list() {
        PortalTracker.decodeparams(params) 
        print("API List")
        def tracker = null
        def toreturn = []
        if(params.tracker_id) {
            print("Checking id:" + params.tracker_id)
            tracker = portalTrackerService.get(params.tracker_id)
            print("Got this:" + tracker)
        }
        if(tracker == null) {
            return render(contentType: "application/json") {
                data toreturn
            }
        }
        print("Got tracker:" + tracker)
        if(params.item=='fields') {
            tracker.fields.each {
                toreturn << ['id':it.id,'name':it.name]
            }
            return render(contentType: "application/json") {
                fields toreturn
            }
        }
        else if(params.item=='status') {
            print("Listing status for tracker:" + tracker)
            toreturn << ['id':'null','name':'None']
            tracker.statuses.each {
                // Exclude composite statuses when requested (e.g. for transition/initial_status selectors)
                if(params.exclude_composite == 'true' && it.compositeStatuses) {
                    return
                }
                toreturn << ['id':it.id,'name':it.name]
            }
            return render(contentType: "application/json") {
                statuses toreturn
            }
        }
        else if(params.item=='roles') {
            tracker.roles.each {
                toreturn << ['id':it.id,'name':it.name]
            }
            return render(contentType: "application/json") {
                roles toreturn
            }
        }
        return render(contentType: "application/json") {
            data toreturn
        }
    }

    def list() {
        PortalTracker.decodeparams(params) 
        def tracker = PortalTracker.findByModuleAndSlug(params.module,params.slug)
        // def groovyPagesTemplateEngine = new GroovyPagesTemplateEngine() 
        // groovyPagesTemplateEngine.afterPropertiesSet()
        def sessiondata = sessionFactory.currentSession.connection()
        def sql = new Sql(sessiondata)
        def curuser = null
        if(session.curuser) {
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        if(!tracker) {
            flash.message = "Tracker Not Found"
            redirect controller:"portalPage", action:"home"
            return
        }
        if(tracker && params.excel){
            // ===== ASYNC STATUS POLL =====
            if(params.excel_token && params.excel_status) {
                def job = excelJobs[params.excel_token as String]
                if(!job) {
                    render(contentType: 'application/json', text: '{"ready":false,"error":"Job not found or expired"}')
                } else if(job.error) {
                    render(contentType: 'application/json', text: new groovy.json.JsonBuilder([ready:false, error:job.error.toString()]).toString())
                } else {
                    render(contentType: 'application/json', text: "{\"ready\":${job.done}}")
                }
                return
            }

            // ===== ASYNC FILE DOWNLOAD =====
            if(params.excel_token && !params.excel_status) {
                def tkn = params.excel_token as String
                def job = excelJobs[tkn]
                if(job?.done && job?.file) {
                    def tmpFile = new File(job.file as String)
                    if(tmpFile.exists()) {
                        excelJobs.remove(tkn)
                        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        response.setHeader("Content-Disposition", "attachment;filename=${job.filename}.xlsx")
                        response.setContentLength((int)tmpFile.length())
                        tmpFile.withInputStream { is -> response.outputStream << is }
                        response.outputStream.flush()
                        tmpFile.delete()
                    } else {
                        response.sendError(404, "Export file not found")
                    }
                } else if(job?.error) {
                    excelJobs.remove(tkn)
                    response.sendError(500, "Export failed: ${job.error}")
                } else {
                    response.sendError(404, "Export not ready or expired")
                }
                return
            }

            if(tracker.require_login && !curuser){
                session['redirectAfterLogin'] = [ controller: controllerName, action: actionName, params: params ]
                flash.message = "Please login to access the system"
                redirect(controller:'user',action:'login')
                return
            }

            // Capture web context before launching background thread
            def bgToken = java.util.UUID.randomUUID().toString()
            def bgTrackerId = tracker.id
            def bgParams = new LinkedHashMap(params)
            def bgCurUser = curuser
            def bgStoredCurUser = session.curuser
            def bgTrackerSlug = tracker.slug
            def bgSessionFactory = sessionFactory

            bgParams.remove('max')
            bgParams.remove('offset')
            bgParams.remove('excel')

            excelJobs[bgToken] = [done: false, file: null, error: null, filename: bgTrackerSlug]

            Thread.start {
              try {
              PortalTracker.withNewSession {
                tracker = PortalTracker.get(bgTrackerId)
                def bgSql = new Sql(bgSessionFactory.openSession().connection())
                def fields = []
                def ftags = null
                if(tracker.excelfields){
                    ftags = tracker.excelfields.tokenize(',')*.trim()
                }
                else if(tracker.listfields){
                    ftags = tracker.listfields.tokenize(',')*.trim()
                }
                ftags?.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }

                def anonExcelUsers = PortalSetting.namedefault(bgTrackerSlug + "_anon_excel", [])
                if(bgParams['user_id'] && bgParams['user_id'] in anonExcelUsers){
                    bgCurUser = User.findByUserID(bgParams['user_id'])
                }

                def wb = new SXSSFWorkbook(100)
                wb.setCompressTempFiles(true)

                Sheet sheet = wb.createSheet(tracker.name.replaceAll("[^A-Za-z0-9]"," "))
                Row headerRow = sheet.createRow(0)
                def curpos = 0
                fields.each { dh->
                    if(dh.field_type!='File'){
                        Cell cell = headerRow.createCell(curpos++)
                        cell.setCellValue(dh.label)
                    }
                }
                if(tracker.excel_audit) {
                    Cell cell = headerRow.createCell(curpos++)
                    cell.setCellValue("Audit Trail")
                }
                def currow = 1
                def query = tracker.listquery(bgParams, bgCurUser)
                def rename_checkbox = PortalSetting.namedefault(tracker.module + '.' + tracker.slug + '_rename_checkbox',[])
                def rows = null
                if(query['qparams']) {
                    rows = bgSql.rows(query['query'].toString(),query['qparams'])
                }
                else {
                    rows = bgSql.rows(query['query'].toString())
                }
                rows.each { row->
                    curpos = 0
                    Row excelrow = sheet.createRow(currow)
                    fields.each { field->
                        if(field.field_type!='File'){
                            Cell cell = excelrow.createCell(curpos++)
                            def fieldval = field.fieldval(row[field.name])
                            if(field.field_type=='Date'){
                                if(fieldval){
                                    cell.setCellValue(fieldval.toLocalDate().toString())
                                }
                            }
                            else if(field.field_type=='DateTime'){
                                if(fieldval){
                                    cell.setCellValue(fieldval.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' ').substring(0,16))
                                }
                            }
                            else if(field.field_type=='BelongsTo'){
                                if(fieldval){
                                    def othertokens = field.field_options.tokenize(":")
                                    def othermodule = tracker.module
                                    def otherslug = othertokens[0]
                                    if(othertokens.size()>1) {
                                        othermodule = othertokens[0]
                                        otherslug = othertokens[1]
                                    }
                                    def othertracker = PortalTracker.findByModuleAndSlug(othermodule,otherslug)
                                    if(othertracker) {
                                        def datas = bgSql.firstRow("select * from " + othertracker.data_table() + " where id=" + row[field.name])
                                        if(datas){
                                            if(field.field_format){
                                                cell.setCellValue(datas[field.field_format])
                                            }
                                            else{
                                                cell.setCellValue(datas[othertracker.default_field()])
                                            }
                                        }
                                    }
                                }
                            }
                            else if(field.field_type=='Checkbox'){
                                if(rename_checkbox.size()) {
                                    if (fieldval == true){
                                        cell.setCellValue(rename_checkbox[0])
                                    }else{
                                        cell.setCellValue(rename_checkbox[1])
                                    }
                                }
                                else {
                                    cell.setCellValue(fieldval)
                                }
                            }
                            else if(field.field_type=='File'){
                                cell.setCellValue(fieldval?.name ?: '')
                            }
                            else if(field.field_query){
                                def curval = bgSql.firstRow(field.evalquery(null,row))?.value
                                if(curval) {
                                    if(!(curval.toString()[0] in ['=','+','-','@'])){
                                        cell.setCellValue(curval)
                                    }
                                    else {
                                        cell.setCellValue(' ' + curval)
                                    }
                                }
                            }
                            else{
                                if(fieldval) {
                                    if(!(fieldval.toString()[0] in ['=','+','-','@'])){
                                        cell.setCellValue(fieldval)
                                    }
                                    else{
                                        cell.setCellValue(' ' + fieldval)
                                    }
                                }
                            }
                        }
                    }
                    if(tracker.excel_audit) {
                        def userroles = tracker.user_roles(bgCurUser, row['id'])
                        def userrules = ''
                        if(userroles.size()){
                            def currules = []
                            userroles.each { urole->
                                currules << " allowedroles like '%" + urole.name + "%' "
                            }
                            userrules = "and (allowedroles is null or allowedroles = 'null' or allowedroles = '' or " + currules.join('or') + ")"
                        }
                        Cell cell = excelrow.createCell(curpos++)
                        def audit_trail = ""
                        def auditQuery = "select * from " + tracker.trail_table() + " where record_id=" + row['id'] + " $userrules order by update_date desc,id desc"
                        def auditrows = bgSql.rows(auditQuery.toString())
                        def first = true
                        auditrows.each { auditrow ->
                            if(!first) {
                                audit_trail += '----------------------------------------------------\n\r'
                            }
                            else {
                                first = false
                            }
                            audit_trail += auditrow['description']
                            def updater = User.get(auditrow['updater_id'])
                            audit_trail += '\n\rUpdated by: ' + updater?.name
                            audit_trail += '\n\rOn: ' + (auditrow['update_date']?.format('dd-MMM-yy') ?: '')
                            audit_trail += '\n\r\n\r'
                        }
                        cell.setCellValue(audit_trail)
                    }
                    currow++
                }
                try{
                    def tmpFile = File.createTempFile("g6excel_", ".xlsx")
                    tmpFile.deleteOnExit()
                    tmpFile.withOutputStream { fos -> wb.write(fos) }
                    excelJobs[bgToken] = [done: true, file: tmpFile.absolutePath, error: null, filename: bgTrackerSlug]
                    println "Background Excel export completed: ${currow-1} rows -> ${tmpFile.absolutePath}"
                }
                catch(Exception exp){
                    println "Background Excel export error writing workbook: " + exp
                    excelJobs[bgToken] = [done: true, file: null, error: exp.message ?: "Export failed while writing workbook"]
                }
                finally {
                    try { wb.dispose() } catch(Exception e) {}
                    try { bgSql?.close() } catch(Exception e) {}
                }
              } // closes withNewSession
              } catch(Exception outerEx) {
                println "Unexpected error in background Excel export: " + outerEx.message
                outerEx.printStackTrace()
                excelJobs[bgToken] = [done: true, file: null, error: outerEx.message ?: "Unexpected export error"]
              }
            } // closes Thread.start

            // Return polling page immediately so proxy never times out
            def pollingHtml = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Generating Excel...</title>
<style>
body{font-family:Arial,sans-serif;text-align:center;padding:60px 20px;background:#f5f5f5;}
.box{background:#fff;border-radius:8px;padding:40px;display:inline-block;box-shadow:0 2px 8px rgba(0,0,0,.1);min-width:320px;}
h2{color:#333;margin-bottom:10px;}
#status{color:#666;margin:16px 0;}
.spinner{display:inline-block;width:40px;height:40px;border:4px solid #ddd;border-top-color:#0078d4;border-radius:50%;animation:spin .8s linear infinite;margin-bottom:16px;}
@keyframes spin{to{transform:rotate(360deg);}}
.error{color:#c00;}
</style>
</head>
<body>
<div class="box">
  <div class="spinner" id="spinner"></div>
  <h2>Generating Excel Report</h2>
  <p id="status">Please wait while your report is being prepared&hellip;</p>
</div>
<script>
var token = '${bgToken}';
var baseUrl = window.location.href.split('?')[0];
var checkUrl = baseUrl + '?excel=1&excel_token=' + token + '&excel_status=1';
var downloadUrl = baseUrl + '?excel=1&excel_token=' + token;
function check() {
    fetch(checkUrl, {credentials:'same-origin'})
        .then(function(r){return r.json();})
        .then(function(data){
            if(data.ready){
                document.getElementById('status').textContent='Report ready! Downloading…';
                document.getElementById('spinner').style.display='none';
                var a=document.createElement('a');
                a.href=downloadUrl;
                a.download='';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                setTimeout(function(){window.location.href=baseUrl;},1500);
            } else if(data.error){
                document.getElementById('spinner').style.display='none';
                document.getElementById('status').innerHTML='<span class="error">Error: '+data.error+'</span>';
            } else {
                setTimeout(check,2000);
            }
        })
        .catch(function(){setTimeout(check,2000);});
}
setTimeout(check,2000);
</script>
</body>
</html>"""
            render(contentType: 'text/html', text: pollingHtml)
            return
        }
        else {
            def page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_list")
            def datas = [:]
            if(page){
                def content = page.content
                def output = new StringWriter()
                Binding binding = new Binding()
                binding.setVariable('params',params)
                binding.setVariable('tracker',tracker)
                binding.setVariable('curuser',curuser)
                if(page.datasources.size()){
                    page.datasources.each { datasource->
                        def dquery = datasource.query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                        def inquery = new GroovyShell(binding).evaluate(dquery)
                        inquery=inquery.replaceAll('\r\n'," ").replaceAll('  ',' ')
                        datas[datasource.name]=[]
                        sql.eachRow(inquery) { row->
                            datas[datasource.name]<<row.toRowResult()
                        }
                    }
                }
                try{
                    def pagename = 'page' + page.id + page.lastUpdated
                    def preprocess = null
                    if(page.preprocess) {
                        binding.setVariable("datas",datas)
                        def shell = new GroovyShell(this.class.classLoader,binding)
                        preprocess = shell.evaluate(page.preprocess)
                    }
                    Template template = groovyPagesTemplateEngine.createTemplate(content,pagename)
                    def _userroles = tracker.user_roles(curuser,datas)*.name
                    def pageparams = [pp:preprocess,portalService:portalService,sql:sql,datas:datas,datasource:sessiondata,sessionFactory:sessionFactory,curcontroller:this,curuser:curuser,tracker:tracker,params:params,userroles:_userroles]
                    template.make(pageparams).writeTo(output)
                    return render(view:"render",model:['pageInstance':page,'content':output.toString()] + pageparams)
                }
                catch(Exception e){
                    println 'Error with page ' + page.title + ' : ' + e.toString()
                    def emailpagerror = PortalSetting.findByName("emailpagerror")
                    if(emailpagerror){
                        sendMail {
                            to emailpagerror.value().trim()
                            subject "Page Error"
                            body 'Error with page ' + page.title + ' : ' + e.toString() + '''
                            Params: ''' + params
                        }
                    }
                    PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString(),page.slug,page.module)
                }
            }
        }
        ['tracker':tracker,'curuser':curuser]
    }

    def create_data() {
        PortalTracker.decodeparams(params) 
        def tracker = PortalTracker.findByModuleAndSlug(params.module,params.slug)
        def new_transition = PortalTrackerTransition.findByTrackerAndName(tracker,'New')
        if(!new_transition) {
            if(tracker.initial_status) {
                new_transition = PortalTrackerTransition.findAllByNext_status(tracker.initial_status)
                if(new_transition) {
                    if(new_transition.size()>1) {
                        def opt = new_transition[0]
                        new_transition.each { nt->
                            if(nt.prev_status.size()==0) {
                                opt = nt
                            }
                        }
                        new_transition = opt
                    }
                    else {
                        new_transition = new_transition[0]
                    }
                }
            }
        }
        if(new_transition) {
                /* then redirect to where we were supposed to go */
            def cp = ['module':tracker.module,'slug':tracker.slug,'transition':new_transition.name.replaceAll(" ","_").toLowerCase()]
            def paramsfields = PortalTrackerField.findAllByTrackerAndUrl_value(tracker,true)
            params.each { prm,pval ->
                if(prm in paramsfields*.name) {
                    cp[prm] = params[prm]
                }
            }
            if(params.backtransition) {
                cp['backtransition'] = params.backtransition
                def bts = PortalTrackerField.findAllByTrackerAndField_type(tracker,'BelongsTo')
                bts.each { bf->
                    if(bf.name in params) {
                        cp[bf.name] = params[bf.name]
                    }
                }
                redirect action:"transition", method:"GET", params:cp
            }
            else{
                redirect action:"transition", method:"GET", params:cp
           }
        }
        else {
            redirect action:"list", method:"GET", params:['module':tracker.module,'slug':tracker.slug]
        }
        return 
    }

    def display_data() {
        PortalTracker.decodeparams(params)
        def sessiondata = sessionFactory.currentSession.connection()
        def sql = new Sql(sessiondata)
        // def groovyPagesTemplateEngine = new GroovyPagesTemplateEngine()
        // groovyPagesTemplateEngine.afterPropertiesSet()
        def tracker = PortalTracker.findByModuleAndSlug(params.module,params.slug)
        def page = null
        def datas = [:]
        def curuser = null
        if(session.curuser) {
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        def defaultdata = tracker.getdatas(params.id,sql)
        if(params.id && !defaultdata) {
            redirect action:"list", method:"GET", params:['module':tracker.module,'slug':tracker.slug]
            return
        }
        def datasRecordStatus = null
        try { datasRecordStatus = datas ? datas['record_status'] : null } catch(e) {}
        if(tracker.tracker_type in ['Tracker','Statement','DataStore'] && datasRecordStatus){
            page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_show_" + datasRecordStatus.replaceAll(' ','_').toLowerCase())
        }
        if(!page){
            page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_show_default")
        }
        if(page){
            def content = page.content
            def output = new StringWriter()
            def preprocess = null
            datas = defaultdata
            Binding binding = new Binding()
            binding.setVariable('params',params)
            binding.setVariable('tracker',tracker)
            binding.setVariable('curuser',curuser)
            binding.setVariable("datas",datas)
            if(page.datasources.size()){
              page.datasources.each { datasource->
                def dquery = datasource.query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                def inquery = new GroovyShell(binding).evaluate(dquery)
                inquery=inquery.replaceAll('\r\n'," ").replaceAll('  ',' ')
                datas[datasource.name]=[]
                sql.eachRow(inquery) { row->
                    datas[datasource.name]<<row.toRowResult()
                }
              }
            }
            if(page.preprocess) {
                binding.setVariable("datas",datas)
                def shell = new GroovyShell(this.class.classLoader,binding)
                preprocess = shell.evaluate(page.preprocess)
            }
            try{
                def pagename = 'page' + page.id + page.lastUpdated
                Template template = groovyPagesTemplateEngine.createTemplate(content,pagename)
                def _userroles = tracker.user_roles(curuser,datas)*.name
                def pageparams = [pp:preprocess,portalService:portalService,sql:sql,defaultdata:defaultdata,datas:datas,datasource:sessiondata,sessionFactory:sessionFactory,curcontroller:this,curuser:curuser,tracker:tracker,params:params,userroles:_userroles]
                template.make(pageparams).writeTo(output)
                return render(view:"render",model:['pageInstance':page,'content':output.toString()] + pageparams)
            }
            catch(Exception e){
                println 'Error with page ' + page.title + ' : ' + e.toString()
                def emailpagerror = PortalSetting.findByName("emailpagerror")
                if(emailpagerror){
                    sendMail {
                        to emailpagerror.value().trim()
                        subject "Page Error"
                        body 'Error with page ' + page.title + ' : ' + e.toString() + '''
                        Params: ''' + params
                    }
                }
                PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString(),page.slug,page.module)
            }
        }
        ['portalService':portalService,'tracker':tracker,'datas':datas,'curuser':curuser]
    }

    def update_record() {
        PortalTracker.decodeparams(params) 
        params.id = params.id
        def datasource = sessionFactory.currentSession.connection()
        def tracker = PortalTracker.findByModuleAndSlug(params.module,params.slug)
        def curuser = null
        if(session.curuser) {
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        tracker.updatetrail(params,session,request,curuser,datasource)
        redirect action:"display_data", method:"GET", params:['module':tracker.module,'slug':tracker.slug,'id':params.id]
    }

    @Transactional
    def transition_submit() {
        PortalTracker.decodeparams(params) 
        def tracker = PortalTracker.findByModuleAndSlug(params.module,params.slug)
        def curuser = null
        def datas = null
        if(session.curuser) {
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        def tname = params.transition.replace("_"," ").capitalize()
        def ctransall = PortalTrackerTransition.findAllByTrackerAndNameIlike(tracker,tname)
        def ctrans = null
        def abandon = false
        if(params.id) {
            datas = tracker.getdatas(params.id)
        }
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            if(tracker?.slug){
                redirect(action:"list",params:[slug:tracker?.slug,module:tracker?.module])
            }
            else{
                redirect(controller:'portalPage',action:'index')
            }
            abandon = true
        }
        if(ctransall) {
            ctransall.each { cc->
                 if(cc.testenabled(session,datas)) {
                    ctrans = cc
                 }
            }
        }
        if(!ctrans) { 
            // make sure the transition can be done by the user
            flash.message = "You are not authorized to do that"
            redirect(controller:'portalPage',action:'index')
            abandon = true
        }
        if(abandon) {
            return true
        }
        else {
            PortalTracker.withTransaction { transaction ->
                def datasource = sessionFactory.currentSession.connection()
                def sql = new Sql(datasource)
                def editfields = []
                def defaultfields = []
                if(ctrans) {
                    def fieldtokens = ctrans.editfields?.tokenize(',')*.trim()
                    fieldtokens.each { ft->
                        def field = PortalTrackerField.findByTrackerAndName(tracker,ft)
                        if(field) {
                            if(!field.field_default || field.field_default.size()==0){
                                editfields << field
                            }
                            else{
                                defaultfields << field
                            }
                        }
                    }
                    if(editfields.size()>0) {
                        defaultfields = []
                    }
                }

                if(params.submit){
                    /* this is the part where we actually create or update the data */
                    datas = tracker.updaterecord(params,request,session,sql,defaultfields)
                    datas = tracker.getdatas(datas['id'],sql)
                    if(!datas && session['datas']) {
                        datas = session['datas']
                    }
                    def postoutput = null
                    /* end of create or update data */
                    if(ctrans && datas) {
                        def trail_id = null
                        if(ctrans.updatetrails) {
                            params.statusUpdateDesc = ctrans.updatetrail(session,datas,portalService)
                        }
                        /* then we update the trail */
                        if(tracker.tracker_type!='DataStore') {
                            // println "Updating trail first"
                            trail_id = tracker.updatetrail(params,session,request,curuser,datasource,groovyPagesTemplateEngine,portalService)
                        }
                        /* runonupdate and emailonupdate for current status */
                        def curdatas = tracker.getdatas(datas['id'])
                        def curstatus = null
                        def curRecordStatus = null
                        try { curRecordStatus = curdatas ? curdatas['record_status'] : null } catch(e) {}
                        if(curRecordStatus) {
                            curstatus = PortalTrackerStatus.findByTrackerAndName(tracker,curRecordStatus)
                        }
                        if(curstatus && curstatus.runonupdate) {
                            try {
                                Binding runbinding = new Binding()
                                runbinding.setVariable("session",session)
                                runbinding.setVariable("params",params)
                                runbinding.setVariable("datas",curdatas)
                                runbinding.setVariable("tracker",tracker)
                                runbinding.setVariable("curuser",curuser)
                                runbinding.setVariable("portalService",portalService)
                                runbinding.setVariable("mailService",mailService)
                                def runshell = new GroovyShell(this.class.classLoader,runbinding)
                                runshell.evaluate(curstatus.runonupdate.content)
                            }
                            catch(Exception e) {
                                println "Error running runonupdate for status " + curstatus + " : " + e
                                PortalErrorLog.record(params,curuser,'tracker','runonupdate',e.toString(),tracker.slug,tracker.module)
                            }
                        }
                        if(curstatus && curstatus.emailonupdate) {
                            Binding updatebinding = new Binding()
                            updatebinding.setVariable("session",session)
                            curdatas['update_desc'] = params.statusUpdateDesc
                            updatebinding.setVariable("datas",curdatas)
                            updatebinding.setVariable("portalService",portalService)
                            def shell = new GroovyShell(PortalTracker.class.classLoader,updatebinding)
                            def email = curstatus.emailonupdate
                            def toccs = null
                            def tosend = null
                            try {
                                tosend = shell.evaluate(email.emailto)
                                if(tosend && (tosend.getClass().isArray() || tosend instanceof List || tosend instanceof Set)) {
                                    tosend = tosend.join(',')
                                }
                                if(email.emailcc) {
                                    toccs = shell.evaluate(email.emailcc)
                                    if(toccs && (toccs.getClass().isArray() || toccs instanceof List || toccs instanceof Set)) {
                                        toccs = toccs.join(',')
                                    }
                                }
                            }
                            catch(Exception e) {
                                PortalErrorLog.record(params,curuser,'tracker','emailonupdate - tosend and toccs',e.toString(),tracker.slug,tracker.module)
                            }
                            if(tosend) {
                                def emailcontent = email.evalbody(curdatas,groovyPagesTemplateEngine,portalService)
                                try {
                                    def sendemail = new PortalEmail()
                                    sendemail.emailto = tosend
                                    if(toccs) {
                                        sendemail.emailcc = toccs
                                    }
                                    sendemail.title = emailcontent['title']
                                    sendemail.module = tracker.module
                                    sendemail.body = emailcontent['body']
                                    sendemail.deliveryTime = new Date()
                                    sendemail.send(mailService)
                                }
                                catch(Exception e) {
                                    println 'Error with sending emailonupdate ' + email.body?.title + ' : ' + e.toString()
                                    PortalErrorLog.record(params,curuser,'tracker','emailonupdate',e.toString(),tracker.slug,tracker.module)
                                }
                            }
                        }
                        if(ctrans.postprocess){
                            try {
                                // println "Running postprocess"
                                Binding binding = new Binding()
                                binding.setVariable("datasource",datasource)
                                binding.setVariable("sql",sql)
                                binding.setVariable("session",session)
                                binding.setVariable("params",params)
                                binding.setVariable("datas",datas)
                                binding.setVariable("ctrans",ctrans)
                                binding.setVariable("tracker",tracker)
                                binding.setVariable("curuser",curuser)
                                binding.setVariable("trail_id",trail_id)
                                binding.setVariable("portalService",portalService)
                                binding.setVariable("mailService",mailService)
                                def shell = new GroovyShell(this.class.classLoader,binding)
                                postoutput = shell.evaluate(ctrans.postprocess.content)
                                if(postoutput && postoutput instanceof LinkedHashMap && 'flash' in postoutput){
                                    flash.message = postoutput['flash']
                                }
                            }
                            catch(Exception e){
                                println "Error postprocessing for transition " + ctrans + " with " + ctrans.postprocess.content + " error:" + e
                            }
                        }
                        datas = tracker.getdatas(datas['id'])
                        if(!datas && session['datas']) {
                            datas = session['datas']
                        }
                        Binding updatebinding = new Binding()
                        updatebinding.setVariable("session",session)
                        datas['update_desc']=params.statusUpdateDesc
                        updatebinding.setVariable("datas",datas)
                        updatebinding.setVariable("tracker",tracker)
                        updatebinding.setVariable("portalService",portalService)
                        def shell = new GroovyShell(PortalTracker.class.ClassLoader,updatebinding)
                        ctrans.emails.each { email-> 
                            def toccs = null
                            def tosend = null
                            try{
                                // println "Emailto: " + email.emailto + " CC:" + email.emailcc
                                tosend = shell.evaluate(email.emailto)
                                if(tosend && (tosend.getClass().isArray() || tosend instanceof List || tosend instanceof Set)) {
                                    // println "Is an array"
                                    tosend = tosend.join(',')
                                }
                                if(email.emailcc){
                                    toccs = shell.evaluate(email.emailcc)
                                    if(toccs && (toccs.getClass().isArray() || toccs instanceof List || toccs instanceof Set)) {
                                        // println "Is an array"
                                        toccs = toccs.join(',')
                                    }
                                }
                                // println "Final Emailto: " + tosend + " CC:" + toccs
                            }
                            catch(Exception e){
                                PortalErrorLog.record(params,curuser,'tracker','updatetrail - tosend and toccs',e.toString() + "\n\n" + email,tracker.slug,tracker.module)
                                println "Error processing email to: " + e
                            }
                            if(tosend) {
                                def emailcontent = email.evalbody(datas,groovyPagesTemplateEngine,portalService)
                                try {
                                    def sendemail = new PortalEmail()
                                    sendemail.emailto = tosend
                                    if(toccs) {
                                        sendemail.emailcc = toccs
                                    }
                                    sendemail.title = emailcontent['title']
                                    sendemail.module = tracker.module
                                    sendemail.body = emailcontent['body']
                                    sendemail.deliveryTime = new Date()
                                    sendemail.send(mailService)
                                }
                                catch(Exception e){
                                    println 'Error with sending email ' + email.body?.title + ' : ' + e.toString()
                                    def emailpagerror = PortalSetting.findByName("emailpagerror")
                                    if(emailpagerror && mailService){
                                        try {
                                            mailService.sendMail {
                                                to emailpagerror.value().trim()
                                                subject "Page Error"
                                                body 'Error with sending email ' + email.body?.title + ' : ' + e.toString() + '''
                                                Params: ''' + params
                                            }
                                        } catch(Exception e2) {
                                            println 'Error sending error notification email: ' + e2.toString()
                                        }
                                    }
                                    PortalErrorLog.record(params,curuser,'tracker','updatetrail - sending email',e.toString(),tracker.slug,tracker.module)
                                }
                            }
                        }
                    }
                    if(session['datas']) {
                        session['datas'] = null
                    }

                    /* then redirect to where we were supposed to go */
                    if(postoutput && postoutput instanceof LinkedHashMap && 'redirect_slug' in postoutput){
                        // println "Running postprocess redirect_slug"
                        def rparams = ['slug':postoutput['redirect_slug']]
                        if('redirect_module' in postoutput) {
                            rparams += ['module':postoutput['redirect_module']]
                        }
                        else {
                            rparams += ['module':tracker.module]
                        }
                        if('redirect_params' in postoutput){
                            rparams += postoutput['redirect_params']
                        }
                        def nextpage = PortalPage.findByModuleAndSlug(rparams['module'],rparams['slug'])
                        if(nextpage) {
                            if(nextpage.runable) {
                                return redirect(controller:'portalPage',action:'runpage',params:rparams)
                            }
                            else {
                                return redirect(controller:'portalPage',action:'display',params:rparams)
                            }
                        }
                        else {
                            def nexttracker = PortalTracker.findByModuleAndSlug(rparams['module'],rparams['slug'])
                            if(nexttracker) {
                                return redirect(controller:'portalTracker',action:'list',params:rparams)
                            }
                            else {
                                return redirect(controller:'portalPage',action:'display',params:rparams)
                            }
                        }
                    }
                    if(ctrans && (ctrans.name.toLowerCase().trim()!='delete' || params.backto || ctrans.redirect_after)) {
                      // println "Not delete trans and got backgto or redirect after"
                      if(params.backto) {
                          // println "In backto"
                          def btrans = PortalTrackerTransition.get(params.backtransition)
                          def target = null
                          if(btrans) {
                              target = btrans.tracker
                          }
                          else {
                              def backtoparam = params.backto
                              if(backtoparam != null && backtoparam.getClass().isArray()) {
                                  backtoparam = backtoparam[0]
                              }
                              def bdt = backtoparam.tokenize(':')
                              if(bdt.size()==1) {
                                  target = PortalTracker.findBySlug(bdt[0])
                              }
                              else if(bdt.size()>1) {
                                  target = PortalTracker.findByModuleAndSlug(bdt[0],bdt[1])
                              }
                          }
                          if(target) {
                              def bts = PortalTrackerField.findAllByTrackerAndField_type(tracker,'BelongsTo')
                              def bid = null
                              if('backtoid' in params) {
                                  bid = params['backtoid']
                              }
                              else {
                                  bts.each { bf->
                                      if(bf.name in params) {
                                          bid = params[bf.name]
                                      }
                                  }
                              }
                              def clp = ['module':target.module,'slug':target.slug,'id':bid]
                              if(btrans) {
                                  clp['transition'] = btrans.name.replaceAll(" ","_").toLowerCase()
                                  redirect action:"transition", method:"GET", params:clp
                              }
                              else {
                                  redirect action:"display_data", method:"GET", params:clp
                              }
                          }
                          else {
                              redirect action:"display_data", method:"GET", params:['module':tracker.module,'slug':tracker.slug,'id':params.id]
                          }
                      }
                      else if(ctrans.redirect_after) {
                          //println "In redirect after"
                          def bdt = ctrans.redirect_after.tokenize(':')
                          def redirect_page = null
                          if(bdt.size()==1) {
                              redirect_page = PortalPage.findBySlug(bdt[0])
                          }
                          else if(bdt.size()>1) {
                              redirect_page = PortalPage.findByModuleAndSlug(bdt[0],bdt[1])
                          }
                          if(redirect_page) {
                              def rparams = ['module':redirect_page.module,'slug':redirect_page.slug,'id':datas['id']]
                              if(redirect_page.runable) {
                                  return redirect(controller:'portalPage',action:'runpage',params:rparams)
                              }
                              else {
                                  return redirect(controller:'portalPage',action:'display',params:rparams)
                              }
                          }
                      }
                      else {
                          // println "Just got to display data"
                          if(datas?.id) {
                              redirect action:"display_data", method:"GET", params:['module':tracker.module,'slug':tracker.slug,'id':datas.id]
                          }
                          else {
                              redirect action:"display_data", method:"GET", params:['module':tracker.module,'slug':tracker.slug,'id':params.id]
                          }
                      }
                      return
                    }
                    else {
                      // println "Failed in general. going back to list"
                      redirect action:"list", method:"GET", params:['module':tracker.module,'slug':tracker.slug]
                      return
                    }
                }
                else {
                    def page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_edit")
                    if(page){
                        def content = page.content
                        def output = new StringWriter()
                        try{
                            def pagename = 'page' + page.id + page.lastUpdated
                            datas = sql.firstRow("select * from " + tracker.data_table() + " where id=:id",['id':params.id])
                            Binding binding2 = new Binding()
                            binding2.setVariable('params',params)
                            binding2.setVariable('tracker',tracker)
                            binding2.setVariable('curuser',curuser)
                            binding2.setVariable('datas',datas)
                            def pagedata = [:]
                            if(page.datasources.size()){
                                page.datasources.each { pds->
                                    def dquery = pds.query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                                    def inquery = new GroovyShell(binding2).evaluate(dquery)
                                    inquery=inquery.replaceAll('\r\n'," ").replaceAll('  ',' ')
                                    pagedata[pds.name]=[]
                                    sql.eachRow(inquery) { row->
                                        pagedata[pds.name]<<row.toRowResult()
                                    }
                                }
                            }
                            def preprocess2 = null
                            if(page.preprocess) {
                                def shell2 = new GroovyShell(this.class.classLoader,binding2)
                                preprocess2 = shell2.evaluate(page.preprocess)
                            }
                            Template template = groovyPagesTemplateEngine.createTemplate(content,pagename)
                            def _userroles2 = tracker.user_roles(curuser,datas)*.name
                            def pageparams = [pp:preprocess2,portalService:portalService,datas:datas,pagedata:pagedata,sessionFactory:sessionFactory,curcontroller:this,curuser:curuser,tracker:tracker,params:params,transition:ctrans,userroles:_userroles2]
                            template.make(pageparams).writeTo(output)
                            return render(view:"render",model:['pageInstance':page,'content':output.toString()] + pageparams)
                        }
                        catch(Exception e){
                            println 'Error with page ' + page.title + ' : ' + e.toString()
                            def emailpagerror = PortalSetting.findByName("emailpagerror")
                            if(emailpagerror){
                                sendMail {
                                    to emailpagerror.value().trim()
                                    subject "Page Error"
                                    body 'Error with page ' + page.title + ' : ' + e.toString() + '''
                Params: ''' + params
                                }
                            }
                            PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString(),page.slug,page.module)
                        }
                    }

                    [portalService:portalService,sql:sql,curuser:curuser,tracker:tracker,transition:ctrans,datas:datas,userroles:tracker.user_roles(curuser,datas)*.name,datasource:datasource]
                }
            }
        }
    }

    @Transactional
    def transition_direct() {
        PortalTracker.decodeparams(params)
        def tracker = PortalTracker.findByModuleAndSlug(params.module, params.slug)
        def curuser = session.curuser
        def datas = null
        if (params.id) {
            datas = tracker.getdatas(params.id)
        }
        def tname = params.transition?.replace("_", " ")?.capitalize()
        def ctransall = PortalTrackerTransition.findAllByTrackerAndNameIlike(tracker, tname)
        def ctrans = null
        if (ctransall) {
            ctransall.each { cc ->
                if (cc.testenabled(session, datas)) {
                    ctrans = cc
                }
            }
        }
        if (!ctrans || !ctrans.immediate_submission) {
            flash.message = "You are not authorized to do that"
            redirect(controller: 'portalPage', action: 'index')
            return
        }
        def postoutput = PortalTracker.withTransaction { transaction ->
            def datasource = sessionFactory.currentSession.connection()
            def sql = new Sql(datasource)
            def defaultfields = []
            def fieldtokens = ctrans.editfields?.tokenize(',')*.trim()
            fieldtokens?.each { ft ->
                def field = PortalTrackerField.findByTrackerAndName(tracker, ft)
                if (field && field.field_default) {
                    defaultfields << field
                }
            }
            params.submit = true
            params.next_status = ctrans.next_status?.id
            datas = tracker.updaterecord(params, request, session, sql, defaultfields)
            datas = tracker.getdatas(datas['id'])
            if (!datas && session['datas']) {
                datas = session['datas']
            }
            def postoutput = null
            if (ctrans && datas) {
                def trail_id = null
                if (ctrans.updatetrails) {
                    params.statusUpdateDesc = ctrans.updatetrail(session, datas, portalService)
                }
                if (tracker.tracker_type != 'DataStore') {
                    trail_id = tracker.updatetrail(params, session, request, curuser, datasource, groovyPagesTemplateEngine, portalService)
                }
                if (ctrans.postprocess) {
                    try {
                        Binding binding = new Binding()
                        binding.setVariable("datasource", datasource)
                        binding.setVariable("sql", sql)
                        binding.setVariable("session", session)
                        binding.setVariable("datas", datas)
                        binding.setVariable("ctrans", ctrans)
                        binding.setVariable("params", params)
                        binding.setVariable("tracker", tracker)
                        binding.setVariable("curuser", curuser)
                        binding.setVariable("trail_id", trail_id)
                        binding.setVariable("portalService", portalService)
                        binding.setVariable("mailService", mailService)
                        def shell = new GroovyShell(this.class.classLoader, binding)
                        postoutput = shell.evaluate(ctrans.postprocess.content)
                        if (postoutput && postoutput instanceof LinkedHashMap && 'flash' in postoutput) {
                            flash.message = postoutput['flash']
                        }
                    } catch (Exception e) {
                        println "Error postprocessing for transition_direct " + ctrans + ": " + e
                    }
                }
                datas = tracker.getdatas(datas['id'])
                if (!datas && session['datas']) {
                    datas = session['datas']
                }
                Binding updatebinding = new Binding()
                updatebinding.setVariable("session", session)
                if (datas) {
                    datas['update_desc'] = params.statusUpdateDesc
                }
                updatebinding.setVariable("datas", datas)
                updatebinding.setVariable("tracker", tracker)
                updatebinding.setVariable("portalService", portalService)
                def shell = new GroovyShell(PortalTracker.class.ClassLoader, updatebinding)
                ctrans.emails.each { email ->
                    def tosend = null
                    def toccs = null
                    try {
                        tosend = shell.evaluate(email.emailto)
                        if (tosend && (tosend.getClass().isArray() || tosend instanceof List || tosend instanceof Set)) {
                            tosend = tosend.join(',')
                        }
                        if (email.emailcc) {
                            toccs = shell.evaluate(email.emailcc)
                            if (toccs && (toccs.getClass().isArray() || toccs instanceof List || toccs instanceof Set)) {
                                toccs = toccs.join(',')
                            }
                        }
                    } catch (Exception e) {
                        PortalErrorLog.record(params, curuser, 'tracker', 'transition_direct - tosend/toccs', e.toString() + "\n\n" + email, tracker.slug, tracker.module)
                    }
                    if (tosend) {
                        def emailcontent = email.evalbody(datas, groovyPagesTemplateEngine, portalService)
                        try {
                            def sendemail = new PortalEmail()
                            sendemail.emailto = tosend
                            if (toccs) sendemail.emailcc = toccs
                            sendemail.title = emailcontent['title']
                            sendemail.module = tracker.module
                            sendemail.body = emailcontent['body']
                            sendemail.deliveryTime = new Date()
                            sendemail.send(mailService)
                        } catch (Exception e) {
                            PortalErrorLog.record(params, curuser, 'tracker', 'transition_direct - sending email', e.toString(), tracker.slug, tracker.module)
                        }
                    }
                }
            }
            if (session['datas']) {
                session['datas'] = null
            }
            postoutput
        }
        if (postoutput && postoutput instanceof LinkedHashMap && 'redirect_slug' in postoutput) {
            def rparams = ['slug': postoutput['redirect_slug'], 'module': postoutput['redirect_module'] ?: tracker.module]
            if ('redirect_params' in postoutput) rparams += postoutput['redirect_params']
            def nextpage = PortalPage.findByModuleAndSlug(rparams['module'], rparams['slug'])
            if (nextpage) {
                if (nextpage.runable) {
                    redirect(controller: 'portalPage', action: 'runpage', params: rparams); return
                } else {
                    redirect(controller: 'portalPage', action: 'display', params: rparams); return
                }
            } else {
                def nexttracker = PortalTracker.findByModuleAndSlug(rparams['module'], rparams['slug'])
                if (nexttracker) {
                    redirect(controller: 'portalTracker', action: 'list', params: rparams); return
                } else {
                    redirect(controller: 'portalPage', action: 'display', params: rparams); return
                }
            }
        } else if (postoutput && postoutput instanceof LinkedHashMap && 'redirect_url' in postoutput) {
            redirect(url: postoutput['redirect_url']); return
        }
        redirect(action: 'display_data', params: [module: params.module, slug: params.slug, id: params.id])
    }

    def transition() {
      PortalTracker.decodeparams(params)
      def datasource = sessionFactory.currentSession.connection()
      def sql = new Sql(datasource)
      // def groovyPagesTemplateEngine = new GroovyPagesTemplateEngine() 
      // groovyPagesTemplateEngine.afterPropertiesSet()
      def curuser = null
      if(session.curuser) {
          // curuser = User.get(session.userid)
          curuser = session.curuser
      }
      def tracker = PortalTracker.findByModuleAndSlug(params.module,params.slug)
      def tname = params.transition?.replace("_"," ")?.capitalize()
      def ctransall = PortalTrackerTransition.findAllByTrackerAndNameIlike(tracker,tname)
      def ctrans = null
      def datas = null
      if(params.id) {
          datas = tracker.getdatas(params.id)
      }
      if(ctransall) {
          ctransall.each { cc->
               if(cc.testenabled(session,datas)) {
                  ctrans = cc
               }
          }
      }
      if(!ctrans) { 
          // make sure the transition can be done by the user
          flash.message = "You are not authorized to do that"
          redirect(controller:'portalPage',action:'index')
          return true
      }
      def page = null
      def editfields = []
      def defaultfields = []
      if(ctrans) {
          def fieldtokens = ctrans.editfields?.tokenize(',')*.trim()
          fieldtokens.each { ft->
              def field = PortalTrackerField.findByTrackerAndName(tracker,ft)
              if(field) {
                  if(!field.field_default || field.field_default.size()==0){
                      editfields << field
                  }
                  else{
                      defaultfields << field
                  }
              }
          }
          if(editfields.size()>0) {
              defaultfields = []
          }
          if(ctrans.next_status==tracker.initial_status) {  // this is actually transition to create a new record
              page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_add")
          }
          if(!page) {
              page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_" + ctrans.name.replaceAll(" ","_").toLowerCase())
          }
      }

      if(!page) {
          if(!(ctrans && ctrans?.next_status?.name?.toLowerCase()=='delete')) {
              page = PortalPage.findByModuleAndSlug(tracker.module,tracker.slug + "_edit")
          }
      }
      if(page){
          def content = page.content
          def output = new StringWriter()
          try{
              def pagename = 'page' + page.id + page.lastUpdated
              def userroles = tracker.user_roles(curuser,datas)*.name
              def preprocess = null
              if(page.preprocess) {
                  Binding binding = new Binding()
                  binding.setVariable("datas",datas)
                  binding.setVariable("curuser",curuser)
                  binding.setVariable("tracker",tracker)
                  binding.setVariable("params",params)
                  binding.setVariable("transition",ctrans)
                  def shell = new GroovyShell(this.class.classLoader,binding)
                  preprocess = shell.evaluate(page.preprocess)
              }
              Template template = groovyPagesTemplateEngine.createTemplate(content,pagename)
              def pageparams = [pp:preprocess,portalService:portalService,sql:sql,datas:datas,sessionFactory:sessionFactory,curcontroller:this,curuser:curuser,tracker:tracker,params:params,transition:ctrans,userroles:userroles]
              template.make(pageparams).writeTo(output)
              return render(view:"render",model:['pageInstance':page,'content':output.toString()] + pageparams)
          }
          catch(Exception e){
              println 'Error with page ' + page.title + ' : ' + e.toString()
              def emailpagerror = PortalSetting.findByName("emailpagerror")
              if(emailpagerror){
                  sendMail {
                      to emailpagerror.value().trim()
                      subject "Page Error"
                      body 'Error with page ' + page.title + ' : ' + e.toString() + '''
        Params: ''' + params
                  }
              }
              PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString(),page.slug,page.module)
          }
      }

      [portalService:portalService,sql:sql,curuser:curuser,tracker:tracker,transition:ctrans,datas:datas,userroles:tracker.user_roles(curuser,datas)*.name,datasource:datasource]
    }

    def userlist() {
        def trackerfield = PortalTrackerField.get(params.id)
        def lusers = []
        if(params.value) {
            lusers << g6portal.User.get(params.value)
        }
        lusers += trackerfield.userlist(session,params)
        lusers = lusers.unique()
        return render(contentType: "text/json") {
            users lusers.collect{ ['id':it.id,'value':it.id,'name':it.name] }
        }
    }

    def nodeslist() {
        def lobjects = []
        def trackerfield = PortalTrackerField.get(params.id)
        def defaultfield = 'name'
        if(trackerfield) {
            if(params.value) {
                lobjects << PortalTreeNode.get(params.value)
            }
            lobjects += trackerfield.nodeslist(session,params)
            lobjects = lobjects.unique()
        }
        return render(contentType: "text/json") {
            objects lobjects.collect{ ['id':it.id,'value':it.id,'name':it.name] }
        }
    }

    def objectlist() {
        def lobjects = []
        def trackerfield = PortalTrackerField.get(params.id)
        def defaultfield = 'name'
        if(trackerfield) {
            def trackersetting = PortalSetting.namedefault('tracker_objects',[])
            if(trackerfield.field_type in trackersetting) {
                def tokens = trackersetting[trackerfield.field_type].tokenize('.')
                if(tokens.size()>=3) {
                    defaultfield = tokens[2]
                }
                else if(trackerfield.tracker.defaultfield) {
                    defaultfield = trackerfield.tracker.defaultfield.name
                }
                else {
                    defaultfield = trackerfield.tracker.fields[0].name
                }
                if(params.value) {
                    lobjects << trackerfield.tracker.getdatas(params.value)
                }
                lobjects += trackerfield.objectlist(session,params)
                lobjects = lobjects.unique()
            }
        }
        return render(contentType: "text/json") {
            objects lobjects.collect{ ['id':it.id,'value':it.id,'name':it[defaultfield]] }
        }
    }

    def dropdowndata() {
        println "In dropdowndata"
        def field = PortalTrackerField.get(params.id)
        def data = null
        if(field) {
            if(params.data_id) {
                def othertracker = field.othertracker()
                if(params.field) {
                    data = othertracker.getdatas(params.data_id)?[params.field]
                }
                else {
                    data = othertracker.getdatas(params.data_id)
                }
            }
        }
        return render(contentType: "text/json") {
            datas data
        }
    }

    def dropdownlist() {
        def lobjects = []
        def trackerfield = PortalTrackerField.get(params.id)
        def defaultfield = 'name'
        if(trackerfield) {
            def trackersetting = PortalSetting.namedefault('tracker_objects',[])
            def othertracker = trackerfield.othertracker()
            if(othertracker) {
                defaultfield = trackerfield.field_format
                if(params.q && params.q.size()>1) {
                    def dparam = [:]
                    dparam[defaultfield]='%' + params.q + '%'
                    lobjects = othertracker.rows(dparam)
                }
                else {
                    lobjects = othertracker.rows(null,null,0,100)
                }
                lobjects = lobjects.unique()
            }
        }
        return render(contentType: "text/json") {
            objects lobjects.collect{ ['id':it.id,'value':it.id,'name':it[defaultfield]] }
        }
    }

    @Transactional
    def fix_status(Long id) {
        def tracker = portalTrackerService.get(id)
        def datas = tracker.rows(['record_status':null],null)
        datas.each { data->
            data['record_status'] = tracker.initial_status.name
            tracker.savedatas(data)
        }
        flash.message = "Status for " + tracker + " fixed"
        redirect tracker
    }

    @Transactional
    def export_data(Long id) {
        def tracker = portalTrackerService.get(id)
        def curfolder = System.getProperty("user.dir")
        def migrationfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + tracker.module
        if(!(new File(migrationfolder).exists())){
            new File(migrationfolder).mkdirs()
        }
        def datafile = new File(migrationfolder + '/data.json')
        def datas = []
        datafile.write(JsonOutput.toJson(datas))
        flash.message = "Data for " + tracker + " exported"
        redirect tracker
    }
}
