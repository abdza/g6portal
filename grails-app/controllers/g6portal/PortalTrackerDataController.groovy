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

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

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
        if(params.tracker_id) {
            tracker = PortalTracker.get(params.tracker_id)
            if(tracker) {
                tracker.fields.each { field ->
                    if (params[field.name]) {
                        customdata << ['name':field.name,'value':params[field.name]]
                    }
                    else {
                        if(field.name in ['created_by']) {
                            customdata << ['name':'created_by','value':session.curuser?.id]
                        }
                        else if(field.name in ['created_date']) {
                            customdata << ['name':'created_date','value':new Date().format('yyyy-MM-dd HH:mm') ]
                        }
                    }
                }
            }
        }
        respond new PortalTrackerData(params), model:[trackers:trackers,tracker:tracker,customdata:customdata]
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
                                            cell.setCellValue(fieldval.format('yyyy-MM-dd'))
                                        }
                                    }
                                    else if(field.field_type=='DateTime'){
                                        if(fieldval){
                                            cell.setCellValue(fieldval.format('yyyy-MM-dd HH:mm'))
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
            def fileName = f.originalFilename
            def curfolder = System.getProperty("user.dir")
            def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
            folderbase += '/' + portalTrackerData.tracker.module + '/' + portalTrackerData.tracker.slug
            if(!(new File(folderbase).exists())){
                new File(folderbase).mkdirs()
            }
            if(new File(folderbase).exists()){
              def copytarget = folderbase+'/'+fileName
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
        fields = poiExcel.getHeaders(portalTrackerData.path)

        params.each { key,dparm->
            if(dparm=='All'){
                params[key]=null
            }
        }
        
        fields.sort{ it.name }.each { field->
            def foundfield = PortalTrackerField.findByTrackerAndName(portalTrackerData.tracker,field.name)
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
        if(curuser && tracker && 'Admin' in curuser.modulerole(tracker.module)) {
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
        if(curuser && tracker && 'Admin' in curuser.modulerole(tracker.module)) {
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
        if(curuser && tracker && 'Admin' in curuser.modulerole(tracker.module)) {
            def sessiondata = sessionFactory.currentSession.connection()
            def sql = new Sql(sessiondata)
            PortalTrackerData.withTransaction { transaction -> 
                sql.execute("drop table " + tracker.data_table())
                if(tracker.tracker_type!='DataStore') {
                    sql.execute("drop table " + tracker.trail_table())
                }
            }
            flash.message = 'Reset database ' + tracker + ' has been done'
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
            if(mailService) {
              update.update(mailService)
            }
        }
        flash.message = 'Update ' + update + ' has been run'
        redirect controller:"portalTracker", action:"list", method:"GET", params:['module':update.tracker.module,'slug':update.tracker.slug]
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
                def fileName = f.originalFilename
                def curfolder = System.getProperty("user.dir")
                def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
                folderbase += '/' + portalTrackerData.tracker.module + '/' + portalTrackerData.tracker.slug
                if(!(new File(folderbase).exists())){
                    new File(folderbase).mkdirs()
                }
                if(new File(folderbase).exists()){
                    def copytarget = folderbase+'/'+fileName
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

    def datadump(Long id) {
        def sessiondata = sessionFactory.currentSession.connection()
        def sql = new Sql(sessiondata)
        if (id == null) {
            notFound()
            return
        }
        def curuser = session.curuser
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
        response.setContentType("application/octet-stream")

        response.setHeader("Content-disposition", "attachment;filename=" + tracker.slug + ".xlsx")

        def wb = new SXSSFWorkbook(100)

        Sheet sheet = wb.createSheet(tracker.name.replaceAll("[^A-Za-z0-9]"," "))
        Row headerRow = sheet.createRow(0)
        def curpos = 0
        (fields*.label).each { dh->
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
        def query = tracker.listquery(params,curuser)
        def rename_checkbox = PortalSetting.namedefault(tracker.module + '.' + tracker.slug + '_rename_checkbox',[])
        sql.eachRow(query['query'],query['qparams']) { row->
            curpos = 0
            Row excelrow = sheet.createRow(currow)
            fields.each { field->
                Cell cell = excelrow.createCell(curpos++)
                def fieldval = field.fieldval(row[field.name])
                if(field.field_type=='Date'){
                    if(fieldval){
                        cell.setCellValue(fieldval.format('yyyy-MM-dd'))
                    }
                }
                else if(field.field_type=='DateTime'){
                    if(fieldval){
                        cell.setCellValue(fieldval.format('yyyy-MM-dd HH:mm'))
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
                    cell.setCellValue(fieldval.name)
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
                    audit_trail += '\n\rUpdated on: ' + formatDate(format:"HH:mm a dd-MMM-yy",date:auditrow['update_date'])
                    audit_trail += '\n\r\n\r'
                }
                cell.setCellValue(audit_trail)
            }
            currow++
        }
        try{
            wb.write(response.outputStream)
            response.outputStream.close()
            wb.dispose()
        }
        catch(Exception exp){
            println "Tracker download list excel error writing:" + exp
        }
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
