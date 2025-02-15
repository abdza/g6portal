package g6portal

import groovy.sql.Sql
import org.springframework.transaction.annotation.Transactional
import grails.plugins.mail.MailService
import static grails.util.Holders.config

class PortalTracker {

    transient MailService mailService

    static hasMany=[emails:PortalTrackerEmail,datas:PortalTrackerData,fields:PortalTrackerField,statuses:PortalTrackerStatus,roles:PortalTrackerRole,transitions:PortalTrackerTransition,flows:PortalTrackerFlow,indexes:PortalTrackerIndex]

    static constraints = {
        name()
        module()
        slug()
        tracker_type(nullable:true,inList:['Statement','DataStore','Tracker'])  // Tracker have audit trail, record status and role. Statement has role. DataStore just have data
        initial_status(nullable:true)
        listfields(nullable:true,widget:'textarea')
        allowedroles(nullable:true)
        hiddenlistfields(nullable:true,widget:'textarea')
        excelfields(nullable:true,widget:'textarea')
        searchfields(nullable:true,widget:'textarea')
        filterfields(nullable:true,widget:'textarea')
        allowadd(nullable:true)
        downloadexcel(nullable:true)
        excel_audit(nullable:true)
        defaultsort(nullable:true)
        defaultlimit(nullable:true)
        rolesort(nullable:true)
        tickactions(nullable:true)
        actionbuttons(nullable:true)
        anonymous_list(nullable:true)
        anonymous_view(nullable:true)
        require_login(nullable:true)
        condition_q(nullable:true,widget:'textarea')
        rowclassval(nullable:true)
        datatable(nullable:true)
        trailtable(nullable:true)
        postprocess(nullable:true)
        defaultfield(nullable:true)
        side_menu(nullable:true)
    }

    static transients = ['sqlfieldnames','sqlvalues','curdatas']

    static mapping = {
        listfields type: 'text'
        excelfields type: 'text'
        filterfields type: 'text'
        searchfields type: 'text'
        hiddenlistfields type: 'text'
        cache true
    }

    String name
    String slug
    String tracker_type
    String module
    String allowedroles
    String side_menu
    String listfields
    String hiddenlistfields
    String excelfields
    String filterfields
    String searchfields
    PortalPage postprocess
    PortalTrackerStatus initial_status
    PortalTrackerField defaultfield

    String sqlfieldnames
    String sqlvalues

    String datatable
    String trailtable
    String defaultsort
    Integer defaultlimit
    String rolesort
    

    Boolean allowadd
    Boolean downloadexcel
    Boolean anonymous_list
    Boolean anonymous_view
    Boolean require_login
    Boolean excel_audit

    String tickactions
    String actionbuttons

    String condition_q

    String rowclassval

    def curdatas=[:]

    String toString() {
        return name
    }

    def static load_tracker(slug,module='portal') {
        def tokens = slug.tokenize(':')
        slug = tokens[0]
        if(tokens.size()==2) {
            module = tokens[1]
        }
        return PortalTracker.findByModuleAndSlug(module,slug)
    }

    def static array2sql(arrayval) {
        def toret = "('" + arrayval.join("','") + "')"
        return toret
    }

    def transition(transition_name) {
        def toret = PortalTrackerTransition.findByTrackerAndName(this,transition_name)
        return toret
    }

    def cleardb() {
        PortalTrackerData.withSession { sessiondata ->
            def sql = new Sql(sessiondata.connection())
            sql.execute("truncate table " + data_table())
            if(tracker_type=='Tracker') {
                sql.execute("truncate table " + trail_table())
            }
        }
    }

    def camelcase(dstring) {
        return dstring.capitalize().replace(' ','')
    }

    def field(name){
        return PortalTrackerField.findByTrackerAndName(this,name)
    }

    def createIndex(datasource){
        def sql = new Sql(datasource)
        def query = ""
        def totalindex = []
        def tablename = this.data_table()
        this.indexes.each { cin ->
            cin.updateDb()
        }
        if(searchfields) {
            def tfields = PortalTrackerField.createCriteria().list(){
                'eq'('tracker',this)
                'in'('name',searchfields?.tokenize(',')*.trim())
            } 
            tfields.each { tfield->
                if(!(tfield.field_type in ['Text Area','HasMany'])) {
                    def fname = tfield.name.trim()
                    totalindex << fname
                    def indexname = "ix_f_" + tablename + "_" + fname
                    if(config.dataSource.url.contains("jdbc:postgresql")){
                        query = "create index if not exists " + indexname + " on " + tablename + " (" + fname + ")"
                    }
                    else{
                        query = "if not exists (select * from sys.indexes where name='" + indexname + "' and object_id=object_id('" + tablename + "'))begin create nonclustered index " + indexname + " on [" + tablename + "] ([" + fname + "]); end"
                    }
                    try {
                        println "Creating search index:" + query
                        sql.execute(query)
                    }
                    catch(Exception e){
                        PortalErrorLog.record(null,null,'tracker','create index',e.toString() + " query: " + query,slug,module)
                    }
                }
            }
        }
        if(filterfields) {
            def ftfields = PortalTrackerField.createCriteria().list(){
                'eq'('tracker',this)
                'in'('name',filterfields?.tokenize(',')*.trim())
            } 
            ftfields.each { tfield->
                if(!(tfield.field_type in ['Text Area','HasMany'])) {
                    def fname = tfield.name.trim()
                    def indexname = "ix_f_" + tablename + "_" + fname
                    if(!(fname in totalindex)) {
                        totalindex << fname
                    }
                    if(config.dataSource.url.contains("jdbc:postgresql")){
                        query = "create index if not exists " + indexname + " on " + tablename + " (" + fname + ")"
                    }
                    else{
                        query = "if not exists (select * from sys.indexes where name='" + indexname + "' and object_id=object_id('" + tablename + "'))begin create nonclustered index " + indexname + " on [" + tablename + "] ([" + fname + "]); end"
                    }
                    try {
                        println "Creating filter index:" + query
                        sql.execute(query)
                    }
                    catch(Exception e){
                        PortalErrorLog.record(null,null,'tracker','create index',e.toString() + " query: " + query,slug,module)
                    }
                }
            }
        }
        if(totalindex.size()>2) {
            def indexname = "ix_total_" + tablename
            def delquery = "drop index if exists " + indexname
            if(config.dataSource.url.contains("jdbc:postgresql")){
                query = "CREATE INDEX IF NOT EXISTS " + indexname + " ON " + tablename + " (" + totalindex.join(',') + ");"
            }
            else{
                delquery = "drop index if exists " + indexname + " ON " + tablename
                query = "if not exists (select * from sys.indexes where name='" + indexname + "' and object_id=object_id('" + tablename + "'))begin create nonclustered index " + indexname + " on [" + tablename + "] ([" + totalindex.join('],[') + "]); end"
            }
            try {
                sql.execute(delquery)
                sql.execute(query)
            }
            catch(Exception e){
                PortalErrorLog.record(null,null,'tracker','create total index',e.toString() + " query: " + query,slug,module)
            }
        }
    }

    def updatedb(datasource){
        def sql = new Sql(datasource)
        def query = ""
        if(!sql.firstRow("select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + this.data_table() + "'")){
            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                query = 'create table if not exists "' + this.data_table() + '" (id SERIAL PRIMARY KEY, dataupdate_id numeric(19,0) null )'
            }
            else {
                query = 'create table ' + this.data_table() + ' (id INT NOT NULL IDENTITY(1, 1), dataupdate_id numeric(19,0) null,CONSTRAINT PK_'+this.data_table() +' PRIMARY KEY(id))'
            }
            sql.execute(query)
        }
        if(this.tracker_type!='DataStore') {
            def rsq = "select * from INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + this.data_table() + "' and COLUMN_NAME = 'record_status'"
            if(!sql.firstRow(rsq)){
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    sql.execute('alter table "' + this.data_table() + '" add record_status varchar(255) NULL' )
                }
                else {
                    sql.execute("alter table " + this.data_table() + " add [record_status]varchar(255) NULL" )
                }
            }
        }
        def didq = "select * from INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + this.data_table() + "' and COLUMN_NAME = 'dataupdate_id'"
        if(!sql.firstRow(didq)){
            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                sql.execute('alter table "' + this.data_table() + '" add dataupdate_id numeric(19,0) NULL' )
            }
            else {
                sql.execute("alter table " + this.data_table() + " add [dataupdate_id] numeric(19,0) NULL" )
            }
        }
        if(this.tracker_type=='Tracker') {
            def testq = "select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + this.trail_table() + "'"

            if(!sql.firstRow(testq)){
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    query = 'create table ' + this.trail_table() + ' (id SERIAL PRIMARY KEY, attachment_id numeric(19,0), description text, record_id numeric(19,0), update_date timestamp, updater_id numeric(19,0), status varchar(255), changes text, allowedroles varchar(255))'
                }
                else {
                    query = 'create table ' + this.trail_table() + ' (id INT NOT NULL IDENTITY(1, 1), [attachment_id] numeric(19,0),[description] text,[record_id] numeric(19,0),[update_date] datetime,[updater_id] numeric(19,0),[status]varchar(255),[changes]text,[allowedroles]varchar(255),CONSTRAINT PK_'+this.trail_table() + ' PRIMARY KEY(id))'
                }
                sql.execute(query)
            }
            def tablename = this.trail_table()
            def fname = 'record_id'
            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                query = "create index if not exists ix_" + fname + " on " + tablename + " (" + fname + ")"
            }
            else{
                query = "if not exists (select * from sys.indexes where name='ix_" + fname + "' and object_id=object_id('" + tablename + "'))begin create nonclustered index ix_" + fname + " on [" + tablename + "] ([" + fname + "]); end"
            }
            sql.execute(query)
        }
        this.fields.each { field->
            field.updatedb(datasource)
        }
    }

    def fromTable(datasource){
        PortalTrackerField.withTransaction { sqltrans->
            def sql = new Sql(datasource)
            def rowquery = "select * from INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + this.data_table() + "'"
            try {
                def tfields = sql.rows(rowquery.toString())
                def type_column = 'DATA_TYPE'
                if(config.dataSource.url.contains("jdbc:postgresql")){
                    type_column = 'udt_name'
                }
                tfields.each { tfield-> 
                    def ftype = 'Text'
                    if(tfield[type_column] in ['int','int8','numeric','int4']){
                        ftype = 'Integer'
                    }
                    else if(tfield[type_column]=='date'){
                        ftype = 'Date'
                    }
                    else if(tfield[type_column] in ['datetime','timestamp']){
                        ftype = 'DateTime'
                    }
                    else if(tfield['DATA_TYPE']=='decimal'){
                        ftype = 'Number'
                    }
                    else if(tfield['DATA_TYPE']=='text'){
                        ftype = 'Text Area'
                    }
                    def nfield = PortalTrackerField.findByTrackerAndName(this,tfield['COLUMN_NAME'])
                    if(!nfield){
                        nfield = new PortalTrackerField(tracker:this,name:tfield['COLUMN_NAME'],label:tfield['COLUMN_NAME'].replaceAll("_"," ").capitalize(),field_type:ftype)
                        nfield.save(flush:true)
                    }
                    else {
                        nfield.field_type = ftype
                        nfield.save(flush:true)
                    }
                }
            }
            catch(Exception exp) {
                println "Error import from table. <br/>" + exp
            }
        }
    }

    def rowclass(row) {
        if(this.rowclassval){
            try{
                Binding binding = new Binding()
                binding.setVariable("row",row)
                def shell = new GroovyShell(this.class.classLoader,binding)
                return shell.evaluate(this.rowclassval)
            }
            catch(Exception e){
                PortalErrorLog.record(null,null,'tracker','rowclass',e.toString())
            }
        }
        return ''
    }

    def default_field() {
        def toret = null
        fields.each { field->
            if(field.name in ['name','title']){
                toret = field.name
            }
        }
        return toret
    }

    def data_table() {
        if(datatable){
            return datatable
        }
        else{
            return 'trak_' + module.toLowerCase() + '_' + slug.toLowerCase() + '_data'
        }
    }

    def trail_table() {
        if(trailtable){
            return trailtable
        }
        else{
            return 'trak_' + module.toLowerCase() + '_' + slug.toLowerCase() + '_updates'
        }
    }

    def newtransition(curuser=null) {
        def newt = null
        if(curuser){
            def croles = this.module_roles(curuser)
            if(croles){
                croles.each { crole->
                    if(!newt){
                        newt = PortalTrackerTransition.createCriteria().get() {
                            'in'('roles',crole)
                            'eq'('tracker',this)
                            'eq'('next_status',this.initial_status)
                        }
                    }
                }
            }
        }
        if(!newt){
            newt = PortalTrackerTransition.createCriteria().get() {
                'eq'('tracker',this)
                'eq'('next_status',this.initial_status)
            }
        }
        return newt
    }

    def transitionallowed(tname,curuser,datas=null) {
        println "In transition test"
        def dt = PortalTrackerTransition.findAllByTrackerAndName(this,tname)
        def toreturn = false
        if(dt) {
            println "Transition to test :" + dt
            PortalTrackerData.withSession { sessiondata ->
                def datasource = sessiondata.connection()
                def uroles = this.user_roles(curuser,datas)
                def dtroles = dt.roles*.name
                println "Dtroles:" + dtroles
                println "Size:" + dtroles[0].size()
                if(dtroles instanceof Collection && dtroles[0].size()>0) {
                    uroles.each { urole->
                        if(urole.name in dtroles) {
                            toreturn = true
                        }
                        if(!toreturn && dtroles instanceof Collection){
                          dtroles.each { cdt->
                            if(urole.name in cdt) {
                                toreturn = true
                            }
                          }
                        }
                    }
                }
                else {
                    toreturn = true
                }
            }
        }
        return toreturn
    }

    def savedatas(datas) {
        PortalTracker.withSession { sqlsession ->
            return updaterow(sqlsession.connection(),datas)
        }
    }

    def static savedatas(module,slug,datas) {
        def curtracker = PortalTracker.findByModuleAndSlug(module,slug)
        if(curtracker) {
            PortalTracker.withSession { sqlsession ->
                return curtracker.updaterow(sqlsession.connection(),datas)
            }
        }
    }

    def static base64Encode(inputString){
        def encoded = inputString.bytes.encodeBase64().toString()
        encoded = encoded.replaceAll('=','-')
        return encoded
    }

    def static base64Decode(encodedString){
        encodedString = encodedString.replaceAll('-','=')
        byte[] decoded = encodedString.decodeBase64()
        String decode = new String(decoded)
        return decode
    }

    def static decodeparams(params){
        def changedparams = [:]
        def toremove = []
        params.each { pkey,pval ->
            if(pkey.size()>3 && pkey[0..2]=='g5e') {
               def dval = base64Decode(pkey[3..-1])
               changedparams[dval] = base64Decode(pval)
               toremove << pkey
            }
        }
        changedparams.each { ckey,cval->
            params[ckey] = cval
        }
        toremove.each { pkey ->
            params.remove(pkey)
        }
    }

    def static encodeparams(params){
        def toret = [:]
        params.each { key,val ->
            try {
                if(key in ['slug','module','controller','action']) {
                    toret[key] = val
                }
                else {
                    def ekey = base64Encode(key.toString())
                    def eval = base64Encode(val.toString())
                    toret['g5e' + ekey] = eval
                }
            }
            catch(Exception exp) {
                println "Exp:" + exp
            }
        }
        return params
    }

    def savetrail(record_id,description,curuser,status=null,session=null,request=null) {
        def dparams = [:]
        dparams['statusUpdateDesc'] = description
        dparams['id'] = record_id
        dparams['status'] = status
        PortalTracker.withSession { sqlsession ->
            return updatetrail(dparams,session,request,curuser,sqlsession.connection())
        }
    }

    def updatetraildesc(record_id,description) {
        PortalTracker.withSession { sqlsession ->
            def query = "update " + trail_table() + " set description=:description where id=:id"
            def qparams = ['id':record_id,'description':description]
            def datasource = sqlsession.connection()
            def sql = new Sql(datasource)
            sql.execute(query,qparams)
        }
    }

    def updaterow(datasource,datas){
        def sql = new Sql(datasource)
        def query = ''
        def qparams = [:]
        if(datas['id']){
            def updatefields = []
            datas.each { k,v->
                if(k!='id'){
                    def dfield = PortalTrackerField.findByTrackerAndName(this,k)
                    if(dfield){
                        updatefields << k + "=:" + k
                        if(v) {
                            qparams[k] = dfield.safeval(v)
                        }
                        else {
                            qparams[k] = v
                        }
                    }
                    else if(k=='record_status'){
                        updatefields << k + "=:" + k
                        qparams[k] = v
                    }
                }
            }
            qparams['id']=datas['id']
            query = 'update ' + data_table() + ' set ' + updatefields.join(' , ') + ' where id=:id'
            try{
                sql.execute(query,qparams)
            }
            catch(Exception e){
                PortalErrorLog.record(datas,null,'tracker','updaterow',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
            }
        }
        else{
            def fieldname = []
            def fieldval = []
            datas.each { key,val->
                def dfield = PortalTrackerField.findByTrackerAndName(this,key)
                if(dfield){
                    fieldname << key
                    fieldval << ":" + key
                    qparams[key] = dfield.safeval(val)
                }
            }
            query = "insert into " + data_table() + " (" + fieldname.join(',') + ") values (" + fieldval.join(" , ") + ")"
            def maxid = null
            if(config.dataSource.url.contains("jdbc:postgresql")) {
                query += " returning id"
                try{
                    maxid = sql.firstRow(query,qparams)
                }
                catch(Exception e){
                    PortalErrorLog.record(datas,null,'tracker','updaterow',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
                }
            }
            else if(config.dataSource.url.contains("jdbc:h2")){
                try{
                    maxid = ['id':sql.executeInsert(query,qparams)[0][0]]
                }
                catch(Exception e){
                    PortalErrorLog.record(datas,null,'tracker','updaterow',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
                }
            }
            else {
                try{
                    maxid = ['id':sql.executeInsert(query,qparams)[0][0]]
                }
                catch(Exception e){
                    println "Got exception:" + e
                    PortalErrorLog.record(datas,null,'tracker','updaterow',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
                }
            }
            if(maxid){
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    datas['id'] = maxid['id']
                }
                else {
                    datas['id'] = maxid[0][0]
                }
            }
            else{
                datas['id'] = 1
            }
        }
        return datas
    }

    def row_status(datasource,record_id){
        if(tracker_type!='DataStore') {
            def sql = new Sql(datasource)
            def query = "select top 1 record_status from " + data_table() + " where id=:id"
            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                query = "select record_status from " + data_table() + " where id=:id limit 1"
            }
            def rec_status=sql.firstRow(query,['id':record_id])
            if(rec_status){
                return PortalTrackerStatus.createCriteria().get() {
                    'eq'('tracker',this)
                    'eq'('name',rec_status['record_status'])
                }
            }
            else{
                return null
            }
        }
        else {
            return null
        }
    }

    def module_roles(curuser=null){
        if(curuser){
            def user_roles = []
            def mroles = curuser.modulerole(this.module)
            roles.each { role->
                if(role.name=='Authenticated' && curuser){
                    user_roles << role
                }
                else if(role.role_type=='User Role'){
                    if(role.name in mroles){
                        user_roles << role
                    }
                }
            }
            return user_roles
        }
        else{
            return []
        }
    }

    def checkAdmin(curuser) {
        def roles = module_roles(curuser)
        if('Admin' in roles*.name) {
            return true
        }
        return false
    }

    def user_roles(curuser,datas=null) {
        def cuser_roles = []
        if(curuser){
            roles.each { role->
                if(role.name=='Admin' && checkAdmin(curuser)){
                    cuser_roles << role
                }
                else if(role.role_type=='User Role'){
                    def userrole = UserRole.createCriteria().list() {
                        'eq'('user',curuser)
                        'eq'('module',module)
                        'eq'('role',role.name)
                    }
                    userrole.each { ur->
                        cuser_roles << role
                    }
                    if(curuser.currentrole()?.role?.trim()==role.name.trim()) {
                        cuser_roles << role
                    }
                }
                else if(role.role_type=='Data Compare'){
                    def hasileval = role.evalrole(curuser,datas)
                    if(hasileval && hasileval.trim()){
                        def query = ""
                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                            query = "select id from " + data_table() + " where "
                        }
                        else{
                            query = "select top 1 id from " + data_table() + " where "
                        }
                        if(datas && datas['id'] ){
                            query += " id= " + datas['id'] + " and " + hasileval
                        }
                        else{
                            query += hasileval
                        }
                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                            query += " limit 1"
                        }
                        def data = raw_firstRow(query)
                        if(data){
                            cuser_roles << role
                        }
                    }
                }
            }
        }
        return cuser_roles
    }

    def role_query(user){
        def queries = []
        roles.each { role->
            if(role.role_type=='Data Compare'){
                def deval = role.evalrole(user,null)
                if(deval.trim()){
                    queries << deval
                }
            }
        }
        return queries
    }

    def updatetrail(params,session,request,curuser,datasource) {
        def sql = new Sql(datasource)
        def attachment = null
        if(params.uploadfile){
            def uploadedfile = request?.getFile('uploadfile');
            if(uploadedfile && PortalSetting.findByName(slug + '_attachment_path')){
                if(uploadedfile.originalFilename.size()>0){
                    def settingname = slug + '_attachment_path'
                    def defaultfolder = System.getProperty("user.dir").toString() + '/uploads/' + slug
                    def folderbasepath = PortalSetting.namedefault(settingname,defaultfolder) + '/' + params.id
                    def folderbase = new File(folderbasepath)
                    if(folderbase){
                        if(!folderbase.exists()){
                            folderbase.mkdirs()
                        }
                        def copytarget = folderbasepath+'/'+uploadedfile.originalFilename
                        uploadedfile.transferTo(new File(copytarget))
                        if(new File(copytarget).exists()){
                            def curdate = new java.text.SimpleDateFormat("yyyyMMddHHmm").format(new Date())
                            attachment = new FileLink(name:uploadedfile.originalFilename,path:copytarget,module:module,slug:slug+'_'+params.id+'_'+curdate,tracker_data_id:params?.id,tracker_id:this.id)
                            attachment.save()
                        }
                    }
                }
            }
        }
        def userroles = user_roles(curuser,['id':params.id])
        def curstatus = row_status(datasource,params.id)
        def gotrules = []
        def updateallowedroles = ''
        if(curstatus?.updateallowedroles){
            def statusrules = curstatus.updateallowedroles.tokenize(';')*.trim()
            userroles.each { urole->
                statusrules.each  { srule->   //eg: Admin->Admin; RD->RD,Admin  === would mean updates done by admin can only be seen by admin, but updates done by rd can be seen by rd and admin
                    def parts=srule.tokenize('->')*.trim()
                    if(urole.name.trim()==parts[0].trim()){
                        if(parts[1]?.trim()?.toLowerCase()!='all'){
                            gotrules << parts[1]?.trim()
                        }
                    }
                }
            }
        }
        if(gotrules){
            updateallowedroles = gotrules.join(',')
        }
        def query=''
        def qparams=[:]
        qparams['description'] = params?.statusUpdateDesc
        qparams['record_id'] = params?.id
        qparams['updater_id'] = curuser?.id
        qparams['status'] = params?.record_status
        qparams['allowedroles'] = updateallowedroles
        def curdate = new Date()
        qparams['update_date'] = curdate
        def maxid = null
        if(config.dataSource.url.contains("jdbc:postgresql")) {
            if(attachment){
                qparams['attachment_id'] = attachment?.id
                query = "insert into " + trail_table() + " (attachment_id,description,record_id,update_date,updater_id,status,allowedroles) values (:attachment_id , :description , :record_id , :update_date , :updater_id , :status , :allowedroles) returning id"
            }
            else{
                query = "insert into " + trail_table() + " (description,record_id,update_date,updater_id,status,allowedroles) values (:description , :record_id , :update_date , :updater_id , :status , :allowedroles) returning id"
            }
            try{
                // print("Update trail query:" + query)
                // print("Update trail params:" + qparams)
                // sql.execute(query,qparams)
                maxid = sql.firstRow(query,qparams)
            }
            catch(Exception e){
                PortalErrorLog.record(params,curuser,'tracker','updatetrail',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
            }
        }
        else if(config.dataSource.url.contains("jdbc:h2")){
            if(attachment){
                qparams['attachment_id'] = attachment?.id
                query = "insert into " + trail_table() + " (attachment_id,description,record_id,update_date,updater_id,status,allowedroles) values (:attachment_id , :description , :record_id , :update_date , :updater_id , :status , :allowedroles)"
            }
            else{
                query = "insert into " + trail_table() + " (description,record_id,update_date,updater_id,status,allowedroles) values (:description , :record_id , :update_date , :updater_id , :status , :allowedroles)"
            }
            try{
                // print("Update trail query:" + query)
                // print("Update trail params:" + qparams)
                maxid = ['id':sql.executeInsert(query,qparams)[0][0]]
            }
            catch(Exception e){
                PortalErrorLog.record(params,curuser,'tracker','updatetrail',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
            }
        }
        else {
            if(attachment){
                qparams['attachment_id'] = attachment?.id
                query = "insert into " + trail_table() + " (attachment_id,description,record_id,update_date,updater_id,status,allowedroles) values (:attachment_id , :description , :record_id , :update_date , :updater_id , :status , :allowedroles)"
            }
            else{
                query = "insert into " + trail_table() + " (description,record_id,update_date,updater_id,status,allowedroles) values (:description , :record_id , :update_date , :updater_id , :status , :allowedroles)"
            }
            try{
                /* sql.execute(query,qparams)
                maxid = sql.firstRow("select SCOPE_IDENTITY() as id") */
                maxid = sql.firstRow(query + "; select SCOPE_IDENTITY() as id", qparams)
            }
            catch(Exception e){
                println "Error :" + e
                PortalErrorLog.record(params,curuser,'tracker','updatetrail',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug)
            }
        }

        if(curstatus && curstatus.emailonupdate){
            Binding updatebinding = new Binding()
            updatebinding.setVariable("session",session)
            curdatas['update_desc']=params.statusUpdateDesc
            updatebinding.setVariable("datas",curdatas)
            def shell = new GroovyShell(this.class.classLoader,updatebinding)
            def email = curstatus.emailonupdate
            def toccs = null
            def tosend = null
            try{
                tosend = shell.evaluate(email.emailto)
                if(email.emailcc){
                    toccs = shell.evaluate(email.emailcc)
                }
            }
            catch(Exception e){
                PortalErrorLog.record(params,curuser,'tracker','updatetrail - tosend and toccs',e.toString(),this.slug)
            }
            def emailcontent = email.evalbody(curdatas)
            try {
                mailService.sendMail {
                    to tosend
                    if(toccs){
                        cc toccs
                    }
                    subject emailcontent['title']
                    html emailcontent['body']
                }
            }
            catch(Exception e){
                println 'Error with sending email ' + email.body.title + ' : ' + e.toString()
                def emailpagerror = PortalSetting.findByName("emailpagerror")
                if(emailpagerror && mailService){
                    mailService.sendMail {
                        to emailpagerror.value().trim()
                        subject "Page Error"
                        body 'Error with sending email ' + email.body.title + ' : ' + e.toString() + '''
                        Params: ''' + params
                    }
                }
                PortalErrorLog.record(params,curuser,'tracker','updatetrail',e.toString(),this.slug)
            }
        }
        return maxid
    }

    def static getdatas(module,slug,id,sql=null){
        def curtracker = PortalTracker.findByModuleAndSlug(module,slug)
        if(curtracker) {
            PortalTracker.withSession { sqlsession ->
                if(!sql) {
                    sql = new Sql(sqlsession.connection())
                }
                return sql.firstRow("select * from " + curtracker.data_table() + " where id=:id",['id':id])
            }
        }
        else {
            return null
        }
    }

    def getdatas(id,sql=null){
        if(id.getClass().isArray()) {
            id=id[0]
        }
        if(sql) {
            return sql.firstRow("select * from " + this.data_table() + " where id=:id",['id':id])
        }
        else {
            PortalTracker.withSession { sqlsession ->
                sql = new Sql(sqlsession.connection())
                return sql.firstRow("select * from " + this.data_table() + " where id=:id",['id':id])
            }
        }
    }

    def parseqparams(qparams,joiner='and') {
        def qq = []
        def finalparams = [:]
        qparams.each { qpkey,qpval->
            if(qpkey in ['and','or']){
                def tocombine = parseqparams(qpval,qpkey)
                finalparams += tocombine['params']
                qq += tocombine['query']
            }
            else if(qpkey == 'not') {
                def tocombine = parseqparams(qpval)
                finalparams += tocombine['params']
                qq << ' not ' + tocombine['query']
            }
            else {
                def gotq = false
                if(qpval instanceof String || qpval instanceof GString) {
                    if(qpval.indexOf('%')>-1) {
                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                            qq << " " + qpkey + " ilike :" + qpkey + " "
                        }
                        else {
                            qq << " " + qpkey + " like :" + qpkey + " "
                        }
                        gotq = true
                    }
                    else if(qpval[0] in ['<','>']) {
                        if(qpval[1]=='=') {
                            qq << " " + qpkey + " " + qpval[0] + "= :" + qpkey + " "
                            qpval = qpval[2..-1]
                        }
                        else {
                            qq << " " + qpkey + " " + qpval[0] + " :" + qpkey + " "
                            qpval = qpval[1..-1]
                        }
                        gotq = true
                    }
                    else if(qpval[0] in ['!']) {
                        qq << " " + qpkey + " != :" + qpkey + " "
                        qpval = qpval[1..-1]
                        gotq = true
                    }
                    else if(qpval==null) {
                        qq << " " + qpkey + " is null "
                    }
                }
                else if(qpval instanceof List) {
                    def dp = []
                    def posp = 1
                    qpval.each { arval->
                        if(arval==null) {
                            dp << qpkey + " is null"
                        }
                        else {
                            def ddkey = qpkey + '_' + posp.toString()
                            if(arval.indexOf('%')>-1) {
                                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                    dp << " " + qpkey + " ilike :" + ddkey + " "
                                }
                                else {
                                    dp << " " + qpkey + " like :" + ddkey + " "
                                }
                            }
                            else if(arval[0] in ['<','>']) {
                                if(arval[1]=='=') {
                                    dp << " " + qpkey + " " + arval[0] + "= :" + ddkey + " "
                                    arval = arval[2..-1]
                                }
                                else {
                                    dp << " " + qpkey + " " + arval[0] + " :" + ddkey + " "
                                    arval = arval[1..-1]
                                }
                            }
                            else {
                                dp << qpkey + "=:" + ddkey
                            }
                            posp += 1
                            finalparams[ddkey] = arval
                        }
                    }
                    qpval = null
                    qq << "(" + dp.join(" or ") + ")"
                    gotq = true
                }
                else if(qpval instanceof HashMap) {
                    gotq = true
                    qpval.each { inkey,inval->
                        if(inkey in ['and','or']){
                            if(inval instanceof List) {
                                def inp = []
                                def inposp = 1
                                inval.each { iv->
                                    if(iv==null) {
                                        inp << qpkey + " is null"
                                    }
                                    else {
                                        def ddkey = qpkey + '_in_' + inposp.toString()
                                        if(iv.indexOf('%')>-1) {
                                            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                                inp << " " + qpkey + " ilike :" + ddkey + " "
                                            }
                                            else {
                                                inp << " " + qpkey + " like :" + ddkey + " "
                                            }
                                        }
                                        else if(iv[0] in ['<','>']) {
                                            if(iv[1]=='=') {
                                                inp << " " + qpkey + " " + iv[0] + "= :" + ddkey + " "
                                                iv = iv[2..-1]
                                            }
                                            else {
                                                inp << " " + qpkey + " " + iv[0] + " :" + ddkey + " "
                                                iv = iv[1..-1]
                                            }
                                        }
                                        else {
                                            inp << qpkey + "=:" + ddkey
                                        }
                                        inposp += 1
                                        finalparams[ddkey] = iv
                                    }
                                }
                                qpval = null
                                qq << "(" + inp.join(" " + inkey + " ") + ")"
                            }
                            else {
                                def tocombine = parseqparams(inval,inkey)
                                finalparams += tocombine['params']
                                qq += tocombine['query']
                            }
                        }
                    }
                    qpval = null
                }
                else if(qpval==null) {
                    qq << " " + qpkey + " is null "
                    gotq = true
                }
                if(!gotq) {
                    qq << " " + qpkey + "=:" + qpkey + " "
                }
                if(qpval) {
                    finalparams[qpkey] = qpval
                }
            }
        }
        return ['query':"(" + qq.join(' ' + joiner + ' ') + ")" , 'params':finalparams]
    }

    def firstRow(qparams=null,qorder=null){
        PortalTracker.withSession { sqlsession ->
            def query = "select * from " + this.data_table()
            try {
                def sql = new Sql(sqlsession.connection())
                if(qparams && qparams.size()>0){
                    query += " where "
                    def ppquery = parseqparams(qparams)
                    query += ppquery['query']
                    qparams = ppquery['params']
                }
                if(qorder) {
                    query += " order by " + qorder
                }
                return sql.firstRow(query,qparams)
            }
            catch(Exception e){
                def msg = "Error with firstRow query -- " + query + " -- " + qparams + " -- " + e
                PortalErrorLog.record(null,null,'tracker','firstrow',msg,this.slug,this.module)
            }
        }
    }

    def static raw_firstRow(query,params=null) {
        PortalTracker.withSession { sqlsession ->
            try {
                def sql = new Sql(sqlsession.connection())
                if(params!=null) {
                    return sql.firstRow(query,params)
                }
                else{
                    return sql.firstRow(query)
                }
            }
            catch(Exception e){
                def msg = "Error with raw_firstRow query -- " + query + " -- " + params + " -- " + e
                PortalErrorLog.record(null,null,'tracker','raw firstrow',msg,null,null)
            }
        }
    }

    def static raw_rows(query,params=null) {
        PortalTracker.withSession { sqlsession ->
            try {
                def sql = new Sql(sqlsession.connection())
                if(params!=null) {
                    return sql.rows(query.toString(),params)
                }
                else {
                    return sql.rows(query.toString())
                }
            }
            catch(Exception e){
                def msg = "Error with raw_rows query -- " + query + " -- " + params + " -- " + e
                PortalErrorLog.record(null,null,'tracker','raw rows',msg,null,null)
            }
        }
    }

    def static raw_execute(query,params=null) {
        PortalTracker.withSession { sqlsession ->
            try {
                def sql = new Sql(sqlsession.connection())
                if(params!=null) {
                    return sql.execute(query,params)
                }
                else {
                    return sql.execute(query)
                }
            }
            catch(Exception e){
                def msg = "Error with raw_execute query -- " + query + " -- " + params + " -- " + e
                PortalErrorLog.record(null,null,'tracker','raw execute',msg,null,null)
            }
        }
    }


    def static load_rows(module,slug,qparams=null,order=null) {
        try {
            def curtracker = PortalTracker.findByModuleAndSlug(module,slug)
            if(curtracker) {
                return curtracker.rows(qparams,order)
            }
            return null
        }
        catch(Exception e){
            def msg = "Error with load_rows query -- " + qparams + " -- " + e
            PortalErrorLog.record(null,null,'tracker','raw execute',msg,null,null)
        }
    }

    def static load_datas(module,slug,qparams=null,order=null) {
        try {
            def curtracker = PortalTracker.findByModuleAndSlug(module,slug)
            if(curtracker) {
                return curtracker.firstRow(qparams,order)
            }
            return null
        }
        catch(Exception e){
            def msg = "Error with load_datas query -- " + qparams + " -- " + e
            PortalErrorLog.record(null,null,'tracker','raw execute',msg,slug,module)
        }
    }

    def rows(qparams=null,order=null){
        PortalTracker.withSession { sqlsession ->
            def sql = new Sql(sqlsession.connection())
            def query = "select * from " + this.data_table()
            try {
                if(qparams && qparams.size()>0){
                    query += " where "
                    def ppquery = parseqparams(qparams)
                    query += ppquery['query']
                    qparams = ppquery['params']
                }
                if(order) {
                    query += " order by " + order
                }
                if(qparams && qparams.size()>0){
                    return sql.rows(query.toString(),qparams)
                }
                else{
                    return sql.rows(query.toString())
                }
            }
            catch(Exception e){
                def msg = "Error with rows query -- " + query + " -- " + qparams + " -- " + e
                PortalErrorLog.record(null,null,'tracker','rows',msg,this.slug,this.module)
            }
        }
    }

    @Transactional()
    def updaterecord(params,request,session,sql,defaultfields=[]) {
        def fieldnames = []
        def fieldvalues = []
        def deleted = false
        def toreturn = [:]
        def query = ''
        def qparams = [:]
        def curuser = null
        if(session && session.userid){
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        if(params.id.getClass().isArray()) {
            params.id=params.id[0]
        }
        if(params.id){
            curdatas = getdatas(params['id'],sql)
            def next_status = PortalTrackerStatus.get(params['next_status'])
            if(next_status?.name?.toLowerCase()=='delete'){
                query = "select * from " + data_table() + " where id=:id"
                def data = sql.firstRow(query,['id':params.id])
                this.fields.each { dfield->
                    if(dfield.field_type=='File'){
                        def dfile = FileLink.get(data[dfield.name])
                        if(dfile){
                            dfile.delete()
                        }
                    }
                    else if(dfield.field_type=='HasMany'){
                        def othertracker = PortalTracker.findBySlug(dfield.field_options)
                        if(othertracker){
                            def linkback = PortalTrackerField.createCriteria().get() {
                                'eq'('tracker',othertracker)
                                'eq'('field_type','BelongsTo')
                                'like'('field_options',dfield.tracker.slug)
                            }
                            if(linkback){
                                try {
                                    query = "select id from " + othertracker.data_table() + " where " + linkback.name + "=" + params.id
                                    sql.eachRow(query) { row->
                                        query = "delete from " + othertracker.trail_table() + " where " + othertracker.slug + "_id=" + row['id']
                                        sql.execute(query)
                                        query = "delete from " + othertracker.data_table() + " where id=" + row['id']
                                        sql.execute(query)
                                    }
                                }
                                catch(Exception e){
                                    def msg = "Error with query -- " + query + " -- " + e
                                    PortalErrorLog.record(null,null,'tracker','updaterecord',msg,this.slug,this.module)
                                }
                            }
                        }
                    }
                }
                if(tracker_type=='Tracker') {
                    try {
                        query = "delete from " + trail_table() + " where record_id=:id"
                        sql.execute(query,['id':params.id])
                    }
                    catch(Exception e){
                        def msg = "Error with query -- " + query + " -- " + e
                        PortalErrorLog.record(null,null,'tracker','updaterecord',msg,this.slug,this.module)
                    }
                }
                try {
                    query = "delete from " + data_table() + " where id=:id"
                    sql.execute(query,['id':params.id])
                }
                catch(Exception e){
                    def msg = "Error with query -- " + query + " -- " + e
                    PortalErrorLog.record(null,null,'tracker','updaterecord',msg,this.slug,this.module)
                }
                deleted = true
            }
        }
        else{
            def maxid = null
            if(tracker_type!='DataStore') {
                if(config.dataSource.url.contains("jdbc:postgresql")) {
                    def ddq = "insert into " + data_table() + " (record_status) values ('sys_draft') returning id"
                    maxid = sql.firstRow(ddq);
                } 
                else if(config.dataSource.url.contains("jdbc:h2")){
                    def ddq = "insert into " + data_table() + " (record_status) values ('sys_draft')"
                    maxid = ['id':sql.executeInsert(ddq)[0][0]]
                }
                else {
                    sql.execute("insert into " + data_table() + " (record_status) values ('sys_draft')");
                    maxid = sql.firstRow("select SCOPE_IDENTITY() as id")
                }
                if(maxid['id']){
                    curdatas['id']=maxid['id']
                    params.id = curdatas['id']
                }
                else{
                    curdatas['id']=1
                    params.id = 1
                }
            }
        }
        if(!deleted){
            params.each { key,value->
                def validfield = true
                if(key=='next_status'){
                    if(tracker_type!='DataStore') {
                        def next_status = PortalTrackerStatus.get(value)
                        if(next_status){
                            fieldnames << "record_status"
                            fieldvalues << next_status.name
                        }
                    }
                }
                else if(key=='record_status'){  //skip record status update?
                }
                else if(key=='id'){  //also do not update id
                }
                else {
                    if(key in ['indata_slug','slug']) {
                        if('indata_slug' in params) {
                            if(key=='indata_slug') {
                                key = 'slug'
                            }
                            else {
                                validfield = false
                            }
                        }
                    }
                    def pfield = PortalTrackerField.createCriteria().get() {
                        'eq'('tracker',this)
                        'eq'('name',key)
                    }
                    if(!pfield && !(key.substring(1) in params)){
                        pfield = PortalTrackerField.createCriteria().get() {
                            'eq'('tracker',this)
                            'eq'('name',key.substring(1))
                        }
                    }
                    if(pfield){
                        if(pfield.is_encrypted){
                            value = CryptoUtilsFile.encrypt(value,new File(config.encryption.key))
                        }
      
                        def defaultval = null
                        if(pfield.field_default){
                            try{
                                Binding binding = new Binding()
                                binding.setVariable("session",session)
                                binding.setVariable("curuser",curuser)
                                binding.setVariable("sql",sql)
                                def shell = new GroovyShell(this.class.classLoader,binding)
                                defaultval = shell.evaluate(pfield.field_default)
                            }
                            catch(Exception e){
                                println("Error with default value of " + pfield + " :" + e)
                                // PortalErrorLog.record(params,curuser,'tracker','updaterecord',e.toString(),pfield.tracker.slug,pfield.tracker.module)
                                defaultval = -1
                            }
                        }
                        if(defaultval!=null) {
                            defaultfields << pfield
                            validfield = false
                        }
                        else{
                            if(pfield.field_type=='DateTime'){
                                try {
                                    if(value){
                                        value = value.replace('T',' ')
                                    }
                                    else{
                                        value = null
                                    }
                                }
                                catch(Exception e) {
                                    validfield = false
                                }
                            }
                            else if(pfield.field_type=='Date'){
                                try {
                                    if(!value){
                                        value = null
                                    }
                                }
                                catch(Exception e) {
                                    validfield = false
                                }
                            }
                            else if(pfield.field_type in ['Number']) {
                                if(value==''){
                                    validfield = false
                                }
                                else {
                                    try {
                                        def ival = Double.parseDouble(value)
                                        value = ival
                                    }
                                    catch(Exception e) {
                                        validfield = false
                                    }
                                }
                            }
                            else if(pfield.field_type in ['Integer']) {
                                if(value==''){
                                    validfield = false
                                }
                                else {
                                    try {
                                        def ival = Integer.parseInt(value)
                                        value = ival
                                    }
                                    catch(Exception e) {
                                        validfield = false
                                    }
                                }
                            }
                            else if(pfield.field_type=='Checkbox'){
                                if(pfield.field_options){
                                    value = "'" + [value].flatten().join(',') + "'"
                                }
                                else {
                                    if(value=='on' || value=='1' || value==1){
                                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                            value = true
                                        }
                                        else {
                                            value = 1
                                        }
                                    }
                                    else{
                                        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                            value = false
                                        }
                                        else {
                                            value = 0
                                        }
                                    }
                                }
                            }
                            else if(pfield.field_type in ['User','Branch','File','Event']){
                                if(!value){
                                    validfield = false
                                }
                                else{
                                    if(pfield.field_type=='File'){
                                        def attachment = null
                                        validfield = false
                                        def uploadedfile = request.getFile(key)
                                        def settingname = module + '.' + slug + '_upload_path'
                                        if(uploadedfile){
                                            if(uploadedfile.originalFilename.size()>0){
                                                def defaultfolder = System.getProperty("user.dir").toString() + '/uploads/' + slug
                                                def folderbasepath = PortalSetting.namedefault(settingname,defaultfolder) + '/' + curdatas['id']
                                                def fileprepend = ''
                                                if(pfield.field_options){
                                                    def baseoption = pfield.evaloptions(session,curdatas)
                                                    if(baseoption){
                                                        def basetokens = baseoption.tokenize('/')*.trim()
                                                        def folderbase = basetokens.join('/')
                                                        if(basetokens.size()>1){
                                                            if(baseoption[-1]!='/'){
                                                                folderbase = basetokens[0..-2].join('/')
                                                                fileprepend = basetokens[-1]
                                                            }
                                                        }
                                                        folderbasepath = PortalSetting.findByModuleAndName(module,settingname).value() + '/' + folderbase
                                                    }
                                                }
                                                def folderbase = new File(folderbasepath)
                                                if(folderbase){
                                                    if(!folderbase.exists()){
                                                        folderbase.mkdirs()
                                                    }
                                                    def copytarget = folderbasepath+'/'+fileprepend+uploadedfile.originalFilename
                                                    def thetarget = new File(copytarget)
                                                    if(thetarget.exists()){
                                                        def appendfile = 1
                                                        while(thetarget.exists()){
                                                            def filetokens = copytarget.tokenize('.')
                                                            if(filetokens.size()>1){
                                                                thetarget = new File(filetokens[0..-2].join('.') + '_' + appendfile + '.' + filetokens[-1])
                                                            }
                                                            else{
                                                                thetarget = new File(copytarget + '_' + appendfile)
                                                            }
                                                            appendfile += 1
                                                        }
                                                    }
                                                    uploadedfile.transferTo(thetarget)
                                                    if(thetarget.exists()){
                                                        def curdate = new java.text.SimpleDateFormat("yyyyMMddHHmm").format(new Date())
                                                        attachment = new FileLink(name:uploadedfile.originalFilename,path:thetarget,module:module,slug:key+'_'+curdatas['id']+'_'+curdate,tracker_data_id:params.id,tracker_id:this.id)
                                                        if(attachment.save()){
                                                            validfield = true
                                                            value = attachment.id
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if(validfield){
                                curdatas[pfield.name] = value
                                fieldnames << pfield.name
                                fieldvalues << value
                            }
                        }
                    }
                }
            }
        }
        defaultfields.unique()
        defaultfields.each { pfield->
            if(pfield.field_default){
                try{
                    Binding binding = new Binding()
                    binding.setVariable("session",session)
                    binding.setVariable("datas",curdatas)
                    binding.setVariable("curuser",curuser)
                    binding.setVariable("sql",sql)
                    def shell = new GroovyShell(this.class.classLoader,binding)
                    fieldnames << pfield.name
                    fieldvalues << shell.evaluate(pfield.field_default)
                }
                catch(Exception e){
                    println("Error with default value of " + pfield + " :" + e)
                    PortalErrorLog.record(curdatas,curuser,'tracker','updaterecord',e.toString(),this.slug,this.module)
                }
            }
        }
        if(fieldnames){
            def updatefields = []
            def fieldnamepos = []
            fieldnames.eachWithIndex() { val,i->
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    updatefields << '"' + val + '"=:' + val
                }
                else{
                    updatefields << val + '=:' + val
                }
                qparams[val] = fieldvalues[i]
                fieldnamepos << ":" + val
                toreturn[val] = fieldvalues[i]
            }
            try{
                if(params.id){  // under most circumstance it will only update here. unless
                    qparams['id'] = params.id
                    try {
                        query = "update " + data_table() + " set " + updatefields.join(' , ') + " where id=:id"
                        println "Updaterecord query:" + query
                        println "Updaterecord qparams:" + qparams
                        sql.execute(query,qparams)
                    }
                    catch(Exception e){
                        def msg = "Error with query -- " + query + " -- " + qparams + " -- " + e
                        PortalErrorLog.record(null,null,'tracker','updaterecord',msg,this.slug,this.module)
                    }
                }       
                else{  // it was a datastore, then it will update here because id has not been set yet
                    PortalTracker.withSession { intsession -> 
                        def maxid = null
                        def internalsql = new Sql(intsession.connection())
                        if(config.dataSource.url.contains("jdbc:postgresql")) {
                            def ddq = "insert into " + data_table() + " (\"" + fieldnames.join('","') + "\") values (" + fieldnamepos.join(' , ') + ") returning id"
                            maxid = internalsql.firstRow(ddq,qparams)
                        }
                        else if(config.dataSource.url.contains("jdbc:h2")){
                            def ddq = "insert into " + data_table() + " (\"" + fieldnames.join('","') + "\") values (" + fieldnamepos.join(' , ') + ")"
                            maxid = ['id':internalsql.executeInsert(ddq,qparams)[0][0]]
                        }
                        else {
                            try {
                                query = "insert into " + data_table() + " (" + fieldnames.join(',') + ") values (" + fieldnamepos.join(' , ') + ");select SCOPE_IDENTITY() as id;"
                                maxid = internalsql.firstRow(query,qparams)
                                qparams['id'] = maxid.id
                            }
                            catch(Exception e){
                                def msg = "Error with query -- " + query + " -- " + qparams + " -- " + e
                                PortalErrorLog.record(null,null,'tracker','updaterecord',msg,this.slug,this.module)
                            }
                        }
                        if(maxid?.id){
                            curdatas['id']=maxid.id
                        }
                        else{
                            curdatas['id']=1
                        }
                    }
                }
            }
            catch(Exception e){
                println("Error query:" + query)
                println("Error qprams:" + qparams)
                println("Error:" + e)
                PortalErrorLog.record(curdatas,curuser,'tracker','updaterecord',e.toString() + ' query:' + query + ' qparams:' + qparams,this.slug,this.module)
            }
        }
        toreturn['id']=curdatas['id']
        return toreturn
    }

    def listquery(params,curuser,select="select * ") {
        def query = ""
        def offset = 0
        def activesort = 'id'
        def qparams = [:]

        def activecond = null
        if(defaultsort){
            activesort = defaultsort
        }
        if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
        }
        else {
            if(select=="select * ") {
                if(defaultlimit) {
                    if(defaultlimit>0) {
                        select = "select top ${defaultlimit} * "
                    }
                }
                else {
                    select = "select top 1000 * "
                }
            }
        }
        if(condition_q){
            activecond = condition_q
        }
        if(params.offset){
            offset = params.offset
        }
        // if(params.max && params.offset){
        //     if(config.dataSource.url.contains("jdbc:postgresql")){
        //         query = "select * from " + this.data_table() + " " 
        //     }
        //     else {
        //         query = "select * from ( select *,ROW_NUMBER() OVER  (ORDER BY ${Sql.expand(activesort)})  as row from " + this.data_table() + " " 
        //     }
        // }
        // else{         
        //     query = select + " from " + this.data_table() + " "
        // }
        query = select + " from " + this.data_table() + " "
        def selectpart = query
        def condition = []
        def querylinked = false

        params.each { curkey,curval->
            if(curval){
                if(curkey=='search'){
                    def likequery = []
                    def tfields = PortalTrackerField.createCriteria().list(){
                        'eq'('tracker',this)
                        'in'('name',searchfields.tokenize(',')*.trim())
                    } 
                    tfields.each { tfield->
                        if(tfield.field_type=='BelongsTo') {
                            def othertracker = tfield.othertracker()
                            if(othertracker){
                                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                    likequery << tfield.name + " in (select id from " + othertracker.data_table() + " where " + tfield.field_format + " ilike '%" + params.search + "%')"
                                }
                                else {
                                    likequery << tfield.name + " in (select id from " + othertracker.data_table() + " where " + tfield.field_format + " like '%" + params.search + "%')"

                                }
                            }
                        }
                        else if(tfield.field_type=='User'){
                            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                likequery << tfield.name + " in (select id from portal_user where name ilike '%" + params.search + "%' or StaffId ilike '%" + params.search + "%' or EMAIL ilike '%" + params.search + "%')"
                            }
                            else {
                                likequery << tfield.name + " in (select id from portal_user where name like '%" + params.search + "%' or StaffId like '%" + params.search + "%' or EMAIL like '%" + params.search + "%')"

                            }
                        }
                        else{
                            // likequery << tfield.name + " like '%" + params.search.replace("'","''") + "%'"
                            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                likequery << tfield.name + " ilike :" + tfield.name + " "
                            }
                            else {
                                likequery << tfield.name + " like :" + tfield.name + " "
                            }
                            qparams[tfield.name] = '%' + params.search + '%'

                      }
                    }
                    if(likequery){
                        condition << " (" + likequery.join(' or ')  + ") "
                    }
                }
                else if(curkey=='eventdatequery'){
                    def ddates = curval.tokenize('_')*.trim()
                    condition << " (start_date between '" + ddates[0] + "' and '" + ddates[1] + "') or (end_date between '" + ddates[0] + "' and '" + ddates[1] + "')"
                }
                else if(!(curkey in ['slug','module','action','controller','max','offset'])){
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',this)
                        'eq'('name',curkey)
                    } 
                    if(tfield){
                        if(tfield.field_type in ['Text','Text Area','Date','DateTime','Drop Down']){
                            if(tfield.field_type in ['Date','DateTime']) {
                                def dparts = curval.tokenize('-')
                                if(dparts.size()==2){
                                    condition << "datepart(YEAR," + curkey + ")=" + dparts[0] + " and datepart(MONTH," + curkey + ")=" + dparts[1]  + " "
                                }
                                else {
                                    if(curval.size()>8 && curval[0..6]=='between'){
                                        def ddates = curval[8..-1].tokenize('_')*.trim()
                                        condition << curkey + " between :${curkey}_1 and :${curkey}_2"
                                        qparams[curkey + '_1'] = ddates[0]
                                        qparams[curkey + '_2'] = ddates[1]
                                    }
                                    else {
                                        if(curval[0]=='<') {
                                            condition << curkey + " < :${curkey}"
                                        }
                                        else {
                                            condition << curkey + " > :${curkey}"
                                        }
                                        qparams[curkey] = curval[1..-1]

                                    }
                                }
                            }
                            else if(tfield.field_type=='Text' && curval.size()>8 && curval[0..6]=='between'){
                                def ddates = curval[8..-1].tokenize('_')*.trim()
                                condition << curkey + " between '" + ddates[0] + "' and '" + ddates[1] + "'"
                            }
                            else{
                                if(tfield.field_type == 'Drop Down'){
                                    if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                                        condition << curkey + "= :" + curkey
                                    }
                                    else {
                                        condition << "convert(nvarchar(max)," + curkey + ")= :" + curkey
                                    }
                                    qparams[curkey] = curval
                                }
                                else {
                                    condition << curkey + "= :" + curkey
                                    qparams[curkey] = curval
                                }
                                // condition << curkey + "='" + curval + "'"
                            }
                        }
                        else if(tfield.field_type == 'Checkbox'){
                            if(curval=='true'){
                                condition << curkey + "=1"
                            }
                            else if(curval=='false'){
                                condition << curkey + "=0"
                            }
                            else{
                            }
                        }
                        else{
                            condition << curkey + "=" + curval
                            if(tfield.field_type=='BelongsTo'){
                                querylinked = true
                            }
                        }
                    }
                }
            }
        }

        def userrules = false
        def isBackgroundProcess= params.isBackgroundProcess
        if(isBackgroundProcess)
        {
            userrules = true
        }
        else
        {
            if(curuser){
                if(this.module_roles(curuser).size()){  //not part of module users so need to filter by role
                    userrules = true
                }
                else if(this.role_query(curuser)){
                    if(params['excel'] && querylinked){
                        userrules = true
                    }
                    else{
                        condition << " (" + this.role_query(curuser).join(' or ') + ") "
                        userrules = true
                    }
                }
            }
            else {
                println("no users")
            }

            if(!userrules && !this.anonymous_list){
                condition << " 1!=1 "
            }
        }


        if(condition){
            query += " where " + condition.join(' and ')
            if(condition_q){
                query +=  " and " + activecond
            }
        }

        if(condition_q){
            if(condition_q && params.max){
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    query += "where " + activecond + " order by " + activesort + " limit " + params.max + " offset " + offset
                }
                else {
                    query += "where " + activecond + " order by " + activesort + " offset " + offset + " rows fetch next " + params.max + " rows only"
                    // query += "where " + activecond + ")a where row >"+ offset + " and row <= " + (offset.toInteger() + params.max.toInteger())
                }
            }
            else if(activesort){
                if(select.trim()!="select count(*)") {
                    query += "where " + activecond + " order by " + activesort
                }
                else {
                    query += "where " + activecond
                }
            }
        }else{
            if(params.max){
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    query += " order by " + activesort + " limit " + params.max + " offset " + offset
                }
                else {
                    query += " order by " + activesort + " offset " + offset + " rows fetch next " + params.max + " rows only"
                    // query += ") a where row > " + offset + " and row <= " + (offset.toInteger() + params.max.toInteger())    
                }
            }
            else if(activesort){
                if(select.trim()!="select count(*)") {
                    query += " order by " + activesort
                }
            }
        }

        // if(params.max && params.offset){
        //     if(config.dataSource.url.contains("jdbc:postgresql")){
        //     }
        //     else {
        //         query += " order by " + activesort
        //     }
        // }
       
        // if(params.max && select=="select count(id)" && condition_q){
        //     query += " where " + activecond
        // }

        // return query
        def toreturn = [:]
        toreturn['query'] = query
        toreturn['activecond'] = activecond
        toreturn['selectpart'] = selectpart
        if(activecond) {
          toreturn['selectquery'] = toreturn['selectpart'] + " where " + activecond
        }
        else{
          toreturn['selectquery'] = toreturn['selectpart']
        }
        toreturn['qparams'] = qparams
        return toreturn

    }
}
