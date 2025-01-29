package g6portal

import org.springframework.transaction.annotation.Transactional
import groovy.sql.Sql
import groovy.io.FileType
import grails.web.mapping.LinkGenerator
import groovy.text.Template
import org.grails.gsp.*
import java.util.zip.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension


class PortalService {
    private final ExecutorService executorService = Executors.newFixedThreadPool(10)

    def sessionFactory
    LinkGenerator grailsLinkGenerator

    public field_error_messages(curfield,val,params,curuser) {
        def errormsg = []
        def goterror = false
        curfield.error_checks.each { error->
            if(error.error_type=='Not Empty') {
                if(!val || (val instanceof String && val.trim().size()==0)) {
                    if(error.error_msg) {
                        Binding binding = new Binding()
                        binding.setVariable("field",curfield)
                        binding.setVariable("datas",params)
                        binding.setVariable("params",params)
                        binding.setVariable("curuser",curuser)
                        try {
                            def shell = new GroovyShell(this.class.classLoader,binding)
                            errormsg << shell.evaluate(error.error_msg) + " "
                        }
                        catch(Exception e) {
                            PortalErrorLog.record(params,curuser,'PortalService','field_error_messages',e.toString(),curfield.tracker.slug,curfield.tracker.module)
                        }
                    }
                    else {
                        errormsg << curfield.label + " cannot be empty "
                    }
                    if(!error.allow_submission) {
                        goterror = true
                    }
                }
            }
            else if(error.error_type=='Unique') {
                if(val && val.trim().size()>0) {
                    def qparams = [:]
                    qparams[curfield.name] = val.trim()
                    def prevdata = curfield.tracker.rows(qparams)
                    if(prevdata.size()>0) {
                        if(error.error_msg) {
                            Binding binding = new Binding()
                            binding.setVariable("val",val.trim())
                            binding.setVariable("field",curfield)
                            binding.setVariable("datas",params)
                            binding.setVariable("params",params)
                            binding.setVariable("curuser",curuser)
                            binding.setVariable("prevdata",prevdata)
                            try {
                                def shell = new GroovyShell(this.class.classLoader,binding)
                                errormsg << shell.evaluate(error.error_msg) + " "
                            }
                            catch(Exception e) {
                                PortalErrorLog.record(params,curuser,'PortalService','field_error_messages',e.toString(),curfield.tracker.slug,curfield.tracker.module)
                            }
                        }
                        else {
                            errormsg << curfield.label + " already exists "
                        }
                        if(!error.allow_submission) {
                            goterror = true
                        }
                    }
                }
            }
            else if(error.error_type=='Format') {
                if(val && val.trim().size()>0) {
                    if(!(val.trim() ==~ error.format)) {
                        if(error.error_msg) {
                            Binding binding = new Binding()
                            binding.setVariable("val",val.trim())
                            binding.setVariable("field",curfield)
                            binding.setVariable("datas",params)
                            binding.setVariable("params",params)
                            binding.setVariable("curuser",curuser)
                            try {
                                def shell = new GroovyShell(this.class.classLoader,binding)
                                errormsg << shell.evaluate(error.error_msg) + " "
                            }
                            catch(Exception e) {
                                PortalErrorLog.record(params,curuser,'PortalService','field_error_messages',e.toString(),curfield.tracker.slug,curfield.tracker.module)
                            }
                        }
                        else {
                            errormsg << curfield.label + " need to be in the form of " + error.format
                        }
                        if(!error.allow_submission) {
                            goterror = true
                        }
                    }
                }
            }
            else if(error.error_type=='E-mail') {
                if(val && val.trim().size()>0) {
                    if(!(val.trim() ==~ /[a-zA-Z0-9.!#$%&'*+=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*/)) {
                        if(error.error_msg) {
                            Binding binding = new Binding()
                            binding.setVariable("val",val.trim())
                            binding.setVariable("field",curfield)
                            binding.setVariable("datas",params)
                            binding.setVariable("params",params)
                            binding.setVariable("curuser",curuser)
                            try {
                                def shell = new GroovyShell(this.class.classLoader,binding)
                                errormsg << shell.evaluate(error.error_msg) + " "
                            }
                            catch(Exception e) {
                                PortalErrorLog.record(params,curuser,'PortalService','field_error_messages',e.toString(),curfield.tracker.slug,curfield.tracker.module)
                            }
                        }
                        else {
                            errormsg << curfield.label + " need to be a proper e-mail address "
                        }
                        if(!error.allow_submission) {
                            goterror = true
                        }
                    }
                }
            }
            else if(error.error_type=='Custom') {
                if(error.error_function) {
                    Binding binding = new Binding()
                    binding.setVariable("val",val?.trim())
                    binding.setVariable("field",curfield)
                    binding.setVariable("datas",params)
                    binding.setVariable("params",params)
                    binding.setVariable("curuser",curuser)
                    try {
                        def shell = new GroovyShell(this.class.classLoader,binding)
                        def curmsg = shell.evaluate(error.error_function)
                        if(curmsg && curmsg?.trim().size()>0) {
                            errormsg << curmsg + " "
                            if(!error.allow_submission) {
                                goterror = true
                            }
                        }
                    }
                    catch(Exception e) {
                        PortalErrorLog.record(params,curuser,'PortalService','field_error_messages',e.toString(),curfield.tracker.slug,curfield.tracker.module)
                    }
                }
            }
        }
        return [errormsg, goterror]
    }

    def select(opts) {
        def toreturn = "<select "
        if(opts['name']) {
            toreturn += " id='${opts['name']}' name='${opts['name']}' "
        }
        toreturn += ">"
        opts['from'].each { frm ->
            toreturn += "<option "
            if(opts['value'] && frm==opts['value']) {
                toreturn += "selected"
            }
            toreturn += ">${frm}</option>"
        }
        toreturn += "</select>"
        return toreturn
    }
    
    public field_error_js(curfield,params,session) {
        def val = params[curfield.name]
        def hyperscript = ""
        def fieldclass = ''
        def goterror = false
        // def curuser = User.get(session.userid)
        def curuser = session.curuser
        if(curfield.classes) {
            fieldclass = curfield.classes
        }
        def content = ""
        if(curfield.error_checks.size()) {
            if(curfield.field_type in ['Date','DateTime','Drop Down','File']){
                hyperscript = "hx-trigger='change from:#${curfield.name}' "
            }
            else {
                hyperscript = "hx-trigger='change from:#${curfield.name},keyup from:#${curfield.name} changed delay:500ms' "
            }
            hyperscript += "hx-post='" + grailsLinkGenerator.link(controller:'portalTrackerField',action:'onchange',params:['hx_field_id':curfield.id]) + "' hx-swap='outerHTML' hx-target='this' " 
        }
        def errormsg = []
        if(curfield.hyperscript) {
            Binding binding = new Binding()
            binding.setVariable("field",curfield)
            binding.setVariable("datas",params)
            binding.setVariable("params",params)
            binding.setVariable("curuser",curuser)
            try {
                def shell = new GroovyShell(this.class.classLoader,binding)
                hyperscript += shell.evaluate(curfield.hyperscript)
            }
            catch(Exception e) {
                PortalErrorLog.record(params,curuser,'PortalService','field_error_js',e.toString(),curfield.tracker.slug,curfield.tracker.module)
            }
        }
        def spanclass = ""
        (errormsg,goterror) = field_error_messages(curfield,val,params,curuser)
        if(goterror) {
            spanclass = "fatal_error"
        }
        content += " <span class='text-danger ${spanclass}' ${hyperscript} _='on load update_submit()'>"
        if(errormsg.size()) {
            content += errormsg.join('; ')
        }
        content += "</span>"
        return content
    }

    public String markdownToHtml(String markdown) {
        Parser parser = Parser.builder().extensions(Arrays.asList(TablesExtension.create())).build();
        Node document = parser.parse(markdown);
        return HtmlRenderer.builder().extensions(Arrays.asList(TablesExtension.create())).build().render(document);
    }

    void perform_data_dump() {
	    def processing = PortalSetting.namedefault('datadump_processing',0)
	    if(!processing){
	    // if(1==1){
            PortalModule.withTransaction { current_transaction->
                def psetting = PortalSetting.findByName('datadump_processing')
                if(psetting){
                    psetting.number = 1
                }
                else{
                    psetting = new PortalSetting(name:'datadump_processing',module:'data_dumper',type:'Number',number:1)
                }
                psetting.save(flush:true)

                def sdsettinglog = PortalSetting.findByName('datadump_log')
                if(sdsettinglog) {
                    sdsettinglog.text = "Dumping tracker begin"
                }
                else {
                    sdsettinglog = new PortalSetting(name:'datadump_log',module:'data_dumper',type:'Text',text:"Dumping tracker begin")
                }
                sdsettinglog.save(flush:true)

                executorService.submit({
                    // Your background task here
                    println "Doing data dump"
                    println "Hello"
                    println "In session"
                    PortalModule.withTransaction { internal_transaction->
                    def dump_tracker = PortalTracker.findByModuleAndSlug('data_dumper','data_dumper')
                    println "Dump tracker:" + dump_tracker
                    if(dump_tracker) {
                        println "Found data dump tracker"
                        def dumpquery = "select * from " + dump_tracker.data_table()
                        def dumprows = PortalTracker.raw_rows(dumpquery)
                        dumprows.each { dumprow->
                            println "Doing data dump for:" + dumprow
                            def dsettinglog = PortalSetting.findByName('datadump_log')
                            if(dsettinglog) {
                                dsettinglog.text = "Dumping tracker " + dumprow['tracker_module'] + ":" + dumprow['tracker_slug']
                            }
                            else {
                                dsettinglog = new PortalSetting(name:'datadump_log',module:'data_dumper',type:'Text',text:"Dumping tracker " + dumprow['tracker_module'] + ":" + dumprow['tracker_slug'])
                            }
                            dsettinglog.save(flush:true)
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
                                println "Will write to sheet " + sheet
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
                                def query = target_tracker.listquery(params,curuser,"select " + (fields*.name).join(',') + " ")
                                println "Running query:" + query
                                def rows = PortalTracker.raw_rows(query['query'],query['qparams'])
                                rows.each { row->
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
                                                def othertracker = PortalTracker.findBySlug(field.field_options)
                                                def datas = ("select * from " + othertracker.data_table() + " where id=" + row[field.name])
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
                                            def curval = PortalTracker.raw_firstRow(field.evalquery(session,row))?.value
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
                                        def userroles = target_tracker.user_roles(curuser,row['id'])
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
                                        def audit_rows = PortalTracker.raw_rows(query)
                                        def first = true
                                        audit_rows.each { auditrow ->
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
                                    xlfile = null
                                    wb = null
                                    sheet = null
                                    Thread.sleep(5000)
                                }
                                catch(Exception exp){
                                    println "Tracker download list excel error writing:" + exp
                                    PortalErrorLog.record(params,null,'PortalService','perform_data_dump',exp.toString())
                                }
                            }
                            println "Trying to trigger gc"
                            System.gc()
                            Thread.sleep(5000)
                        }
                    }
                    def dsetting = PortalSetting.findByName('datadump_processing')
                    dsetting.number = 0
                    dsetting.save(flush:true)
                    }
                })
            }
        }
        else {
            println "Already doing data dump"
        }
    }

    def link(linkparams) {
        println "In link generator:" + linkparams
        def toret = grailsLinkGenerator.link(*:linkparams)
        println "Toret:" + toret
        return grailsLinkGenerator.link(*:linkparams)
    }

    def evaltext(content,args,template_id = null,groovyPagesTemplateEngine = null) {
        def bodyreturn = null
        try{
            def bodyoutput = new StringWriter()
            /* def shortcontent = content
            if(content.size()>50) {
                shortcontent = content[-50..-1]
            } */
            def location = "text_" + template_id
            // println "loc:" + location
            if(!groovyPagesTemplateEngine) {
                // println "Setting new engine"
                groovyPagesTemplateEngine = new GroovyPagesTemplateEngine() 
                groovyPagesTemplateEngine.afterPropertiesSet()
            }
            Template template = groovyPagesTemplateEngine.createTemplate(content,location)
            template.make(args).writeTo(bodyoutput)
            bodyreturn = bodyoutput.toString()
        }
        catch(Exception e){
            println 'There was an error in evaltext:' + content + ':' + e
            def emailreporterror = PortalSetting.findByName("emailpageerror")
            if(emailreporterror){
                mailService.sendMail {
                    to emailreporterror.value().trim()
                    subject "Text Content Error"
                    body '''There was an error in evaltext: ${content} : ${e}'''
                }
            }
            PortalErrorLog.record(args,null,'page','evalcontent',e.toString())
            return ''
        }
        return bodyreturn
    }

    def find_filepath(filepath,verbose=false) {
        filepath = filepath.replace('\\','/')
        def foundfilepath = false
        def fileroot = filepath.substring(0,filepath.lastIndexOf('/'))+'/'
        def fileregex = filepath.substring(filepath.lastIndexOf('/')+1)
        if(verbose) {
            println "filepath:" + filepath
            println "fileroot:" + fileroot
            println "fileregex:" + fileregex
        }
        if(new File(filepath).exists()){
            foundfilepath = true
        }
        else {
            if(filepath.contains('/')){
                if(new File(fileroot).exists()){
                    new File(fileroot).eachFileMatch(~fileregex) { f ->
                        if(!foundfilepath) {
                            filepath = fileroot + f.getName()
                            foundfilepath = true
                        }
                    }
                    foundfilepath = true
                }
            }
        }
        // println "Found file path:" + foundfilepath
        if(foundfilepath) {
            return filepath
        }
        else {
            return null
        }
    }

    def gd(moduleslug,id,sql=null){
        def tokens = moduleslug.tokenize(':')
        def module = 'portal'
        def slug = ''
        def tobjects = PortalSetting.namedefault('tracker_objects',[])
        def toreturn = null
        if(moduleslug in tobjects) {
            tokens = tobjects[moduleslug].tokenize(".")
        }
        if(tokens.size()>1) {
            module = tokens[0]
            slug = tokens[1]
        }
        else {
            slug = tokens[0]
        }

        def thistracker = PortalTracker.findByModuleAndSlug(module,slug)
        if(thistracker) {
            def curdatas = thistracker?.getdatas(id,sql)
            if(curdatas) {
                toreturn = new PortalData()
                toreturn.tracker = thistracker
                toreturn.module = module
                toreturn.slug = slug
                toreturn.id = id
                toreturn.curdatas = curdatas
                if(moduleslug=='Branch') {
                    toreturn.typename = 'csdportal.Branch'
                }
                else {
                    toreturn.typename = moduleslug
                }
                toreturn.getnode()
            }
        }
        return toreturn
    }

    def ld(moduleslug,id,sql=null){
        def tokens = moduleslug.tokenize(':')
        def module = 'portal'
        def slug = ''
        if(tokens.size()==2) {
            module = tokens[0]
            slug = tokens[1]
        }
        else {
            slug = tokens[0]
        }

        def thistracker = PortalTracker.findByModuleAndSlug(module,slug)
        println "Found this tracker:" + thistracker
        return thistracker?.getdatas(id,sql)
    }

    @Transactional
    def export_module(Long id,file_on,user_on) {
        def module = PortalModule.get(id)
        module.exportmodule(file_on,user_on)
    }

    def static File compress(final File srcDir, final File zipFile) {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))
        srcDir.eachFileRecurse({
            def pathout = it.path - srcDir.path + (it.directory ? "/" : "")
            if(pathout[0]=='\\' || pathout[0]=='/') {
                pathout = pathout[1..-1]
            }
            zos.putNextEntry(new ZipEntry(pathout))
            if(it.file) { zos << it.bytes }
            zos.closeEntry()
        })
        zos.close()
        return zipFile
    }

    @Transactional
    def import_module(Long id,file_on,user_on) {
        def module = PortalModule.get(id)
        module.importmodule(file_on,user_on)
    }

    def update_module_list(params = null, curuser = null) {
        def sql = new Sql(sessionFactory.currentSession.connection())
        def page_modules = true
        def tracker_modules = true
        def role_modules = false
        def tree_modules = true
        PortalModule.withTransaction { transaction->
            sql.execute("delete from portal_module")
            if(page_modules) {
                sql.eachRow("select distinct module from portal_page") { mod->
                    println "Adding module:" + mod[0]
                    def modinstance = PortalModule.findByName(mod[0])
                    if(!modinstance) {
                        modinstance = new PortalModule(name:mod[0])
                        modinstance.save(flush:true)
                    }
                }
            }
            if(tracker_modules) {
                sql.eachRow("select distinct module from portal_tracker") { mod->
                    println "Adding module:" + mod[0]
                    def modinstance = PortalModule.findByName(mod[0])
                    if(!modinstance){
                        modinstance = new PortalModule(name:mod[0])
                        modinstance.save(flush:true)
                    }
                }
            }
            if(tree_modules) {
                sql.eachRow("select distinct module from tree") { mod->
                    println "Adding module:" + mod[0]
                    def modinstance = PortalModule.findByName(mod[0])
                    if(!modinstance){
                        modinstance = new PortalModule(name:mod[0])
                        modinstance.save(flush:true)
                    }
                }
            }
            if(role_modules) {
                sql.eachRow("select distinct module from user_role") { mod->
                    println "Adding module:" + mod[0]
                    def modinstance = PortalModule.findByName(mod[0])
                    if(!modinstance){
                        modinstance = new PortalModule(name:mod[0])
                        modinstance.save(flush:true)
                    }
                }
            }
            try {
              def curfolder = System.getProperty("user.dir")
              def migrationfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration')
              if(!(new File(migrationfolder).exists())){
                  new File(migrationfolder).mkdirs()
              }
              def dfolder = new File(migrationfolder)
              dfolder.eachFile(FileType.DIRECTORIES) { cdir->
                  print "cdir:" + cdir.name
                  def modinstance = PortalModule.findByName(cdir.name)
                  if(!modinstance){
                      modinstance = new PortalModule()
                      modinstance.name = cdir.name
                      modinstance.save(flush:true)
                  }
              }
            } catch(Exception e) {
                PortalErrorLog.record(params,curuser,'Module','Update List',e.toString())
                println "Error:" + e
            }
        }
    }

    @Transactional
    PortalTracker fromTable(PortalTracker tracker) {
        println "Import table for :" + tracker
        tracker.fromTable(sessionFactory.currentSession.connection())
        return tracker
    }

    @Transactional
    PortalTrackerData run_update(PortalTrackerData data) {
        println "Will run update for : " + data
        data.update(sessionFactory.currentSession.connection())
        return data
    }

    @Transactional
    PortalTracker updateDb(PortalTracker tracker) {
        println "Will update Db for :" + tracker
        tracker.updatedb(sessionFactory.currentSession.connection())
        return tracker
    }

    @Transactional
    PortalTracker createIndex(PortalTracker tracker) {
        println "Will created Index for :" + tracker
        tracker.createIndex(sessionFactory.currentSession.connection())
        return tracker
    }

    @Transactional
    PortalTreeNode movenode(node,othernode,position) {
        PortalTreeNode.withTransaction { status ->
            PortalTreeNode.withSession {
                //first move all the nodes we want to move out of the tree (lft is less than 0)
                def curdiff = (node.rgt - node.lft) + 1
                def moveby = node.rgt + 1
                def tmplft = node.lft
                def tmprgt = node.rgt
                PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft-:moveby,rgt=rgt-:moveby where lft>=:curlft and rgt<=:currgt and tree=:ctree",[curlft:node.lft,currgt:node.rgt,ctree:node.tree,moveby:moveby])
                node.refresh()
                //then we delete the old node (not really delete but move all the lft and rgt as if deleting it)
                PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft-:curdiff where lft>=:curlft and tree=:ctree",[curlft:tmplft,ctree:node.tree,curdiff:curdiff])
                PortalTreeNode.executeUpdate("update PortalTreeNode set rgt=rgt-:curdiff where rgt>=:curlft and tree=:ctree",[curlft:tmplft,ctree:node.tree,curdiff:curdiff])

                othernode.refresh()

                if(position=='last'){
                    //make space for the node to be inserted
                    PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+:curdiff where lft>=:curlft and tree=:ctree",[curlft:othernode.rgt,ctree:node.tree,curdiff:curdiff])
                    PortalTreeNode.executeUpdate("update PortalTreeNode set rgt=rgt+:curdiff where rgt>=:curlft and tree=:ctree",[curlft:othernode.rgt,ctree:node.tree,curdiff:curdiff])
                    othernode.refresh()
                    //then we put back the node where it was supposed to be
                    moveby = othernode.rgt
                    PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+:moveby,rgt=rgt+:moveby where lft>=:curlft and rgt<=:currgt and tree=:ctree",[curlft:node.lft,currgt:node.rgt,ctree:node.tree,moveby:moveby])
                    node.refresh()
                    node.parent = othernode
                    node.save()
                }
                else if(position=='before'){
                    //make space for the node to be inserted
                    PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+:curdiff where lft>=:curlft and tree=:ctree",[curlft:othernode.lft,ctree:node.tree,curdiff:curdiff])
                    PortalTreeNode.executeUpdate("update PortalTreeNode set rgt=rgt+:curdiff where rgt>=:curlft and tree=:ctree",[curlft:othernode.lft,ctree:node.tree,curdiff:curdiff])
                    node.refresh()
                    //then we put back the node where it was supposed to be
                    moveby = othernode.lft + 2
                    PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+:moveby,rgt=rgt+:moveby where lft>=:curlft and rgt<=:currgt and tree=:ctree",[curlft:node.lft,currgt:node.rgt,ctree:node.tree,moveby:moveby])
                    node.refresh()
                    node.parent = othernode.parent
                    node.save()
                }
                else if(position=='after'){
                    //make space for the node to be inserted
                    PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+:curdiff where lft>:curlft and tree=:ctree",[curlft:othernode.rgt,ctree:node.tree,curdiff:curdiff])
                    PortalTreeNode.executeUpdate("update PortalTreeNode set rgt=rgt+:curdiff where rgt>:curlft and tree=:ctree",[curlft:othernode.rgt,ctree:node.tree,curdiff:curdiff])
                    node.refresh()
                    //then we put back the node where it was supposed to be
                    moveby = curdiff + othernode.rgt + 1
                    PortalTreeNode.executeUpdate("update PortalTreeNode set lft=lft+:moveby,rgt=rgt+:moveby where lft>=:curlft and rgt<=:currgt and tree=:ctree",[curlft:node.lft,currgt:node.rgt,ctree:node.tree,moveby:moveby])
                    node.refresh()
                    node.parent = othernode.parent
                    node.save()
                }
            }
        }
        return node
    }
}
