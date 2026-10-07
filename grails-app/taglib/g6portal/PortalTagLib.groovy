package g6portal

class PortalTagLib {

    static defaultEncodeAs = 'html'
    static encodeAsForTags = [fmbreadcrumbs: 'raw',rolelist: 'raw',ifnotrole: 'raw',ifrole: 'raw',user_selector: 'raw',local_select2: 'raw',moduleSelect: 'raw',choiceSelect: 'raw',continueparams: 'raw',hashlink: 'raw',createHashLink:'raw'] 
    static returnObjectForTags = ['ifroleb']

    def hashlink = { attrs,body->
        def toret = [:]
        attrs.params.each { key,val ->
            try {
                if(key in ['slug','module','controller','action']) {
                    toret[key] = val
                }
                else {
                    def ekey = PortalTracker.base64Encode(key.toString())
                    def eval = PortalTracker.base64Encode(val.toString())
                    toret['g5e' + ekey] = eval
                }
            }
            catch(Exception exp) {
                println "Exp:" + exp
            }
        }
        out << link(id:attrs.id,controller:attrs.controller,action:attrs.action,params:toret) { body() }
    }

    def createHashlink = { attrs->
        def toret = [:]
        attrs.params.each { key,val ->
            try {
                if(key in ['slug','module','controller','action']) {
                    toret[key] = val
                }
                else {
                    def ekey = PortalTracker.base64Encode(key.toString())
                    def eval = PortalTracker.base64Encode(val.toString())
                    toret['g5e' + ekey] = eval
                }
            }
            catch(Exception exp) {
                println "Exp:" + exp
            }
        }
        out << createLink(id:attrs.id,controller:attrs.controller,action:attrs.action,params:toret)
    }

    def fmbreadcrumbs = { attrs->
        out << "Path : "
        def filemanager = PortalFileManager.get(params.id)
        if(filemanager){
            out << "<span hx-get='" + createLink(action:"explorepage",id:params.id) + "' hx-target='#explorepage'>" + filemanager.name + "</span>"
        }
        if(params.fname){
            def pathpart = params.fname.tokenize('/')
            def donepart = []
            pathpart.each { curpart->
                if(donepart.size()>1){
                    out << " / " + "<span hx-get='" + createLink(action:"explorepage",id:params.id,params:[fname:'/' + donepart.join('/') + '/' + curpart]) + "' hx-target='#explorepage'>" + curpart  + "</span>"
                }
                else if(donepart.size()==1){
                    out << " / " + "<span hx-get='" + createLink(action:"explorepage",id:params.id,params:[fname:donepart[0] + '/' + curpart]) + "' hx-target='#explorepage'>" + curpart + "</span>"
                }
                else{
                    out << " / " + "<span hx-get='" + createLink(action:"explorepage",id:params.id,params:[fname:'/' + curpart]) + "' hx-target='#explorepage'>" + curpart + "</span>"
                }
                donepart << curpart
            }
        }
    }


    def ifroleb = { attrs ->
        if('All' in attrs.role){
            return true
        }
        if((attrs.item || attrs.module) && attrs.role && session.userid){
            def verified = false
            // def curuser = User.get(session.userid)
            def curuser = session.curuser
            if(('Admin' in attrs.role || attrs.role=='Admin') && curuser?.isAdmin){
                verified = true
            }
            if(!verified && attrs.module){
                def modulerole = PortalUserRole.findAllByModuleAndUser(attrs.module,curuser)
                modulerole.each { cmod->
                    if(cmod.role==attrs.role || cmod.role in attrs.role){
                        verified = true
                    }
                }
            }
            if(!verified && attrs.item){
                if(attrs.item.getrole() in attrs.role){
                    verified = true
                }
                else if(attrs.item.getrole()==attrs.role){
                    verified = true
                }
            }            
            if(!verified){
                curuser?.treeroles().each { urole->
                    if(urole.role in attrs.role){
                        verified = true
                    }
                    else if(urole.role==attrs.role){
                        verified = true
                    }
                }
            }
            if(verified){
                return true
            }
        }
        return false
    }

    def ifrole = { attrs,body ->
        if(ifroleb(item:attrs.item,module:attrs.module,role:attrs.role)){
            out << body()
        }
    }

    def ifnotroleb = { attrs->
        return !ifroleb(item:attrs.item,module:attrs.module,role:attrs.role)
    }

    def ifnotrole = { attrs,body ->
        if(!ifroleb(item:attrs.item,module:attrs.module,role:attrs.role)){
            out << body()
        }
    }

    def user_selector = { attrs->
        def ajaxlink = ""
        def dropdownParent = ""
        def url = ""
        if(attrs.url){
            url = "url: " + attrs.url + ","
        }
        else {
            if(attrs.controller) {
                if(attrs.id) {
                    ajaxlink = createLink([controller:attrs.controller,action:attrs.action,id:attrs.id,params:attrs.params])
                }
                else {
                    ajaxlink = createLink([controller:attrs.controller,action:attrs.action,params:attrs.params])
                }
            }
            else {
                ajaxlink = createLink([controller:'user',action:'completelist'])
            }
            url = "url:'" + ajaxlink + "',"
        }
        if(attrs.parent) {
            dropdownParent = """dropdownParent: \$('${attrs.parent}'),"""
        }
        def output = """
      \$('#${attrs.property}').select2({
        ${dropdownParent}
        ajax: {
          ${url}
          dataType: 'json',
          data: function (params) {
            return {
              q: params.term, // search term
      """
        if(attrs.external_value){
            output += "value:" + attrs.external_value + " , "
        }
        else {
            if(attrs.value) {
                if(attrs.value.class.name=='User') {
                    output += "value:" + attrs.value.id + " , "
                }
                else {
                    output += "value:'" + attrs.value + "' , "
                }
            }
        }
        output += """page: params.page
            };
          },
          processResults: function (data) {
            // Transforms the top-level key of the response object from 'items' to 'results'
            var toret = [];
        """
        if(attrs.action && (attrs.action=='objectlist' || attrs.action=='nodeslist' || attrs.action=='dropdownlist')) {
            output += """ data.objects.forEach(function(object) {
        toret.push( {'id':object.id,'text':object.name} );
            }); """
        }
        else {
            output += """ data.users.forEach(function(user) {
        toret.push( {'id':user.id,'text':user.name} );
            }); """
        }
        output += """
            return {
              results: toret
            };
          }
        }
      });
      \$('#${attrs.property}').on('select2:select', function(e) { htmx.trigger(this,'change'); });
        """
        out << output
    }

    /**
     * Turns a <select> that already carries all of its options inline into a searchable
     * select2 widget. Unlike user_selector there is no ajax source: the option list is
     * small enough to ship with the page, we only want the search box on top of it.
     *
     * attrs.property - id of the existing <select>
     * attrs.parent   - optional selector for dropdownParent, keeps the panel from being
     *                  clipped by the field container
     * attrs.width    - optional css width, defaults to the 40% the other pickers use
     */
    def local_select2 = { attrs->
        def dropdownParent = ""
        if(attrs.parent) {
            dropdownParent = """dropdownParent: \$('${attrs.parent}'),"""
        }
        def output = """
      \$('#${attrs.property}').select2({
        ${dropdownParent}
        width: '${attrs.width ?: '40%'}'
      });
      \$('#${attrs.property}').on('select2:select', function(e) { htmx.trigger(this,'change'); });
        """
        out << output
    }

    /**
     * Searchable module picker - one select2 for every "which module" field in the portal,
     * now that there are hundreds of modules.
     *
     * attrs.name      - form field name (required)
     * attrs.id        - element id, defaults to name
     * attrs.value     - current module name
     * attrs.from      - module names to offer; defaults to every PortalModule
     * attrs.allOption - label of an extra first option submitted as-is (the list filters' 'All')
     * attrs.noSelection - label of a blank first option (submits '')
     * attrs.allowNew  - true lets the user type a name not in the list (select2 tags)
     * attrs.class / attrs.style / attrs.width
     *
     * Options read "name - title" so people can search by either. The current value is always
     * offered, even when it is not in 'from', so opening a record can never silently switch
     * its module to the first entry.
     */
    def moduleSelect = { attrs ->
        def fname = attrs.name?.toString()
        if(!fname) return
        def fid = (attrs.id ?: fname).toString()
        def value = attrs.value?.toString()
        def names = (attrs.from != null ? attrs.from : PortalModule.executeQuery('select m.name from PortalModule m'))
        names = names.findAll { it }.collect { it.toString() }.unique().sort { it.toLowerCase() }
        def titles = PortalModule.executeQuery('select m.name, m.title from PortalModule m where m.title is not null')
                                 .collectEntries { [(it[0]): it[1]] }
        def enc = { v -> v == null ? '' : v.toString().encodeAsHTML() }
        def opt = { String v, String label ->
            "<option value='${enc(v)}'${v == value ? ' selected' : ''}>${enc(label)}</option>"
        }
        out << "<select name='${enc(fname)}' id='${enc(fid)}' class='module-select ${enc(attrs.class ?: '')}'" +
               (attrs.style ? " style='${enc(attrs.style)}'" : '') + ">"
        if(attrs.noSelection != null) out << opt('', attrs.noSelection.toString())
        if(attrs.allOption) out << opt(attrs.allOption.toString(), attrs.allOption.toString())
        if(value && !(value in names) && value != attrs.allOption?.toString()) out << opt(value, value)
        names.each { n -> out << opt(n, titles[n] ? (n + ' \u2014 ' + titles[n]) : n) }
        out << "</select>"
        out << asset.script() {
            """\$('#${fid}').select2({
                width: '${attrs.width ?: 'resolve'}',
                dropdownAutoWidth: true${attrs.allowNew ? ', tags: true' : ''}
            });"""
        }
    }

    /**
     * Searchable dropdown whose options come from a PortalSetting holding one value per line
     * (a Text setting, so values may contain commas - an Array setting would split them).
     * Falls back to a plain text box when the setting is missing or empty, so a portal that
     * has not configured the list still works.
     *
     * attrs.name, attrs.id, attrs.value - as usual
     * attrs.setting   - 'module.name' of the setting, e.g. 'portal.module_categories'
     * attrs.maxlength - for the text-box fallback
     */
    def choiceSelect = { attrs ->
        def fname = attrs.name?.toString()
        if(!fname) return
        def fid = (attrs.id ?: fname).toString()
        def value = attrs.value?.toString()
        def enc = { v -> v == null ? '' : v.toString().encodeAsHTML() }
        def choices = PortalSetting.lines(attrs.setting?.toString())
        if(!choices) {
            out << "<input type='text' name='${enc(fname)}' id='${enc(fid)}' value='${enc(value)}'" +
                   (attrs.maxlength ? " maxlength='${enc(attrs.maxlength)}'" : '') + "/>"
            return
        }
        out << "<select name='${enc(fname)}' id='${enc(fid)}'>"
        out << "<option value=''></option>"
        // a stored value that has since left the list stays selectable, marked, so saving the
        // form unchanged does not blank it
        if(value && !(value in choices)) {
            out << "<option value='${enc(value)}' selected>${enc(value)} (not in list)</option>"
        }
        choices.each { c -> out << "<option value='${enc(c)}'${c == value ? ' selected' : ''}>${enc(c)}</option>" }
        out << "</select>"
        out << asset.script() {
            """\$('#${fid}').select2({ width: '${attrs.width ?: '40%'}', dropdownAutoWidth: true, allowClear: true, placeholder: '' });"""
        }
    }

    def continueparams = { attrs->
        def notcontinue = ['action','controller']
        if(attrs.notcontinue){
            notcontinue += attrs.notcontinue
        }
        params.each { dkey,dval->
            try {                          
                if(!(dkey in notcontinue) && (dkey!=dkey.toUpperCase())){
                    out << hiddenField(name:dkey,value:dval)
                }
            }
            catch(Exception e){
                println 'continue params got error:' + e
            }
        }
    }

    def picture_file = { attrs->
        def module = 'portal'
        if(attrs.module) {
            module = attrs.module
        }
        if(attrs.thumbsize){
            out << createLink(controller:'fileLink',action:'download',params:[module:module,slug:attrs.slug,thumbsize:attrs.thumbsize])
        }
        else{
            out << createLink(controller:'fileLink',action:'download',params:[module:module,slug:attrs.slug])
        }
    }

    def stream_file = { attrs->
        def module = 'portal'
        if(attrs.module) {
            module = attrs.module
        }
        out << createLink(controller:'fileLink',action:'stream',params:[module:module,slug:attrs.slug])
    }

}
