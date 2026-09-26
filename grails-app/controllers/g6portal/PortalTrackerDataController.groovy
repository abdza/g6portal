package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import groovy.sql.Sql
import grails.converters.JSON

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.streaming.SXSSFWorkbook

import g6portal.PoiExcel

class PortalTrackerDataController {

    PortalTrackerDataService portalTrackerDataService
    PortalTrackerService portalTrackerService
    def mailService
    def sessionFactory
    def dataSource

    // Data Dump jobs in flight, keyed by the token handed to the browser. Same mechanism as
    // PortalPageController.xlsxJobs: in-memory, so a restart loses any dump still building.
    static java.util.concurrent.ConcurrentHashMap dumpJobs = new java.util.concurrent.ConcurrentHashMap()

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    private String validateIdentifier(String identifier) {
        if (!identifier) return ""
        if (!identifier.matches(/^[a-zA-Z_][a-zA-Z0-9_]*$/)) {
            throw new SecurityException("Invalid database identifier: ${identifier}")
        }
        return identifier
    }

    def index(Integer max) {
        def curuser = session.curuser
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All')) {
            def query = '%' + params.q + '%'
            if(params.module && params.module!='All') {
                def trackers = portalTrackerService.list(query,params.module)
                respond portalTrackerDataService.list(trackers,dparam), model:[portalTrackerDataCount: portalTrackerDataService.count(trackers), params:params, curuser:curuser]
            }
            else {
                if(session.enablesuperuser) {
                    def trackers = portalTrackerService.list(query)
                    respond portalTrackerDataService.list(trackers,dparam), model:[portalTrackerDataCount: portalTrackerDataService.count(trackers), params:params, curuser:curuser]
                }
                else {
                    def trackers = portalTrackerService.list(query,session.adminmodules)
                    respond portalTrackerDataService.list(trackers,dparam), model:[portalTrackerDataCount: portalTrackerDataService.count(trackers), params:params, curuser:curuser]
                }
            }
        }
        else {
            if(session.enablesuperuser) {
                respond portalTrackerDataService.list(dparam), model:[portalTrackerDataCount: portalTrackerDataService.count(), params:params, curuser:curuser]
            }
            else {
                def trackers = portalTrackerService.list(session.adminmodules)
                respond portalTrackerDataService.list(trackers,dparam), model:[portalTrackerDataCount: portalTrackerDataService.count(trackers),params:params, curuser:curuser]
            }
        }
    }

    def show(Long id) {
        def curuser = session.curuser
        respond portalTrackerDataService.get(id), model:[curuser:curuser]
    }

    def create() {
        def tracker = null
        def trackers = []
        def customdata = []
        if(session.enablesuperuser) {
            trackers = PortalTracker.findAll()
        }
        else {
            trackers = PortalTracker.findAllByModuleInList(session.adminmodules)
        }
        def keydata = [:]
        def internalParams = ['tracker_id','module','slug','action','controller','format']
        if(params.tracker_id) {
            tracker = PortalTracker.get(params.tracker_id)
            if(tracker) {
                def fieldNames = tracker.fields*.name
                tracker.fields.each { field ->
                    if (params[field.name]) {
                        def iskey = params.customkeyfields?.tokenize(',')?.contains(field.name) ?: false
                        customdata << ['name':field.name,'value':params[field.name],'iskey':iskey]
                        if(iskey) { keydata[field.id] = params[field.name] }
                    }
                    else {
                        if(field.name in ['created_by']) {
                            customdata << ['name':'created_by','value':session.curuser?.id,'iskey':false]
                        }
                        else if(field.name in ['created_date']) {
                            customdata << ['name':'created_date','value':new Date().format('yyyy-MM-dd HH:mm'),'iskey':false]
                        }
                    }
                }
                // Also handle non-field params (excluding internal ones)
                params.each { k, v ->
                    if(!(k in internalParams) && !(k in fieldNames) && v) {
                        customdata << ['name':k,'value':v,'iskey':false]
                    }
                }
            }
        }
        respond new PortalTrackerData(params), model:[trackers:trackers,tracker:tracker,customdata:customdata,keydata:keydata]
    }


    def data_dump = {
      println "Dumping data for g6portal"
	    def processing = PortalSetting.namedefault('datadump_processing',0)
	    if(!processing){
		    def psetting = PortalSetting.findByName('datadump_processing')
		    if(psetting){
			    psetting.number = 1
		    }
		    else{
			    psetting = new PortalSetting(module:'portal',name:'datadump_processing',type:'Number',number:1)
		    }
            PortalTrackerData.withTransaction { transaction -> 
                psetting.save(flush:true)
            }
		    def ctx = startAsync()
		    ctx.start {
                def sql = new Sql(sessionFactory.currentSession.connection())
                def dump_tracker = PortalTracker.findByModuleAndSlug('data_dumper','data_dumper')
                if(dump_tracker) {
                    def dumpquery = "select * from " + dump_tracker.data_table()
                    sql.eachRow(dumpquery) { dumprow->
                        def target_tracker = PortalTracker.findByModuleAndSlug(dumprow['tracker_module'],dumprow['tracker_slug'])
                        if(target_tracker) {
                            def fields = []
                            def ftags = null
                            if(target_tracker.excelfields){
                                ftags = target_tracker.excelfields.tokenize(',')*.trim()
                            }
                            else if(target_tracker.listfields){
                                ftags = target_tracker.listfields.tokenize(',')*.trim()
                            }
                            ftags.each { ftag->
                                def tfield = PortalTrackerField.createCriteria().get(){
                                    'eq'('tracker',target_tracker)
                                    'eq'('name',ftag)
                                }
                                if(tfield){
                                    fields << tfield
                                }
                            }
                            println "Dumping " + target_tracker
                            def wb = new SXSSFWorkbook(100)

                            Sheet sheet = wb.createSheet(target_tracker.name.replaceAll("[^A-Za-z0-9]"," "))
                            Row headerRow = sheet.createRow(0)
                            def curpos = 0
                            (fields*.label).each { dh->
                                Cell cell = headerRow.createCell(curpos++)            
                                cell.setCellValue(dh)
                            }
                            if(target_tracker.excel_audit) {
                                Cell cell = headerRow.createCell(curpos++)
                                cell.setCellValue("Audit Trail")
                            }
                            def currow = 1
                            def curuser = null
                            if(params.user_id && params.user_id in PortalSetting.namedefault(target_tracker.slug + "_anon_excel",[])){
                                curuser = User.findByUserID(params.user_id)
                            }
                            def query = target_tracker.listquery(sessionFactory.currentSession.connection(),params,curuser,"select " + (fields*.name).join(',') + " ")
                            sql.eachRow(query['query'],query['qparams']) { row->
                                println "Writing to excel " + row
                                curpos = 0
                                Row excelrow = sheet.createRow(currow)
                                fields.each { field->
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
                                            def othertracker = Tracker.findBySlug(field.field_options)
                                            def datas = sql.firstRow("select * from " + othertracker.data_table() + " where id=" + row[field.name])
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
                                    else if(field.field_type=='Checkbox'){
                                        def chck = PortalSetting.namedefault('rename_checkboxname',[])
                                        def slugsexcel = PortalSetting.findByName("changebooleannameinexcel").text.tokenize(',')

                                        slugsexcel.any { slug->
                                            if(slug==target_tracker.slug)
                                            { 
                                                if (fieldval == true){
                                                    cell.setCellValue(chck[0])
                                                }else{
                                                    cell.setCellValue(chck[1])
                                                }
                                            }else{
                                                cell.setCellValue(fieldval)
                                            }
                                        }
                                    }
                                    else if(field.field_query){
                                        def curval = sql.firstRow(field.evalquery(session,row))?.value
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
                                if(target_tracker.excel_audit) {
                                    def userroles = target_tracker.user_roles(datasource,curuser,row['id'])
                                    def userrules = ''
                                    if(userroles.size()){
                                        def currules = []
                                        userroles.each { urole->
                                            currules << " allowedroles like '%" + urole.name + "%' "
                                        }
                                        userrules = "and (allowedroles = 'null' or allowedroles = '' or " + currules.join('or') + ")"
                                    }
                                    Cell cell = excelrow.createCell(curpos++)
                                    def audit_trail = ""
                                    query = "select * from " + target_tracker.trail_table() + " where [" + target_tracker.slug + "_id]=" + row['id'] + " $userrules order by update_date desc,id desc"
                                    def rows = sql.rows(query)
                                    def first = true
                                    rows.each { auditrow ->
                                        if(!first) {
                                            audit_trail += '----------------------------------------------------\n\r'
                                        }
                                        else {
                                            first = false
                                        }
                                        audit_trail += auditrow['description']
                                        /* if(auditrow['attachment_id']){
                                            def attachment = FileLink.get(auditrow['attachment_id'])
                                            out << "Attached file : " + filelink(slug:attachment.slug) + "<br/>"
                                        } */
                                        def updater = User.get(auditrow['updater_id'])
                                        audit_trail += '\n\rUpdated by: ' + updater?.name
                                        audit_trail += '\n\rUpdated on: ' + formatDate(format:"HH:mm a dd-MMM-yy",date:auditrow['update_date'])
                                        audit_trail += '\n\r\n\r'
                                    }
                                    cell.setCellValue(audit_trail)
                                }
                                currow++
                            }
                            try{
                                def xlfile = new File(dumprow['target_file']).newOutputStream()
                                wb.write(xlfile)
                                wb.dispose()
                                xlfile.close()
                            }
                            catch(Exception exp){
                                println "Tracker download list excel error writing:" + exp
                            }
                        }
                    }
                }
			    def dsetting = PortalSetting.findByName('datadump_processing')
                if(dsetting) {
                    dsetting.number = 0
                    PortalTrackerData.withTransaction { transaction -> 
                        dsetting.save(flush:true)
                    }
                }
            }
        }
        else {
            println "Already doing data dump"
        }
        redirect controller:"portalTracker", action:"list", method:"GET", params:['module':'data_dumper','slug':'data_dumper']
    }

    def customfield() {
      def sessiondata = sessionFactory.currentSession.connection()
      def sql = new Sql(sessiondata)
      def curfield = null
      def choices = null
      def otherfield = null
      if(params.id) {
        curfield = PortalTrackerField.get(params.id)
        if(curfield) {
          if(curfield.field_type=='BelongsTo') {
            def mp = curfield.field_options.tokenize(':')
            def othertracker = null
            otherfield = curfield.field_format
            if(mp.size()==1) {
                othertracker = PortalTracker.findByModuleAndSlug(curfield.tracker.module,curfield.field_options)
            }
            else if(mp.size()>1) {
                othertracker = PortalTracker.findByModuleAndSlug(mp[0],mp[1])
                if(mp.size()>2) {
                    otherfield = mp[2]
                }
            }
            def objquery = "select id," + otherfield + " from " + othertracker.data_table()
            choices = sql.rows(objquery)
          }
        }
      }
      [curfield:curfield,choices:choices,otherfield:otherfield]
    }

    def save(PortalTrackerData portalTrackerData) {
        if (portalTrackerData == null) {
            notFound()
            return
        }
        try {
            if(params.tracker_id) {
                portalTrackerData.tracker = portalTrackerService.get(params.tracker_id)
            }
            portalTrackerData.module = portalTrackerData.tracker.module
            def f = request.getFile('fileupload')
            if (f.empty) {
                flash.message = 'file cannot be empty'
                render(view: 'create')
                return
            }
            // Bulk-upload files were previously written to disk unchecked. Attachment
            // mode blocks dangerous extensions and spoofed known types while still
            // accepting the spreadsheet formats these uploads actually use.
            def filemanagermax = PortalSetting.namedefault('filemanager_max_' + session.curuser?.userID,50000000)
            def uploadvalidation = FileSecurityValidator.validateAttachment(f,filemanagermax)
            if (!uploadvalidation.valid) {
                flash.message = "File upload failed: ${uploadvalidation.errors.join(', ')}"
                render(view: 'create')
                return
            }
            def fileName = uploadvalidation.sanitizedFilename
            def curfolder = System.getProperty("user.dir")
            def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
            folderbase += '/' + portalTrackerData.tracker.module + '/' + portalTrackerData.tracker.slug
            if(!(new File(folderbase).exists())){
                new File(folderbase).mkdirs()
            }
            if(new File(folderbase).exists()){
              def copytarget = FileSecurityValidator.createSecurePath(folderbase, fileName)
              f.transferTo(new File(copytarget))
              portalTrackerData.path = copytarget
            }
            try {
              portalTrackerData.header_start = params.header_start?.toInteger()?:1
            }
            catch(Exception exp) {
              portalTrackerData.header_start = 1
            }
            try {
              portalTrackerData.header_end = params.header_end?.toInteger()?:portalTrackerData.header_start
            }
            catch(Exception exp) {
              portalTrackerData.header_end = portalTrackerData.header_start
            }
            try {
              portalTrackerData.data_row = params.data_row?.toInteger()?:portalTrackerData.header_end + 1
            }
            catch(Exception exp) {
              portalTrackerData.data_row = portalTrackerData.header_end + 1
            }
            portalTrackerData = portalTrackerDataService.save(portalTrackerData)
        } catch (ValidationException e) {
            respond portalTrackerData.errors, view:'create'
            return
        }

        def excelfields = []
        def setfields = [:]
        def customdata = [:]
        def fields = []

        excelfields << ['id':'ignore','name':'Ignore']
        excelfields << ['id':'custom','name':'Custom']

        PoiExcel poiExcel = new PoiExcel()
        poiExcel.headerstart = portalTrackerData.header_start?:1
        poiExcel.headerend = portalTrackerData.header_end?:1
        fields = poiExcel.getHeaders(portalTrackerData.path,portalTrackerData.excel_password)

        params.each { key,dparm->
            if(dparm=='All'){
                params[key]=null
            }
        }
        
        fields.sort{ it.name }.each { field->
            if(field.name && field.name.endsWith('_')) {
                field.name = field.name[0..-2]
            }
            def foundfield = PortalTrackerField.findByTrackerAndNameIlike(portalTrackerData.tracker,field.name)
            if(foundfield){
                setfields[foundfield.id] = field.col
            }
            excelfields << ['id':field.col,'name':field.text]
        }

        // Set customdata from URL parameters for fields with defaults
        portalTrackerData.tracker.fields.each { field ->
            if (params[field.name]) {
                customdata[field.id] = params[field.name]
                setfields[field.id] = 'custom'
            }
        }

        [portalTrackerData:portalTrackerData,excelfields:excelfields,setfields:setfields,customdata:customdata]
    }

    def cleardb() {
        def tracker = PortalTracker.get(params.tracker_id)
        def curuser = session.curuser
        if(curuser && tracker && ('Admin' in curuser.modulerole(tracker.module) || curuser.isAdmin)) {
            PortalTrackerData.withTransaction { transaction -> 
                tracker.datas.each { tdata->
                    tdata.tracker.discard()
                    tdata.delete(flush:true)
                }
            }
            tracker.cleardb()
            flash.message = 'Clearing database ' + tracker + ' has been done'
        }
        else {
            flash.message = 'You do not have the clearance to clear database for ' + tracker
        }
        redirect tracker
    }

    def syncupload() {
        println "In syncupload"
        def tracker = PortalTracker.get(params.id)
        if(tracker) {
            println "Got tracker:" + tracker
            def dataupdate_ids = PortalTracker.raw_rows("select distinct dataupdate_id from " + tracker.data_table())
            dataupdate_ids.each { duid ->
                println "Duid:" + duid
                def prevupdate = PortalTrackerData.get(duid['dataupdate_id'])
                if(!prevupdate) {
                    PortalTrackerData.withTransaction { transaction -> 
                        println "Dataupdate not found:" + duid['dataupdate_id']
                        def newdata = new PortalTrackerData(tracker:tracker,module:tracker.module,messages:"Created dataupdate using syncupload",date_created:new Date())
                        newdata.save(flush:true)
                        PortalTracker.raw_execute("update " + tracker.data_table() + " set dataupdate_id=" + newdata.id + " where dataupdate_id=" + duid['dataupdate_id'])
                    }
                }
            }
            redirect tracker
        }
        else {
            redirect controller:"portalTracker", action:"list", method:"GET"
        }
    }

    def cleandb() {
        def tracker = PortalTracker.get(params.tracker_id)
        def curuser = session.curuser
        if(curuser && tracker && ('Admin' in curuser.modulerole(tracker.module) || curuser.isAdmin)) {
            if(tracker.tracker_type!='DataStore') {
                def sessiondata = sessionFactory.currentSession.connection()
                def sql = new Sql(sessiondata)
                PortalTrackerData.withTransaction { transaction -> 
                    sql.execute("delete from " + tracker.data_table() + " where record_status='sys_draft'")
                }
                flash.message = 'Cleaning database ' + tracker + ' has been done'
            }
            else {
                flash.message = 'Cleaning database ' + tracker + ' not done on a datastore'
            }
        }
        else {
            flash.message = 'You do not have the clearance to cleaning the database for ' + tracker
        }
        redirect tracker
    }

    def resetdb() {
        def tracker = PortalTracker.get(params.tracker_id)
        def curuser = session.curuser
        if(curuser && tracker && ('Admin' in curuser.modulerole(tracker.module) || curuser.isAdmin)) {
            try {
                def dataTableName = validateIdentifier(tracker.data_table())
                def sql = new Sql(sessionFactory.currentSession.connection())
                sql.execute("drop table " + dataTableName)
                flash.message = 'Reset database ' + tracker + ' has been done'
            } catch (SecurityException e) {
                log.error("Security violation in resetdb(): ${e.message}")
                flash.message = 'Database operation failed due to security violation'
            } catch (Exception e) {
                log.error("Error dropping data table in resetdb(): ${e.message}")
                flash.message = 'Reset failed: ' + e.message
            }
            if(tracker.tracker_type=='Tracker') {
                try {
                    def trailTableName = validateIdentifier(tracker.trail_table())
                    def sql = new Sql(sessionFactory.currentSession.connection())
                    sql.execute("drop table " + trailTableName)
                    flash.message = 'Reset database ' + tracker + ' has been done'
                } catch (SecurityException e) {
                    log.error("Security violation in resetdb(): ${e.message}")
                    flash.message = 'Database operation failed due to security violation'
                } catch (Exception e) {
                    log.error("Error dropping trail table in resetdb(): ${e.message}")
                    flash.message = 'Reset failed: ' + e.message
                }
            }
        }
        else{
            flash.message = 'You do not have the clearance to reset database for ' + tracker
        }
        redirect tracker
    }

    def doupload() {
        println "Full params after save:" + params
        def update = portalTrackerDataService.get(params.update_id)
        def saveparams = [:]
        params.each { key,val->
            if(key[0]!='_'){
                saveparams[key]=val
            }
        }
        update.messages = "Upload is currently in queue"
        update.savedparams = saveparams as JSON
        PortalTrackerData.withTransaction { transaction ->
            update.save(flush:true)
        }
        // Process in the background so large files cannot time out the request/proxy
        // (same approach as the async XLSX report export). Progress and the final
        // summary are persisted on portal_tracker_data, which uploadsummary polls.
        launchBackgroundUpload(update.id)
        redirect action:"uploadsummary", id: update.id
    }

    private void launchBackgroundUpload(Long updateId) {
        def bgMailService = mailService
        Thread.start {
            try {
                PortalTrackerData.withNewSession {
                    PortalTrackerData.withTransaction {
                        def bgUpdate = PortalTrackerData.get(updateId)
                        if(bgUpdate && bgMailService) {
                            bgUpdate.update(bgMailService)
                        }
                    }
                }
            } catch(Exception e) {
                // update() has already persisted the error to messages via raw SQL
                println "Background dataupdate ${updateId} failed: " + e
            }
        }
    }

    /**
     * Uploads stuck at "Upload is currently in queue" — their background thread
     * died before finishing (typically a server restart mid-processing).
     */
    private List findStuckUploads() {
        return PortalTrackerData.createCriteria().list {
            isNull('uploaded')
            like('messages', 'Upload is currently in queue')
            if(!session.enablesuperuser) {
                'in'('module', session.adminmodules ?: ['__none__'])
            }
            order('id', 'desc')
        }
    }

    def stuckuploads() {
        def curuser = session.curuser
        if(!curuser) { notFound(); return }
        [stuckUploads: findStuckUploads(), curuser: curuser]
    }

    /**
     * Restart one stuck upload: remove the partial rows the interrupted run left
     * behind, then re-run the load in a fresh background thread.
     */
    def requeueupload(Long id) {
        def curuser = session.curuser
        def update = portalTrackerDataService.get(id)
        if(!curuser || !update) { notFound(); return }
        if(!(session.enablesuperuser || (session.adminmodules && update.module in session.adminmodules))) {
            flash.message = "You do not have the clearance to restart upload ${id}"
            redirect action:'stuckuploads'
            return
        }
        if(update.uploaded) {
            flash.message = "Upload ${id} has already completed - nothing to restart"
            redirect action:'stuckuploads'
            return
        }
        def problem = requeueOne(update)
        if(problem) {
            flash.message = problem
            redirect action:'stuckuploads'
        }
        else {
            redirect action:'uploadsummary', id: update.id
        }
    }

    /** Restart every stuck upload the current user can administer. */
    def requeueall() {
        def curuser = session.curuser
        if(!curuser) { notFound(); return }
        def restarted = 0
        def problems = []
        findStuckUploads().each { upd ->
            def problem = requeueOne(upd)
            if(problem) { problems << problem } else { restarted++ }
        }
        flash.message = "Restarted ${restarted} upload(s)." + (problems ? " Skipped: " + problems.join('; ') : "")
        redirect action:'stuckuploads'
    }

    /**
     * Clean up partial rows from the interrupted run and relaunch the upload.
     * Returns null on success, or a message describing why it cannot be restarted.
     */
    private String requeueOne(PortalTrackerData update) {
        if(!update.savedparams) {
            return "Upload ${update.id} has no saved column mapping - delete it and upload the file again"
        }
        if(!update.path || !(new File(update.path).exists())) {
            return "Upload ${update.id} - the uploaded file is no longer on disk, upload it again"
        }
        try {
            PortalTrackerData.withTransaction {
                def dataTableName = validateIdentifier(update.tracker.data_table())
                def sql = new Sql(sessionFactory.currentSession.connection())
                sql.execute("delete from " + dataTableName + " where dataupdate_id=" + ((long)update.id))
            }
        } catch(Exception e) {
            println "Could not clean partial rows for dataupdate ${update.id}: " + e
            return "Upload ${update.id} - could not clean up partial rows: ${e.message}"
        }
        launchBackgroundUpload(update.id)
        return null
    }

    def uploadsummary(Long id) {
        def update = portalTrackerDataService.get(id)
        if (!update) { notFound(); return }
        // Force refresh from DB — raw SQL in update() bypasses the Hibernate L2 cache
        PortalTrackerData.withSession { session -> session.refresh(update) }
        [portalTrackerData: update]
    }

    def edit(Long id) {
        respond portalTrackerDataService.get(id)
    }

    def update(PortalTrackerData portalTrackerData) {
        if (portalTrackerData == null) {
            notFound()
            return
        }
        try {
            def f = request.getFile('fileupload')
            if (!f.empty) {
                // Same validation as the create path — see comment there.
                def filemanagermax = PortalSetting.namedefault('filemanager_max_' + session.curuser?.userID,50000000)
                def uploadvalidation = FileSecurityValidator.validateAttachment(f,filemanagermax)
                if (!uploadvalidation.valid) {
                    flash.message = "File upload failed: ${uploadvalidation.errors.join(', ')}"
                    respond portalTrackerData, view:'edit'
                    return
                }
                def fileName = uploadvalidation.sanitizedFilename
                def curfolder = System.getProperty("user.dir")
                def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
                folderbase += '/' + portalTrackerData.tracker.module + '/' + portalTrackerData.tracker.slug
                if(!(new File(folderbase).exists())){
                    new File(folderbase).mkdirs()
                }
                if(new File(folderbase).exists()){
                    def copytarget = FileSecurityValidator.createSecurePath(folderbase, fileName)
                    f.transferTo(new File(copytarget))
                    portalTrackerData.path = copytarget
                } 
            }
            portalTrackerDataService.save(portalTrackerData)
        } 
        catch (ValidationException e) {
            respond portalTrackerData.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerData.label', default: 'PortalTrackerData'), portalTrackerData.id])
                redirect portalTrackerData.tracker
            }
            '*'{ respond portalTrackerData, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        portalTrackerDataService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerData.label', default: 'PortalTrackerData'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }
    /**
     * Data Dump. Asynchronous, the same shape as the XLSX report pages in PortalPageController:
     * the first request starts a background build and immediately returns a small polling page,
     * the poll asks whether the file is ready, and the third request streams it.
     *
     * Synchronous was not survivable on a large tracker. The browser - and anything proxying it -
     * sat on an open connection for ten minutes with nothing to show, and the request eventually
     * died carrying an xlsx filename on a 500, which renders as a broken download rather than an
     * error. Both dumps go through here, readable and forimport alike.
     */
    def datadump(Long id) {
        if (id == null) {
            notFound()
            return
        }

        // Is it ready yet?
        if(params.async_token && params.async_status) {
            def job = dumpJobs[params.async_token as String]
            if(!job) {
                render(contentType: 'application/json', text: '{"ready":false,"error":"Job not found or expired"}')
            } else if(job.error) {
                render(contentType: 'application/json', text: new groovy.json.JsonBuilder([ready:false, error:job.error.toString()]).toString())
            } else {
                render(contentType: 'application/json', text: "{\"ready\":${job.done}}")
            }
            return
        }

        // Hand the file over, then forget the job and delete the temp file.
        if(params.async_token && !params.async_status) {
            def tkn = params.async_token as String
            def job = dumpJobs[tkn]
            if(job?.done && job?.file) {
                def tmpFile = new File(job.file as String)
                if(tmpFile.exists()) {
                    dumpJobs.remove(tkn)
                    response.setContentType("application/octet-stream")
                    response.setHeader("Content-Disposition", "attachment;filename=${job.filename}.xlsx")
                    response.setContentLength((int)tmpFile.length())
                    tmpFile.withInputStream { is -> response.outputStream << is }
                    response.outputStream.flush()
                    tmpFile.delete()
                } else {
                    response.sendError(404, "Dump file not found")
                }
            } else if(job?.error) {
                dumpJobs.remove(tkn)
                response.sendError(500, "Dump failed: ${job.error}")
            } else {
                response.sendError(404, "Dump not ready or expired")
            }
            return
        }

        def tracker = PortalTracker.get(id)
        if(!tracker) {
            notFound()
            return
        }

        // Everything the worker needs, captured while the request still exists.
        def bgToken = java.util.UUID.randomUUID().toString()
        def bgParams = new LinkedHashMap(params)
        def bgCurUser = session.curuser
        def bgSessionAttrs = [curuser: session.curuser, userid: session.userid]
        def bgId = id
        def label = tracker.slug + (bgParams.forimport?.toString() == '1' ? '_forimport' : '')

        dumpJobs[bgToken] = [done: false, file: null, error: null, filename: label]

        Thread.start {
            try {
                // Its own Hibernate session: the request's is gone the moment the polling page
                // is returned, and the readable dump resolves names through GORM per row.
                PortalTrackerData.withNewSession {
                    def built = buildDumpFile(bgId, bgParams, bgCurUser, bgSessionAttrs)
                    dumpJobs[bgToken] = [done: true, file: built.absolutePath, error: null, filename: label]
                    println "Data dump completed: ${built.absolutePath} (${built.length()} bytes)"
                }
            } catch(Throwable e) {
                // Throwable, not Exception: an Error (OutOfMemoryError on a big workbook,
                // NoClassDefFoundError) left the job at done:false and the page spinning forever.
                println "Data dump failed: " + e
                e.printStackTrace()
                PortalErrorLog.capture(e, "data dump of tracker ${bgId} (${label}) for ${bgCurUser?.userID}",
                                       [controller: 'portalTrackerData', action: 'datadump',
                                        params: bgParams, slug: label])
                // e.message is null for an NPE or UnsupportedOperationException, which left the
                // page saying only "Dump failed". The type at least says what kind of failure.
                dumpJobs[bgToken] = [done: true, file: null,
                                     error: "Dump failed: " + e.getClass().simpleName +
                                            (e.message ? " - " + e.message : "") + " (details in the error log)"]
            }
        }

        render(contentType: 'text/html', text: dumpWaitingPage(bgToken, tracker.name))
    }

    /** The page the browser sits on while the dump builds. */
    private String dumpWaitingPage(String token, String trackerName) {
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Preparing data dump\u2026</title>
<style>
body{font-family:Arial,Helvetica,sans-serif;text-align:center;padding:60px 20px;background:#f5f5f5;color:#333;}
.box{background:#fff;border-radius:8px;padding:40px;display:inline-block;box-shadow:0 2px 8px rgba(0,0,0,.1);min-width:340px;}
h2{margin:0 0 6px;font-size:20px;}
.sub{color:#777;font-size:13px;margin:0 0 18px;}
#status{color:#666;margin:16px 0 0;}
.spinner{display:inline-block;width:40px;height:40px;border:4px solid #ddd;border-top-color:#0078d4;border-radius:50%;animation:spin .8s linear infinite;margin-bottom:16px;}
@keyframes spin{to{transform:rotate(360deg);}}
.error{color:#c00;}
</style>
</head>
<body>
<div class="box">
  <div class="spinner" id="spinner"></div>
  <h2>Preparing data dump</h2>
  <p class="sub">${trackerName}</p>
  <p id="status">Building the file on the server. A large tracker takes a minute or two &mdash; leave this tab open.</p>
</div>
<script>
var token = '${token}';
var baseUrl = window.location.href.split('?')[0];
var checkUrl = baseUrl + '?async_token=' + token + '&async_status=1';
var downloadUrl = baseUrl + '?async_token=' + token;
var waited = 0;
function check() {
    fetch(checkUrl, {credentials:'same-origin'})
        .then(function(r){ return r.json(); })
        .then(function(data){
            if(data.ready){
                document.getElementById('spinner').style.display='none';
                document.getElementById('status').textContent='Ready \u2014 downloading\u2026';
                var a=document.createElement('a');
                a.href=downloadUrl; a.download='';
                document.body.appendChild(a); a.click(); document.body.removeChild(a);
                document.getElementById('status').textContent='Download started. You can close this tab.';
            } else if(data.error){
                document.getElementById('spinner').style.display='none';
                var st=document.getElementById('status'); st.textContent=data.error; st.className='error';
            } else {
                waited += 2;
                if(waited % 30 === 0){
                    document.getElementById('status').textContent='Still building\u2026 ' + waited + 's so far.';
                }
                setTimeout(check, 2000);
            }
        })
        .catch(function(){ setTimeout(check, 2000); });
}
setTimeout(check, 2000);
</script>
</body>
</html>"""
    }


    /**
     * Builds the dump workbook and returns the temp file it was written to.
     *
     * Runs on a background thread with no web request behind it, so everything it needs is
     * passed in: `params` is a copy taken at request time, `curuser` the user who asked, and
     * `sessionAttrs` stands in for the http session where a field query wants one.
     */
    private File buildDumpFile(Long id, Map params, def curuser, Map sessionAttrs) {
        def session = sessionAttrs
        def sessiondata = dataSource.getConnection()
        def sql = new Sql(sessiondata)
        try {
        def tracker = PortalTracker.get(id)
        def fields = []
        def ftags = null
        if(tracker.excelfields){
            ftags = tracker.excelfields.tokenize(',')*.trim()
        }
        else if(tracker.listfields){
            ftags = tracker.listfields.tokenize(',')*.trim()
        }
        ftags.each { ftag->
            def tfield = PortalTrackerField.createCriteria().get(){
                'eq'('tracker',tracker)
                'eq'('name',ftag)
            }
            if(tfield){
                fields << tfield
            }
        }
        tracker.fields.each { cfield ->
            if(!(cfield in fields)) {
                if(cfield.field_type!='HasMany') {
                    fields << cfield
                }
            }
        }
        // A FieldGroup is a rendering container with no column of its own, so reading it off
        // the row failed the whole dump ("Column not found") for any tracker that has one.
        fields = fields.findAll { !(it.field_type in ['FieldGroup','HasMany']) }
        // ?computed=0 drops the fields whose value comes from a field_query rather than
        // from the row. Two reasons to want that when the dump is being used to move data
        // between environments, which is what this action is for:
        //
        //   it is not the row's data.  A field_query is evaluated at display time - aging
        //   on itis_reporting is `datediff(day, tat_deadline, ...)`. Re-importing the
        //   number it printed would freeze a value that is supposed to keep moving.
        //
        //   it is nearly all of the time.  The query runs once PER ROW. On the 125k-row
        //   itis_reporting table that is 125k extra round trips for columns the importer
        //   should ignore anyway.
        //
        // Left opt-out rather than opt-in so an existing dump keeps every column it had.
        //
        // ?forimport=1 is the switch to reach for when the dump is going to be fed back in
        // through a data update - moving a tracker between environments, which is what this
        // action is for. It implies computed=0 and turns on `raw` below.
        def forimport = (params.forimport?.toString() == '1')
        if(forimport || params.computed?.toString() == '0') {
            fields = fields.findAll { !it.field_query }
        }
        // Reference fields normally export what a person needs to read - a user's name, a
        // branch's name, the linked record's title, the file's name. None of those can go
        // back where they came from: the columns behind them hold ids, so an import of the
        // default dump dies on every row with "Error converting data type nvarchar to
        // numeric". `raw` writes the stored value instead, at the cost of being unreadable.
        def raw = forimport || (params.raw?.toString() == '1')
        def wb = new SXSSFWorkbook(100)

        Sheet sheet = wb.createSheet(tracker.name.replaceAll("[^A-Za-z0-9]"," "))
        Row headerRow = sheet.createRow(0)
        def curpos = 0
        // Field NAME, not label. This dump exists to be fed back in through a data update,
        // and the importer matches a header against the field name and the field label by
        // longest-common-subsequence, taking the better score - so a header that IS the name
        // scores exactly and cannot be beaten by a near-miss on some other column. Labels are
        // free text ("Customer Acct Number (Starworks)"), may be duplicated across fields and
        // may be null; names are the identifiers the rest of the migration format already uses.
        // The tracker list's own Download Excel still writes labels - that one is read by
        // people, not by the importer.
        (fields*.name).each { dh->
            Cell cell = headerRow.createCell(curpos++)
            cell.setCellValue(dh)
        }
        if(tracker.excel_audit) {
            Cell cell = headerRow.createCell(curpos++)
            cell.setCellValue("Audit Trail")
        }
        def currow = 1
        if(params.user_id && params.user_id in PortalSetting.namedefault(params.slug + "_anon_excel",[])){
            curuser = User.findByUserID(params.user_id)
        }
        if('max' in params) {
            params.remove('max')
        }
        if('offset' in params) {
            params.remove('offset')
        }
        if('id' in params) {
            params.remove('id')
        }
        // Which fields actually need fieldval(), decided ONCE rather than per cell.
        //
        // fieldval() opens with PortalSetting.namedefault("tracker_objects") - a GORM call - and
        // for most field types it then just hands the value straight back. Calling it for every
        // cell is ~57 x 125,138 = 7 million GORM calls on one dump of itis_reporting, and the
        // connection behind them does not survive that: the readable dump died at ~624s with
        // "The connection is closed" whether it ran in the request, in its own Hibernate
        // session, or on a background thread.
        //
        // It genuinely transforms only these: User/File/TreeNode resolve to a name, Date blanks
        // the 1900-01-01 sentinel, a type listed in tracker_objects (Branch, TreeReport) resolves
        // through another tracker, and is_encrypted/encode_exception rewrite the value. Everything
        // else falls through fieldval's else branch unchanged, so reading the row directly gives
        // the identical cell. On itis_reporting that is 5 fields of 57 - a 91% cut.
        def trackerObjectTypes = [:]
        try { trackerObjectTypes = PortalSetting.namedefault("tracker_objects",[]) ?: [:] } catch(Exception e) { trackerObjectTypes = [:] }
        def needsFieldval = [:]
        fields.each { f ->
            needsFieldval[f.id] = (f.field_type in ['User','File','TreeNode','Date']) ||
                                  (f.field_type in trackerObjectTypes) ||
                                  (f.is_encrypted ? true : false) ||
                                  (f.encode_exception ? true : false)
        }

        def query = tracker.listquery(params,curuser,"select all * ")
        def rename_checkbox = PortalSetting.namedefault(tracker.module + '.' + tracker.slug + '_rename_checkbox',[])
        sql.eachRow(query['query'],query['qparams']) { row->
            curpos = 0
            Row excelrow = sheet.createRow(currow)
            fields.each { field->
                Cell cell = excelrow.createCell(curpos++)
                // In raw mode take the column straight off the row and never call fieldval().
                //
                // Two reasons, and the second is why the full-table dump kept dying. fieldval()
                // resolves a User/Branch/BelongsTo/File to its display value, which is exactly
                // what raw mode does not want. And it opens with
                // PortalSetting.namedefault("tracker_objects") - a GORM call, on the Hibernate
                // session's connection, once per cell. That is ~56 x 125,138 = 7 million GORM
                // calls for one dump of itis_reporting, and after about ten minutes that session's
                // connection is gone: every row then threw "The connection is closed" from THIS
                // line. Giving the cursor its own DataSource connection did not help, because the
                // connection that died was never the cursor's.
                def fieldval = (raw || !needsFieldval[field.id]) ? row[field.name]
                                                                 : field.fieldval(row[field.name])
                if(field.field_type=='Date'){
                    def ld = dumpLocalDateTime(fieldval)
                    if(ld){
                        cell.setCellValue(ld.toLocalDate().toString())
                    }
                    else if(fieldval){
                        cell.setCellValue(fieldval.toString())
                    }
                }
                else if(field.field_type=='DateTime'){
                    def ldt = dumpLocalDateTime(fieldval)
                    if(!ldt && fieldval){
                        cell.setCellValue(fieldval.toString())
                    }
                    if(ldt){
                        if(raw) {
                            // Seconds are not decoration here. The loader parses a DateTime with
                            // Timestamp.valueOf(), which demands yyyy-MM-dd HH:mm:ss - the minute
                            // form this dump has always written throws, and the column is dropped
                            // from the insert without a word. Every DateTime on every row came
                            // back null before this.
                            cell.setCellValue(ldt.format(java.time.format.DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss')))
                        }
                        else {
                            cell.setCellValue(ldt.toString().replace('T',' ').substring(0,16))
                        }
                    }
                }
                else if(raw && field.field_type in ['User','Branch','BelongsTo','File']){
                    // The id exactly as stored, and as TEXT with no decimal part. Writing it
                    // as a POI number puts "246.0" in the sheet, and the loader hands that
                    // straight to an integer column, which refuses it - the same conversion
                    // error the display values caused, just further down the line.
                    def stored = row[field.name]
                    if(stored != null) {
                        if(stored instanceof Number) {
                            def n = (Number) stored
                            cell.setCellValue(n.doubleValue() == Math.floor(n.doubleValue())
                                                ? Long.toString(n.longValue())
                                                : n.toString())
                        }
                        else {
                            cell.setCellValue(stored.toString())
                        }
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
                            def datas = sql.firstRow("select * from " + othertracker.data_table() + " where id=" + row[field.name])
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
                    if(rename_checkbox.size() && !raw) {
                        if (fieldval == true){
                            cell.setCellValue(rename_checkbox[0])
                        }else{
                            cell.setCellValue(rename_checkbox[1])
                        }
                    }
                    else if(fieldval != null) {
                          cell.setCellValue(fieldval)
                    }
                }
                else if(field.field_type=='File'){
                    // A File field is null on every row that never carried an upload, and
                    // FileLink.get(null) hands back null - so reading .name off it aborted the
                    // whole dump on its first empty row. Optional attachments are the norm:
                    // all 125,138 itis_reporting cases have none.
                    if(fieldval) {
                        cell.setCellValue(fieldval.name)
                    }
                }
                else if(field.field_query){
                    def curval = sql.firstRow(field.evalquery(session,row))?.value
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
            if(tracker.excel_audit) {
                def userroles = tracker.user_roles(curuser,row['id'])
                def userrules = ''
                if(userroles.size()){
                    def currules = []
                    userroles.each { urole->
                        currules << " allowedroles like '%" + urole.name + "%' "
                    }
                    userrules = "and (allowedroles = 'null' or allowedroles = '' or " + currules.join('or') + ")"
                }
                Cell cell = excelrow.createCell(curpos++)
                def audit_trail = ""
                query = "select * from " + tracker.trail_table() + " where [" + tracker.slug + "_id]=" + row['id'] + " $userrules order by update_date desc,id desc"
                def rows = sql.rows(query)
                def first = true
                rows.each { auditrow ->
                    if(!first) {
                        audit_trail += '----------------------------------------------------\n\r'
                    }
                    else {
                        first = false
                    }
                    audit_trail += auditrow['description']
                    /* if(auditrow['attachment_id']){
                        def attachment = FileLink.get(auditrow['attachment_id'])
                        out << "Attached file : " + filelink(slug:attachment.slug) + "<br/>"
                    } */
                    def updater = User.get(auditrow['updater_id'])
                    audit_trail += '\n\rUpdated by: ' + updater?.name
                    audit_trail += '\n\rUpdated on: ' + (auditrow['update_date'] ? new java.text.SimpleDateFormat("HH:mm a dd-MMM-yy").format(auditrow['update_date']) : '')
                    audit_trail += '\n\r\n\r'
                }
                cell.setCellValue(audit_trail)
            }
            currow++
        }
        def tmpFile = File.createTempFile("g5dump_", ".xlsx")
        tmpFile.deleteOnExit()
        tmpFile.withOutputStream { fos -> wb.write(fos) }
        try { wb.dispose() } catch(Exception e) { }
        return tmpFile
        }
        finally {
            try { sql?.close() } catch(Exception ce) { }
            try { sessiondata?.close() } catch(Exception ce) { println "datadump: could not close its connection: " + ce }
        }
    }

    /**
     * Whatever the driver handed back for a Date/DateTime column, as a LocalDateTime - or null
     * when there is nothing to convert.
     *
     * The column type does not always match the field type: the importer never alters an
     * existing column, so a field switched from Date to DateTime still sits on a `date` column
     * and comes back as java.sql.Date. java.sql.Date.toInstant() throws
     * UnsupportedOperationException - with no message - and that one cell failed the whole dump
     * with nothing but "Dump failed" to show for it. A varchar column hands back a String.
     */
    private static java.time.LocalDateTime dumpLocalDateTime(def v) {
        if(v == null) return null
        if(v instanceof java.time.LocalDateTime) return v
        if(v instanceof java.time.LocalDate) return v.atStartOfDay()
        if(v instanceof java.sql.Timestamp) return v.toLocalDateTime()
        if(v instanceof java.sql.Date) return v.toLocalDate().atStartOfDay()
        if(v instanceof Date) return v.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        if(v instanceof java.time.OffsetDateTime) return v.toLocalDateTime()
        def txt = v.toString().trim()
        if(!txt) return null
        try { return java.sql.Timestamp.valueOf(txt.length() == 16 ? txt + ':00' : txt).toLocalDateTime() } catch(Exception ignored) { }
        try { return java.time.LocalDate.parse(txt.take(10)).atStartOfDay() } catch(Exception ignored) { }
        return null
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerData.label', default: 'PortalTrackerData'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
