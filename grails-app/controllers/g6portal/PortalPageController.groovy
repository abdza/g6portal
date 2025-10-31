package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import groovy.text.Template
import groovy.sql.Sql
import grails.converters.JSON
import grails.converters.XML
import grails.web.mapping.LinkGenerator
import static grails.util.Holders.config

class PortalPageController {

    PortalPageService portalPageService
    PortalService   portalService
    UserService userService
    LinkGenerator grailsLinkGenerator
    def mailService

    def sessionFactory
    def groovyPagesTemplateEngine

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All')) {
            def query = '%' + params.q + '%'
            if(params.module && params.module!='All') {
                def thelist = portalPageService.list(query,params.module,dparam)
                respond thelist, model:[portalPageCount: portalPageService.count(query,params.module), params:params]
            }
            else {
                if(session.enablesuperuser) {
                    def thelist = portalPageService.list(query,dparam)
                    respond thelist, model:[portalPageCount: portalPageService.count(query), params:params]
                }
                else {
                    def thelist = portalPageService.list(query,session.developermodules,dparam)
                    respond thelist, model:[portalPageCount: portalPageService.count(query,session.developermodules), params:params]
                }
            }
        }
        else {
            if(session.enablesuperuser) {
                def thelist = portalPageService.list(dparam)
                respond thelist, model:[portalPageCount: portalPageService.count(), params:params]
            }
            else {
                def thelist = portalPageService.list(session.developermodules,dparam)
                respond thelist, model:[portalPageCount: portalPageService.count(session.developermodules), params:params]
            }
        }
    }

    def show(Long id) {
        respond portalPageService.get(id)
    }

    def create() {
        respond new PortalPage(params)
    }

    def runpage() {
        PortalTracker.decodeparams(params) 
        // def curuser = User.get(session.userid)
        def curuser = session.curuser
        def gotolist = true
        def page = null
        def content = null
        def sql = new Sql(sessionFactory.currentSession.connection())
        if(params.id){
            page = PortalPage.get(params.id)
        }
        else if(params.slug){
            if(params.module) {
                page = PortalPage.findByModuleAndSlug(params.module,params.slug)
            }
            else {
                page = PortalPage.findByModuleAndSlug('portal',params.slug)
            }
        }
        if(page){
            if(page && page.published){
                if(curuser?.isAdmin || (page.runable && (!page.requirelogin || curuser))){
                    try{
                        PortalPage.withTransaction { sqltrans->
                            Binding binding = new Binding()
                            binding.setVariable("datasource",sessionFactory.currentSession.connection())
                            binding.setVariable("sessionFactory",sessionFactory)
                            binding.setVariable("session",session)
                            binding.setVariable("sql",sql)
                            binding.setVariable("flash",flash)
                            binding.setVariable("curuser",curuser)
                            binding.setVariable("mailService",mailService)
                            binding.setVariable("params",params)
                            binding.setVariable("request",request)
                            binding.setVariable("grailsLinkGenerator",grailsLinkGenerator)
                            binding.setVariable("portalService",portalService)
                            binding.setVariable("userService",userService)
                            def datas = [:]
                            page.datasources.each { ds->
                                def dquery = ds.query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                                def query = new GroovyShell(binding).evaluate(dquery)
                                query=query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                                datas[ds.name]=[]
                                def pattern = ~/:([a-zA-Z_]+)/
                                def matcher = query =~ pattern
                                if(matcher.size()>0) {
                                    if(ds.return_one) {
                                        datas[ds.name] = sql.firstRow(query,params)
                                    }
                                    else {
                                        sql.eachRow(query,params) { row->
                                            datas[ds.name]<<row.toRowResult()
                                        }
                                    }
                                }
                                else {
                                    if(ds.return_one) {
                                        datas[ds.name] = sql.firstRow(query)
                                    }
                                    else {
                                        sql.eachRow(query) { row->
                                            datas[ds.name]<<row.toRowResult()
                                        }
                                    }
                                }
                            }
                            binding.setVariable("datas",datas)
                            def shell = new GroovyShell(this.class.classLoader,binding)
                            content = shell.evaluate(page.content)
                            gotolist = false
                        }
                    }
                    catch(Exception e){
                        println 'Error running page ' + page.title + ' : ' + e.toString()
                        PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString(),page.slug,page.module)
                        def emailpagerror = PortalSetting.findByName("emailpagerror")
                        if(emailpagerror){
                            sendMail {
                                to emailpagerror.value().trim()
                                subject "Page Error"
                                body 'Error running page ' + page.title + ' : ' + e.toString() + '''
                Params: ''' + params + '''
                Curuser: ''' + curuser
                            }
                        }
                    }
                }
            }
        }
        if(gotolist){
            redirect(action: "index", params: params)
        }
        else if(page.render=='JSON') {
            return render(text: content as JSON, contentType: "text/html")
        }
        else if(page.render=='XML') {
            return render(text: content, contentType: "text/xml")
        }
        else if(page.render=='HTML') {
            return render(text: content, contentType: "text/html")
        }
        else if(page.render=='XLSX') {
            if(!('wb' in content)) {
                PortalErrorLog.record(params,curuser,controllerName,actionName,"No wb defined in excel page return",page.slug,page.module)
                flash.message = "Error generating excel file" 
                redirect(action: "index", params: params)
            }
            if(!('filename' in content)) {
                PortalErrorLog.record(params,curuser,controllerName,actionName,"No filename defined in excel page return",page.slug,page.module)
                flash.message = "Error generating excel file" 
                redirect(action: "index", params: params)
            }
            response.setContentType("application/octet-stream")
            response.setHeader("Content-disposition","attachment;filename=" + content['filename'] + ".xlsx")
            try {
                content['wb'].write(response.outputStream)
                response.outputStream.flush()
                response.outputStream.close()
                content['wb'].dispose()
            }
            catch(Exception exp) {
              println "Error generating excel:" + exp
            }
        }
        else if(page.render=='File') {
            if(content) {
                def thefile = new File(content)
                if(thefile.exists()){
                    try{
                        response.setContentType("application/octet-stream")
                        response.setHeader("Content-disposition", "attachment;filename=${thefile.getName().replace(' ','_')}")
                        if(params.thumbsize){
                            resize(thefile.getBytes(),response.outputStream,params.int('thumbsize'),params.int('thumbsize'))
                        }
                        else{
                            def bis = null
                            try{
                                bis = thefile.newInputStream()
                                response.outputStream << bis
                            }
                            finally {
                                bis.close()
                                response.outputStream.flush()
                            }
                        }
                        return
                    }
                    catch(Exception ex){
                        def errormsg = "Got error download file " + thefile + ":" + ex
                        PortalErrorLog.record(params,curuser,controllerName,actionName,errormsg,page.slug,page.module)
                    }
                }
                else{
                    flash.message = "The file you requested does not exist" 
                    def errormsg = "File requested does to exist. " + thefile + ":" + ex
                    PortalErrorLog.record(params,curuser,controllerName,actionName,errormsg,page.slug,page.module)
                    redirect(action: "index", params: params)
                }
            }
            else{
                flash.message = "You do not have access to that file" 
                redirect(action: "index", params: params)
            }
        }
        else{
            if(page.redirectafter){
                def keytokens = page.redirectafter.tokenize(':')
                if(keytokens.size()>1){
                    if(keytokens[0].trim()=='statement'){
                        def nextmodule = page.module
                        def nextslug = keytokens[1].trim()
                        def stoken = nextslug.tokenize('.')   // if slug contains a dot, then it's actually a module.slug
                        def shortcut = false
                        if(stoken.size()>1) {
                            nextmodule = stoken[0].trim()
                            nextslug = stoken[1].trim()
                        }
                        redirect(controller:'statement',action:'view',params:[module:nextmodule,slug:nextslug])
                    }
                    else if(keytokens[0].trim()=='tracker'){
                        def nextmodule = page.module
                        def nextslug = keytokens[1].trim()
                        def stoken = nextslug.tokenize('.')   // if slug contains a dot, then it's actually a module.slug
                        def shortcut = false
                        if(stoken.size()>1) {
                            nextmodule = stoken[0].trim()
                            nextslug = stoken[1].trim()
                        }
                        redirect(controller:'portalTracker',action:'list',params:[module:nextmodule,slug:nextslug])
                    }
                    else if(keytokens[0].trim()=='page'){
                        def nextmodule = page.module
                        def nextslug = keytokens[1].trim()
                        def stoken = nextslug.tokenize('.')   // if slug contains a dot, then it's actually a module.slug
                        def shortcut = false
                        if(stoken.size()>1) {
                            nextmodule = stoken[0].trim()
                            nextslug = stoken[1].trim()
                            shortcut = true
                        }
                        def nextparams = [module:nextmodule,slug:nextslug]
                        if(keytokens.size()>2 || shortcut) {
                            def start = 3
                            if(shortcut) {
                                start = 2
                            }
                            for(int i=start;i<keytokens.size();i++){
                                if(keytokens[i] in params){
                                    nextparams[keytokens[i]] = params[keytokens[i]]
                                }
                            }
                        }
                        def nextpage = PortalPage.findByModuleAndSlug(nextmodule,nextslug)
                        if(nextpage && nextpage.runable) {
                            redirect(controller:'portalPage',action:'runpage',params:nextparams)
                        }
                        else{
                            redirect(controller:'portalPage',action:'display',params:nextparams)
                        }
                    }
                    else{
                        redirect(controller:keytokens[0],action:keytokens[1])
                    }
                }
                else{
                    redirect(controller:'portalPage',action:'display',params:[slug:page.redirectafter])
                }
            }
            else{
                if('redirect_slug' in content){
                    def rparams = ['slug':content['redirect_slug']]
                    if('redirect_module' in content) {
                        rparams += ['module':content['redirect_module']]
                    }
                    else {
                        rparams += ['module':page.module]
                    }
                    if('redirect_params' in content){
                        rparams += content['redirect_params']
                    }
                    def nextpage = PortalPage.findByModuleAndSlug(rparams['module'],rparams['slug'])
                    if(nextpage) {
                        if(nextpage.runable) {
                            redirect(controller:'portalPage',action:'runpage',params:rparams)
                        }
                        else {
                            redirect(controller:'portalPage',action:'display',params:rparams)
                        }
                    }
                    else {
                        def nexttracker = PortalTracker.findByModuleAndSlug(rparams['module'],rparams['slug'])
                        if(nexttracker) {
                            redirect(controller:'portalTracker',action:'list',params:rparams)
                        }
                        else {
                            redirect(controller:'portalPage',action:'display',params:rparams)
                        }
                    }
                }
                else if('redirect_url' in content) {
                    redirect(url:content['redirect_url'])
                }
                else{
                    def finalcontent = content
                    if('content' in content){
                        finalcontent = content['content']
                    }
                    [pageInstance:page,content:finalcontent]
                }
            }
        }
    }

    def display() {
        PortalTracker.decodeparams(params) 
        if(!params.module){
            params.module='portal'
        }
        def pageInstance = PortalPage.findByModuleAndSlug(params.module,params.slug,[cache:true])
        def curuser = null
        if(session.curuser){
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        if (!pageInstance) {
            redirect(controller:"portalPage", action: "home")
        }
        else {
            def output = new StringWriter()
            def content = pageInstance.content
            try{
                def datas = [:]
                def preprocess = null
                def datasource = sessionFactory.currentSession.connection()
                def sql = new Sql(datasource)
                Binding binding = new Binding()
                binding.setVariable("datasource",datasource)
                binding.setVariable("sql",sql)
                binding.setVariable("session",session)
                binding.setVariable("curuser",curuser)
                binding.setVariable("params",params)
                binding.setVariable("mailService",mailService)
                binding.setVariable("request",request)
                binding.setVariable("portalService",portalService)
                binding.setVariable("grailsLinkGenerator",grailsLinkGenerator)
                binding.setVariable("userService",userService)
                if(pageInstance.datasources.size()){
                    PortalPage.withTransaction { ppt->
                        pageInstance.datasources.each { ds->
                            def dquery = ds.query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                            def query = new GroovyShell(binding).evaluate(dquery)
                            query=query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                            datas[ds.name]=[]
                            def pattern = ~/:([a-zA-Z_]+)/
                            def matcher = query =~ pattern
                            if(matcher.size()>0) {
                                if(ds.return_one) {
                                    datas[ds.name] = sql.firstRow(query,params)
                                }
                                else {
                                    sql.eachRow(query,params) { row->
                                        datas[ds.name]<<row.toRowResult()
                                    }
                                }
                            }
                            else {
                                if(ds.return_one) {
                                    datas[ds.name] = sql.firstRow(query)
                                }
                                else {
                                    sql.eachRow(query) { row->
                                        datas[ds.name]<<row.toRowResult()
                                    }
                                }
                            }
                        }
                    }
                }
                if(pageInstance.preprocess) {
                    binding.setVariable("datas",datas)
                    def shell = new GroovyShell(this.class.classLoader,binding)
                    preprocess = shell.evaluate(pageInstance.preprocess)
                }
                def pagename = 'page' + pageInstance.id + pageInstance.lastUpdated.toString()
                Template template = groovyPagesTemplateEngine.createTemplate(pageInstance.content,pagename)
                template.make([pp:preprocess,datas:datas,curcontroller:this,curuser:curuser,sessionFactory:sessionFactory,sql:sql,portalService:portalService,userService:userService,mailService:mailService]).writeTo(output)
            }
            catch(Exception e){
                println 'Error with page ' + pageInstance.title + ' : ' + e.toString()
                PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString())
                def emailpagerror = PortalSetting.findByName("emailpagerror")
                if(emailpagerror){
                    sendMail {
                        to emailpagerror.value().trim()
                        subject "Page Error"
                        body 'Error with page ' + pageInstance.title + ' : ' + e.toString() + '''
				Params: ''' + params + '''
				Curuser: ''' + curuser
                    }
                }
            }
            if(pageInstance.render=='XML') {
              return render(text: output.toString(), contentType: "text/xml")
            }
            else if(pageInstance.render=='HTML') {
              return render(text: output.toString(), contentType: "text/html")
            }
            else {
                [pageInstance:pageInstance,content:output.toString()]
            }
        }
    }

    def setup() {
        println "In setup"
        def allow_setup = config.server.allow_setup
        if(allow_setup) {
            println "Doing setup"
            def admin_user = User.findByIsAdmin(true)
            if(!admin_user) {
                println "Admin not found. Creating one"
                try {
                    PortalPage.withTransaction { sqltrans->
                        admin_user = new User()
                        admin_user.userID = 'admin'
                        admin_user.name = 'Admin'
                        admin_user.isAdmin = true
                        admin_user.isActive = true
                        admin_user.resetPassword = false
                        admin_user.email = 'admin@g6portal.com'
                        admin_user.hashPassword('admin1234$')
                        admin_user.save(flush:true)
                        def portal_dev = new UserRole()
                        portal_dev.user = admin_user
                        portal_dev.module = 'portal'
                        portal_dev.role = 'Developer'
                        portal_dev.save(flush:true)
                        println "Done saving admin. Login with admin, admin1234\$"
                    }
                }
                catch(Exception exp) {
                    println "Error saving admin:" + exp
                }
            }
        }
        redirect(controller:'portalPage',action:'home')
    }

    def home() {
        println "In home"
        def pageInstance = PortalPage.findByModuleAndSlug('portal','home',[cache:true])
        def curuser = null
        def output = new StringWriter()
        if(session.curuser){
            // curuser = User.get(session.userid)
            curuser = session.curuser
        }
        if(pageInstance && pageInstance.published){
            try{
                def content = pageInstance.content
                def sql = new Sql(sessionFactory.currentSession.connection())
                Binding binding = new Binding()
                binding.setVariable("datasource",sessionFactory.currentSession.connection())
                binding.setVariable("sessionFactory",sessionFactory)
                binding.setVariable("session",session)
                binding.setVariable("sql",sql)
                binding.setVariable("curuser",curuser)
                binding.setVariable("params",params)
                binding.setVariable("request",request)
                binding.setVariable("portalService",portalService)
                binding.setVariable("userService",userService)
                def datas = [:]
                if(pageInstance.datasources.size()){
                    pageInstance.datasources.each { datasource->
                        def dquery = datasource.query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                        def query = new GroovyShell(binding).evaluate(dquery)
                        query=query.replaceAll('\r\n'," ").replaceAll('  ',' ')
                        datas[datasource.name]=[]
                        sql.eachRow(query) { row->
                            datas[datasource.name]<<row.toRowResult()
                        }
                    }
                }
               def pagename = 'page' + pageInstance.id + pageInstance.lastUpdated.toString()
                Template template = groovyPagesTemplateEngine.createTemplate(content,pagename)
                template.make([datas:datas,curcontroller:this,curuser:curuser,sessionFactory:sessionFactory,sql:sql]).writeTo(output)
            }
            catch(Exception e){
                println 'Error with page ' + pageInstance.title + ' : ' + e.toString()
                def emailpagerror = PortalSetting.findByName("emailpagerror")
                if(emailpagerror){
                    sendMail {
                        to emailpagerror.value().trim()
                        subject "Page Error"
                        body 'Error with page ' + pageInstance.title + ' : ' + e.toString() + '''
				Params: ''' + params + '''
				Curuser: ''' + curuser
                    }
                }
                PortalErrorLog.record(params,curuser,controllerName,actionName,e.toString())
            }
        }
        [pageInstance:pageInstance,content:output.toString()]
    }

    def save(PortalPage portalPage) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            if(portalPage?.slug){
                redirect(action:"list",params:[slug:portalPage?.slug,module:portalPage?.module])
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
            if (portalPage == null) {
                notFound()
                return
            }

            try {
                portalPageService.save(portalPage)
            } catch (ValidationException e) {
                respond portalPage.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'portalPage.label', default: 'PortalPage'), portalPage.id])
                    redirect portalPage
                }
                '*' { respond portalPage, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond portalPageService.get(id)
    }

    def update(PortalPage portalPage) {
        def abandon = false
        withForm {
        }.invalidToken {
            flash.message = "Invalid session for the forms"
            if(portalPage?.slug){
                redirect(action:"list",params:[slug:portalPage?.slug,module:portalPage?.module])
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
            if (portalPage == null) {
                notFound()
                return
            }

            try {
                portalPageService.save(portalPage)
            } catch (ValidationException e) {
                respond portalPage.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'portalPage.label', default: 'PortalPage'), portalPage.id])
        if(params.submitpage=='Update') {
          redirect action: "edit", method: "GET", id: portalPage.id
        }
        else {
          redirect portalPage
        }
                }
                '*'{ respond portalPage, [status: OK] }
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

            portalPageService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalPage.label', default: 'PortalPage'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalPage.label', default: 'PortalPage'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
