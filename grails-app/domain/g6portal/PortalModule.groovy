package g6portal

import groovy.sql.Sql
import groovy.json.*
import groovy.io.FileType
import java.text.SimpleDateFormat

class PortalModule {

    static constraints = {
    }

    String name

    def user_roles(curuser) {
      return roles = UserRole.findAllByUserAndModule(curuser,name)*.role
    }

    def textconvert(source) {
        if(source) {
            println "Before source:" + source
            source = source.replaceAll('g5portal','g6portal')
            source = source.replaceAll(
                /\(new Date\(\)\)\.format\('yyyy-MM-dd HH:mm:ss'\)/, 
                /new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm:ss').format\(new Date\(\)\)/
            )
            source = source.replaceAll(
                /\(new Date\(\)\)\.format\('yyyy-MM-dd HH:mm'\)/, 
                /new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format\(new Date\(\)\)/
            )
            source = source.replaceAll(
                /new Date\(\)\.format\('yyyy-MM-dd HH:mm'\)/, 
                /new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format\(new Date\(\)\)/
            )
            source = source.replaceAll(
                /\(new Date\(\)\)\.format\('yyyy'\)/, 
                /new java.text.SimpleDateFormat('yyyy').format\(new Date\(\)\)/
            )
            source = source.replaceAll(
                /\(new Date\(\)\)\.format\('HH:mm'\)/, 
                /new java.text.SimpleDateFormat('HH:mm').format\(new Date\(\)\)/
            )
            println "After source :" + source
        }
        return source
    }

    def importfiles(migrationfolder, jsonSlurper) {
        def filelinkfile = new File(migrationfolder + '/filelinklist.json')
        if(filelinkfile.exists()){
            def filelinkarray = jsonSlurper.parseText(filelinkfile.text)
            filelinkarray.each { ifilelink->
                try {
                    def exportpath = migrationfolder + '/files/fl_' + ifilelink.id + '_' + ifilelink.name.replace(" ","_")
                    // Detect Windows absolute paths (backslash or drive letter like d:\...)
                    def finalpath = ifilelink.path
                    if(finalpath && (finalpath.contains('\\') || finalpath ==~ /^[A-Za-z]:.*/)){
                        println "Windows path detected, using exportpath instead: " + exportpath
                        finalpath = exportpath
                    }
                    if((new File(exportpath)).exists() ){
                        println "Import file exists at:" + exportpath
                        if(finalpath != exportpath) {
                            def outfile = new File(finalpath)
                            if(!(new File(outfile.getParent()).exists())){
                                new File(outfile.getParent()).mkdirs()
                            }
                            println "Writing out to:" + outfile
                            def srcStream = new File(exportpath).newDataInputStream()
                            def dstStream = new File(finalpath).newDataOutputStream()
                            dstStream << srcStream
                            srcStream.close()
                            dstStream.close()
                            println "Done writing file"
                        }
                        else {
                            println "File already at final path (exportpath), no copy needed"
                        }
                    }
                    else{
                        println "No import file found at:" + exportpath
                    }

                    def curfilelink = FileLink.findByModuleAndSlug(ifilelink.module,ifilelink.slug)
                    if(!curfilelink){
                        curfilelink = new FileLink()
                        println "No existing filelink so creating a new one"
                    }
                    curfilelink.module=ifilelink.module
                    curfilelink.slug=ifilelink.slug
                    curfilelink.name=ifilelink.name
                    curfilelink.path=finalpath
                    curfilelink.allowedroles=ifilelink.allowedroles
                    curfilelink.filegroup=ifilelink.filegroup
                    curfilelink.sortnum=ifilelink.sortnum
                    if(!curfilelink.validate()){
                        curfilelink.errors.allErrors.each {
                            println 't error:' + it
                        }
                    }
                    curfilelink.save(flush:true)
                    println "Filelink saved"
                }
                catch(Exception e){
                    println "Error importing file:" + e
                }
            }
        }
    }

    def importuserroles(migrationfolder,jsonSlurper) {
        def userrolefile = new File(migrationfolder + '/userrolelist.json')
        if(userrolefile.exists()){
            def userrolearray = jsonSlurper.parseText(userrolefile.text)
            userrolearray.each { iuserrole->
                def cuser = User.findByUserID(iuserrole.user)
                if(cuser){
                    def curuserrole = UserRole.findByUserAndModuleAndRole(cuser,iuserrole.module,iuserrole.role)
                    if(!curuserrole){
                      curuserrole = new UserRole()
                    }
                    curuserrole.user = cuser
                    curuserrole.module = iuserrole.module
                    curuserrole.role = iuserrole.role
                    if(!curuserrole.validate()){
                        curuserrole.errors.allErrors.each {
                            println 't error:' + it
                        }
                    }
                    curuserrole.save(flush:true)
                }
            }
        }
    }

    def importsettings(migrationfolder,jsonSlurper) {
        def settingfile = new File(migrationfolder + '/settinglist.json')
        if(settingfile.exists()){
            def settingarray = jsonSlurper.parseText(settingfile.text)
            settingarray.each { isetting->
                def cursetting = PortalSetting.findByModuleAndName(isetting.module,isetting.name)
                if(!cursetting){
                  cursetting = new PortalSetting()
                }
                cursetting.name = isetting.name
                cursetting.module = isetting.module
                cursetting.text = isetting.text
                cursetting.number = isetting.number
                cursetting.type = isetting.type
                cursetting.datum_type = isetting.datum_type
                if(isetting.date_value){
                  cursetting.date_value=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(isetting.date_value)
                }
                if(!cursetting.validate()){
                    cursetting.errors.allErrors.each {
                        println 't error:' + it
                    }
                }
                cursetting.save(flush:true)
            }
        }
    }

    def importpages(migrationfolder,jsonSlurper) {
        println "Importing pages from " + migrationfolder
        def pagefile = new File(migrationfolder + '/pagelist.json')
        if(pagefile.exists()){
            println "Got json file"
            def pagearray = jsonSlurper.parseText(pagefile.text)
            pagearray.each { ipage->
                println "Importing page: " + ipage.module + ":" + ipage.slug
                PortalPage.withTransaction { dbtrans-> 
                    try {
                        def curpage = PortalPage.findByModuleAndSlug(ipage.module,ipage.slug)
                        /* if(curpage.size()>0){
                            curpage.each { dp->
                                dp.delete(flush:true)
                            }
                        } */
                        if(!curpage) {
                            curpage = new PortalPage()
                        }
                        def contentfile = new File(migrationfolder + '/pages/content_' + ipage.slug + '.gsp')
                        if(contentfile.exists()){
                            curpage.content = textconvert(contentfile.text)
                        }
                        def ppfile = new File(migrationfolder + '/pages/pp_' + ipage.slug + '.gsp')
                        if(ppfile.exists()){
                            curpage.preprocess = textconvert(ppfile.text)
                        }
                        curpage.title=ipage.title
                        curpage.slug=ipage.slug
                        curpage.allowedroles=ipage.allowedroles
                        curpage.module=ipage.module
                        curpage.requirelogin=ipage.requirelogin
                        curpage.published=ipage.published
                        curpage.runable=ipage.runable
                        curpage.render=ipage.render
                        curpage.side_menu=ipage.side_menu
                        curpage.fullpage=ipage.fullpage
                        curpage.redirectafter=ipage.redirectafter
                        curpage.lastUpdated=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(ipage.lastUpdated)
                        if(!curpage.validate()){
                            curpage.errors.allErrors.each {
                                println 't error:' + it
                            }
                        }
                        curpage.save(flush:true)

                        if(ipage.datasources){
                            ipage.datasources.each { ids->
                                def cds = PortalPageData.findByPageAndName(curpage,ids.name)
                                if(!cds){
                                    cds = new PortalPageData()
                                }
                                cds.page = curpage
                                cds.name = ids.name
                                cds.return_one = ids.return_one
                                cds.query = textconvert(ids.query)
                                if(!cds.validate()){
                                    cds.errors.allErrors.each {
                                        println 't error:' + it
                                    }
                                }
                                cds.save(flush:true)
                            }
                        }
                    }
                    catch(Exception e){
                        println "Error importing page:" + e
                        PortalErrorLog.record(null,null,'module','import page',e.toString(),ipage.slug,ipage.module)
                    }
                }
            }
        }
    }

    def importtrackers(migrationfolder,jsonSlurper) {
        println "Importing trackers"
        def trackerfile = new File(migrationfolder + '/trackerlist.json')

        if(trackerfile.exists()){
            def trackerarray = jsonSlurper.parseText(trackerfile.text)
            trackerarray.each { itracker->
                println "Importing tracker: " + itracker
                PortalTracker.withTransaction { dbtrans-> 
                    def curtracker = PortalTracker.findAllByModuleAndSlug(itracker.module,itracker.slug)
                    // Preserve upload records before deleting tracker; savedparams uses field IDs
                    // which change after import, so remap to field names for safe restore.
                    def savedTrackerDatas = []
                    if(curtracker.size()>0){
                        curtracker.each { ct->
                            def fieldIdToName = [:]
                            ct.fields.each { f -> fieldIdToName[f.id] = f.name }
                            ct.datas.each { ctd->
                                def remappedParams = null
                                if(ctd.savedparams) {
                                    try {
                                        def parsedParams = new JsonSlurper().parseText(ctd.savedparams)
                                        def remapped = [:]
                                        parsedParams.each { pk, pv ->
                                            def m = (pk =~ /^(datasource|custom|update)_(\d+)$/)
                                            if(m) {
                                                def fname = fieldIdToName[m[0][2].toLong()]
                                                if(fname) remapped["${m[0][1]}_fname_${fname}"] = pv
                                            } else {
                                                remapped[pk] = pv
                                            }
                                        }
                                        remappedParams = JsonOutput.toJson(remapped)
                                    } catch(Exception ep) {
                                        remappedParams = ctd.savedparams
                                    }
                                }
                                savedTrackerDatas << [
                                    tracker_module: ct.module, tracker_slug: ct.slug,
                                    old_id: ctd.id, module: ctd.module, path: ctd.path,
                                    date_created: ctd.date_created, data_row: ctd.data_row,
                                    data_end: ctd.data_end, header_start: ctd.header_start,
                                    header_end: ctd.header_end, uploaded: ctd.uploaded,
                                    send_email: ctd.send_email, sent_email_date: ctd.sent_email_date,
                                    messages: ctd.messages, savedparams_remapped: remappedParams,
                                    uploadStatus: ctd.uploadStatus, file_link_id: { try { ctd.file_link?.id } catch(Exception e) { null } }(),
                                    uploader_id: { try { ctd.uploader?.id } catch(Exception e) { null } }(), excel_password: ctd.excel_password
                                ]
                                ctd.isTrackerDeleting = true  // must set on in-memory object; HQL executeUpdate only updates DB, not the first-level cache Hibernate uses for beforeDelete
                            }
                            ct.delete(flush:true)
                        }
                    }
                    curtracker = new PortalTracker()
                    curtracker.name = itracker.name
                    curtracker.slug = itracker.slug
                    curtracker.module = itracker.module
                    curtracker.tracker_type = itracker.tracker_type
                    curtracker.side_menu = itracker.side_menu
                    curtracker.listfields = itracker.listfields
                    curtracker.allowedroles = itracker.allowedroles
                    curtracker.hiddenlistfields = itracker.hiddenlistfields
                    curtracker.excelfields = itracker.excelfields
                    curtracker.filterfields = itracker.filterfields
                    curtracker.searchfields = itracker.searchfields
                    curtracker.postprocess = PortalPage.findByModuleAndSlug(itracker.module,itracker.postprocess)
                    curtracker.sqlfieldnames = itracker.sqlfieldnames
                    curtracker.sqlvalues = itracker.sqlvalues
                    curtracker.datatable = itracker.datatable
                    curtracker.trailtable = itracker.trailtable
                    curtracker.defaultsort = itracker.defaultsort
                    curtracker.rolesort = itracker.rolesort
                    curtracker.allowadd = itracker.allowadd
                    curtracker.downloadexcel = itracker.downloadexcel
                    curtracker.anonymous_list = itracker.anonymous_list
                    curtracker.anonymous_view = itracker.anonymous_view
                    curtracker.require_login = itracker.require_login
                    curtracker.excel_audit = itracker.excel_audit
                    curtracker.defaultlimit = itracker.defaultlimit
                    curtracker.tickactions = itracker.tickactions
                    curtracker.actionbuttons = itracker.actionbuttons
                    curtracker.condition_q = itracker.condition_q
                    curtracker.rowclassval = itracker.rowclassval
                    if(!curtracker.validate()){
                        curtracker.errors.allErrors.each {
                            println 't error:' + it
                        }
                    }
                    if(curtracker.save(flush:true)){
                        // Create data/trail tables before processing fields so that
                        // curfield.updatedb() ALTER TABLE calls succeed without exceptions.
                        // Exceptions from DDL on a non-existent table corrupt the Hibernate
                        // session's transaction state, causing subsequent saves to fail.
                        try {
                            def importDs = grails.util.Holders.applicationContext.getBean('dataSource')
                            curtracker.updatedb(importDs)
                        } catch(Exception tbe) {
                            println "Could not create tables for tracker ${curtracker.slug}: ${tbe}"
                        }
                        itracker.fields.each { ifield->
                            def curfield = PortalTrackerField.findByTrackerAndName(curtracker,ifield.name)
                            def isNewField = (curfield == null)
                            if(!curfield){
                                curfield = new PortalTrackerField()
                            }
                            curfield.tracker=curtracker
                            curfield.name=ifield.name
                            curfield.label=ifield.label
                            curfield.field_type=ifield.field_type
                            curfield.field_options=textconvert(ifield.field_options)
                            curfield.field_format=textconvert(ifield.field_format)
                            curfield.field_default=textconvert(ifield.field_default)
                            curfield.hyperscript=textconvert(ifield.hyperscript)
                            curfield.field_display=ifield.field_display
                            curfield.field_query=ifield.field_query
                            curfield.classes=ifield.classes
                            curfield.hide_heading=ifield.hide_heading
                            curfield.params_override=ifield.params_override
                            curfield.url_value=ifield.url_value
                            curfield.is_encrypted=ifield.is_encrypted
                            curfield.role_query=ifield.role_query
                            curfield.encode_exception=ifield.encode_exception
                            curfield.suppress_follow_link=ifield.suppress_follow_link
                            curfield.field_description=ifield.field_description
                            curfield.field_tooltip=ifield.field_tooltip
                            curfield.field_placeholder=ifield.field_placeholder
                            if(!curfield.validate()){
                                curfield.errors.allErrors.each {
                                    println 't error:' + it
                                }
                            }
                            curfield.save(flush:true)
                            // For new fields, ensure the DB column exists immediately
                            if(isNewField) {
                                try {
                                    def importDs = grails.util.Holders.applicationContext.getBean('dataSource')
                                    curfield.updatedb(importDs)
                                } catch(Exception fdbe) {
                                    println "Could not add column for new field ${curfield.name}: ${fdbe}"
                                }
                            }
                            ifield.error_checks.each { ec->
                                def error_check = new PortalTrackerError()
                                error_check.field = curfield
                                error_check.description = ec.description
                                error_check.error_type = ec.error_type
                                error_check.format = ec.format
                                error_check.allow_submission = ec.allow_submission
                                error_check.error_msg = textconvert(ec.error_msg)
                                error_check.error_function = textconvert(ec.error_function)
                                error_check.save(flush:true)
                            }
                        }

                        itracker.statuses.each { istatus->
                            def curstatus = PortalTrackerStatus.findByTrackerAndName(curtracker,istatus.name)
                            if(!curstatus){
                                curstatus = new PortalTrackerStatus()
                            }
                            curstatus.tracker = curtracker
                            curstatus.name=istatus.name
                            curstatus.displayfields=istatus.displayfields
                            curstatus.updateallowedroles=istatus.updateallowedroles
                            curstatus.editroles=istatus.editroles
                            curstatus.editfields=istatus.editfields
                            curstatus.flow=istatus.flow
                            curstatus.updateable=istatus.updateable
                            curstatus.attachable=istatus.attachable
                            curstatus.suppressupdatebutton=istatus.suppressupdatebutton
                            curstatus.actiontransitions=istatus.actiontransitions
                            curstatus.compositeStatuses=istatus.compositeStatuses
                            if(!curstatus.validate()){
                                curstatus.errors.allErrors.each {
                                    println 't error:' + it
                                }
                            }
                            curstatus.save(flush:true)

                            if(istatus.emailonupdate){
                                def emailonupdate = new PortalTrackerEmail()
                                emailonupdate.status = curstatus
                                emailonupdate.name = istatus.emailonupdate.name
                                emailonupdate.tracker = curtracker
                                emailonupdate.emailto = istatus.emailonupdate.emailto
                                emailonupdate.emailcc = istatus.emailonupdate.emailcc
                                def curbody = PortalPage.findByModuleAndSlug(curtracker.module,istatus.emailonupdate.body)
                                if(curbody){
                                    emailonupdate.body = curbody
                                }
                                if(!emailonupdate.validate()){
                                    emailonupdate.errors.allErrors.each {
                                        println 't error:' + it
                                    }
                                }
                                emailonupdate.save(flush:true)
                                curstatus.emailonupdate = emailonupdate
                                curstatus.save(flush:true)
                            }

                            if(istatus.runonupdate){
                                def runonupdatepage = PortalPage.findByModuleAndSlug(curtracker.module,istatus.runonupdate)
                                if(runonupdatepage){
                                    curstatus.runonupdate = runonupdatepage
                                    curstatus.save(flush:true)
                                }
                            }
                        }

                        itracker.roles.each { irole-> 
                            def currole = PortalTrackerRole.findByTrackerAndNameAndRole_type(curtracker,irole.name,irole.role_type)
                            if(!currole){
                                currole = new PortalTrackerRole()
                            }
                            currole.tracker = curtracker
                            currole.name=irole.name
                            currole.role_type=irole.role_type
                            currole.role_rule=textconvert(irole.role_rule)
                            currole.role_desc=irole.role_desc
                            // currole.lastUpdated=Date.parse("yyyy-MM-dd'T'HH:mm:ss",irole.lastUpdated)
                            currole.lastUpdated = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(irole.lastUpdated)
                            if(!currole.validate()){
                                currole.errors.allErrors.each {
                                    println 't error:' + it
                                }
                            }
                            currole.save(flush:true)
                        }

                        itracker.transitions.each { itransition-> 
                            def curprev = []
                            def curnext = null
                            def currole = []
                            if(itransition.prev_status){
                                itransition.prev_status.each { prev->
                                    curprev << PortalTrackerStatus.findByTrackerAndName(curtracker,prev)
                                }
                            }
                            if(itransition.next_status){
                                curnext = PortalTrackerStatus.findByTrackerAndName(curtracker,itransition.next_status)
                            }
                            if(itransition.roles){
                                itransition.roles.each { role->
                                    def allroles = PortalTrackerRole.findAllByTrackerAndName(curtracker,role)
                                    allroles.each { carole->
                                        if(curprev.size()==0) {  // no prev status specified so should only accept user role roles only since there is nothing to compare with if no prev status is checked
                                            if(carole.role_type=='User Role') {
                                                currole << carole
                                            }
                                        }
                                        else {
                                            if(allroles.size()>1) {  // since prev status specified and there is more than 1 role with the same name, then only set the Data Compare type since we should probably compare the data if a comparison and data exists
                                                if(carole.role_type=='Data Compare') {
                                                    currole << carole
                                                }
                                            }
                                            else {  // since only 1 role found, accept it as the required role no matter the type
                                              currole << carole
                                            }
                                        }
                                    }
                                }
                            }

                            def curtransition = new PortalTrackerTransition()
                            curtransition.tracker = curtracker
                            curtransition.name=itransition.name
                            curtransition.display_name=itransition.display_name
                            curtransition.editfields=itransition.editfields
                            curtransition.displayfields=itransition.displayfields
                            curtransition.requiredfields=itransition.requiredfields
                            curtransition.richtextfields=itransition.richtextfields
                            curtransition.enabledcondition=textconvert(itransition.enabledcondition)
                            curtransition.updatetrails=itransition.updatetrails
                            curtransition.submitbuttontext=itransition.submitbuttontext
                            curtransition.cancelbuttontext=itransition.cancelbuttontext
                            curtransition.gotoprevstatuslist=itransition.gotoprevstatuslist
                            curtransition.same_status=itransition.same_status
                            curtransition.cancelbutton=itransition.cancelbutton
                            curtransition.redirect_after=itransition.redirect_after
                            curtransition.button_class=itransition.button_class
                            curtransition.immediate_submission=itransition.immediate_submission
                            if(curprev){
                                curtransition.prev_status = curprev
                            }
                            if(curnext){
                                curtransition.next_status = curnext
                            }
                            if(currole){
                                curtransition.roles = currole
                            }
                            if(!curtransition.validate()){
                                curtransition.errors.allErrors.each {
                                    println 't error:' + it
                                }
                            }
                            curtransition.save(flush:true)

                            if(itransition.emails){
                                def emails = []
                                itransition.emails.each { iemail->
                                    def cemail = PortalTrackerEmail.findByTransitionAndName(curtransition,iemail.name)
                                    if(!cemail){
                                        cemail = new PortalTrackerEmail()
                                    }
                                    cemail.transition = curtransition
                                    cemail.name = iemail.name
                                    cemail.tracker = curtracker
                                    cemail.emailto = textconvert(iemail.emailto)
                                    cemail.emailcc = textconvert(iemail.emailcc)
                                    def curbody = PortalPage.findByModuleAndSlug(curtracker.module,iemail.body)
                                    if(curbody){
                                        cemail.body = curbody
                                    }
                                    if(!cemail.validate()){
                                        cemail.errors.allErrors.each {
                                            println 't error:' + it
                                        }
                                    }
                                    cemail.save(flush:true)
                                    emails << cemail
                                }
                                curtransition.emails = emails
                                curtransition.save(flush:true)
                            }

                            if(itransition.postprocess){
                                def curpostprocess = PortalPage.findByModuleAndSlug(curtracker.module,itransition.postprocess)
                                if(curpostprocess){
                                    curtransition.postprocess = curpostprocess
                                }
                            }
                            curtransition.save(flush:true)
                        }

                        itracker.flows.each { iflow-> 
                            def curflow = PortalTrackerFlow.findByTrackerAndName(curtracker,iflow.name)
                            if(!curflow){
                                curflow = new PortalTrackerFlow()
                            }
                            curflow.tracker = curtracker
                            curflow.name=iflow.name
                            curflow.fields=iflow.fields
                            curflow.transitions=iflow.transitions
                            curflow.save(flush:true)
                        }
                        itracker.indexes.each { iindex->
                            def curindex = PortalTrackerIndex.findByTrackerAndName(curtracker,iindex.name)
                            if(!curindex) {
                                curindex = new PortalTrackerIndex()
                            }
                            curindex.tracker = curtracker
                            curindex.name = iindex.name
                            curindex.fields = iindex.fields
                            curindex.save(flush:true)
                        }

                        if(itracker.initial_status){
                            def curstatus = PortalTrackerStatus.findByTrackerAndName(curtracker,itracker.initial_status)
                            if(curstatus){
                                curtracker.initial_status = curstatus
                            }
                        }
                        if(itracker.defaultfield){
                            def curfield = PortalTrackerField.findByTrackerAndName(curtracker,itracker.defaultfield)
                            if(curfield){
                                curtracker.defaultfield = curfield
                            }
                        }
                        curtracker.save(flush:true)

                        // Restore upload records, remapping savedparams back to new field IDs
                        // and fixing dataupdate_id references in the data table.
                        def fieldNameToId = [:]
                        curtracker.fields.each { f -> fieldNameToId[f.name] = f.id }
                        PortalTrackerData.withSession { cs ->
                            def sql = new Sql(cs.connection())
                            savedTrackerDatas.findAll { it.tracker_module == itracker.module && it.tracker_slug == itracker.slug }.each { savedData ->
                                def newData = new PortalTrackerData()
                                newData.tracker = curtracker
                                newData.module = savedData.module
                                newData.path = savedData.path
                                newData.date_created = savedData.date_created
                                newData.data_row = savedData.data_row
                                newData.data_end = savedData.data_end
                                newData.header_start = savedData.header_start
                                newData.header_end = savedData.header_end
                                newData.uploaded = savedData.uploaded
                                newData.send_email = savedData.send_email
                                newData.sent_email_date = savedData.sent_email_date
                                newData.messages = savedData.messages
                                newData.uploadStatus = savedData.uploadStatus
                                newData.excel_password = savedData.excel_password
                                if(savedData.file_link_id) newData.file_link = FileLink.get(savedData.file_link_id)
                                if(savedData.uploader_id) newData.uploader = User.get(savedData.uploader_id)
                                if(savedData.savedparams_remapped) {
                                    try {
                                        def parsedParams = new JsonSlurper().parseText(savedData.savedparams_remapped)
                                        def remapped = [:]
                                        parsedParams.each { pk, pv ->
                                            def m = (pk =~ /^(datasource|custom|update)_fname_(.+)$/)
                                            if(m) {
                                                def newFid = fieldNameToId[m[0][2]]
                                                if(newFid) remapped["${m[0][1]}_${newFid}"] = pv
                                            } else {
                                                remapped[pk] = pv
                                            }
                                        }
                                        newData.savedparams = JsonOutput.toJson(remapped)
                                    } catch(Exception ep) {
                                        newData.savedparams = savedData.savedparams_remapped
                                    }
                                }
                                newData.save(flush:true)
                                try {
                                    def tableName = curtracker.data_table()
                                    sql.execute("update ${tableName} set dataupdate_id = ${newData.id} where dataupdate_id = ${savedData.old_id}" as String)
                                } catch(Exception ep) {
                                    println "Error updating dataupdate_id from ${savedData.old_id} to ${newData.id}: ${ep}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    def ensureColumnSizes() {
        def columnsToExpand = [
            [table: 'portal_tracker_field',     column: 'label'],
            [table: 'portal_tracker_field',     column: 'field_description'],
            [table: 'portal_tracker_field',     column: 'field_tooltip'],
            [table: 'portal_tracker_field',     column: 'field_placeholder'],
            [table: 'portal_tracker_field',     column: 'field_type'],
            [table: 'portal_tracker_transition', column: 'enabledcondition'],
        ]
        println "ensureColumnSizes: checking ${columnsToExpand.size()} column(s)"
        PortalModule.withSession { sqlsession ->
            def conn = sqlsession.connection()
            def sql = new Sql(conn)
            def isPostgres = conn.metaData.databaseProductName.toLowerCase().contains('postgresql')
            try {
                columnsToExpand.each { check ->
                    def info = sql.firstRow("""
                        SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = :table AND COLUMN_NAME = :column
                    """, [table: check.table, column: check.column])
                    def alreadyUnbounded = isPostgres
                        ? info?.DATA_TYPE == 'text'
                        : info?.CHARACTER_MAXIMUM_LENGTH == -1
                    if (!info) {
                        if (isPostgres) {
                            sql.execute("ALTER TABLE ${check.table} ADD COLUMN ${check.column} TEXT" as String)
                        } else {
                            sql.execute("ALTER TABLE ${check.table} ADD ${check.column} NVARCHAR(MAX) NULL" as String)
                        }
                        println "ensureColumnSizes: [CREATED] ${check.table}.${check.column}"
                    } else if (alreadyUnbounded) {
                        println "ensureColumnSizes: [OK]      ${check.table}.${check.column} already unbounded"
                    } else {
                        if (isPostgres) {
                            sql.execute("ALTER TABLE ${check.table} ALTER COLUMN ${check.column} TYPE TEXT" as String)
                        } else {
                            sql.execute("ALTER TABLE ${check.table} ALTER COLUMN ${check.column} NVARCHAR(MAX)" as String)
                        }
                        println "ensureColumnSizes: [DONE]    ${check.table}.${check.column} expanded to unbounded"
                    }
                }
            } catch (Exception e) {
                println "ensureColumnSizes error: ${e.message}"
            }
        }
    }

    def importmodule(file_on,staff_on) {
        def curfolder = System.getProperty("user.dir")
        def migrationfolder = PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + this.name
        def jsonSlurper = new JsonSlurper()

        ensureColumnSizes()

        PortalModule.withTransaction { curt ->
            if(file_on) {
              importfiles(migrationfolder,jsonSlurper)
            }
            if(staff_on) {
              importuserroles(migrationfolder,jsonSlurper)
            }
            importsettings(migrationfolder,jsonSlurper)
            importpages(migrationfolder,jsonSlurper)
            importtrackers(migrationfolder,jsonSlurper)
        }
    }

    // Pretty-printed JSON (one key per line, sorted collections) keeps export
    // files line-oriented so diffs between imports stay small and readable
    static String formatExportJson(obj) {
        return JsonOutput.prettyPrint(JsonOutput.toJson(obj))
    }

    def exportfilelinks(migrationfolder) {
        def filelinks = FileLink.findAllByModule(this.name).sort { a,b -> (a.slug?:'') <=> (b.slug?:'') ?: (a.name?:'') <=> (b.name?:'') ?: a.id <=> b.id }
        if(filelinks.size()){
            def filelinkfile = new File(migrationfolder + '/filelinklist.json')
            def filelinkarray = []
            filelinks.each { filelink->
                if(!(new File(migrationfolder + '/files').exists())){
                  new File(migrationfolder + '/files').mkdirs()
                }
                def exportpath = null
                if((new File(filelink.path)).exists() ){
                    exportpath = migrationfolder + '/files/fl_' + filelink.id + '_' + filelink.name.replace(" ","_")
                    def srcStream = new File(filelink.path).newDataInputStream()
                    def dstStream = new File(exportpath).newDataOutputStream()
                    dstStream << srcStream
                    srcStream.close()
                    dstStream.close()
                    filelinkarray << [
                    id:filelink.id,
                    module: filelink.module,
                    slug: filelink.slug,
                    name: filelink.name,
                    path: filelink.path,
                    exportpath: exportpath,
                    allowedroles: filelink.allowedroles,
                    filegroup: filelink.filegroup,
                    sortnum: filelink.sortnum
                    ]
                }
            }
            filelinkfile.write(formatExportJson(filelinkarray))
        }
    }

    def exportsettings(migrationfolder) {
        def settings = PortalSetting.findAllByModule(this.name).sort { it.name }
        if(settings.size()){
            def settingfile = new File(migrationfolder + '/settinglist.json')
            def settingarray = []
            settings.each { setting->
                settingarray << [
                  module: setting.module,
                  name: setting.name,
                  text: setting.text,
                  date_value: setting.date_value,
                  number: setting.number,
                  type: setting.type,
                  datum_type: setting.datum_type
                ]
            }
          settingfile.write(formatExportJson(settingarray))
        }
    }

    def exportuserroles(migrationfolder) {
        def userroles = UserRole.findAllByModule(this.name).sort { a,b -> (a.user.newStaffID?:'') <=> (b.user.newStaffID?:'') ?: (a.role?:'') <=> (b.role?:'') }
        if(userroles.size()){
            def userrolefile = new File(migrationfolder + '/userrolelist.json')
            def userrolearray = []
            userroles.each { userrole->
                userrolearray << [
                    user: userrole.user.newStaffID, 
                    module: userrole.module,
                    role: userrole.role
                ]
            }
            userrolefile.write(formatExportJson(userrolearray))
        }
    }

    def exportpages(migrationfolder) {
        def pages = PortalPage.findAllByModule(this.name).sort { it.slug }
        if(pages.size()){
            def pagefile = new File(migrationfolder + '/pagelist.json')
            def pagearray = []
            pages.each { page->
                println "Exporting page:" + page
                if(!(new File(migrationfolder + '/pages').exists())){
                    new File(migrationfolder + '/pages').mkdirs()
                }
                def contentfile = new File(migrationfolder + '/pages/content_' + page.slug + '.gsp')
                contentfile.write(page.content?:'')
                if(page.preprocess) {
                    def ppfile = new File(migrationfolder + '/pages/pp_' + page.slug + '.gsp')
                    ppfile.write(page.preprocess)
                }
                def datasources = null
                if(page.datasources){
                    datasources = []
                    page.datasources.sort { it.name }.each { ds->
                        datasources << [
                            name: ds.name,
                            return_one: ds.return_one,
                            query: ds.query
                        ]
                    }
                }
                pagearray << [
                    title: page.title, 
                    slug: page.slug,
                    allowedroles: page.allowedroles,
                    module: page.module,
                    side_menu: page.side_menu,
                    requirelogin: page.requirelogin,
                    published: page.published,
                    runable: page.runable,
                    render: page.render,
                    fullpage: page.fullpage,
                    redirectafter: page.redirectafter,
                    lastUpdated: page.lastUpdated,
                    datasources: datasources
                ]
            }
            pagefile.write(formatExportJson(pagearray))
        }
    }

    def exporttrackers(migrationfolder) {
        def trackers = PortalTracker.findAllByModule(this.name).sort { it.slug }
        if(trackers.size()){
            def trackerfile = new File(migrationfolder + '/trackerlist.json')
            def trackerarray = []
            trackers.each { tracker->
                def fieldsarray = []
                tracker.fields.sort{ it.name }.each { field->
                    def errorarray = []
                    field.error_checks.sort { a,b -> (a.error_type?:'') <=> (b.error_type?:'') ?: (a.description?:'') <=> (b.description?:'') }.each { ec->
                        errorarray << [
                            error_type: ec.error_type,
                            description: ec.description,
                            format: ec.format,
                            error_msg: ec.error_msg,
                            allow_submission: ec.allow_submission,
                            error_function: ec.error_function
                        ]
                    }
                    fieldsarray << [
                        name: field.name,
                        label: field.label,
                        field_type: field.field_type,
                        field_options: field.field_options,
                        field_format: field.field_format,
                        field_default: field.field_default,
                        hyperscript: field.hyperscript,
                        field_display: field.field_display,
                        field_query: field.field_query,
                        classes: field.classes,
                        hide_heading: field.hide_heading,
                        params_override: field.params_override,
                        url_value: field.url_value,
                        is_encrypted: field.is_encrypted,
                        role_query: field.role_query,
                        encode_exception: field.encode_exception,
                        suppress_follow_link: field.suppress_follow_link,
                        field_description: field.field_description,
                        field_tooltip: field.field_tooltip,
                        field_placeholder: field.field_placeholder,
                        error_checks: errorarray
                    ]
                }
                def statusesarray = []
                tracker.statuses.sort { it.name }.each { status->
                    def emailonupdate = null
                    if(status.emailonupdate){
                        emailonupdate = [
                            name: status.emailonupdate.name,
                            emailto: status.emailonupdate.emailto,
                            emailcc: status.emailonupdate.emailcc,
                            body: status.emailonupdate.body.slug
                        ]
                    }
                    statusesarray << [
                        name: status.name,
                        displayfields: status.displayfields,
                        updateallowedroles: status.updateallowedroles,
                        editroles: status.editroles,
                        editfields: status.editfields,
                        flow: status.flow,
                        updateable: status.updateable,
                        attachable: status.attachable,
                        suppressupdatebutton: status.suppressupdatebutton,
                        actiontransitions: status.actiontransitions,
                        compositeStatuses: status.compositeStatuses,
                        emailonupdate: emailonupdate,
                        runonupdate: status.runonupdate?.slug
                    ]
                }
                def rolesarray = []
                tracker.roles.sort { a,b -> (a.name?:'') <=> (b.name?:'') ?: (a.role_type?:'') <=> (b.role_type?:'') }.each { role->
                    rolesarray << [
                        name: role.name,
                        role_type: role.role_type,
                        role_rule: role.role_rule,
                        role_desc: role.role_desc,
                        lastUpdated: role.lastUpdated
                    ]
                }
                def transitionsarray = []
                tracker.transitions.sort { a,b -> (a.name?:'') <=> (b.name?:'') ?: (a.next_status?.name?:'') <=> (b.next_status?.name?:'') }.each { transition->
                    def emails = []
                    if(transition.emails){
                        transition.emails.sort { it.name }.each { cemail->
                            emails << [
                                name: cemail.name,
                                emailto: cemail.emailto,
                                emailcc: cemail.emailcc,
                                body: cemail.body.slug
                            ]
                        }
                    }
                    def roles = transition.roles*.name.sort()
                    def prev_status = transition.prev_status*.name.sort()
                    transitionsarray << [
                        name: transition.name,
                        display_name: transition.display_name,
                        editfields: transition.editfields,
                        displayfields: transition.displayfields,
                        requiredfields: transition.requiredfields,
                        richtextfields: transition.richtextfields,
                        enabledcondition: transition.enabledcondition,
                        updatetrails: transition.updatetrails,
                        submitbuttontext: transition.submitbuttontext,
                        cancelbuttontext: transition.cancelbuttontext,
                        redirect_after: transition.redirect_after,
                        button_class: transition.button_class,
                        immediate_submission: transition.immediate_submission,
                        gotoprevstatuslist: transition.gotoprevstatuslist,
                        same_status: transition.same_status,
                        cancelbutton: transition.cancelbutton,
                        postprocess: transition.postprocess?.slug,
                        prev_status: prev_status,
                        next_status: transition.next_status?.name,
                        roles: roles,
                        emails: emails
                    ]
                }
                def flowsarray = []
                tracker.flows.sort { it.name }.each { flow->
                    flowsarray << [
                        name: flow.name,
                        fields: flow.fields,
                        transitions: flow.transitions,
                    ]
                }
                def indexarray = []
                tracker.indexes.sort { it.name }.each { ind->
                    indexarray << [
                        name: ind.name,
                        fields: ind.fields
                    ]
                }
                trackerarray << [
                    name: tracker.name, 
                    slug: tracker.slug,
                    tracker_type: tracker.tracker_type,
                    module: tracker.module,
                    side_menu: tracker.side_menu,
                    listfields: tracker.listfields,
                    allowedroles: tracker.allowedroles,
                    hiddenlistfields: tracker.hiddenlistfields,
                    excelfields: tracker.excelfields,
                    filterfields: tracker.filterfields,
                    searchfields: tracker.searchfields,
                    postprocess: tracker.postprocess?.slug,
                    initial_status: tracker.initial_status?.name,
                    defaultfield: tracker.defaultfield?.name,
                    sqlfieldnames: tracker.sqlfieldnames,
                    sqlvalues: tracker.sqlvalues,
                    datatable: tracker.datatable,
                    trailtable: tracker.trailtable,
                    defaultsort: tracker.defaultsort,
                    rolesort: tracker.rolesort,
                    allowadd: tracker.allowadd,
                    downloadexcel: tracker.downloadexcel,
                    anonymous_list: tracker.anonymous_list,
                    anonymous_view: tracker.anonymous_view,
                    require_login: tracker.require_login,
                    excel_audit: tracker.excel_audit,
                    defaultlimit: tracker.defaultlimit,
                    tickactions: tracker.tickactions,
                    actionbuttons: tracker.actionbuttons,
                    condition_q: tracker.condition_q,
                    rowclassval: tracker.rowclassval,
                    fields: fieldsarray,
                    statuses: statusesarray,
                    roles: rolesarray,
                    transitions: transitionsarray,
                    flows: flowsarray,
                    indexes: indexarray
                ]
            }
            trackerfile.write(formatExportJson(trackerarray))
        }
    }

    def exportmodule(file_on,staff_on,targetfolder = null) {
        def curfolder = System.getProperty("user.dir")
        def migrationfolder = targetfolder ?: (PortalSetting.namedefault('migrationfolder',curfolder + '/uploads/modulemigration') + '/' + this.name)
        if(!(new File(migrationfolder).exists())){
            new File(migrationfolder).mkdirs()
        }

        PortalModule.withTransaction { ctran ->
            if(file_on) {
              exportfilelinks(migrationfolder)
            }
            if(staff_on) {
              exportuserroles(migrationfolder)
            }
            exportsettings(migrationfolder)
            exportpages(migrationfolder)
            exporttrackers(migrationfolder)
        }
    }
}
