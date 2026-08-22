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
