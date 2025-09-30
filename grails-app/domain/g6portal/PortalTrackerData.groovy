package g6portal 
import groovy.sql.Sql
import grails.converters.JSON
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
                PortalTrackerData.withSession { cursession ->
                    def sql = new Sql(cursession.connection())
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
                    try {
                        rowcount = poiExcel.loadData(this.path,sql,savedparams,statementfields,this.tracker.data_table(),(int)this.id,gotupdate)
                    }
                    catch(Exception exp){
                        PortalErrorLog.record(null,null,'data update','data update - updating ',e.toString(),this.tracker.slug,this.tracker.module)
                    }
                    def reloadupdate = PortalTrackerData.get(this.id)
                    reloadupdate.uploaded = true
                    reloadupdate.uploadStatus = 1
                    reloadupdate.messages = rowcount + ' rows uploaded'
                    reloadupdate.save(flush:true)

                    if(this?.tracker?.tracker_type!='DataStore' && this?.tracker?.initial_status){
                        try {
                            sql.execute("update " + this.tracker.data_table() + " set record_status = '" + this.tracker.initial_status.name + "' where record_status is null")
                        }
                        catch(Exception e){
                            PortalErrorLog.record(null,null,'data update','data update - setting initial status',e.toString(),this.tracker.slug,this.tracker.module)
                            println "Error updating to default status for tracker " + this.tracker + " to default " + this.tracker.initial_status.name
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
            }
            catch(Exception e){
                println "Got error uploading data:" + e
                PortalErrorLog.record(null,null,'data update','data update - general',e.toString(),this.tracker.slug,this.tracker.module)
                // this.messages = 'Data file not found.' + e
                // this.save(flush:true)
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
        def curheaders = poiExcel.findHeaders(this.file_link.path,statementfields)
        if(curheaders){
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
            PortalTracker.withSession { cs->
                this.save(flush:true)
            }
        }
    }
}
