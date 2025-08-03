package g6portal

import groovy.sql.Sql
import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class PortalTrackerFieldController {

    PortalTrackerFieldService portalTrackerFieldService
    PortalTrackerService portalTrackerService
    PortalService portalService

    def sessionFactory

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def onchange() {
        def sessiondata = sessionFactory.currentSession.connection()
        def sql = new Sql(sessiondata)
        def curfield = portalTrackerFieldService.get(params['hx_field_id'])
        if(curfield.field_type=='File' && params['id']) {
            def datas = PortalTracker.getdatas(curfield.tracker.module,curfield.tracker.slug,params['id'])
            if(!params[curfield.name]) {
                params[curfield.name] = datas[curfield.name]
            }
        }
        def content = portalService.field_error_js(curfield,params,session)
        return render(text: content, contentType: "text/html")
    }

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond portalTrackerFieldService.list(params), model:[portalTrackerFieldCount: portalTrackerFieldService.count()]
    }

    def show(Long id) {
        respond portalTrackerFieldService.get(id)
    }

    def create() {
        respond new PortalTrackerField(params)
    }

    def save(PortalTrackerField portalTrackerField) {
        if (portalTrackerField == null) {
            notFound()
            return
        }

        try {
            portalTrackerFieldService.save(portalTrackerField)
        } catch (ValidationException e) {
            respond portalTrackerField.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'portalTrackerField.label', default: 'PortalTrackerField'), portalTrackerField.id])
                redirect portalTrackerField.tracker
            }
            '*' { respond portalTrackerField, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond portalTrackerFieldService.get(id)
    }

    def update(PortalTrackerField portalTrackerField) {
        if (portalTrackerField == null) {
            notFound()
            return
        }

        try {
            portalTrackerFieldService.save(portalTrackerField)
        } catch (ValidationException e) {
            respond portalTrackerField.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'portalTrackerField.label', default: 'PortalTrackerField'), portalTrackerField.id])
                redirect portalTrackerField.tracker
            }
            '*'{ respond portalTrackerField, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        def currentTracker = portalTrackerFieldService.get(id)?.tracker
        portalTrackerFieldService.delete(id)
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'portalTrackerField.label', default: 'PortalTrackerField'), id])
                redirect controller: "portalTracker", action: "show", id:currentTracker.id
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'portalTrackerField.label', default: 'PortalTrackerField'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    def excelTemplate(Long id) {
        def currentTracker = portalTrackerService.get(id)
        return [tracker:currentTracker]
    }

    def multifields(Long id) {
        def currentTracker = portalTrackerService.get(id)
        params.tracker = currentTracker
        params.name = 'test'
        def trackerField = new PortalTrackerField(params)
        def submitted = false
        params.list('field_name').eachWithIndex { val,i->
            def oldfield = PortalTrackerField.findByTrackerAndName(currentTracker,val)
            if(!oldfield) {
                PortalTrackerField.withTransaction { trn ->
                    def newfield = new PortalTrackerField()
                    newfield.tracker = currentTracker
                    newfield.name = val
                    newfield.label = params.field_label[i]
                    newfield.field_type = params.field_type[i]
                    newfield.save(flush:true)
                    submitted = true
                }
            }
        }
        if(submitted) {
            redirect currentTracker
            return
        }
        else {
            return [trackerField:trackerField,tracker:currentTracker]
        }
    }

    def createIndex(Long id) {
        def currentTracker = portalTrackerService.get(id)
        flash.message = 'DB index created'
        portalService.createIndex(currentTracker)
        redirect controller: "portalTracker", action: "show", id:currentTracker.id
    }

    def excelTemplateFields(Long id) {
        def currentTracker = portalTrackerService.get(id)
        def f = request.getFile('fileupload')
        if (f.empty) {
            flash.message = 'file cannot be empty'
            render(view: 'create')
            return
        }
        def fileName = f.originalFilename
        def curfolder = System.getProperty("user.dir")
        def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
        folderbase += '/tmp'
        if(!(new File(folderbase).exists())){
            new File(folderbase).mkdirs()
        }
        def copytarget = null
        if(new File(folderbase).exists()){
            copytarget = folderbase+'/'+fileName
            f.transferTo(new File(copytarget))
        } 

        def fields = []

        if(copytarget) {
            // Check file extension to determine file type
            def fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
            
            if (fileExtension in ['xls', 'xlsx']) {
                // Handle Excel file
                PoiExcel poiExcel = new PoiExcel()
                poiExcel.headerstart = params.header_start?params.header_start.toInteger():0
                poiExcel.headerend = params.header_end?params.header_end.toInteger():0
                fields = poiExcel.getHeaders(copytarget)
            } else if (fileExtension in ['json','txt']) {
                // Handle JSON file
                try {
                    def jsonSlurper = new groovy.json.JsonSlurper()
                    def jsonData = jsonSlurper.parse(new File(copytarget))
                    
                    // Expecting JSON structure as an array of field objects with name, label, type properties
                    if (jsonData instanceof List) {
                        fields = jsonData.collect { fieldDef ->
                            [
                                name: fieldDef.name ?: fieldDef.field_name ?: '',
                                text: fieldDef.label ?: fieldDef.field_label ?: fieldDef.name ?:  fieldDef.field_name ?: '',
                                type: fieldDef.type ?: fieldDef.field_type ?: 'Text',
                                options: fieldDef.options ? "['" + fieldDef.options.join("','") + "']" : '',
                                col: fieldDef.name ?: fieldDef.field_name // Using name as col for compatibility with existing template
                            ]
                        }
                    } else if (jsonData.fields && jsonData.fields instanceof List) {
                        // Alternative structure: {"fields": [...]}
                        fields = jsonData.fields.collect { fieldDef ->
                            [
                                name: fieldDef.name ?: fieldDef.field_name ?: '',
                                text: fieldDef.label ?: fieldDef.field_label ?: fieldDef.name ?:  fieldDef.field_name ?: '',
                                type: fieldDef.type ?: fieldDef.field_type ?: 'Text',
                                options: fieldDef.options ? "['" + fieldDef.options.join("','") + "']" : '',
                                col: fieldDef.name ?: fieldDef.field_name // Using name as col for compatibility with existing template
                            ]
                        }
                    } else {
                        flash.message = 'JSON file must contain an array of field definitions or an object with a "fields" array property'
                        render(view: 'create')
                        return
                    }
                } catch (Exception e) {
                    flash.message = "Error parsing JSON file: ${e.message}"
                    render(view: 'create')
                    return
                }
            } else {
                flash.message = 'Unsupported file type. Please upload an Excel (.xls, .xlsx) or JSON (.json) file.'
                render(view: 'create')
                return
            }
        }

        def fieldtypes = ["Ignore","Text","Text Area","Integer","Number","Date","DateTime","Checkbox","Drop Down","MultiSelect","User","File","Branch","BelongsTo","HasMany","Hidden"] 

        return [tracker:currentTracker,excelfields:fields,fieldtypes:fieldtypes]
    }

    def submitExcelTemplate(Long id) {
        def currentTracker = portalTrackerService.get(id)
        params.each { key,val->
            if(key.size()>6) {
                if(key[0..5]=='ftype_' && val!='Ignore') {
                    def fname = key[6..-1]
                    def nfield = portalTrackerFieldService.findByTrackerAndName(currentTracker,params['fname_' + fname])
                    if(!nfield) {
                        def newfield = new PortalTrackerField(name:params['fname_' + fname],label:params['flabel_' + fname],field_default:params['fdefault_' + fname],field_options:params['foptions_' + fname],tracker:currentTracker,field_type:val)
                        portalTrackerFieldService.save(newfield)
                    }
                    else {
                        nfield.name = params['fname_' + fname]
                        nfield.label = params['flabel_' + fname]
                        nfield.field_default = params['fdefault_' + fname]
                        nfield.field_options = params['foptions_' + fname]
                        nfield.field_type = val
                        portalTrackerFieldService.save(nfield)
                    }
                }
            }
        }
        redirect controller: "portalTracker", action: "show", id:currentTracker.id
    }

    def updateDb(Long id) {
        def currentTracker = portalTrackerService.get(id)
        flash.message = 'DB updated'
        portalService.updateDb(currentTracker)
        redirect controller: "portalTracker", action: "show", id:currentTracker.id
    }

    def fromTable(Long id) {
        def currentTracker = portalTrackerService.get(id)
        flash.message = 'Fields imported from DB'
        portalService.fromTable(currentTracker)
        redirect controller: "portalTracker", action: "show", id:currentTracker.id
    }


    def fromExcel() {
        if (portalTrackerData == null) {
            notFound()
            return
        }
        try {
            if(params.tracker_id) {
                portalTrackerData.tracker = portalTrackerService.get(params.tracker_id)
            }
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

        Binding binding = new Binding()
        params.each { key,dparm->
            if(dparm=='All'){
                params[key]=null
            }
        }
        binding.setVariable('params',params)
        fields.sort{ it.name }.each { field->
            def foundfield = PortalTrackerField.findByTrackerAndName(portalTrackerData.tracker,field.name)
            if(foundfield){
                setfields[foundfield.id] = field.col
                if(foundfield.field_default){
                    customdata[foundfield.id]= new GroovyShell(binding).evaluate(foundfield.field_default)
                }
            }
            excelfields << ['id':field.col,'name':field.text]
        }

        [portalTrackerData:portalTrackerData,excelfields:excelfields,setfields:setfields,customdata:customdata]
    }
}
