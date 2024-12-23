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
}
