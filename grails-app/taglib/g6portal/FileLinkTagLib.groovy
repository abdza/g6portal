package g6portal

class FileLinkTagLib {
    static defaultEncodeAs = [taglib:'html']
    // static returnObjectForTags = ['content']
    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]
    static encodeAsForTags = [ filelink_link: [taglib:'none'], file_not_exists: [taglib:'none'], file_exists: [taglib:'none']]

    def file_not_exists = { attrs,body ->
        def slug = ''
        def module = ''
        if(attrs.slug) {
            slug = attrs.slug
        }
        if(attrs.module) {
            module = attrs.module
        }
        else {
            module = 'portal'
        }
        def fl = g6portal.FileLink.findBySlugAndModule(slug,module)
        if(fl && fl.exists()) {
            out << ''
        }
        else {
            out << body()
        }
    }

    def file_exists = { attrs,body ->
        def slug = ''
        def module = ''
        if(attrs.slug) {
            slug = attrs.slug
        }
        if(attrs.module) {
            module = attrs.module
        }
        else {
            module = 'portal'
        }
        def fl = g6portal.FileLink.findBySlugAndModule(slug,module)
        if(fl && fl.exists()) {
            out << body()
        }
        else {
            out << ''
        }
    }

    def download_file = { attrs->
        if(attrs.id) {
            out << createLink(controller:'fileLink',action:'download',params:[id:attrs.id])
        }
        else {
            def slug = ''
            def module = ''
            if(attrs.slug) {
                slug = attrs.slug
            }
            if(attrs.module) {
                module = attrs.module
            }
            else {
                module = 'portal'
            }
            out << createLink(controller:'fileLink',action:'download',params:[slug:slug,module:module])
        }
    }

    def filelink_link = { attrs->
        if(attrs.id) {
            def dfile = FileLink.get(attrs.id)
            if(dfile) {
                out << link(controller:'FileLink',action:'download',params:[id:attrs.id]) { dfile.name }
            }
        }
        else {
            def slug = ''
            def module = ''
            if(attrs.slug) {
                slug = attrs.slug
            }
            if(attrs.module) {
                module = attrs.module
            }
            else {
                module = 'portal'
            }
            def dfile = FileLink.findByModuleAndSlug(module,slug)
            if(dfile) {
                out << link(controller:'FileLink',action:'download',params:[slug:slug,module:module]) { dfile.name }
            }
        }
    }
}
