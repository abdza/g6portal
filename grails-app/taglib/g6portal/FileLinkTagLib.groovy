package g6portal

class FileLinkTagLib {
    static defaultEncodeAs = [taglib:'html']
    // static returnObjectForTags = ['content']
    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]
    static encodeAsForTags = [ filelink_link: [taglib:'none'], file_not_exists: [taglib:'none'], file_exists: [taglib:'none'],
                               filelink: [taglib:'none'], filelist: [taglib:'none']]

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
            def module = null
            if(attrs.slug) {
                def parts = attrs.slug.tokenize('.')
                if(parts.size()>1) {
                    slug = parts[1]
                    module = parts[0]
                }
                else {
                    slug = attrs.slug
                }
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

    // ------------------------------------------------------------------ ported from the old portal
    // <g:filelink> and <g:filelist> keep the old portal's attributes so its page content runs
    // unchanged. A file is shown only when FileLink.readable() lets the viewer download it, so a
    // listed link never answers 401.

    /** True when the file is readable by this session and present on disk. */
    private boolean showable(FileLink file) {
        return file && FileLink.readable(file, session)
    }

    private static boolean ondisk(FileLink file) {
        return file?.path && new File(file.path).exists()
    }

    /**
     * Download link for one file, named by its original filename.
     *   id        FileLink id, or
     *   slug      FileLink slug (unique, so no module is needed)
     *   dispname  when the file is missing on disk, still print its name (no link)
     * <g:filelink slug="annual_report" dispname="true"/>
     */
    def filelink = { attrs ->
        def file = null
        if(attrs.id) {
            file = FileLink.get(attrs.id.toString().isLong() ? attrs.id.toString().toLong() : null)
        }
        else if(attrs.slug) {
            file = FileLink.findBySlug(attrs.slug.toString(), [cache: true])
        }
        if(!showable(file)) return
        if(ondisk(file)) {
            out << link(controller: 'fileLink', action: 'download', params: [id: file.id]) { file.name?.encodeAsHTML() }
        }
        else if(attrs.dispname) {
            out << file.name?.encodeAsHTML()
        }
    }

    /**
     * List of download links for every file in a file group, in sortnum then upload order.
     *   group       FileLink.filegroup (required)
     *   dispname    when a file is missing on disk, still list its name (no link)
     *   numberlist  render an <ol> instead of a <ul>
     * Renders <ul class="filelist {group}_filegroup">; prints nothing when the group is empty.
     * <g:filelist group="rdscorecard2026" dispname="true"/>
     */
    def filelist = { attrs ->
        if(!attrs.group) return
        def group = attrs.group.toString().trim()
        def files = FileLink.findAllByFilegroup(group, [cache: true]).sort { a, b ->
            (a.sortnum ?: 0) <=> (b.sortnum ?: 0) ?: a.id <=> b.id
        }
        if(!files) return
        def tag = attrs.numberlist ? 'ol' : 'ul'
        out << "<${tag} class='filelist ${group.replaceAll(/[^A-Za-z0-9_-]/, '_')}_filegroup'>"
        files.each { file ->
            if(!showable(file)) return
            if(ondisk(file)) {
                out << '<li>'
                out << link(controller: 'fileLink', action: 'download', params: [id: file.id]) { file.name?.encodeAsHTML() }
                out << '</li>'
            }
            else if(attrs.dispname) {
                out << '<li>' << file.name?.encodeAsHTML() << '</li>'
            }
        }
        out << "</${tag}>"
    }
}
