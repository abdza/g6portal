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
            // Validate ID is numeric to prevent injection
            try {
                Long.parseLong(params.id.toString())
                filelink = FileLink.get(params.id)
            } catch (NumberFormatException e) {
                response.status = 400
                render(text: "Invalid file ID")
                return
            }
        }
        
        if(filelink && filelink.path){
            // Security check: verify user has permission to access this file
            def hasAccess = false
            
            // Check if user is authenticated
            if(!session.userid) {
                response.status = 401
                render(text: "Authentication required")
                return
            }
            
            // Check if user is admin or has access to the file's module
            if(session.enablesuperuser) {
                hasAccess = true
            } else if(session.adminmodules && filelink.module) {
                hasAccess = (filelink.module in session.adminmodules)
            } else if(filelink.tracker_id) {
                // Check if user has access to the tracker that owns this file
                def tracker = PortalTracker.get(filelink.tracker_id)
                if(tracker && session.curuser) {
                    def userRoles = tracker.user_roles(session.curuser)
                    hasAccess = userRoles.size() > 0
                }
            }
            
            if(!hasAccess) {
                response.status = 403
                render(text: "Access denied")
                return
            }
            
            def thefile = new File(filelink.path)
            
            // Security: Validate file path to prevent directory traversal
            def canonicalPath = thefile.getCanonicalPath()
            def basePath = System.getProperty("user.dir") + "/uploads"
            def baseCanonical = new File(basePath).getCanonicalPath()
            
            if(!canonicalPath.startsWith(baseCanonical)) {
                response.status = 403  
                render(text: "Invalid file path")
                return
            }
            
            if(thefile.exists()){
                try{
                    // Enhanced file type validation
                    def allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'txt', 'zip']
                    def filename = thefile.getName().toLowerCase()
                    def extension = filename.substring(filename.lastIndexOf('.') + 1)
                    
                    if(!(extension in allowedExtensions)) {
                        PortalErrorLog.record(params, session.curuser, 'filelink', 'download', 
                            "Blocked download of file with disallowed extension: " + extension, 
                            filelink.slug, filelink.module)
                        response.status = 403
                        render(text: "File type not allowed")
                        return
                    }
                    
                    // Check file size limit (10MB default)
                    def maxFileSize = 10 * 1024 * 1024 // 10MB
                    if(thefile.size() > maxFileSize) {
                        PortalErrorLog.record(params, session.curuser, 'filelink', 'download', 
                            "Blocked download of oversized file: " + thefile.size() + " bytes", 
                            filelink.slug, filelink.module)
                        response.status = 413
                        render(text: "File too large")
                        return
                    }
                    
                    response.setContentType("application/octet-stream")
                    response.setHeader("Content-disposition", "attachment;filename=${thefile.getName().replace(' ','_').replaceAll('[^a-zA-Z0-9._-]', '_')}")
                    
                    if(params.thumbsize){
                        // Validate thumbsize parameter 
                        def thumbSize = 0
                        try {
                            thumbSize = Integer.parseInt(params.thumbsize.toString())
                            if(thumbSize < 1 || thumbSize > 500) {
                                thumbSize = 150 // default
                            }
                        } catch (NumberFormatException e) {
                            thumbSize = 150 // default
                        }
                        resize(thefile.getBytes(),response.outputStream,thumbSize,thumbSize)
                    }
                    else{
                        def bis = null
                        try{
                            bis = thefile.newInputStream()
                            response.outputStream << bis
                        }
                        finally {
                            bis?.close()
                            response.outputStream.flush()
                        }
                    }
                    
                    // Log successful download
                    PortalErrorLog.record(params, session.curuser, 'filelink', 'download_success', 
                        "Downloaded file: " + thefile.getName(), 
                        filelink.slug, filelink.module)
                    return
                }
                catch(Exception ex){
                    PortalErrorLog.record(params, session.curuser, 'filelink', 'download_error', 
                        "Error downloading file " + thefile + ": " + ex.toString(), 
                        filelink.slug, filelink.module)
                    response.status = 500
                    render(text: "Error downloading file")
                }
            }
            else{
                PortalErrorLog.record(params, session.curuser, 'filelink', 'file_not_found', 
                    "File does not exist: " + thefile, 
                    filelink.slug, filelink.module)
                response.status = 404
                render(text: "File not found")
            }
        } else {
            response.status = 404
            render(text: "File not found")
        }
    }
}
