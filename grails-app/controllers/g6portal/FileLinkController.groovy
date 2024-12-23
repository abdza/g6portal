package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class FileLinkController {

    FileLinkService fileLinkService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        def dparam = [max:params.max?:10,offset:params.offset?:0]
        params.max = dparam.max
        if(params.q || (params.module && params.module!='All')) {
            def query = '%' + params.q + '%'
            if(params.module && params.module!='All') {
                def thelist = fileLinkService.list(query,params.module,dparam)
                respond thelist, model:[fileLinkCount: fileLinkService.count(query,params.module), params:params]
            }
            else {
                if(session.enablesuperuser) {
                    def thelist = fileLinkService.list(query,dparam)
                    respond thelist, model:[fileLinkCount: fileLinkService.count(query), params:params]
                }
                else {
                    def thelist = fileLinkService.list(query,session.adminmodules,dparam)
                    respond thelist, model:[fileLinkCount: fileLinkService.count(query,session.adminmodules), params:params]
                }
            }
        }
        else {
            if(session.enablesuperuser) {
                def thelist = fileLinkService.list(dparam)
                respond thelist, model:[fileLinkCount: fileLinkService.count(), params:params]
            }
            else {
                def thelist = fileLinkService.list(session.adminmodules,dparam)
                respond thelist, model:[fileLinkCount: fileLinkService.count(session.adminmodules), params:params]
            }
        }
    }

    def show(Long id) {
        respond fileLinkService.get(id)
    }

    def create() {
        respond new FileLink(params)
    }

    def save(FileLink fileLink) {
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
            if (fileLink == null) {
                notFound()
                return
            }
            try {
                def f = request.getFile('fileupload')
                if (f.empty) {
                    flash.message = 'file cannot be empty'
                    render(view: 'create')
                    return
                }
                def fileName = f.originalFilename
                def curfolder = System.getProperty("user.dir")
                def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
                folderbase += '/' + fileLink.module
                if(!(new File(folderbase).exists())){
                    println "Folderbase -----" + folderbase + "-------- does not exists. Creating it"
                    new File(folderbase).mkdirs()
                }
                if(new File(folderbase).exists()){
                    println "Folderbase -----" + folderbase + "-------- does exists. Can copy over"
                    def copytarget = folderbase+'/'+fileName
                    println "Will copy to " + copytarget
                    f.transferTo(new File(copytarget))
                    fileLink.path = copytarget
                } 
                fileLinkService.save(fileLink)
            } catch (ValidationException e) {
                respond fileLink.errors, view:'create'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.created.message', args: [message(code: 'fileLink.label', default: 'FileLink'), fileLink.id])
                    redirect fileLink
                }
                '*' { respond fileLink, [status: CREATED] }
            }
        }
    }

    def edit(Long id) {
        respond fileLinkService.get(id)
    }

    def update(FileLink fileLink) {
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
            if (fileLink == null) {
                notFound()
                return
            }

            try {
                def f = request.getFile('fileupload')
                if (!f.empty) {
                    def fileName = f.originalFilename
                    def curfolder = System.getProperty("user.dir")
                    def folderbase = PortalSetting.namedefault('uploadfolder',curfolder + '/uploads')
                    folderbase += '/' + fileLink.module
                    if(!(new File(folderbase).exists())){
                        println "Folderbase -----" + folderbase + "-------- does not exists. Creating it"
                        new File(folderbase).mkdirs()
                    }
                    if(new File(folderbase).exists()){
                        println "Folderbase -----" + folderbase + "-------- does exists. Can copy over"
                        def copytarget = folderbase+'/'+fileName
                        println "Will copy to " + copytarget
                        f.transferTo(new File(copytarget))
                        fileLink.path = copytarget
                    } 
                }
                fileLinkService.save(fileLink)
            } catch (ValidationException e) {
                respond fileLink.errors, view:'edit'
                return
            }

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.updated.message', args: [message(code: 'fileLink.label', default: 'FileLink'), fileLink.id])
                    redirect fileLink
                }
                '*'{ respond fileLink, [status: OK] }
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

            fileLinkService.delete(id)

            request.withFormat {
                form multipartForm {
                    flash.message = message(code: 'default.deleted.message', args: [message(code: 'fileLink.label', default: 'FileLink'), id])
                    redirect action:"index", method:"GET"
                }
                '*'{ render status: NO_CONTENT }
            }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'fileLink.label', default: 'FileLink'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }


    def download = {
        def filelink=null
        if(params.slug){
            def slugroot = params.slug.tokenize('.')
            filelink = FileLink.find("from FileLink as fl where fl.slug=:slug",[slug:slugroot[0]],[cache:true])
        }
        if(params.id){
            filelink = FileLink.get(params.id)
        }
        if(filelink && filelink.path){            
            def thefile = new File(filelink.path)
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
                    println "Got error download file " + thefile + ":" + ex
                }
            }
            else{
                println "File " + thefile + " does not exist"
            }
        }
    }
}
