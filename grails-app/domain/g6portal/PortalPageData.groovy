package g6portal

class PortalPageData {

    static belongsTo = [page:PortalPage]

    static constraints = {
        name()
        return_one(nullable:true)
        query(nullable:true,widget:'textarea',maxSize:5000)
    }

    String query
    String name
    Boolean return_one = false
    
    static mapping = {
        query type: 'text'
        cache true
    }
}
