package g6portal

class PortalTagLib {

    static defaultEncodeAs = 'html'
    static encodeAsForTags = [fmbreadcrumbs: 'raw',rolelist: 'raw',ifnotrole: 'raw',ifrole: 'raw',user_selector: 'raw',continueparams: 'raw',hashlink: 'raw',createHashLink:'raw'] 
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
                    output += "value:" + attrs.value + " , "
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
        """
        out << output
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
