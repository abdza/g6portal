package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*
import grails.async.Promises
import static grails.async.Promises.*

class FileLinkController {

    FileLinkService fileLinkService
    FileLinkUpdateService fileLinkUpdateService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def updateMissingSizes() {
        // Scope to one module when asked. Unscoped runs cover every file record in the
        // system, which on a production-sized table is a long job — the view warns about it.
        //
        // The param is deliberately NOT called "module": SecurityInterceptor's fileLink
        // branch treats params.module as "the module of the file being accessed" and
        // demands the caller be an Admin/Developer of it, which blocks a global admin
        // from backfilling a module they hold no role in. params.module is still honoured
        // for anything that already links here with it.
        def scope = params.scopemodule ?: params.module ?: null

        // Because the interceptor no longer gates this per module, enforce the same
        // global-admin rule the UI uses to decide whether to show the link at all.
        if (!session.curuser?.isAdmin) {
            flash.message = "Administrator rights are required to run a file size job."
            redirect action: "jobstatus"
            return
        }

        // Refuse to stack concurrent runs: two jobs would fight over the same rows and
        // both progress counters would be meaningless.
        def alreadyRunning = JobStatus.findByJobTypeAndStatus('FILE_SIZE_UPDATE', 'RUNNING')
        if (alreadyRunning) {
            flash.message = "A file size job is already running (Job #${alreadyRunning.id}). Wait for it to finish, or cancel it first."
            redirect action: "jobstatus"
            return
        }

        // Create the tracking row here, on the request thread: a task{} thread has no
        // Hibernate session, so GORM calls fail there. The job itself is pure SQL and
        // only needs the id.
        def remaining = scope ? FileLink.countByModuleAndSizeIsNull(scope) : FileLink.countBySize(null)
        // Created via the service: controller actions are not transactional, so a save()
        // here fails with "no transaction is in progress".
        def jobId = fileLinkUpdateService.createBackfillJob(scope)

        // Read tuning settings here too: PortalSetting.namedefault is a GORM dynamic
        // finder, which throws "No Session found for current thread" inside task{}.
        int batchSize = (PortalSetting.namedefault('filesize_job_batch_size', 500) ?: 500) as Integer
        int delayMs   = (PortalSetting.namedefault('filesize_job_batch_delay_ms', 100) ?: 100) as Integer

        def promise = task {
            fileLinkUpdateService.updateMissingFileSizesWithTracking(jobId, scope, batchSize, delayMs)
        }

        promise.onComplete { result ->
            log.info "File size update job completed (Job ID: ${result})."
        }

        promise.onError { Throwable error ->
            log.error "File size update job failed: ${error.message}", error
        }

        flash.message = "File size job #${jobId} started" + (scope ? " for module '${scope}'" : " for all modules") +
                        " covering ${remaining} unsized records. Progress refreshes automatically."
        redirect action: "jobstatus"
    }

    /**
     * Requests cancellation. The job itself checks this flag between batches and stops
     * cleanly, so work already committed is kept.
     */
    def cancelSizeJob() {
        def problem = fileLinkUpdateService.requestCancel(params.id ? params.long('id') : null)
        flash.message = problem ?:
            "Cancellation requested for job #${params.id}. It will stop after the current batch."
        redirect action: "jobstatus"
    }

    /**
     * Status page for background file-size jobs. Shows the JobStatus history plus
     * overall sizing coverage; the view auto-refreshes while a job is RUNNING.
     */
    def jobstatus() {
        def jobs = JobStatus.findAllByJobType('FILE_SIZE_UPDATE', [sort:'startTime', order:'desc', max:20])
        def running = jobs.any { it.status == 'RUNNING' }
        // render, not respond: respond treats an empty/null result as NOT_FOUND and 404s,
        // which is exactly the "no jobs have run yet" case this page needs to handle.
        render view:'jobstatus', model:[jobs:jobs, running:running, progress:fileLinkUpdateService.getSizeUpdateProgress()]
    }

    // Progress check endpoint
    def sizeUpdateProgress() {
        def progress = fileLinkUpdateService.getSizeUpdateProgress()
        render(contentType: "application/json") {
            progress
        }
    }

    // Alternative endpoint for job with tracking
    def updateMissingSizesWithTracking() {
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

        if(!session.enablesuperuser) {
            flash.message = "Insufficient permissions to perform this operation"
            redirect(controller:'portalPage',action:'index')
            return
        }

        // Both endpoints now run the same tracked job; this one just adds the form-token
        // and superuser checks in front of it. Kept as a single code path so the two
        // cannot drift apart again.
        updateMissingSizes()
    }

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
                    if(fileLink.path) {
                        def existingFile = new File(fileLink.path)
                        if(existingFile.exists()) {
                            fileLink.size = (int) existingFile.length()
                            fileLinkService.save(fileLink)
                            request.withFormat {
                                form multipartForm {
                                    flash.message = message(code: 'default.created.message', args: [message(code: 'fileLink.label', default: 'FileLink'), fileLink.id])
                                    redirect fileLink
                                }
                                '*' { respond fileLink, [status: CREATED] }
                            }
                            return
                        }
                    }
                    flash.message = 'file cannot be empty'
                    render(view: 'create')
                    return
                }
                // Validate file security before processing
                def filemanagermax = PortalSetting.namedefault('filemanager_max_' + session.curuser?.userID,5242880)
                def validationResult = FileSecurityValidator.validateFile(f,null,filemanagermax)
                if (!validationResult.valid) {
                    flash.message = "File upload failed: ${validationResult.errors.join(', ')}"
                    respond fileLink.errors, view:'create'
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
                    // Validate file security before processing
                    def filemanagermax = PortalSetting.namedefault('filemanager_max_' + session.curuser?.userID,5242880)
                    def validationResult = FileSecurityValidator.validateFile(f,null,filemanagermax)
                    if (!validationResult.valid) {
                        flash.message = "File upload failed: ${validationResult.errors.join(', ')}"
                        respond fileLink.errors, view:'edit'
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
            def hasAccess = hasFileAccess(filelink)

            if(!hasAccess) {
                response.status = 401
                render(text: "Authentication required")
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
                    
                    // Detect MIME type from slug suffix to serve CSS/JS/fonts inline with correct type
                    def slugLower = (filelink.slug ?: '').toLowerCase()
                    def mimeType = "application/octet-stream"
                    def isAttachment = false
                    if (slugLower.endsWith('_css')) {
                        mimeType = "text/css"
                    } else if (slugLower.endsWith('_js')) {
                        mimeType = "application/javascript"
                    } else if (slugLower.endsWith('_woff2')) {
                        mimeType = "font/woff2"
                    } else if (slugLower.endsWith('_woff')) {
                        mimeType = "font/woff"
                    } else if (slugLower.endsWith('_ttf')) {
                        mimeType = "font/ttf"
                    } else if (slugLower.endsWith('_eot')) {
                        mimeType = "application/vnd.ms-fontobject"
                    } else if (slugLower.endsWith('_svg')) {
                        mimeType = "image/svg+xml"
                    } else {
                        def bis = new java.io.BufferedInputStream(thefile.newInputStream())
                        def guessed = URLConnection.guessContentTypeFromStream(bis)
                        bis.close()
                        mimeType = guessed ?: "application/octet-stream"
                        if (mimeType == "application/octet-stream") { isAttachment = true }
                    }
                    response.setContentType(mimeType)
                    if (isAttachment) {
                        response.setHeader("Content-disposition", "attachment;filename=${thefile.getName().replace(' ','_').replaceAll('[^a-zA-Z0-9._-]', '_')}")
                    }

                    if(params.thumbsize && mimeType.startsWith('image/')){
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


    /**
     * Shared access rule for serving a stored file. Extracted from download() so that
     * download and stream cannot drift apart — two copies of a security check is one
     * copy too many.
     *
     * @param filelink - the FileLink being served
     * @return boolean - true when the current session may read this file
     */
    private boolean hasFileAccess(FileLink filelink) {
        def hasAccess = false

        def whitelist_modules = PortalSetting.namedefault('download_module_whitelist',['portal'])
        if(filelink.module in whitelist_modules && !filelink.allowedroles) {
            hasAccess = true
        } else if(filelink.allowedroles) {
            def testroles = filelink.allowedroles.tokenize(',')*.trim()
            if('All' in testroles) {
                hasAccess = true
            } else if(session.userid) {
                def curuser = session.curuser
                if('Authenticated' in testroles) {
                    hasAccess = true
                } else if(curuser && testroles.any { tr -> tr in curuser.modulerole(filelink.module) }) {
                    hasAccess = true
                } else if(curuser && curuser.currentrole()?.role in testroles) {
                    hasAccess = true
                }
            }
        } else if(session.userid) {
            // Check if user is admin or has access to the file's module
            if(session.enablesuperuser) {
                hasAccess = true
            } else if(session.adminmodules && filelink.module && filelink.module in session.adminmodules) {
                hasAccess = true
            }
            // Also check tracker-level access (record owner/manager/pic roles) — runs even if adminmodules check failed
            if(!hasAccess && filelink.tracker_id) {
                def tracker = PortalTracker.get(filelink.tracker_id)
                if(tracker && session.curuser) {
                    def recordDatas = filelink.tracker_data_id ? tracker.firstRow(['id': filelink.tracker_data_id]) : null
                    hasAccess = tracker.user_roles(session.curuser, recordDatas).size() > 0
                }
            }
            // Fallback for trail attachment FileLinks that may lack tracker_id: look up by module
            if(!hasAccess && !filelink.tracker_id && filelink.module && session.curuser) {
                def trackers = PortalTracker.findAllByModule(filelink.module)
                for(def t : trackers) {
                    def recordDatas = filelink.tracker_data_id ? t.firstRow(['id': filelink.tracker_data_id]) : null
                    if(t.user_roles(session.curuser, recordDatas).size() > 0) {
                        hasAccess = true
                        break
                    }
                }
            }
        }

        return hasAccess
    }

    /**
     * Streams a stored file, with HTTP range support, for content that is too large to
     * push out in one response — video, audio, big PDFs.
     *
     * Differs from download() in three ways that matter for large files:
     *  - honours the Range header and answers 206 Partial Content, so players can seek
     *    and browsers can resume rather than refetching from byte zero;
     *  - copies through a fixed buffer with RandomAccessFile, so memory use stays flat
     *    regardless of file size (download() reads getBytes() for thumbnails);
     *  - serves inline with Accept-Ranges advertised, never as an attachment.
     *
     * Access control is the shared hasFileAccess() used by download(), so the two cannot
     * diverge. Reached via <g:stream_file slug="..." module="..."/>.
     */
    def stream = {
        def filelink = null
        if(params.slug){
            // Match download(): the slug may carry a fake extension, e.g. "clip.mp4"
            def slugroot = params.slug.tokenize('.')
            filelink = FileLink.find("from FileLink as fl where fl.slug=:slug",[slug:slugroot[0]],[cache:true])
        }
        if(params.id){
            filelink = FileLink.get(params.id)
        }

        if(!filelink || !filelink.path){
            response.sendError(404, "File not found")
            return
        }
        if(!hasFileAccess(filelink)){
            response.status = 401
            render(text: "Authentication required")
            return
        }

        def thefile = new File(filelink.path)
        if(!thefile.exists() || !thefile.isFile()){
            println "Stream: file " + thefile + " does not exist"
            response.sendError(404, "File not found")
            return
        }

        long filelength = thefile.length()
        long start = 0L
        long end = filelength - 1

        // Range: bytes=START-END | bytes=START- | bytes=-SUFFIXLENGTH
        def rangeHeader = request.getHeader('Range')
        boolean partial = false
        if(rangeHeader && rangeHeader.startsWith('bytes=')){
            def spec = rangeHeader.substring(6).tokenize(',')[0]?.trim()
            try {
                if(spec.startsWith('-')){
                    long suffix = Long.parseLong(spec.substring(1))
                    if(suffix > 0){
                        start = Math.max(0L, filelength - suffix)
                        partial = true
                    }
                }
                else {
                    def parts = spec.tokenize('-')
                    start = Long.parseLong(parts[0])
                    if(parts.size() > 1){
                        end = Long.parseLong(parts[1])
                    }
                    partial = true
                }
                if(end > filelength - 1) end = filelength - 1
            }
            catch(Exception e){
                // Unparseable range: fall back to the whole file rather than failing
                start = 0L; end = filelength - 1; partial = false
            }

            if(partial && (start > end || start >= filelength)){
                // Unsatisfiable: RFC 7233 wants 416 plus the real length.
                // Set the status directly rather than sendError() — UrlMappings only maps
                // 404 and 500 to error views, so sendError(416) gets escalated to a 500.
                response.setHeader('Content-Range', "bytes */" + filelength)
                response.status = 416
                render(text: "Requested range not satisfiable")
                return
            }
        }

        // Content type from the real filename, falling back to the slug. Media types are
        // listed explicitly because guessContentTypeFromName misses several of them.
        def name = (thefile.getName() ?: '').toLowerCase()
        def ext = name.contains('.') ? name.substring(name.lastIndexOf('.') + 1) : ''
        def mimeMap = [
            'mp4':'video/mp4', 'm4v':'video/mp4', 'webm':'video/webm', 'ogv':'video/ogg',
            'mov':'video/quicktime', 'avi':'video/x-msvideo', 'mkv':'video/x-matroska',
            'mp3':'audio/mpeg', 'm4a':'audio/mp4', 'wav':'audio/wav', 'ogg':'audio/ogg',
            'flac':'audio/flac', 'aac':'audio/aac',
            'pdf':'application/pdf', 'zip':'application/zip'
        ]
        def mimeType = mimeMap[ext] ?: URLConnection.guessContentTypeFromName(name) ?: 'application/octet-stream'

        long contentLength = end - start + 1

        response.setContentType(mimeType)
        response.setHeader('Accept-Ranges', 'bytes')
        response.setHeader('Content-Length', String.valueOf(contentLength))
        response.setHeader('Content-Disposition', "inline;filename=\"" + thefile.getName().replace('"','') + "\"")
        if(partial){
            response.status = 206
            response.setHeader('Content-Range', "bytes " + start + "-" + end + "/" + filelength)
        }

        def raf = null
        try {
            raf = new java.io.RandomAccessFile(thefile, 'r')
            raf.seek(start)
            byte[] buf = new byte[64 * 1024]
            long remaining = contentLength
            def out = response.outputStream
            while(remaining > 0){
                int toRead = (int) Math.min((long) buf.length, remaining)
                int read = raf.read(buf, 0, toRead)
                if(read == -1) break
                out.write(buf, 0, read)
                remaining -= read
            }
            out.flush()
        }
        catch(org.apache.catalina.connector.ClientAbortException ce){
            // Normal when a viewer seeks or closes the player mid-stream; not an error
        }
        catch(java.io.IOException ioe){
            // Broken pipe on client disconnect. Headers are already committed by now, so
            // there is nothing useful to send back — just stop.
            println "Stream aborted for " + thefile + ": " + ioe.message
        }
        finally {
            try { if(raf) raf.close() } catch(Exception ignored) { }
        }
    }
}
