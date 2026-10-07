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
        tracker_data_id(nullable:true)
        tracker_id(nullable:true)
        size(nullable:true)
    }

    String name
    String slug
    String path
    String allowedroles
    String module
    String filegroup
    Integer sortnum
    Integer tracker_data_id
    Integer tracker_id
    Integer size

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

    def beforeInsert = {
        updateFileSize()
    }

    def beforeUpdate = {
        updateFileSize()
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
        if(legacyGroups && filelink.filegroup && !filelink.tracker_id && session.userid) {
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

    private void updateFileSize() {
        if (path && !size) {
            def thefile = new File(path)
            if (thefile.exists()) {
                size = (int) thefile.length()
            }
        }
    }
}
