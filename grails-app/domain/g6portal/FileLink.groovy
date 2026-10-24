package g6portal

class FileLink {

    static constraints = {
        name(nullable:true)
        module(nullable:true)
        slug(nullable:true,unique:true)
        allowedroles(nullable:true)
        filegroup(nullable:true)
        sortnum(nullable:true)
        path(nullable:true)
        tracker_slug(nullable:true)
        tracker_data_id(nullable:true)
        size(nullable:true)
        last_updated(nullable:true, bindable:false)   // set by the hooks below, never from a form
    }

    String name
    String slug
    String path
    String allowedroles
    String module
    String filegroup
    Integer sortnum
    // The tracker is found by module + tracker_slug, never by its id: a module import deletes
    // and recreates every tracker, so ids do not survive it.
    String tracker_slug
    Integer tracker_data_id
    Integer size
    Date last_updated           // When the file was uploaded or last replaced (see beforeInsert)

    static mapping = {
        sort 'sortnum'
        cache true
    }

    String toString() {
        name
    }

    def beforeDelete = {
        def thefile = new File(path)
        if(thefile.exists()){
            thefile.delete()
        }
    }

    Boolean exists() {
        def thefile = new File(path)
        return thefile.exists()
    }

    def module_roles(curuser=null){
        if(curuser){
            def mroles = curuser.modulerole(this.module)
            return mroles
        }
        else{
            return []
        }
    }

    def base64() {
        def thefile = new File(this.path)
        if(thefile.exists()){
            byte[] binaryContent = thefile.bytes
            return binaryContent.encodeBase64().toString()
        }
        return ""
    }

    /**
     * last_updated records when the file itself arrived, so a module's growth can be measured
     * over time.
     *
     * Deliberately not GORM's autoTimestamp lastUpdated: that changes on every save, and saves
     * that have nothing to do with the upload (the size backfill, the tracker_slug backfill)
     * would move millions of rows to the day they ran. Only a new row, or a new file
     * (path or name changed), stamps it. A caller that already knows the date - e.g. a file
     * registered from disk - sets it first and is left alone.
     */
    def beforeInsert() {
        if(!last_updated) {
            last_updated = new Date()
        }
        // The framework's upload paths set size themselves; module pages that create a
        // FileLink often do not.
        if(size == null) {
            size = sizeondisk()
        }
    }

    def beforeUpdate() {
        if(isDirty('path') || isDirty('name')) {
            last_updated = new Date()
        }
        if((isDirty('path') || isDirty('last_updated')) && !isDirty('size')) {
            size = sizeondisk()
        }
    }

    /**
     * Size of the file at path, or null when it is not there or too big for the int column -
     * null reads as "not measured", which is better than a wrong number.
     */
    private Integer sizeondisk() {
        try {
            def thefile = path ? new File(path) : null
            if(thefile?.isFile() && thefile.length() <= Integer.MAX_VALUE) {
                return (int) thefile.length()
            }
        }
        catch(Exception e) {
        }
        return null
    }

    /**
     * The access rule for reading a stored file: whether the given HTTP session may download
     * or stream it. Shared by FileLinkController (download, stream) and the file tags
     * (filelink, filelist) so a listed link never answers 401.
     *
     * @param filelink - the FileLink being served
     * @param session - the current HTTP session (userid, curuser, enablesuperuser, adminmodules)
     * @return boolean - true when the session may read this file
     */
    static boolean readable(FileLink filelink, session) {
        if(!filelink) return false

        // Files published into a file group and not attached to a tracker are page documents
        // (the old portal's <g:filelist>/<g:filelink>), not uploads or record attachments. With
        // portal.legacy_filegroup_access on they keep the old portal's rule for logged-in users:
        // unrestricted means everyone; restricted admits the module's Admins as well as the
        // listed roles. OFF by default here - turning it on opens every such file to every
        // logged-in user. Anonymous access, and anything not granted here, falls through to the
        // rule below.
        def legacyGroups = PortalSetting.namedefault('portal.legacy_filegroup_access', false)
        legacyGroups = (legacyGroups in [true, 'true', 1, '1', 'on'])
        if(legacyGroups && filelink.filegroup && !filelink.tracker_slug && session.userid) {
            if(!filelink.allowedroles?.trim()) return true
            def curuser = session.curuser
            if(curuser && filelink.module && 'Admin' in curuser.modulerole(filelink.module)) return true
        }

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
            def tracker = hasAccess ? null : filelink.tracker()
            if(!hasAccess && tracker && session.curuser) {
                def recordDatas = filelink.tracker_data_id ? tracker.firstRow(['id': filelink.tracker_data_id]) : null
                hasAccess = tracker.user_roles(session.curuser, recordDatas).size() > 0
            }
            // Fallback when the tracker cannot be resolved: a trail attachment that lacks
            // tracker_slug, or one whose tracker has since been removed. Look up by module.
            // tracker_data_id is only unique within its own tracker, so it is used here only when
            // tracker_slug is unknown - never against a different tracker than the one named.
            if(!hasAccess && !tracker && filelink.module && session.curuser) {
                def trackers = PortalTracker.findAllByModule(filelink.module)
                for(def t : trackers) {
                    def recordDatas = (filelink.tracker_data_id && !filelink.tracker_slug) ? t.firstRow(['id': filelink.tracker_data_id]) : null
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
     * One-off carry-over for databases from before tracker_slug: give every attachment that
     * still has only the old tracker_id column the slug of that tracker, when the tracker is
     * alive and in the file's own module (an id from a dead or foreign tracker is not trusted).
     * dbCreate:update never drops the old column, so the ids are still there to read. Only rows
     * whose tracker_slug is null are touched, so it is safe on every startup and does nothing on
     * a database that never had tracker_id. Returns the number of rows linked.
     */
    static int backfillTrackerSlug(javax.sql.DataSource dataSource) {
        def sql = new groovy.sql.Sql(dataSource)
        try {
            if(!PortalTracker.tableExists(sql, 'file_link') || !PortalTracker.columnExists(sql, 'file_link', 'tracker_id')) {
                return 0
            }
            def match = "select t.slug from portal_tracker t where t.id = file_link.tracker_id and t.module = file_link.module"
            def linked = sql.executeUpdate("update file_link set tracker_slug = (" + match + ") " +
                                           "where tracker_slug is null and tracker_id is not null and exists (" + match + ")")
            if(linked) {
                println "FileLink: linked ${linked} attachment(s) to their tracker by slug (from tracker_id)"
            }
            return linked
        }
        catch(Exception e) {
            println "FileLink: tracker_slug backfill skipped: " + e
            return 0
        }
        finally {
            sql.close()
        }
    }

    /**
     * The tracker this file is attached to, by module + tracker_slug
     * @return PortalTracker - or null when the file is not a tracker attachment
     */
    PortalTracker tracker() {
        if(!module || !tracker_slug) return null
        return PortalTracker.findByModuleAndSlug(module, tracker_slug)
    }

    /**
     * Formats a byte count for display, e.g. 1536 -> "1.5 KB"
     * Takes a Number so it works with both the Integer size property and the
     * Long that a sum() projection returns.
     * @param bytes - byte count, may be null
     * @return String - human readable size, "0 B" when null or negative
     */
    static String humanSize(Number bytes) {
        if (bytes == null) {
            return '0 B'
        }
        double val = bytes.doubleValue()
        if (val < 1) {
            return '0 B'
        }
        def units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
        int unit = 0
        while (val >= 1024 && unit < units.size() - 1) {
            val = val / 1024
            unit++
        }
        // whole numbers for bytes, one decimal place from KB upward
        return (unit == 0 ? "${(long) val}" : String.format('%.1f', val)) + ' ' + units[unit]
    }
}
