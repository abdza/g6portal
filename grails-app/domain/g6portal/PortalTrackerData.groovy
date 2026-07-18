package g6portal
import groovy.sql.Sql
import grails.converters.JSON
import grails.util.Holders
import static grails.util.Holders.config
import org.apache.commons.validator.EmailValidator
import org.apache.commons.text.similarity.LongestCommonSubsequence


class PortalTrackerData {

    static belongsTo = [tracker:PortalTracker]

    static constraints = {
        tracker()
        module(nullable:true)
        uploadStatus(nullable:true)
        path(nullable:true)
        header_start(nullable:true)
        header_end(nullable:true)
        data_row(nullable:true)
        data_end(nullable:true)
        uploaded(nullable:true)
        send_email(nullable:true)
        sent_email_date(nullable:true)
        messages(nullable:true)
        savedparams(nullable:true)
        file_link(nullable:true)
        date_created(nullable:true)
        isTrackerDeleting(nullable:true)
        uploader(nullable:true)
        excel_password(nullable:true)
    }

    /**
     * Validates database identifiers (table names, column names)
     */
    private String validateTableName(String tableName) {
        if (!tableName) return ""
        // Only allow alphanumeric characters, underscores
        if (!tableName.matches(/^[a-zA-Z_][a-zA-Z0-9_]*$/)) {
            throw new SecurityException("Invalid database table name: ${tableName}")
        }
        return tableName
    }

    /**
     * Sanitizes status names to prevent injection
     */
    private String sanitizeStatusName(String statusName) {
        if (!statusName) return ""
        // Allow only alphanumeric characters, spaces, underscores, and hyphens for status names
        def sanitized = statusName.replaceAll(/[^a-zA-Z0-9\s_-]/, "").trim()
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255)
        }
        return sanitized
    }

    static mapping = {
        savedparams type: 'text'
        messages type: 'text'
        cache true
    }

    Boolean isTrackerDeleting = false

    String module
    PortalTracker tracker
    String path
    Date date_created
    Integer data_row
    Integer data_end
    Integer header_start
    Integer header_end
    Boolean uploaded
    Boolean send_email
    Date sent_email_date
    String messages
    String excel_password
    User uploader
    String savedparams
    Integer uploadStatus
    FileLink file_link

    String toString() {
        return path
    }

    def beforeDelete = {
        /* PortalTrackerData.withSession { cursession ->
            def sql = new Sql(cursession.connection())
            def delquery = "delete * from " + this.tracker.data_table() + " where dataupdate_id=:dupid"
            sql.execute(delquery,['dupid':this.id])
            try {
                sql.execute(delquery)
            }
            catch(Exception e) {
                println "Got error deleting data: " + e
            }
        } */
        if(!isTrackerDeleting) {
            PortalTrackerData.withSession { cs ->
                println "Deleting inside session data " + this.id + " from table " + this.tracker.data_table()
                def sql = new Sql(cs.connection())
                println "Cs: " + cs + " sql:" + sql + " connection: " + cs.connection()
                def delquery = "delete from " + this.tracker.data_table() + " where dataupdate_id=" + this.id
                println "Query to delete:" + delquery
                try {
                    sql.execute(delquery)
                }
                catch(Exception e) {
                    println "Got error deleting data: " + e
                }
            }
        }
        try {
            def thefile = new File(path)
            if(thefile.exists()){
                thefile.delete()
            }
        }
        catch(Exception e) {
            println "Got error deleting file: " + e
        }
    }

    def update(mailService) {
        def debug_dataupdate = PortalSetting.namedefault('debug_dataupdate',1)
        if(debug_dataupdate){
            println 'in update ' + this.tracker
        }
        if(!this.savedparams){
            def errMsg = "Upload failed: the file could not be read or contains no recognisable headers. Please verify the file and try again."
            try {
                if(this.id) {
                    def failed = PortalTrackerData.get(this.id)
                    if(failed) {
                        failed.messages = errMsg
                        failed.uploadStatus = -1
                        failed.save(flush:true)
                    }
                }
            } catch(Exception ce) {
                println "Could not persist error message to PortalTrackerData: ${ce}"
            }
            throw new RuntimeException(errMsg)
        }
        if(this.savedparams){
            if(debug_dataupdate){
                println 'got saved params'
            }
            this.uploadStatus = -1
            this.save(flush:true)
            def savedparams = JSON.parse(this.savedparams)
            if(debug_dataupdate){
                println 'updated status'
            }

            def rowcount = 0
            def allcount = 0
            def currow = 0
            def fieldnames = []
            def warnings = []
            def updateqhead = "update " + this.tracker.data_table() + " set "
            def updatecheck = []
            def datatype = [:]
            def emptyrows = 0
            if(debug_dataupdate){
                println 'done init'
            }
            try {
                // Create a direct JDBC connection (outside the HikariCP pool) so that
                // SQL errors during row loading cannot poison pooled connections that are
                // shared with Hibernate and the firstRow/rows query methods.
                def jdbcUrl = config.dataSource.url
                def jdbcUser = config.dataSource.username
                def jdbcPass = config.dataSource.password
                def jdbcDriver = config.dataSource.driverClassName
                def sql = Sql.newInstance(jdbcUrl, jdbcUser, jdbcPass, jdbcDriver)
                try {
                PortalTrackerData.withSession { cursession ->
                    PoiExcel poiExcel = new PoiExcel()
                    poiExcel.headerstart = this.header_start
                    poiExcel.headerend = this.header_end
                    poiExcel.datastart = this.data_row
                    if(this.data_end && this.data_end>this.data_row){
                        poiExcel.dataend = this.data_end
                    }
                    def statementfields = []
                    def gotupdate = false
                    this.tracker.fields.each { field->
                        if(savedparams.optString('datasource_' + field.id)!='ignore'){
                            statementfields << ['id':field.id,'name':field.name,'type':field.field_type]
                            if(savedparams.optString('update_' + field.id)){
                                gotupdate = true
                            }
                        }
                    }
                    if(statementfields.isEmpty()){
                        throw new RuntimeException("Upload failed: none of the file's column headers matched the expected template. Please verify you are using the correct file format.")
                    }
                    try {
                        rowcount = poiExcel.loadData(this.path,this.excel_password,sql,savedparams,statementfields,this.tracker.data_table(),(int)this.id,gotupdate)
                    }
                    catch(Exception exp){
                        PortalErrorLog.record(null,null,'data update','data update - updating ',exp.toString(),this.tracker.slug,this.tracker.module)
                        throw new RuntimeException("Upload failed while reading file data: ${exp.getMessage() ?: exp.getClass().getSimpleName()}", exp)
                    }
                    def uploadedCount = 0
                    try {
                        def tableName = validateTableName(this.tracker.data_table())
                        // Plain String, not GString — GString expressions become bound
                        // parameters, which turns the table name into "[?]" and fails
                        def countQuery
                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")) {
                            countQuery = "SELECT COUNT(*) AS cnt FROM \"" + tableName + "\" WHERE dataupdate_id = " + ((long)this.id)
                        } else {
                            countQuery = "SELECT COUNT(*) AS cnt FROM [" + tableName + "] WHERE dataupdate_id = " + ((long)this.id)
                        }
                        def countRow = sql.firstRow(countQuery as String)
                        uploadedCount = countRow?.cnt ?: 0
                    } catch(Exception ce) {
                        println "Could not count uploaded rows for dataupdate ${this.id}: ${ce}"
                        // Fall back to the loader's own failure tally instead of assuming success
                        uploadedCount = rowcount - poiExcel.failedcount
                    }
                    def rejectedCount = rowcount - uploadedCount
                    // If rows were present in the file but none loaded, the file format is wrong
                    if (rowcount > 0 && uploadedCount == 0) {
                        throw new RuntimeException("Upload failed: ${rowcount} row(s) were received but none could be imported. The file may be using an incorrect column format — please verify and re-upload.")
                    }
                    def uploadFilename = this.path ? this.path.tokenize('/')[-1] : 'Unknown'
                    def finalMessages = "File: ${uploadFilename}\nStatus: Completed\nRecords Received: ${rowcount}\nRecords Uploaded: ${uploadedCount}\nRecords Rejected: ${rejectedCount}".toString()
                    if (rejectedCount > 0) {
                        def failDetails = ''
                        try { failDetails = poiExcel.failureSummary() } catch(Exception ignore) {}
                        if (failDetails) {
                            finalMessages += "\n\n=== Rejected Row Details ===\n" + failDetails
                        }
                    }
                    // Use raw SQL to update final status — avoids Hibernate optimistic locking
                    // conflicts that occur when the Groovy SQL row operations cause version drift.
                    // upload_status 2 = partial success (some rows rejected). Do NOT set it on
                    // the Hibernate entity: a dirty entity gets re-flushed at commit and would
                    // overwrite this raw update with the stale "in queue" message.
                    def finalStatus = rejectedCount > 0 ? 2 : 1
                    try {
                        def rawDs2 = Holders.applicationContext.getBean('dataSource')
                        def rawSql2 = new Sql(rawDs2)
                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")) {
                            // uploaded is a boolean column on PostgreSQL/H2
                            rawSql2.execute("UPDATE portal_tracker_data SET uploaded = true, upload_status = ?, messages = ? WHERE id = ?",
                                            [finalStatus, finalMessages, (long)this.id])
                        } else {
                            rawSql2.execute("UPDATE portal_tracker_data SET uploaded = 1, upload_status = ?, messages = ? WHERE id = ?",
                                            [finalStatus, finalMessages, (long)this.id])
                        }
                        rawSql2.close()
                    } catch(Exception rawE) {
                        println "Could not update upload status via raw SQL: ${rawE}"
                    }

                    if(this?.tracker?.tracker_type!='DataStore' && this?.tracker?.initial_status){
                        try {
                            // Validate table name and status name to prevent injection
                            def tableName = validateTableName(this.tracker.data_table())
                            def statusName = sanitizeStatusName(this.tracker.initial_status.name)

                            // Use parameterized query to prevent SQL injection
                            if(config.dataSource.url.contains("jdbc:postgresql")) {
                                sql.execute("update \"${tableName}\" set record_status = ? where record_status is null",
                                           [statusName])
                            } else {
                                sql.execute("update [${tableName}] set [record_status] = ? where [record_status] is null",
                                           [statusName])
                            }

                            log.info "Successfully updated initial status for tracker ${this.tracker.slug} to ${statusName}"

                        } catch(SecurityException se) {
                            PortalErrorLog.record(null,null,'security violation','data update - invalid table/status name',se.toString(),this.tracker?.slug,this.tracker?.module)
                            log.error "Security violation updating status for tracker ${this.tracker}: ${se.message}"
                            throw se
                        } catch(Exception e){
                            PortalErrorLog.record(null,null,'data update','data update - setting initial status',e.toString(),this.tracker?.slug,this.tracker?.module)
                            log.error "Error updating to default status for tracker ${this.tracker} to default ${this.tracker?.initial_status?.name}: ${e.message}"
                            // Don't rethrow - allow processing to continue
                        }
                    }
                    if(this?.tracker?.postprocess){
                        def updatesetting = null
                        try {
                            updatesetting = PortalSetting.namedefault('dataupdate_processing',null)
                            if(updatesetting){
                                updatesetting.text = "Post processing tracker " + this.tracker
                                updatesetting.save(flush:true)
                            }
                            Binding binding = new Binding()
                            binding.setVariable("tracker",this.tracker)
                            binding.setVariable("update",this)
                            binding.setVariable("mailService",mailService)
                            def shell = new GroovyShell(this.class.classLoader,binding)
                            shell.evaluate(this.tracker.postprocess.content)
                        }
                        catch(Exception e){
                            println "Error postprocessing for tracker " + this.tracker + " with " + this.tracker.postprocess.content + " error:" + e
                            PortalErrorLog.record(null,null,'data update','data update - postprocess',e.toString(),this.tracker.slug,this.tracker.module)
                        }
                        if(updatesetting){
                            updatesetting.text = "Done statement " + this.tracker
                            updatesetting.save(flush:true)
                        }
                    }
                }
                } finally {
                    sql.close()
                }
            }
            catch(Exception e){
                println "Got error uploading data:" + e
                PortalErrorLog.record(null,null,'data update','data update - general',e.toString(),this.tracker.slug,this.tracker.module)
                // Persist error to PortalTrackerData.messages via raw SQL — Hibernate session
                // may be in a bad state so GORM saves are not safe here.
                try {
                    def rawDs = Holders.applicationContext.getBean('dataSource')
                    def rawSql = new Sql(rawDs)
                    rawSql.execute("UPDATE portal_tracker_data SET messages = ? WHERE id = ?",
                                   ["Upload error: ${e.getMessage() ?: e.getClass().getSimpleName()}" as String, (long)this.id])
                    rawSql.close()
                } catch(Exception ce) {
                    println "Could not persist error message to PortalTrackerData: ${ce}"
                }
                // Clear the Hibernate session so a failure here does not leave the session
                // in a dirty state that breaks subsequent GORM calls in the same request.
                try { PortalTrackerData.withSession { cs -> cs.clear() } } catch(Exception ce) {}
                throw new RuntimeException("Upload failed: ${e.getMessage() ?: e.getClass().getSimpleName()}", e)
            }
        }
        else{
            if(debug_dataupdate){
                println 'no saved params'
            }
        }
    }

    def fromFileLink(manualmaps=null, otherparams=null, update_fields=null, ignore_fields=null,autosearch=true) {
        //refer to the filelink for file path information
        def fheadrow = 1

        def statementfields = []
        this.tracker.fields.each { field->
            statementfields << ['id':field.id,'name':field.name,'type':field.field_type]
        }
        PoiExcel poiExcel = new PoiExcel()
        this.path = this.file_link.path
        def curheaders = poiExcel.findHeaders(this.file_link.path,this.excel_password,statementfields)
        // When explicit manualmaps are provided with autosearch disabled, proceed even if
        // findHeaders found no fuzzy label matches — column positions are already known.
        if(curheaders || (manualmaps && !autosearch)){
            //if found the headers process them
            def fileparams = [:]
            this.tracker.fields.each { dfield->
                fileparams['datasource_' + dfield.id] = 'ignore'
                if(!(dfield in ignore_fields)) {
                    def closest = null
                    def diff = 100000
                    def lcs = new LongestCommonSubsequence()
                    def gotmap = false
                    if(manualmaps){
                        manualmaps.each { cfieldkey,cfieldval->
                            if(cfieldkey==dfield.name){
                                fileparams['datasource_' + dfield.id] = cfieldval
                                gotmap = true
                            }
                        }
                    }
                    if(!gotmap && autosearch) {
                        curheaders.each { fkey,fval->
                            def commsub = 0
                            if(dfield.name){
                                commsub = lcs.apply(fkey,dfield.name)
                            }
                            def commsubtext = 0
                            if(dfield.label){
                                commsubtext = lcs.apply(fkey,dfield.label)
                            }
                            if((dfield.name && dfield.label) && (commsub>0 || commsubtext>0)) {
                                def lendiff = Math.abs(fkey.size()-dfield.name.size()) + 1
                                def finalscore = commsub/lendiff
                                def lendifftext = Math.abs(fkey.size()-dfield.label.size()) + 1
                                def finalscoretext = commsubtext/lendifftext
                                if(closest==null){
                                    closest = fkey
                                    if(finalscoretext>finalscore){
                                        diff = finalscoretext
                                    }
                                    else{
                                        diff = finalscore
                                    }
                                }
                                else{
                                    if(finalscore>diff || finalscoretext>diff){
                                        closest = fkey
                                        if(finalscoretext>finalscore){
                                            diff = finalscoretext
                                        }
                                        else{
                                            diff = finalscore
                                        }
                                    }
                                }
                            }
                        }
                        if(closest){
                            fileparams['datasource_' + dfield.id] = curheaders[closest]['col']
                            fheadrow = curheaders[closest]['row'].toInteger() + 1
                        }
                    }
                    else {
                        if(otherparams){
                            if(otherparams[dfield.name]){
                                //header specified in the otherparams, override whatever was found
                                fileparams['datasource_' + dfield.id] = 'custom'
                                fileparams['custom_' + dfield.id] = otherparams[dfield.name]
                            }
                        }
                    }
                    if(update_fields) {
                        if(dfield.name in update_fields){
                            fileparams['update_' + dfield.id] = "on"
                        }
                    }
                }
            }
            if(autosearch){
                //set the headers according to the file
                this.header_start = fheadrow
                this.header_end = fheadrow
                this.data_row = fheadrow + 1
            }
            this.savedparams = fileparams as JSON
            PortalTrackerData.withSession { cs->
                this.save(flush:true)
            }
        }
    }
}
