package g6portal

import groovy.sql.Sql
import static grails.util.Holders.config

class PortalTrackerField {

    transient PortalService portalService

    static belongsTo = [tracker:PortalTracker]
    static hasMany=[error_checks:PortalTrackerError]

    static constraints = {
        tracker()
        name(nullable:true)
        label(nullable:true)
        field_type(inList:["Text","Text Area","Integer","Number","Date","DateTime","Checkbox","Drop Down","User","File","Branch","BelongsTo","HasMany","TreeNode","Hidden"])
        field_options(nullable:true,widget:'textarea')
        field_format(nullable:true,widget:'textarea')
        field_default(nullable:true,widget:'textarea')
        hyperscript(nullable:true,widget:'textarea')
        hide_heading(nullable:true)
        classes(nullable:true)
        params_override(nullable:true)
        url_value(nullable:true)
        field_display(nullable:true)
        field_query(nullable:true)
        is_encrypted(nullable:true)
        role_query(nullable:true)
        encode_exception(nullable:true)
        suppress_follow_link(nullable:true)
    }

    static mapping = {
        field_options type: 'text'
        field_default type: 'text'
        field_query type: 'text'
        hyperscript type: 'text'
        cache true
    }

    PortalTracker tracker
    String name
    String label
    String field_type
    String field_options
    String field_format
    String field_default
    String hyperscript
    String field_display
    String field_query
    String classes
    Boolean hide_heading
    Boolean params_override
    Boolean url_value
    Boolean is_encrypted
    Boolean role_query
    Boolean encode_exception
    Boolean suppress_follow_link

    String toString() {
        return name
    }

    def safeval(value){
        def toreturn = ''
        if(this.field_type in ['Integer','User','Branch','File','TreeNode']){
            try {
                toreturn = value.toInteger()
            }
            catch(Exception e){
                println "Error changing field value from " + value + " to integer"
            }
        }
        else if(this.field_type == 'Number'){
            try{
                toreturn = new Double(value)
            }
            catch(Exception e){
                println "Error changing field value from " + value + " to double"
            }
        }
        else{
            toreturn = value
        }
        return toreturn
    }

    def updatedb(datasource){
        def createindex = false
        def sql = new Sql(datasource)
        def sqltype = 'varchar(256)'
        if(this.field_type=='Text'){
            sqltype = 'varchar(256)'
        }
        else if(this.field_type=='Text Area'){
            sqltype = 'text'
        }
        else if(this.field_type=='Date'){
            sqltype = 'date'
        }
        else if(this.field_type=='DateTime'){
            if(config.dataSource.url.contains("jdbc:postgresql")){
                sqltype = 'timestamp'
            }
            else {
                sqltype = 'datetime'
            }
        }
        else if(this.field_type=='Checkbox'){
            if(config.dataSource.url.contains("jdbc:postgresql")){
                sqltype = 'boolean'
            }
            else{
                sqltype = 'bit'
            }
        }
        else if(this.field_type=='Integer'){
            sqltype = 'numeric(24,0)'
        }
        else if(this.field_type=='Number'){
            sqltype = 'decimal(24,6)'
        }      
        else if(this.field_type in ['Branch','User','File','BelongsTo','TreeNode']){
            sqltype = 'numeric(19,0)'
            createindex = true
        }
        if(this.field_type!='HasMany') {
            try{
                if(!sql.firstRow("select * from INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + this.tracker.data_table() + "' and COLUMN_NAME = '" + this.name + "'")){
                    def query = ''
                    if(config.dataSource.url.contains("jdbc:postgresql")){
                        query = 'alter table "' + this.tracker.data_table() + '" add "' + this.name + '" ' + sqltype + ' NULL' 
                    }
                    else {
                        query = "alter table " + this.tracker.data_table() + " add [" + this.name + "] " + sqltype + " NULL" 
                    }
                    println "Updatedb query:" + query
                    sql.execute(query)
                }
                if(createindex) {
                    def tablename = this.tracker.data_table()
                    def fname = this.name
                    def query = "if not exists (select * from sys.indexes where name='ix_" + fname + "' and object_id=object_id('" + tablename + "'))begin create nonclustered index ix_" + fname + " on [" + tablename + "] ([" + fname + "]); end"
                    if(config.dataSource.url.contains("jdbc:postgresql")){
                        query = 'create index if not exists ix_' + fname + ' on "' + tablename + '" ("' + fname + '")'
                    }
                    println "Updatedb query:" + query
                    sql.execute(query)
                }
            }
            catch(Exception e){
                println 'There was an error in field updatedb:' + e
                PortalErrorLog.record(null,null,'field','updatedb',e.toString(),this.tracker.slug,this.tracker.module)
                return ''
            }
        }
    }

    def fixnamelabel() {
        if(!this.name){
            // println 'need to fix'
            this.name=this.label.trim().replace(' ','_').replaceAll(~/[^a-zA-Z0-9_]/,'').replaceAll(~/(_+)/,'_').toLowerCase()
            // print 'fixed to ' + this.name
        }
    }

    def evaloptions(session,datas=null,sql=null) {
        try {
            // println "In evaloptions " + this.field_options
            Binding binding = new Binding()
            binding.setVariable("session",session)
            binding.setVariable("datas",datas)
            binding.setVariable("sql",sql)
            binding.setVariable("portalService",portalService)
            def curuser = null
            if(session && session.userid){
                // curuser = User.get(session.userid)
                curuser = session.curuser
                binding.setVariable("curuser",curuser)
            }
            def shell = new GroovyShell(this.class.classLoader,binding)
            return shell.evaluate(this.field_options)
        }
        catch(Exception e){
            def curuser = null
            if(session && session.userid){
                // curuser = User.get(session.userid)
                curuser = session.curuser
            }
            PortalErrorLog.record(datas,curuser,'trackerField','evaloptions',e.toString(),this.tracker.slug,this.tracker.module)
        }
    }

    def evalformat(session,datas=null) {
        try {
            Binding binding = new Binding()
            binding.setVariable("session",session)
            binding.setVariable("datas",datas)
            binding.setVariable("portalService",portalService)
            def curuser = null
            if(session && session.userid){
                // curuser = User.get(session.userid)
                curuser = session.curuser
                binding.setVariable("curuser",curuser)
            }
            def shell = new GroovyShell(this.class.classLoader,binding)
            return shell.evaluate(this.field_format)
        }
        catch(Exception e){
            def curuser = null
            if(session && session.userid){
                // curuser = User.get(session.userid)
                curuser = session.curuser
            }
            PortalErrorLog.record(datas,curuser,'trackerField','evalformat',e.toString(),this.tracker.slug,this.tracker.module)
        }
    }

    def evalquery(session,datas) {
        try{
            Binding binding = new Binding()
            binding.setVariable("session",session)
            binding.setVariable("datas",datas)
            binding.setVariable("portalService",portalService)
            def curuser = null
            if(session && session.userid){
                // curuser = User.get(session.userid)
                curuser = session.curuser
                binding.setVariable("curuser",curuser)
            }
            def shell = new GroovyShell(this.class.classLoader,binding)
            return shell.evaluate(this.field_query)
        }
        catch(Exception e){
            def curuser = null
            if(session && session.userid){
                // curuser = User.get(session.userid)
                curuser = session.curuser
            }
            PortalErrorLog.record(datas,curuser,'trackerField','evalquery',e.toString(),this.tracker.slug,this.tracker.module)
        }
    }

    def fieldval(value,sql=null){
        def trackerObjects = PortalSetting.namedefault("tracker_objects",[])
        // println "trackerObjects:" + trackerObjects
        /* if(this.field_type=='Branch'){
            return Branch.get(value)?.fullname()
        } */
        if(this.field_type=='User'){
            return User.get(value)?.name
        }
        else if(this.field_type=='File'){
            def fl = FileLink.get(value)
            return fl
        }
        else if(this.field_type=='TreeNode'){
            def node = PortalTreeNode.get(value)
            return node?.name
        }
        else if(this.field_type=='Date'){
            if(value.toString()!='1900-01-01'){
                return value
            }
            else{
                return ''
            }
        }
        else if(this.field_type in trackerObjects){
            def dtrck = trackerObjects[this.field_type].tokenize('.')
            // println "dtrck:" + dtrck
            def tobj = null
            if(dtrck.size()>1){
                tobj = PortalTracker.findByModuleAndSlug(dtrck[0].trim(),dtrck[1].trim())
            }
            else {
                tobj = PortalTracker.findBySlug(dtrck[0].trim())
            }
            if(tobj) {
                // println "tobj:" + tobj
                def fdatas = tobj.getdatas(value,sql)
                if(fdatas) {
                    // println "fdatas:" + fdatas
                    if(dtrck.size()==3){
                        return fdatas[dtrck[2].trim()]
                    }
                    else {
                        return fdatas[tobj.default_field()]
                    }
                }
                else {
                    return value
                }
            }
        }
        else{
            if(this.is_encrypted && value){
                try {
                    value = CryptoUtilsFile.decrypt(value.toString(),new File(config.encryption.key))
                }
                catch(Exception e){
                println 'Error decypting info ' + this + ' with val:' + value
                }
            }
            if(this.encode_exception) {
                value = value.encodeAsHTML().replace('\n',"<br/>")
            }
            return value
        }
    }

    def objectlist(session,params){
        // println "In objectlist"
        def objects = null
        def curuser = null
        if(session && session.userid){
            curuser = session.curuser
        }
        if(this.field_options){
            try{
                Binding binding = new Binding()
                binding.setVariable("session",session)
                binding.setVariable("curuser",curuser)
                binding.setVariable("portalService",portalService)
                def shell = new GroovyShell(this.class.classLoader,binding)
                objects = shell.evaluate(this.field_options)
                // println "Tracker objects:" + objects
            }
            catch(Exception e){
                PortalErrorLog.record(params,curuser,'trackerField','objectlist',e.toString(),this.tracker.slug,this.tracker.module)
            }
        }
        else {
            def trackersetting = PortalSetting.namedefault('tracker_objects',[])
            // println "Tracker settings:" + trackersetting
            if(this.field_type in trackersetting) {
                def tokens = trackersetting[this.field_type].tokenize('.')
                // println "Got tokens:" + tokens
                if(tokens.size()>=2) {
                    def targettracker = PortalTracker.findByModuleAndSlug(tokens[0],tokens[1])
                    def qparams = [:]
                    if(targettracker) {
                        if(params.q) {
                            def fields2check = targettracker.searchfields.tokenize(',')*.trim()
                            if(fields2check.size()) {
                                fields2check.each { f2c ->
                                    qparams[f2c] = '%' + params.q + '%'
                                }
                            }
                            else if(tokens.size()>=3) {
                                qparams[tokens[2]] = '%' + params.q + '%'
                            }
                            // println "Qparams:" + qparams
                            objects = targettracker.rows(['or':qparams])
                            // println "Objects: " + objects
                        }
                        else{
                            objects = targettracker.rows()
                        }
                    }
                }
            }
        }
        return objects
    }

    def nodeslist(session,params){
        // println "In objectlist"
        def nodes = null
        if(this.field_options){
            try{
                Binding binding = new Binding()
                binding.setVariable("session",session)
                binding.setVariable("params",params)
                binding.setVariable("portalService",portalService)
                def shell = new GroovyShell(this.class.classLoader,binding)
                nodes = shell.evaluate(this.field_options)
                // println "Tracker objects:" + objects
            }
            catch(Exception e){
                def curuser = null
                if(session && session.userid){
                    // curuser = User.get(session.userid)
                    curuser = session.curuser
                }
                PortalErrorLog.record(params,curuser,'trackerField','nodeslist',e.toString(),this.tracker.slug,this.tracker.module)
            }
        }
        return nodes
    }

    def trackerobject() {
        def trackersetting = PortalSetting.namedefault('tracker_objects',[])
        def objmodule = 'portal'
        def objslug = 'slug'
        def objname = 'name'
        if(this.field_type in trackersetting) {
            def tokens = trackersetting[this.field_type].tokenize('.')
            objmodule = tokens[0]
            if(tokens.size()>1) {
              objslug = tokens[1]
            }
            if(tokens.size()>=2) {
              objname = tokens[2]
            }
        }
        return ['module':objmodule,'slug':objslug,'name':objname]
    }

    def userlist(session,params){
        def users = null
        if(this.field_options){
            try{
                Binding binding = new Binding()
                binding.setVariable("session",session)
                binding.setVariable("params",params)
                binding.setVariable("portalService",portalService)
                def curuser = null
                if(session && session.userid){
                    // curuser = User.get(session.userid)
                    curuser = session.curuser
                    binding.setVariable("curuser",curuser)
                }
                def shell = new GroovyShell(this.class.classLoader,binding)
                def listusers = shell.evaluate(this.field_options)
                if(params.q) {
                    users = User.findAll(max:20){
                        id in listusers*.id
                        (name =~ '%' + params.q?.replace(' ','%').trim() + '%') || (userID =~ '%' + params.q?.trim() + '%')
                    }
                }
                else {
                    users = listusers
                }
            }
            catch(Exception e){
                def curuser = null
                if(session && session.userid){
                    // curuser = User.get(session.userid)
                    curuser = session.curuser
                }
                PortalErrorLog.record(params,curuser,'trackerField','userlist',e.toString(),this.tracker.slug,this.tracker.module)
            }
        }
        else {
            users = User.findAll(max:20){
                // isActive == true
                if(params.q){
                    (name =~ '%' + params.q?.replace(' ','%').trim() + '%') || (userID =~ '%' + params.q?.trim() + '%')
                }
            }
        }
        return users
    }

    def othertracker() {
        def othertracker = null
        if(field_options) {
            def mp = field_options.tokenize(':')
            if(mp.size()==1) {
                othertracker = PortalTracker.findBySlug(mp[0])
            }
            else if(mp.size()>1) {
                othertracker = PortalTracker.findByModuleAndSlug(mp[0],mp[1])
                if(mp.size()>2) {
                    if(othertracker) {
                        field_format = mp[2]
                    }
                }
            }
        }
        return othertracker
    }

}
