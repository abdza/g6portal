package g6portal

import groovy.sql.Sql
import static grails.util.Holders.config

class TrackerTagLib {

    def sessionFactory
    PortalService portalService

    def trackerErrorHandler = { attrs->
        if(attrs.tracker){
            out << "\$('form').on('submit',function() {"
            attrs.tracker.fields.each { field->
                if(attrs.transition && attrs.transition.requiredfields){
                    if(field.name?.trim() in attrs.transition.requiredfields.tokenize(',')*.trim()){
                        out << "if(!\$('#" + field.name?.trim() + "').val()){ alert('Value for " + field.label + " is required'); \$('#" + field.name?.trim() + "').focus(); return false; }"
                    }
                }
                if(field.field_type in ['Integer','Number']){
                    out << "if(\$('#" + field.name?.trim() + "').val() && \$('#" + field.name?.trim() + "').val()!='default' && !\$.isNumeric(\$('#" + field.name?.trim() + "').val())){ alert('Please use only numbers for " + field.label + "'); \$('#" + field.name?.trim() + "').focus(); return false; }"
                }
            }
            out << "});"
        }
    }

    def trackerField = { attrs->
        if(attrs.field){
            if(attrs.field.field_type=='FieldGroup'){
                out << "<fieldset>"
                out << "<legend>${attrs.field.label}</legend>"
                def fields = [] 
                def ftags = attrs.field.field_options?.tokenize(',')*.trim()
                ftags.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',attrs.transition.tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }
                fields.each { fgf->
                    out << trackerField(field:fgf,datas:attrs.datas,transition:attrs.transition,zindex:attrs.zindex)
                }
                out << "</fieldset>"
            }
            else {
                def datasource = sessionFactory.currentSession.connection()
                def sql = new Sql(datasource)
                def defaultval = null
                def curuser = null
                if(session && session.userid){
                    // curuser = User.get(session.userid)
                    curuser = session.curuser
                }
                if(attrs.field.field_default){
                    try{
                        Binding binding = new Binding()
                        binding.setVariable("datas",attrs.datas)
                        binding.setVariable("session",session)
                        binding.setVariable("curuser",curuser)
                        binding.setVariable("sql",sql)
                        def shell = new GroovyShell(this.class.classLoader,binding)
                        defaultval = shell.evaluate(attrs.field.field_default)
                    }
                    catch(Exception e){
                        println("Taglib Error with default value of " + attrs.field + " :" + e)
                        // PortalErrorLog.record(params,curuser,'tracker','updaterecord',e.toString(),attrs.field.tracker.slug,attrs.field.tracker.module)
                        defaultval = -1
                    }
                }
                def value = null
                if(attrs.datas && attrs.datas[attrs.field.name?.trim()]){
                    value = attrs.datas[attrs.field.name?.trim()]
                }
                else if(attrs.field.url_value && params[attrs.field.name?.trim()]) {
                    value = params[attrs.field.name?.trim()]
                }
                if(defaultval!=null) {
                    out << hiddenField(name:attrs.field.name?.trim(),value:"default")
                }
                else if(attrs.field.params_override && params[attrs.field.name?.trim()]){
                    out << hiddenField(name:attrs.field.name?.trim(),value:params[attrs.field.name?.trim()])
                }
                else if(attrs.field.field_type=='Hidden'){
                    out << hiddenField(name:attrs.field.name?.trim(),value:value)
                }
                else{
                    def failed = false
                    if(attrs.field.field_type == 'User'){
                      def users = attrs.field.userlist(session,params)
                      if(!users) {
                          failed = true
                      }
                    }
                    else if(attrs.field.field_type == 'Branch'){
                      def branches = attrs.field.objectlist(session,params)
                      if(!branches) {
                          failed = true
                      }
                    }
                    else if(attrs.field.field_type == 'TreeNode'){
                      def nodes = attrs.field.nodeslist(session,params)
                      if(!nodes) {
                          failed = true
                      }
                    }
                    if(!failed) {
                        def hyperscript = ""
                        def zindex = ''
                        if(attrs.zindex) {
                            zindex = " style='z-index:${attrs.zindex}' "
                        }
                        def fieldclass = ''
                        if(attrs.field.classes) {
                            fieldclass = attrs.field.classes
                        }
                        if(attrs.field.field_type in ['MultiSelect']) {
                            fieldclass += ' multidiv'
                        }
                        out << "<div class='group fieldcontain ${fieldclass}' id='${attrs.field.name?.trim()}_div' ${zindex} >"
                        if(!(attrs.field.field_type in ['BelongsTo'] && params[attrs.field.name?.trim()]) && !(attrs.field.hide_heading)){
                            out << "<label for='${attrs.field.name?.trim()}'>" + attrs.field.label + "</label>"
                        }
                        if(attrs.field.error_checks.size()) {
                            def dtrigger = "change from:#${attrs.field.name?.trim()}, keyup from:#${attrs.field.name?.trim()} changed delay:500ms"
                            def dtarget = "this"
                            if(attrs.field.field_type in ['Date','DateTime','Drop Down','File']){
                                dtrigger = "change from:#${attrs.field.name?.trim()}"
                            }
                            hyperscript = "hx-trigger='${dtrigger}' hx-post='" + createLink(controller:'portalTrackerField',action:'onchange',params:['hx_field_id':attrs.field.id]) + "' hx-swap='outerHTML' hx-target='${dtarget}' " 
                        }
                        def field_hyperscript = ""
                        if(attrs.field.hyperscript) {
                            Binding binding = new Binding()
                            binding.setVariable("field",attrs.field)
                            binding.setVariable("datas",attrs.datas)
                            binding.setVariable("params",params)
                            binding.setVariable("curuser",curuser)
                            def shell = new GroovyShell(this.class.classLoader,binding)
                            field_hyperscript = shell.evaluate(attrs.field.hyperscript)
                        }
                        if(attrs.field.field_type in ['Text','Integer','Number','Date','DateTime','Branch','User','TreeNode']){
                            if(attrs.field.field_type=='Date'){
                                if(value && value.toString()!='1900-01-01'){
                                    if(value.toString()[5..-1] in ['12-30','12-31','12-29','12-28','12-27']){
                                        // needed to mitigate a strange bug in format that will add an additional year to the date if the date is on the final week of the year
                                        value = value.toString()[-2..-1] + "/" + value.toString()[5..6] + "/" + value.toString()[0..3]
                                    }
                                    else{
                                        value = value.toString()
                                    }
                                }
                                else{
                                    value = ''
                                }
                            }
                            if(!(attrs.field.field_type in ['User','Branch','Date','DateTime','TreeNode'])){
                                // out << textField(name:attrs.field.name?.trim(),value:value)
                                def fieldname = attrs.field.name?.trim()
                                if(fieldname=='slug') {
                                    fieldname = 'indata_slug'
                                }
                                def fieldtype = 'text'
                                def step_val = ''
                                if(attrs.field.field_type in ['Number','Integer']) {
                                    fieldtype = 'number'
                                    if(attrs.field.field_type == 'Number') {
                                        step_val = " step='any' "
                                        if(attrs.field.field_format) {
                                            def pos_step = attrs.field.field_format.tokenize('.')
                                            if(pos_step.size()>1) {
                                                println "pos_step: " + pos_step[1]
                                                if(pos_step[1].size()>1) {
                                                    pos_step[1] = pos_step[1][0..-2] + '1'
                                                }
                                                else {
                                                    pos_step[1] = '1'
                                                }
                                                println "pos_step 2: " + pos_step[1]
                                                step_val = " step='0.${pos_step[1]}' "
                                            }
                                        }
                                    }
                                }
                                if(value) {
                                  out << "<input type='${fieldtype}' id='${fieldname}' name='${fieldname}' ${step_val} value='${value}' ${field_hyperscript}/>"
                                }
                                else {
                                  out << "<input type='${fieldtype}' id='${fieldname}' name='${fieldname}' ${step_val} ${field_hyperscript}/>"
                                }
                            }
                            if(attrs.field.field_type=='Date'){
                                out << "<input type='date' id='${attrs.field.name?.trim()}' name='${attrs.field.name?.trim()}' value='${value}' ${field_hyperscript}/>"
                            }
                            if(attrs.field.field_type=='DateTime'){
                                out << "<input type='datetime-local' id='${attrs.field.name?.trim()}' name='${attrs.field.name?.trim()}' value='${value}' ${field_hyperscript}/>"
                            }
                            if(attrs.field.field_type=='TreeNode'){
                                // need to make treeenode selection
                                def nodes = attrs.field.nodeslist(session,params)
                                if(nodes){
                                    if(nodes.size()>5) {
                                        out << "<select name='${attrs.field.name?.trim()}' id='${attrs.field.name?.trim()}' style='width: 40%;'></select>"
                                        out << asset.script() { user_selector(controller:"PortalTracker",action:"nodeslist",id:attrs.field.id,property:attrs.field.name?.trim(),value:value,parent:'#' + attrs.field.name?.trim() + '_div') }
                                        out << asset.script() { 
    """\$('#${attrs.field.name}').on('select2:select', function(e) { htmx.trigger(this,'change'); });"""
    }
                                    }
                                    else{
                                        out << "<select name='${attrs.field.name?.trim()}' style='width: 40%;'>"
                                        nodes.each { optnode->
                                            if(optnode.id==value){
                                                out << "<option selected value='${optnode.id}'>${optnode.name}</option>"
                                            }
                                            else{
                                                out << "<option value='${optnode.id}'>${optnode.name}</option>"
                                            }
                                        }
                                        out << "</select>"
                                    }
                                }
                            }
                            if(attrs.field.field_type=='Branch'){
                                def branches = attrs.field.objectlist(session,params)
                                if(branches){
                                    if(branches.size()>5) {
                                        out << "<select name='${attrs.field.name?.trim()}' id='${attrs.field.name?.trim()}' style='width: 40%;'></select>"
                                        out << asset.script() { user_selector(controller:"PortalTracker",action:"objectlist",id:attrs.field.id,property:attrs.field.name?.trim(),value:value,parent:'#' + attrs.field.name?.trim() + '_div') }
                                        out << asset.script() { 
    """\$('#${attrs.field.name}').on('select2:select', function(e) { htmx.trigger(this,'change'); });"""
    }
                                    }
                                    else{
                                        out << "<select name='${attrs.field.name?.trim()}' style='width: 40%;'>"
                                        branches.each { optbranch->
                                            def objname = optbranch[attrs.field.trackerobject()['name']]
                                            if(optbranch.id==value){
                                                out << "<option selected value='${optbranch.id}'>${objname}</option>"
                                            }
                                            else{
                                                out << "<option value='${optbranch.id}'>${objname}</option>"
                                            }
                                        }
                                        out << "</select>"
                                    }
                                }
                                else{
                                    // println "No branch found"
                                }
                            }
                            if(attrs.field.field_type=='User'){
                                def users = attrs.field.userlist(session,params)
                                if(users) {
                                    if(users.size()>5){
                                        def cfuser = User.get(value)
                                        if(cfuser)  {
                                            out << "<select name='${attrs.field.name?.trim()}' id='${attrs.field.name?.trim()}' style='width: 40%;'><option value='${cfuser.id}'>${cfuser.name}</option></select>"
                                        }
                                        else {
                                            out << "<select name='${attrs.field.name?.trim()}' id='${attrs.field.name?.trim()}' style='width: 40%;'></select>"
                                        }
                                        out << asset.script() { user_selector(controller:"PortalTracker",action:"userlist",id:attrs.field.id,property:attrs.field.name?.trim(),value:value,parent:'#' + attrs.field.name?.trim() + '_div') }
                                        out << asset.script() { 
    """\$('#${attrs.field.name}').on('select2:select', function(e) { htmx.trigger(this,'change'); });"""
    }
                                    }
                                    else{
                                        out << "<select name='${attrs.field.name?.trim()}' style='width: 40%;'>"
                                        users.each { optuser->
                                            if(optuser.id==value){
                                                out << "<option selected value='${optuser.id}'>${optuser.name}</option>"
                                            }
                                            else{
                                                out << "<option value='${optuser.id}'>${optuser.name}</option>"
                                            }
                                        }
                                        out << "</select>"
                                    }
                                }
                            }
                        }
                        else if(attrs.field.field_type=='Text Area'){
                            out << textArea(name:attrs.field.name?.trim(),value:value?.replace("''","'"))
                        }
                        else if(attrs.field.field_type=='Checkbox'){
                            if(attrs.field.field_options){
                                def opts = attrs.field.evaloptions(session,attrs.datas,sql)
                                opts.each { opt->
                                    def optlabel = (opt + attrs.field.name).toLowerCase().replace(' ','_')
                                    out << "<label for='${optlabel}'>${opt} :</label>"
                                    out << checkBox(id:optlabel,name:attrs.field.name?.trim(),value:opt,checked:(opt in value?.tokenize(',')))
                                    out << "<br/>"
                                }
                            }
                            else{
                                out << checkBox(name:attrs.field.name?.trim(),value:value)
                            }
                        }
                        else if(attrs.field.field_type=='MultiSelect'){
                            out << "<div class='multiselect'>"
                            if(attrs.field.field_options){
                                def opts = attrs.field.evaloptions(session,attrs.datas,sql)
                                opts.each { opt->
                                    def optlabel = (opt + attrs.field.name).toLowerCase().replace(' ','_')
                                    out << "<span class='multichoice'>"
                                    out << "<label for='${optlabel}'>${opt} :</label>"
                                    out << checkBox(id:optlabel,name:attrs.field.name?.trim(),value:opt,checked:(opt in value?.tokenize(',')))
                                    out << "</span>"
                                    out << "<br/>"
                                }
                            }
                            else{
                                out << checkBox(name:attrs.field.name?.trim(),value:value)
                            }
                            out << "</div>"
                        }
                        else if(attrs.field.field_type=='File'){
                            out << "<input type='file' name='" + attrs.field.name?.trim() + "' id='" + attrs.field.name?.trim() + "' ${field_hyperscript} />"
                        }
                        else if(attrs.field.field_type=='Drop Down'){
                            def ddown = null
                            def opts = [name:attrs.field.name?.trim(),value:value]
                            def toout = ""
                            if(attrs.field.field_options) {
                                ddown = attrs.field.evaloptions(session,attrs.datas,sql)
                                if(ddown.class.simpleName=='ArrayList') {
                                    if(attrs.field.field_format){
                                        opts += attrs.field.evalformat(session,attrs.datas)
                                    }
                                    opts['from'] = ddown
                                    toout = select(opts)
                                    if(value==null && ddown.size()) {
                                        value = ddown[0]
                                    }
                                }
                                else {
                                    println "Error is:" + ddown
                                }
                            }
                            else if(attrs.field.field_format) {
                                def trackersetting = PortalSetting.namedefault('tracker_objects',[])
                                def defaultfield = null
                                if(attrs.field.field_format in trackersetting) {
                                    def tokens = trackersetting[attrs.field.field_format].tokenize('.')
                                    def othertracker = PortalTracker.findByModuleAndSlug(tokens[0],tokens[1])
                                    if(tokens.size()>=3) {
                                        defaultfield = tokens[2]
                                    }
                                    else if(othertracker.defaultfield) {
                                        defaultfield = othertracker.defaultfield.name?.trim()
                                    }
                                    else {
                                        defaultfield = othertracker.fields[0].name
                                    }
                                    ddown = [['id':'',defaultfield:'Please select']] + othertracker.rows()
                                    opts['optionKey'] = 'id'
                                    opts['optionValue'] = defaultfield
                                }
                                opts['from'] = ddown
                                toout = select(opts)
                                toout += asset.script() { user_selector(controller:"PortalTracker",action:"dropdownlist",id:attrs.field.id,property:attrs.field.name?.trim(),value:value,parent:'#' + attrs.field.name?.trim() + '_div') }
                            }
                            if(field_hyperscript) {
                                toout = toout.replace("<select ", "<select " + field_hyperscript)
                            }
                            out << toout
                        }
                        else if(attrs.field.field_type=='BelongsTo'){
                            if(params[attrs.field.name?.trim()]){
                                out << hiddenField(name:'backtoid',value:params[attrs.field.name?.trim()])
                                out << hiddenField(name:attrs.field.name?.trim(),value:params[attrs.field.name?.trim()])
                                out << hiddenField(name:'backto',value:attrs.field.field_options)
                                out << hiddenField(name:'backtransition',value:params.backtransition)
                            }
                            else{
                                if(attrs.field.field_options){
                                    def othertracker = attrs.field.othertracker()
                                    if(othertracker){
                                        def otherfield = attrs.field.field_format
                                        def options = sql.rows("select id," + otherfield + " from " + othertracker.data_table())
                                        if(options.size()>10) {
                                            out << select(name:attrs.field.name?.trim(),value:value,from:options,optionKey:"id",optionValue:otherfield,noSelection:['':'Please select'])
                                            out << asset.script() { user_selector(controller:"PortalTracker",action:"dropdownlist",id:attrs.field.id,property:attrs.field.name?.trim(),value:value,parent:'#' + attrs.field.name?.trim() + '_div') }
                                        }
                                        else {
                                            out << select(name:attrs.field.name?.trim(),value:value,from:options,optionKey:"id",optionValue:otherfield)
                                        }
                                    }
                                }
                            }
                        }
                        else if(attrs.field.field_type=='HasMany'){
                            // def curuser = User.get(session.userid)
                            // def curuser = session.curuser
                            def othertracker = attrs.field.othertracker()
                            if(othertracker){
                                def format_tokens = null
                                if(attrs.field.field_format) {
                                    format_tokens = attrs.field.field_format.tokenize(',')*.trim()
                                }
                                else {
                                    format_tokens = othertracker.listfields.tokenize(',')*.trim()
                                }
                                def tfields = []
                                def ttfields = PortalTrackerField.createCriteria().list() {
                                    'eq'('tracker',othertracker)
                                    'in'('name',format_tokens)
                                }
                                def transitions = []
                                format_tokens.each { ft->
                                    ttfields.each { ttf->
                                        if(ttf.name==ft){
                                            if(!(ttf.field_type in ['HasMany'])) {
                                                tfields << ttf
                                            }
                                        }
                                    }
                                    def tmptrn = PortalTrackerTransition.createCriteria().get() {
                                        'eq'('tracker',othertracker)
                                        'ilike'('name',ft)
                                    }
                                    if(tmptrn) {
                                        transitions << tmptrn
                                    }
                                }
                                def linkback = PortalTrackerField.createCriteria().get() {
                                    'eq'('tracker',othertracker)
                                    'eq'('field_type','BelongsTo')
                                    'like'('field_options',attrs.field.tracker.module + ':' + attrs.field.tracker.slug + '%')
                                }
                                if(tfields && linkback){
                                    out << "<div style='margin: 2px 0px 2px 10px; padding: 0px;'>"
                                    out << "<table id='field_${attrs.field.name?.trim()}'>"
                                    out << "<tr><td>"
                                    out << link(action:"create_data",params:['module':othertracker.module,'slug':othertracker.slug,(linkback.name):params.id,'backtransition':attrs.transition.id],class:'btn btn-primary'){ "Add" }
                                    out << "</td></tr>"
                                    out << "<tr>"
                                    out << "<th>#</th>"
                                    tfields.each { tf->
                                        out << "<th id='col_" + tf.label.replace(' ','_').toLowerCase() + "'>" + tf.label + "</th>"
                                    }
                                    if(transitions.size() || 'delete' in format_tokens){
                                        out << "<th>Action</th>"
                                    }
                                    out << "</tr>"
                                    def orderby = ""
                                    if(othertracker.defaultsort) {
                                        orderby = " order by " + othertracker.defaultsort
                                    }
                                    def query = "select * from " + othertracker.data_table() + " where " + linkback.name + "=" + params.id + orderby
                                    def curcount = 1
                                    sql.eachRow(query) { row->
                                        def rowclass = ""
                                        if('record_status' in row) {
                                            rowclass = row['record_status'].replace(' ','_').toLowerCase()
                                        }
                                        out << "<tr class='${rowclass}'>"
                                        out << "<td>" + (curcount++) + "</td>"
                                        tfields.each { tf->
                                            out << "<td>" + displayField(field:tf,value:row[tf.name]) + "</td>"
                                        }
                                        if(transitions.size()){
                                            out << "<td>"
                                            out << link(action:"display_data",id:row['id'],params:['module':othertracker.module,'slug':othertracker.slug,(linkback.name):params.id],class:'btn btn-secondary hasmanyview'){ "View" }
                                            transitions.each { ctrans->
                                                def trole = ctrans.roles.any { role-> role.id in othertracker.user_roles(curuser,row)*.id }
                                                def backtostr = attrs.field.tracker.module + ':' + attrs.field.tracker.slug
                                                def prevtest = false
                                                if('record_status' in row && row['record_status'] in ctrans.prev_status*.name){
                                                    prevtest = true
                                                }
                                                else if(attrs.field.tracker?.tracker_type=='DataStore' && attrs.field.tracker?.initial_status?.name in ctrans.prev_status*.name) {
                                                    prevtest = true
                                                }
                                                if(prevtest && ctrans.roles.any { role-> role.id in othertracker.user_roles(curuser,row)*.id }){
                                                    if(ctrans.name.toLowerCase()=='delete'){
                                                        out << link(action:"transition",id:row['id'],params:['module':othertracker.module,'slug':othertracker.slug,'transition':'delete',(linkback.name):params.id,'backto':backtostr,'backtoid':params.id,'backtransition':attrs.transition.id],class:'btn btn-danger hasmanydelete',onclick:"return confirm('Confirm delete record?');"){ "Delete" }

                                                    }
                                                    else {
                                                        out << link(action:"transition",id:row['id'],'transition':ctrans.name.replaceAll(" ","_").toLowerCase(),params:['module':othertracker.module,'slug':othertracker.slug,(linkback.name):params.id,'transition':ctrans.name.replaceAll(' ','_').toLowerCase(),'backto':backtostr,'backtransition':attrs.transition.id,('transition_' + ctrans.id):ctrans.next_status.id],class:'btn btn-secondary hasmanyaction'){ ctrans.submitbuttontext?:ctrans.name }
                                                    }
                                                }
                                            }
                                            out << "</td>"
                                        }
                                        out << "</tr>"
                                    }
                                    out << "</table>"
                                    out << "</div>"
                                }
                            }
                        }
                        def errormsg = []
                        def goterror = false
                        (errormsg,goterror) = portalService.field_error_messages(attrs.field,value,attrs.datas,curuser)
                        out << " &nbsp;<span ${hyperscript} "
                        if(goterror) {
                            out << " class='text-danger fatal_error' "
                        }
                        out << ">"
                        if(errormsg) {
                            out << errormsg.join('; ')
                        }
                        out << "</span>"
                        out << "</div>"
                    }
                }
            }
        }
    }

    def addbutton = { attrs->
        if(attrs.field && attrs.transition){
            def field = PortalTrackerField.findByTrackerAndName(attrs.transition.tracker,attrs.field)
            if(field){
                if(field.field_type=='HasMany'){
                    def othertracker = attrs.field.othertracker()
                    if(othertracker){
                        def linkback = PortalTrackerField.createCriteria().get() {
                            'eq'('tracker',othertracker)
                            'eq'('field_type','BelongsTo')
                            'like'('field_options',attrs.transition.tracker.slug)
                        }
                        if(linkback){
                            out << link(action:"addrecord",params:['slug':field.field_options,(linkback.name):params.id,'backtransition':attrs.transition.id],class:'btn btn-primary'){ "Add" }
                        }
                    }
                }
            }
        }
    }

    def displayField = { attrs->
        if(attrs.field){
            def curuser = session.curuser
            out << "<div id='${attrs.field.name?.trim()}_value_div'>"
            def sql = new Sql(sessionFactory.currentSession.connection())
            if(!(attrs.field.field_type in ['HasMany','BelongsTo'])){
                if(attrs.field.field_query){
                    out << sql.firstRow(attrs.field.evalquery(session,attrs.datas))?.value
                }
                else{
                    if(attrs.field.field_type in ['Integer','Number'] && attrs.field.field_format){
                        out << formatNumber(number:attrs.field.fieldval(attrs.value,sql),format:attrs.field.field_format)
                    }
                    else if(attrs.field.field_type=='File'){
                        def fl = attrs.field.fieldval(attrs.value,sql)
                        // added in case file upload is optional
                        if(fl)
                        {
                            def disp = false
                            if(attrs.field.field_display) {
                                def dtags = attrs.field.field_display.tokenize(':')
                                if(dtags[0]=='img') {
                                    disp = true
                                    def toout = "<img src='" + download_file(module:fl.module,slug:fl.slug) + "' "
                                    if(dtags.size()>1) {
                                        def style = " style='width:${dtags[1]}"
                                        if(dtags.size()>2) {
                                            style += ",height:${dtags[2]}"
                                        }
                                        style += "'"
                                        toout += style
                                    }
                                    toout += "/>"
                                    out << toout
                                }
                            }
                            if(!disp) {
                                out << filelink_link(module:fl.module,slug:fl.slug)
                            }
                        }
                    }
                    else{
                        out << attrs.field.fieldval(attrs.value,sql)
                    }
                }
            }
            else if(attrs.field.field_type=='HasMany'){
                def othertracker = attrs.field.othertracker()
                if(othertracker){
                    def format_tokens = null
                    if(attrs.field.field_format) {
                        format_tokens = attrs.field.field_format.tokenize(',')*.trim()
                    }
                    else {
                        format_tokens = othertracker.listfields.tokenize(',')*.trim()
                    }
                    // def curuser = User.get(session.userid)
                    def datasource = sessionFactory.currentSession.connection()
                    def tfields = []
                    def transitions = []
                    def numberedlines = false
                    def ttfields = PortalTrackerField.createCriteria().list() {
                        'eq'('tracker',othertracker)
                        'in'('name',format_tokens)
                    }
                    format_tokens.each { ft->
                        if(ft=='#'){
                            numberedlines = true
                        }
                        ttfields.each { ttf->
                            if(ttf.name==ft){
                                tfields << ttf
                            }
                        }
                        def tmptrn = PortalTrackerTransition.createCriteria().get() {
                            'eq'('tracker',othertracker)
                            'ilike'('name',ft)
                        }
                        if(tmptrn) {
                            transitions << tmptrn
                        }
                    }
                    def linkback = PortalTrackerField.createCriteria().get() {
                        'eq'('tracker',othertracker)
                        'eq'('field_type','BelongsTo')
                        'ilike'('field_options',attrs.field.tracker.module + ':' + attrs.field.tracker.slug + '%')
                    }
                    if(tfields && linkback){
                        if(!attrs.ajaxrowid){
                            if(attrs.field_filters && attrs.field.name?.trim() in attrs.field_filters){
                                def currentstatus = 'All'
                                if(params[attrs.field.field_options + '.record_status']){
                                    if(params[attrs.field.field_options + '.record_status'].size()==0){
                                        currentstatus = 'All'
                                    }
                                    else{
                                        currentstatus = params[attrs.field.field_options + '.record_status']
                                    }
                                }
                                else if(session['tracker_' + attrs.field.field_options + '.record_status']){
                                    currentstatus = session['tracker_' + attrs.field.field_options + '.record_status']
                                }
                                out << "<label>Status :</label><select id='${attrs.field.name?.trim()}_status'>"
                                out << "<option value='All' "
                                if(params[attrs.field.field_options + '.record_status']?.size()==0){
                                    out << "selected"
                                }
                                out << ">All</option>"
                                othertracker.statuses.sort{ it.name }.each { ostatus->
                                    if(ostatus.name!='Delete'){
                                        out << "<option value='${ostatus.name}'"
                                            if(currentstatus==ostatus.name){
                                                out << " selected"
                                            }
                                        out << ">${ostatus.name}</option>"
                                    }
                                }
                                out << "</select>"
                                out << asset.script() { """\$('#${attrs.field.name?.trim()}_status').on('change',function(){ window.location="${request.forwardURI}?${othertracker.slug}.record_status=" + \$('#${attrs.field.name?.trim()}_status').val(); });""" }
                            }
                            out << "<table id='field_${attrs.field.name?.trim()}'>"
                            if(numberedlines){
                                out << "<th>#</th>"
                            }
                            tfields.each { tf->
                                out << "<th id='col_" + tf.label.replace(' ','_').toLowerCase() + "'>" + tf.label + "</th>"
                            }
                            out << "<th>Actions</th>"
                            out << "</tr>"
                        }
                        def query = ''
                        if(!attrs.ajaxrowid){
                            query = "select * from " + othertracker.data_table() + " where " + linkback.name + "=" + params.id
                            if(attrs.field_filters && attrs.field.name?.trim() in attrs.field_filters){
                                if(params[attrs.field.field_options + '.record_status']){
                                    if(params[attrs.field.field_options + '.record_status']!='All'){
                                        session['tracker_' + attrs.field.field_options + '.record_status'] = params[attrs.field.field_options + '.record_status']
                                        query += " and record_status='" + params[attrs.field.field_options + '.record_status'] + "'"
                                    }
                                    else{
                                        session['tracker_' + attrs.field.field_options + '.record_status'] = null
                                    }
                                }
                                else{
                                    if(session['tracker_' + attrs.field.field_options + '.record_status']){
                                        query += " and record_status='" + session['tracker_' + attrs.field.field_options + '.record_status'] + "'"
                                    }
                                }
                            }
                        }
                        else{
                            query = "select * from " + othertracker.data_table() + " where " + linkback.name + "=" + params.id + " and id=" + attrs.ajaxrowid
                        }
                        def curcount = 1
                        def orderby = ""
                        if(othertracker.defaultsort) {
                            orderby = " order by " + othertracker.defaultsort
                            query += orderby
                        }
                        sql.eachRow(query) { row->
                            def rowclass=""
                            if('record_status' in row) {
                                rowclass = row['record_status'].replace(' ','_').toLowerCase()
                            }
                            out << "<tr class='${rowclass}'>"
                            if(numberedlines){
                                out << "<td>${curcount}</td>"
                            }
                            tfields.each { tf->
                                out << "<td id='col_${tf.label.replace(' ','_').toLowerCase()}'>" + displayField(field:tf,value:row[tf.name],datas:row) + "</td>"
                            }
                            out << "<td>"
                            out << link(action:"display_data",id:row['id'],params:['module':othertracker.module,'slug':othertracker.slug,(linkback.name):params.id],class:'btn btn-secondary hasmanyview'){ "View" }
                            if(transitions.size() && attrs.field?.tracker?.initial_status?.actiontransitions){
                                transitions.each { ctrans->
                                    def trole = ctrans.roles.any { role-> role.id in othertracker.user_roles(curuser,row)*.id }
                                    def backtostr = attrs.field.tracker.module + ':' + attrs.field.tracker.slug
                                    def prevtest = false
                                    if('record_status' in row && row['record_status'] in ctrans.prev_status*.name){
                                        prevtest = true
                                    }
                                    else if(attrs.field.tracker.tracker_type=='DataStore' && attrs.field.tracker?.initial_status?.name in ctrans.prev_status*.name) {
                                        prevtest = true
                                    }
                                    if(prevtest && ctrans.roles.any { role-> role.id in othertracker.user_roles(curuser,row)*.id }){
                                        if(ctrans.name.toLowerCase()=='delete'){
                                            out << link(action:"transition",id:row['id'],params:['module':othertracker.module,'slug':othertracker.slug,'transition':'delete',(linkback.name):params.id,'backtoid':params.id,'backto':backtostr],class:'btn btn-danger hasmanydelete',onclick:"return confirm('Confirm delete record?');"){ "Delete" }

                                        }
                                        else {
                                            out << link(action:"transition",id:row['id'],'transition':ctrans.name.replaceAll(" ","_").toLowerCase(),params:['module':othertracker.module,'slug':othertracker.slug,(linkback.name):params.id,'transition':ctrans.name.replaceAll(' ','_').toLowerCase(),'backto':backtostr,('transition_' + ctrans.id):ctrans.next_status.id],class:'btn btn-secondary hasmanyaction'){ ctrans.submitbuttontext?:ctrans.name }
                                        }
                                    }
                                }
                                out << "</td>"
                            }
                            out << "</tr>"
                            curcount++
                        }
                    }
                    if(!attrs.ajaxrowid){
                        out << "</table>"
                    }
                }
            }
            else if(attrs.field.field_type=='BelongsTo'){
                def othertracker = attrs.field.othertracker()
                if(othertracker) {
                    def objid = params[attrs.field.name?.trim()]
                    if(attrs.value) {
                        objid = attrs.value
                    }
                    def objquery = "select * from " + othertracker.data_table() + " where id=:id"
                    def datas = sql.firstRow(objquery,['id':objid])
                    if(datas){
                        if(attrs.nolinking || attrs.field.suppress_follow_link) {
                            if(attrs.field.field_format){
                                out << datas[attrs.field.field_format]
                            }
                            else{
                                out << datas[othertracker.default_field()]
                            }
                        }
                        else {
                            def otherfield = attrs.field.field_format
                            if(otherfield){
                                out << link(action:"display_data",id:attrs.value,params:['module':othertracker.module,'slug':othertracker.slug]){
                                    out << datas[otherfield]
                                }
                            }
                            else{
                                out << link(action:"display_data",id:attrs.value,params:['module':othertracker.module,'slug':othertracker.slug]){
                                    out << datas[othertracker.default_field()]
                                }
                            }
                        }
                    }
                }
            }
            out << "</div>"
        }
    }

    def trackerFilter = { attrs->
        if(attrs.tracker && (attrs.tracker.filterfields || attrs.tracker.searchfields)){
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            def sql = new Sql(sessionFactory.currentSession.connection())
            def fields = []
            if(attrs.tracker.filterfields){
                def ftags = attrs.tracker.filterfields.tokenize(',')*.trim()
                ftags.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',attrs.tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }
            }
            if(fields || attrs.tracker.searchfields){
                out << form(name:"trackfilter",method:"get",action:"list",params:['module':attrs.tracker.module,'slug':attrs.tracker.slug]){
                    def notcontinue = ['slug']
                    out << "<fieldset class='form'>"
                    def trackerObjects = PortalSetting.namedefault("tracker_objects",[])
                    fields.each { field->
                        out << "<div class='fieldcontain'>"
                        out << "<label>" + field.label + "</label>"
                        if(field.field_type in ['Date','DateTime']) {
                            def filterid = 'date_filter_type_' + field.name?.trim()
                            out << "<input type='hidden' name='${field.name?.trim()}' id='${field.name?.trim()}'/>"
                            out << "<select class='datefilter' id='${filterid}' name='${filterid}' value='${params[filterid]}'>"
                            out << "<option ${if(params[filterid]=='Before'){ 'selected' }}>Before</option>"
                            out << "<option ${if(params[filterid]=='After'){ 'selected' }}>After</option>"
                            out << "<option ${if(params[filterid]=='Between'){ 'selected' }}>Between</option>"
                            out << "</select>"
                            out << " <input type='date' class='${field.name?.trim()}_date' id='${field.name?.trim()}_first' name='${field.name?.trim()}_first' value='${params[field.name?.trim() + '_first']}'/>"
                            out << " <span id='${field.name?.trim()}_second_span'>- <input class='${field.name?.trim()}_date' type='date' id='${field.name?.trim()}_second' name='${field.name?.trim()}_second' value='${params[field.name?.trim() + '_second']}'/></span>"
                            out << asset.script() { """
                                \$('#${filterid}').on('change',function() {
                                    var df = \$('#${filterid}').val();
                                    if(df=='Before'||df=='After') {
                                        \$('#${field.name?.trim()}_second_span').hide();
                                    }
                                    else {
                                        \$('#${field.name?.trim()}_second_span').show();
                                    }
                                });
                                \$('.${field.name?.trim()}_date').on('change',function() {
                                    var df = \$('#${filterid}').val();
                                    if(df=='Before') {
                                        \$('#${field.name?.trim()}').val('<' + \$('#${field.name?.trim()}_first').val());
                                    }
                                    else if(df=='After') {
                                        \$('#${field.name?.trim()}').val('>' + \$('#${field.name?.trim()}_first').val());
                                    }
                                    else {
                                        \$('#${field.name?.trim()}').val('between_' + \$('#${field.name?.trim()}_first').val() + '_' + \$('#${field.name?.trim()}_second').val());
                                    }
                                });
                                ${if(params[filterid]!='Between'){
                                "\$('#${field.name?.trim()}_second_span').hide();" 
                                }} """
                            }
                            notcontinue << filterid
                            notcontinue << field.name?.trim() + '_first'
                            notcontinue << field.name?.trim() + '_second'
                        }
                        else {

                          out << "<select class='filterdropdown' name='" + field.name?.trim() + "' id='" + field.name?.trim() + "'>"
                          out << "<option value=''></option>"
                          def query = "select distinct " + field.name?.trim() + " from " + attrs.tracker.data_table() + " order by " + field.name?.trim()
                          if(field.name?.trim()=='record_status'){
                              attrs.tracker.statuses.sort { it.name }.each {
                                  if(it.name != 'Delete') {
                                      out << "<option value='" + it.name + "'"
                                      if(params[field.name?.trim()] && params[field.name?.trim()]==it.name){
                                          out << " selected"
                                      }
                                      out << ">" + it.name + "</option>"
                                  }
                              }
                          }
                          else if(field.field_type in trackerObjects){
                              def dtrck = trackerObjects[field.field_type].tokenize('.')
                              def tobj = null
                              if(dtrck.size()>1){
                                  tobj = PortalTracker.findByModuleAndSlug(dtrck[0].trim(),dtrck[1].trim())
                              }
                              else {
                                  tobj = PortalTracker.findBySlug(dtrck[0].trim())
                              }
                              if(tobj) {
                                  sql.eachRow(query) { row->
                                      def fdatas = tobj.getdatas(row[0],sql)
                                      if(fdatas) {
                                          // print("fdatas:"+fdatas)
                                          out << "<option value='" + row[0] + "'"
                                          if(params[field.name?.trim()] && params[field.name?.trim()]?.trim()==row[0]?.toString()?.trim()){
                                              out << " selected"
                                          }
                                          if(dtrck.size()==3){ 
                                              out << ">" + fdatas[dtrck[2].trim()] + "</option>"
                                          }
                                          else {
                                              out << ">" + fdatas[tobj.default_field()] + "</option>"
                                          }
                                      }
                                  }
                              }
                          }
                          else if(field.field_type=='User'){
                              sql.eachRow(query) { row->
                                  out << "<option value='" + row[0] + "'"
                                  if(params[field.name?.trim()] && params[field.name?.trim()]?.trim()==row[0]?.toString()?.trim()){
                                      out << " selected"
                                  }
                                  out << ">" + User.get(row[0])?.name + "</option>"
                              }
                          }
                          else if(field.field_type=='Drop Down'){
                              field.evaloptions(session,null,sql).each {
                                  out << "<option value='" + it + "'"
                                  if(params[field.name?.trim()] && params[field.name?.trim()]==it){
                                      out << " selected"
                                  }
                                  out << ">" + it + "</option>"
                              }
                          }
                          else if(field.field_type=='BelongsTo'){
                              def otherquery = query
                              def othertracker = field.othertracker()
                              if(othertracker){
                                def otherfield = 'id as secondid'
                                if(field.field_format){
                                     otherfield = '"' + field.field_format + '"'
                                     otherquery = 'select id,' + otherfield + ' from ' + othertracker.data_table() + " order by " + otherfield
                                }
                                 else{
                                     otherquery = 'select id,id as secondid from ' + othertracker.data_table() + " order by id"
                                }
                              }
                              sql.eachRow(otherquery) { row->
                                out << "<option value='" + row[0] + "'"
                                if(params[field.name?.trim()] && params[field.name?.trim()]==row[0].toString()){
                                     out << " selected"
                                }
                                out << ">" + row[1] + "</option>"
                              }
                          }
                          else{
                            sql.eachRow(query) { row->
                                out << "<option value='" + row[0] + "'"
                                if(params[field.name?.trim()] && params[field.name?.trim()]==row[0].toString()){
                                     out << " selected"
                                }
                                out << ">" + row[0] + "</option>"
                            }
                          }
                          out << "</select>"
                      }
                      out << "</div>"
                      notcontinue << field.name?.trim()
                    }
                    if(attrs.tracker.searchfields){                        
                        out << "<div class='fieldcontain' id='tracksearch'>"
                        out << "<label>Search:</label>"
                        out << textField(name:'search',value:params.search)
                        out << submitButton(name:'dosearch',value:'Search')
                        notcontinue << 'dosearch'
                        notcontinue << 'search'
                        out << "</div>"
                    }
                    out << continueparams(notcontinue:notcontinue)
                    out << "</fieldset>"
                    out << "<br/>"
                }
                out << asset.script() { "\$('.filterdropdown').change(function() { \$('#trackfilter').submit(); });" }
            }
        }
    }

    def trackerCalendar = { attrs->
        if(attrs.tracker){
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            def datasource = sessionFactory.currentSession.connection()
            def sql = new Sql(datasource)
            def query = attrs.tracker.listquery(params,curuser)
            def totalcount = sql.firstRow(attrs.tracker.listquery(params,curuser,"select count(id)"))[0]
            def currow = 0
            def event = []
            sql.eachRow(query) { row->
                def curevent = "{ title :'" + row['title'] + "', start:'" + row['start_date'] + "'}"
                event << curevent
            }
            out << asset.javascript(src:"fullcalendar/fullcalendar.min.js")
            out << asset.javascript(src:"fullcalendar/gjal.js")
            out << asset.stylesheet(href:"fullcalendar/fullcalendar.css")
            out << asset.stylesheet(href:"fullcalendar/fullcalendar.print.css")
            out << asset.javascript(src:"jquery-migrate-1.0.0.js")
            out << asset.javascript(src:"jquery.qtip-1.0.0-rc3.min.js")
            out << "<div id='cal'></div>"
            out << asset.script() { """\$('#cal').fullCalendar({
events: '""" + createLink(controller:'tracker',action:'calendarfeed',params:params) + """/',
            eventRender: function(event, element) {
                if(event.description){
                    element.qtip({
position: {
target: 'mouse'
},
content: event.description
});
}
}
});""" }
        }
    }

    def trackerList = { attrs->
        if(attrs.tracker){
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            def datasource = sessionFactory.currentSession.connection()
            def sql = new Sql(datasource)
            def fields = []
            def field_names = []
            def ftags = null
            if(attrs.tracker.listfields){
                ftags = attrs.tracker.listfields.tokenize(',')*.trim()
            }
            else{
                // println "No list fields specified for tracker"
            }
            ftags.each { ftag->
                def tfield = PortalTrackerField.createCriteria().get(){
                    'eq'('tracker',attrs.tracker)
                    'eq'('name',ftag)
                }
                if(tfield){
                    fields << tfield
                    if(!(tfield.field_type in ['HasMany'] || tfield.field_query!=null || tfield.field_query?.size())) {
                        field_names << tfield.name?.trim()
                    }
                }
                else if(ftag=='row_number'){
                    def row_number = new PortalTrackerField(tracker:attrs.tracker,name:ftag,label:'No.')
                    fields << row_number
                }
            }

            out << """
        <style>
        	@media
	  only screen 
    and (max-width: 760px), (min-device-width: 768px) 
    and (max-device-width: 1024px)  { """
            def num = 1
            fields.each { field->
                out << "td:nth-of-type(${num}):before { content: '${field.label}'; }"
                num += 1
            }
            out << """
    }
        </style>
        """
            out << "<table class='responsive'>"
            out << "<thead>"
            out << "<tr>"
            fields.each { field->
                out << "<th id='col_" + field.label.replace(' ','_').toLowerCase() + "'>" + field.label + "&nbsp;</th>"
            }
            if(attrs.tracker.actionbuttons){
                out << "<th>Action</th>"
            }
            out << "</tr>"
            out << "</thead>"
            if(!params.max){
                params.max = 10
            }
            def countparams = params.clone()
            if(countparams.max) {
                countparams.remove('max')
            }
            if(countparams.offset) {
                countparams.remove('offset')
            }
            def countquery = attrs.tracker.listquery(countparams,curuser,"select count(*)")
            def totalcount = 0
            println "Countquery:" + countquery
            if(countquery['query'].indexOf(':')) {
                def typedParams = countquery['qparams'].collectEntries { key, value ->
                    [key, value?.toString()]
                }
                totalcount = sql.firstRow(countquery['query'], typedParams)[0]
            }
            else {
                totalcount = sql.firstRow(countquery['query'])[0]
            }

            def rolebuttons = [:]
            if(attrs.tracker.actionbuttons){
                // actionbuttons format is 
                // <Role name>-><Transition names, comma separated>;
                // separated by semi-column
                def roles = attrs.tracker.actionbuttons.tokenize(';')*.trim()
                roles.each { crole->
                    def parts = crole.tokenize('->')*.trim()
                    if(parts.size()==1) {
                        def actions = parts[0].tokenize(',')*.trim()
                        actions.each { action->
                            def vt = PortalTrackerTransition.findAllByTrackerAndName(attrs.tracker,action)
                            if(vt) {
                                vt.roles.each { cr->
                                    if(!rolebuttons[cr[0].name]) {
                                        rolebuttons[cr[0].name] = []
                                    }
                                    rolebuttons[cr[0].name] << action
                                }
                            }
                        }
                    }
                    else if(parts.size()>1) {
                        def validroles = parts[0].tokenize(',')*.trim()
                        validroles.each { vrole->
                            def troles = PortalTrackerRole.findAllByTrackerAndName(attrs.tracker,vrole)
                            if(troles){
                                if(rolebuttons[vrole]) {
                                    rolebuttons[vrole] += parts[1].tokenize(',')*.trim()
                                }
                                else {
                                    rolebuttons[vrole] = parts[1].tokenize(',')*.trim()
                                }
                            }
                        }
                    }
                }
            }
            def comp_field = []
            if(!('id' in field_names)){
                comp_field << 'id'
            }
            if(attrs.tracker.tracker_type!='DataStore' && !('record_status' in field_names)){
                comp_field << 'record_status'
            }
            if(attrs.tracker.hiddenlistfields) {
                def hiddenfields = attrs.tracker.hiddenlistfields?.tokenize(',')*.trim()
                hiddenfields.each { hf ->
                   if(!(hf in field_names)){
                      comp_field << hf
                   }
                }
            }
            def comp_string = ""
            if(comp_field.size()>0){ 
                if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                    comp_string = '"' + comp_field.join('","') + '",'
                }
                else {
                    comp_string = comp_field.join(",") + ","
                }
            }

            if(!params.max) {
                params.max = 10
            }

            def query = attrs.tracker.listquery(params,curuser,"select " + comp_string + ' "' + field_names.join('","') + '" ')

            def currow = 0
            def rows = []
            // println "Query:" + query
            if(query['query'].indexOf(':')>0) {
                try {
                    def typedParams = query['qparams'].collectEntries { key, value ->
                        [key, value?.toString()]
                    }
                    rows = sql.rows(query['query'], typedParams)
                }
                catch(Exception e){
                    println "Got error with query: " + query['query']
                    println "Got error with qparams: " + query['qparams']
                    PortalErrorLog.record(null,null,'tracker','trackerlist',e.toString() + " query: " + query,attrs.tracker.slug,attrs.tracker.module)
                }
            }
            else {
                try {
                    rows = sql.rows(query['query'])
                }
                catch(Exception e){
                    println "Got error with query: " + query
                    PortalErrorLog.record(null,null,'tracker','trackerlist',e.toString() + " query: " + query,attrs.tracker.slug,attrs.tracker.module)
                }
            }
            rows.each { row->
                def rec_status = 'default'
                if(attrs.tracker.tracker_type!='DataStore') {
                    if('record_status' in row) {
                        rec_status = row['record_status']?.replace(' ','_')?.toLowerCase()
                    }
                }
                out << "<tr class='status_${rec_status} " + attrs.tracker.rowclass(row) + "'>"
                def firstfield = true
                // println "Row:" + row
                fields.each { field->
                    def tdclass = ''
                    if(field.field_type in ['Integer','Number']){
                        tdclass += ' numbercell'
                    }
                    out << "<td class='${tdclass}'>"
                    def value = ''
                    if(field.name?.trim()=='row_number'){
                        if(params.offset){
                            value = currow + params.offset.toInteger() + 1
                        }
                        else{
                            value = currow + 1
                        }
                    }
                    else{
                        value = row[field.name?.trim()]
                    }
                    if(firstfield){
                        out << link(action:'display_data',class:'linkdetail ',params:[module:attrs.tracker.module,slug:attrs.tracker.slug,id:row['id']]){
                            def todisplay = displayField(field:field,value:value,datas:row,nolinking:true)
                            if(todisplay.size()>0) {
                                out << todisplay
                            }
                            else {
                                out << " - "
                            }
                        }
                    }
                    else{
                        out << displayField(field:field,value:value,datas:row)
                    }
                    firstfield = false
                    out << "&nbsp;</td>"
                }
                if(attrs.tracker.actionbuttons && curuser){
                    def uroles = attrs.tracker.user_roles(curuser,row)
                    out << "<td>"
                    uroles.each { rb -> 
                        def curtrans = null
                        if(rolebuttons[rb.name.trim()]) {
                            curtrans = PortalTrackerTransition.createCriteria().list() {
                                'eq'('tracker', attrs.tracker)
                                'in'('name', rolebuttons[rb.name.trim()])
                                prev_status {
                                    'eq'('name',row['record_status'])
                                }
                            }
                        }
                        if(curtrans){
                            curtrans.each { ctrans->
                                out << link(class:"btn btn-primary m-1",name:"transition_" + ctrans.id,action:"transition",params:['module':attrs.tracker.module,'slug':attrs.tracker.slug,'id':row['id'],'transition':ctrans.name.replace(" ","_").toLowerCase()]) {
                                  ctrans.name
                                }
                            }
                        }
                        else{
                            out << "&nbsp;"
                        } 
                    }
                    out << "</td>"
                }
                out << "</tr>"
                currow++
            }
            out << "</table>"
            if(totalcount>10) {
                out << "<div class='pagination'>"
                out << paginate(total:totalcount,params:params)
                out << "</div>"
            }
        }
    }

    def trackerInfo = { attrs->
        if(attrs.transition){
            def datas = [:]
            out << form(action:"editrecord",useToken:true,params:[slug:attrs.transition.tracker.slug]){
                def fields = []
                def ftags = attrs.transition.displayfields?.tokenize(',')*.trim()
                ftags.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',attrs.transition.tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }
                if(attrs.record_id){
                    def sql = new Sql(sessionFactory.currentSession.connection())
                    def query = "select * from " + attrs.transition.tracker.data_table() + " where id=" + attrs.record_id
                    sql.eachRow(query) { row->
                        fields.each { field->
                            if(field.field_type != 'HasMany') {
                                datas[field.name?.trim()]=row[field.name?.trim()]
                            }
                        }
                    }
                }
                out << "<table class='requestview'>"
                    fields.each { field->
                        out << "<tr class='prop' id='${field.name?.trim()}_tr'>"
                        def colspan = ''
                        if(!field.hide_heading){
                            out << "<td class='name'>" + field.label + "</td>"
                        }
                        else{
                            colspan = "colspan='2' style='text-align:center'"
                        }
                        if(!(field.field_type in ['HasMany'])){
                            out << "<td class='value val_${field.label.replace(' ','_').toLowerCase()}' $colspan>" + displayField(field:field,value:datas[field.name?.trim()],datas:datas) + "</td>"
                        }
                        else{
                            out << "<td class='value val_${field.label.replace(' ','_').toLowerCase()}' $colspan>" + displayField(field:field,datas:datas) + "</td>"
                        }
                        out << "</tr>"
                    }
                out << "</table>"
            }
        }
    }

    def trackerForm = { attrs->
        if(attrs.transition){
            def datas = [:]
            out << uploadForm(name:attrs.transition.tracker.slug + '_form',useToken:true,action:"transition",params:[module:attrs.transition.tracker.module,slug:attrs.transition.tracker.slug,transition:attrs.transition.name.replace(" ","_").toLowerCase()]){
                if(params.backtolist){
                    out << hiddenField(name:'backtolist',value:'backtolist')
                }
                if(attrs.transition?.next_status?.name=='Delete') {
                    if(params.backto){
                        out << hiddenField(name:'backto',value:params.backto)
                        def othertracker = PortalTracker.load_tracker(params.backto)
                        def linkback = PortalTrackerField.createCriteria().get() {
                            'eq'('tracker',attrs.transition.tracker)
                            'eq'('field_type','BelongsTo')
                            'like'('field_options',othertracker.module + ':' + othertracker.slug + '%')
                        }
                        if(linkback) {
                            if(params[linkback.name]) {
                                out << hiddenField(name:linkback.name,value:params[linkback.name])
                                if(params.backtransition) {
                                    out << hiddenField(name:'backtransition',value:params.backtransition)
                                }
                            }
                        }
                    }
                }
                def fields = [] 
                def ftags = attrs.transition.editfields?.tokenize(',')*.trim()
                ftags.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',attrs.transition.tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }
                if(attrs.record_id){
                    out << hiddenField(name:"id",value:attrs.record_id)
                    def sql = new Sql(sessionFactory.currentSession.connection())
                    def query = "select * from " + attrs.transition.tracker.data_table() + " where id=" + attrs.record_id
                    sql.eachRow(query) { row->
                        fields.each { field->
                            if(field.field_type != 'HasMany'){
                                try {
                                    datas[field.name?.trim()]=row[field.name?.trim()]
                                }
                                catch(Exception e) {
                                    println "Error assiging field |" + field.name + "|"
                                }
                            }
                        }
                    }
                }
                def gotfields = false
                def zindex = 100
                fields.each { field->
                    out << trackerField(field:field,datas:datas,transition:attrs.transition,zindex:zindex)
                    zindex += 100
                    if(field.field_default || field.params_override && params[field.name?.trim()]){
                      println "Got hidden fields for " + field
                    }
                    else{
                      println "Got fields for " + field
                      gotfields = true
                    }
                }
                out << hiddenField(name:'next_status',value:attrs.transition.next_status?.id)
                if(attrs.transition.id){
                    out << hiddenField(name:'transition_' + attrs.transition.id,value:attrs.transition.next_status?.id)
                }
                else{
                    out << hiddenField(name:'transition_edit')
                }
                out << "<div class='fieldcontain submitbuttons row'>"
                if(attrs.transition.submitbuttontext){
                    out << submitButton(name:"submit",class:"save button btn btn-primary mx-auto col-1",value:attrs.transition.submitbuttontext)
                }
                else if(attrs.record_id){
                    out << submitButton(name:"submit",class:"save button btn btn-primary mx-auto col-1",value:"Update")
                }
                else{
                    out << submitButton(name:"submit",class:"save button btn btn-primary mx-auto col-1",value:"Create")
                }
                if(attrs.transition.cancelbutton){
                    def buttontext = 'Cancel'
                    if(attrs.transition.cancelbuttontext){
                        buttontext = attrs.transition.cancelbuttontext
                    }
                    if(attrs.record_id){
                        out << link(name:"cancel",class:"save button btn btn-warning mx-auto col-2",controller:'portalTracker',action:'display_data',params:['module':attrs.transition.tracker.module,'slug':attrs.transition.tracker.slug,'id':attrs.record_id]){ 
                            out << buttontext
                        }
                    }
                    else {
                        out << link(name:"cancel",class:"save button btn btn-warning mx-auto col-2",controller:'portalTracker',action:'list',params:['module':attrs.transition.tracker.module,'slug':attrs.transition.tracker.slug]){ 
                            out << buttontext
                        }
                    }
                }
                out << "</div>"
                if(attrs.transition.immediate_submission) {
                    out << asset.script() { """\$(window).on('load',function() { \$('#${attrs.transition.tracker.slug}_form').find('input[type="submit"]').click();});"""}
                }
            }
            out << """
            <script>

            function checkboxArray(field_name) {
                return \$("[name='" + field_name + "']:checked").map(function() { return this.value }).get();
            }

            function toggleAll(field_name,value=null) {
                var inputelement = \$("[name='" + field_name + "']");
                var dispelement = \$("#" + field_name + "_tr");
                if(inputelement) {
                    toggleField(inputelement,value);
                }
                if(dispelement) {
                    if(value!=null) {
                        if(value==1) {
                            dispelement.show();
                        }
                        else {
                            dispelement.hide();
                        }
                    }
                    else {
                        if(isElementVisible(dispelement)) {
                            dispelement.hide();
                        }
                        else {
                            dispelement.show();
                        }
                    }
                }
            }

            function toggleField(\$element,status=null) {
                if(status!=null) {
                    if(status==1) {
                        if(\$element.prop('type')!='hidden') {
                            \$element.closest('div.fieldcontain').show();
                        }
                        \$element.prop('disabled',false);
                        \$element.data('toggle',1);
                    }
                    else {
                        if(\$element.prop('type')!='hidden') {
                            \$element.closest('div.fieldcontain').hide();
                        }
                        \$element.prop('disabled',true);
                        \$element.data('toggle',0);
                    }
                }
                else {
                    if(\$element.data('toggle') || isElementVisible(\$element)) {
                        if(\$element.prop('type')!='hidden') {
                            \$element.closest('div.fieldcontain').hide();
                        }
                        \$element.prop('disabled',true);
                        \$element.data('toggle',0);
                    }
                    else {
                        if(\$element.prop('type')!='hidden') {
                            \$element.closest('div.fieldcontain').show();
                        }
                        \$element.prop('disabled',false);
                        \$element.data('toggle',1);
                    }
                }
            }

            function isElementVisible(\$element) {
              // Check if element exists
              if (!\$element.length) {
                  return false;
              }

              // Check if the element or any of its parents have display: none
              if (\$element.css('display') === 'none' || \$element.parents().filter(function() {
                  return \$(this).css('display') === 'none';
              }).length > 0) {
                  return false;
              }

              // Check visibility property
              if (\$element.css('visibility') === 'hidden' || \$element.parents().filter(function() {
                  return \$(this).css('visibility') === 'hidden';
              }).length > 0) {
                  return false;
              }

              // Check opacity
              if (parseFloat(\$element.css('opacity')) === 0) {
                  return false;
              }

              // Check if element has zero dimensions
              const rect = \$element[0].getBoundingClientRect();
              if (rect.width === 0 || rect.height === 0) {
                  return false;
              }

              return true;
            }

            function update_submit() {
              var enablesubmit = true;
              \$('.fatal_error').each(function() {
                const \$error = \$(this);
                if (isElementVisible(\$error)) {
                    enablesubmit = false;
                } else {
                }
              });
              \$('#submit').prop('disabled',!enablesubmit);
            }

            function set_readonly(field_name, value) {
                var inputelement = \$("[name='" + field_name + "']");
                if(inputelement) {
                    inputelement.val(value);
                    inputelement.prop('readonly',true);
                }
            }
            </script>
            """
        }
    }

    def trackerFieldRow = { attrs->
        out << "<tr class='prop' id='${attrs.field.name?.trim()}_tr'>"
        def colspan = ''
        if(attrs.field.field_type in ['FieldGroup']){
            out << "<tr><th class='group_heading' colspan='2'>" + attrs.field.label + "</th></tr>"
            out << "<tr><td class='group_data' colspan='2'><table>"
            def fields = [] 
            def ftags = attrs.field.field_options?.tokenize(',')*.trim()
            ftags.each { ftag->
                def tfield = PortalTrackerField.createCriteria().get(){
                    'eq'('tracker',attrs.field.tracker)
                    'eq'('name',ftag)
                }
                if(tfield){
                    fields << tfield
                }
            }
            fields.each { fgf->
                out << trackerFieldRow(field:fgf,datas:attrs.datas,field_filters:attrs.field_filters)
            }
            out << "</table></td></tr>"
        }
        else if(!attrs.field.hide_heading){
            out << "<td class='name'>" + attrs.field.label + "</td>"
        }
        else{
            colspan = "colspan='2' style='text-align:center'"
        }
        if(!(attrs.field.field_type in ['HasMany','FieldGroup'])){
            out << "<td class='value val_${attrs.field.label.replace(' ','_').toLowerCase()}' $colspan>" + displayField(field:attrs.field,value:attrs.datas[attrs.field.name?.trim()],datas:attrs.datas,field_filters:attrs.field_filters) + "</td>"
        }
        else if(attrs.field.field_type in ['FieldGroup']){
        }
        else{
            out << "<td class='value val_${attrs.field.label.replace(' ','_').toLowerCase()}' $colspan>" + displayField(field:attrs.field,datas:attrs.datas,field_filters:attrs.field_filters) + "</td>"
        }
        out << "</tr>"
    }

    def trackerDisplay = { attrs->
        if(attrs.tracker && attrs.record_id){
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            def sql = new Sql(sessionFactory.currentSession.connection())
            def query = "select * from " + attrs.tracker.data_table() + " where id=" + attrs.record_id
            def datas = sql.firstRow(query)
            def curstatus = null
            if(datas?.containsKey('record_status')) {
                curstatus = PortalTrackerStatus.createCriteria().get(){
                    'eq'('tracker',attrs.tracker)
                    'eq'('name',datas['record_status'])
                }
            }
            def userroles = attrs.tracker.user_roles(curuser,datas)
            // userroles would consist of tracker roles that is valid for the current user
            if(!userroles && !attrs.tracker.anonymous_view){
                out << "<script>alert('You are not authorised to view that record');window.location='" + createLink(controller:'tracker',action:'display',params:['slug':attrs.tracker.slug])  + "';</script>"
                return
            }
            def fields = []
            if(curstatus){
                def ftags = curstatus.displayfields?.tokenize(',')*.trim()
                ftags.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',attrs.tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }
                if(fields.size()==0) {
                    ftags = attrs.tracker.listfields?.tokenize(',')*.trim()
                    ftags.each { ftag->
                        def tfield = PortalTrackerField.createCriteria().get(){
                            'eq'('tracker',attrs.tracker)
                            'eq'('name',ftag)
                        }
                        if(tfield){
                            fields << tfield
                        }
                    }
                }
            }
            else {
                if(attrs.tracker.tracker_type=='DataStore' && attrs.tracker.initial_status) {
                    def ftags = attrs.tracker.initial_status.displayfields?.tokenize(',')*.trim()
                    ftags.each { ftag->
                        def tfield = PortalTrackerField.createCriteria().get(){
                            'eq'('tracker',attrs.tracker)
                            'eq'('name',ftag)
                        }
                        if(tfield){
                            fields << tfield
                        }
                    }
                } 
                if(fields.size()==0) {
                    def ftags = attrs.tracker.listfields?.tokenize(',')*.trim()
                    ftags.each { ftag->
                        def tfield = PortalTrackerField.createCriteria().get(){
                            'eq'('tracker',attrs.tracker)
                            'eq'('name',ftag)
                        }
                        if(tfield){
                            fields << tfield
                        }
                    }
                }
            }
            if(datas) {
                out << "<table class='requestview'>"
                fields.each { field->
                    out << trackerFieldRow(field:field,datas:datas,field_filters:attrs.field_filters)
                }
                out << "</table>"
            }

            out << form(name:"updaterecord",useToken:true,action:"update_record",id:attrs.record_id,params:[module:attrs.tracker.module,slug:attrs.tracker.slug],enctype:"multipart/form-data"){

                if(curstatus?.checkupdateable(userroles*.name)){
                    out << "<h2>Update Status</h2>"
                    out << hiddenField(name:"id",value:attrs.record_id)
                    out << hiddenField(name:"statusUpdate",value:1)
                    out << hiddenField(name:"record_status",value:datas['record_status'])
                    out << "<label>Remarks/Description</label>"
                    out << textArea(style:"float:none;clear:both;width:100%;",name:"statusUpdateDesc",cols:"150")
                    def apath = PortalSetting.findByName(attrs.tracker.module + "_" + attrs.tracker.slug + '_attachment_path')
                    if(!apath) {
                        apath = PortalSetting.findByName(attrs.tracker.slug + '_attachment_path')
                    }
                    if(curstatus.attachable && apath){
                        out << "<label>Attach File:</label><input style='float:none;' type='file' name='uploadfile'/><br/>"
                    }
                    if(!curstatus.suppressupdatebutton){
                        out << submitButton(class:"btn btn-primary m-1",style:"float:none;clear:right;",name:"submit",value:"Submit Update")
                    }
                }
            }

            attrs.datas = datas
            out << transitionButtons(record_id:attrs.record_id,userroles:userroles,tracker:attrs.tracker,datas:attrs.datas)
            out << "&nbsp;"
            out << """
            <script>

            function toggleAll(field_name,value=null) {
                var dispelement = \$("#" + field_name + "_tr");
                if(dispelement) {
                    if(value!=null) {
                        if(value==1) {
                            dispelement.show();
                        }
                        else {
                            dispelement.hide();
                        }
                    }
                    else {
                        if(isElementVisible(dispelement)) {
                            dispelement.hide();
                        }
                        else {
                            dispelement.show();
                        }
                    }
                }
            }
            </script>"""
        }
    }

    def trackerTransitions = { attrs->
        if(attrs.tracker && attrs.record_id){
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            def sql = new Sql(sessionFactory.currentSession.connection())
            def query = "select * from " + attrs.tracker.data_table() + " where id=" + attrs.record_id
            def datas = sql.firstRow(query)
            def curstatus = null
            if(datas?.containsKey('record_status')) {
                curstatus = PortalTrackerStatus.createCriteria().get(){
                    'eq'('tracker',attrs.tracker)
                    'eq'('name',datas['record_status'])
                }
            }
            def userroles = attrs.tracker.user_roles(curuser,datas)
            // userroles would consist of tracker roles that is valid for the current user
            if(!userroles && !attrs.tracker.anonymous_view){
                out << "<script>alert('You are not authorised to view that record');window.location='" + createLink(controller:'tracker',action:'display',params:['slug':attrs.tracker.slug])  + "';</script>"
                return
            }
            def fields = []
            if(curstatus){
                def ftags = curstatus.displayfields?.tokenize(',')*.trim()
                ftags.each { ftag->
                    def tfield = PortalTrackerField.createCriteria().get(){
                        'eq'('tracker',attrs.tracker)
                        'eq'('name',ftag)
                    }
                    if(tfield){
                        fields << tfield
                    }
                }
                if(fields.size()==0) {
                    ftags = attrs.tracker.listfields?.tokenize(',')*.trim()
                    ftags.each { ftag->
                        def tfield = PortalTrackerField.createCriteria().get(){
                            'eq'('tracker',attrs.tracker)
                            'eq'('name',ftag)
                        }
                        if(tfield){
                            fields << tfield
                        }
                    }
                }
            }
            else {
                if(attrs.tracker.tracker_type=='DataStore' && attrs.tracker.initial_status) {
                    def ftags = attrs.tracker.initial_status.displayfields?.tokenize(',')*.trim()
                    ftags.each { ftag->
                        def tfield = PortalTrackerField.createCriteria().get(){
                            'eq'('tracker',attrs.tracker)
                            'eq'('name',ftag)
                        }
                        if(tfield){
                            fields << tfield
                        }
                    }
                } 
                if(fields.size()==0) {
                    def ftags = attrs.tracker.listfields?.tokenize(',')*.trim()
                    ftags.each { ftag->
                        def tfield = PortalTrackerField.createCriteria().get(){
                            'eq'('tracker',attrs.tracker)
                            'eq'('name',ftag)
                        }
                        if(tfield){
                            fields << tfield
                        }
                    }
                }
            }

            attrs.datas = datas
            out << transitionButtons(record_id:attrs.record_id,userroles:userroles,tracker:attrs.tracker,datas:attrs.datas)
            out << "&nbsp;"
        }
    }

    def transitionButtons = { attrs->
        if(!attrs.datas) {
            return;
        }
        def prev_status = null 
        if(attrs.datas?.containsKey('record_status')) {
            prev_status = PortalTrackerStatus.createCriteria().get(){
                'eq'('tracker',attrs.tracker)
                'eq'('name',attrs.datas['record_status'])
            }
        }

        def transitions = []

        if(attrs.tracker.tracker_type in ['DataStore','Statement']){  // statement or datastore does not need to check previous status. It just enables transition based on transition roles
            attrs.tracker.transitions.each { t->
                if(t.roles) {
                    if(attrs.userroles.any { crole-> crole in t.roles }){
                        transitions << t
                    }
                }
                else{
                    transitions << t
                }
            }
        }
        else {
            if(prev_status){
                attrs.tracker.transitions.each { t->
                    if(prev_status.id in t.prev_status*.id){
                        if(attrs.userroles.any { crole-> crole in t.roles }){
                            transitions << t
                        }
                    }
                }
            }
        }

        transitions.sort{ it.name }.each { transition->
            def enabletrans = transition.testenabled(session,attrs.datas)
            if(enabletrans){
                out << link(class:"btn btn-primary m-1",name:"transition_" + transition.id,action:"transition",params:['module':attrs.tracker.module,'slug':attrs.tracker.slug,'id':attrs.record_id,'transition':transition.name.replace(" ","_").toLowerCase()]) {
                    out << transition
                }
            }
        }
    }

    def trackerUpdates = { attrs->
        if(attrs.tracker && attrs.record_id && attrs.tracker.tracker_type=='Tracker'){
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            def sql = new Sql(sessionFactory.currentSession.connection())
            def query = "select * from " + attrs.tracker.data_table() + " where id=" + attrs.record_id
            def trackrecord = sql.firstRow(query)
            def userroles = attrs.tracker.user_roles(curuser,trackrecord)
            def userrules = ''
            if(userroles.size()){
                def currules = []
                userroles.each { urole->
                    currules << " allowedroles like '%" + urole.name + "%' "
                }
                userrules = "and (allowedroles = 'null' or allowedroles = '' or " + currules.join('or') + ")"
            }
            if(config.dataSource.url.contains("jdbc:postgresql") || config.dataSource.url.contains("jdbc:h2")){
                query = "select * from " + attrs.tracker.trail_table() + " where record_id=:id $userrules order by update_date desc,id desc"
            }
            else {
                query = "select * from " + attrs.tracker.trail_table() + " where [record_id]=:id $userrules order by update_date desc,id desc"
            }
            def rows = sql.rows(query,['id':attrs.record_id])
            if(rows.size()){
                out << "<h3>Status Updates</h3>"
                out << "<table style=background-color:white id='statusupdates'><tr><th>Update Description</th><th>Updated By</th><th>Update Date</th></tr>"
                rows.each { row->
                    out << "<tr>"
                    out << "<td>"
                    out << "<p>" + row['description']?.replace('\n','<br/>') + "</p>"
                    if(row['attachment_id']){
                        def attachment = FileLink.get(row['attachment_id'])
                        out << "Attached file : " + filelink_link(module:attachment.module,slug:attachment.slug) + "<br/>"
                    }
                    out << "</td>"
                    def updater = User.get(row['updater_id'])
                    out << "<td>" + updater?.name + "</td>"
                    out << "<td>" + formatDate(format:"HH:mm a dd-MMM-yy",date:row['update_date']) + "</td>"
                    out <<  "</tr>"
                }
                out << "</table>"
            }
        }
    }
}
