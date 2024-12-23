package g6portal

class PortalAuditTrail {

    static constraints = {
      controller(nullable:true)
      action(nullable:true)
      params(nullable:true,maxSize:1024)
      user_id(nullable:true)
      date(nullable:true)
      useragent(nullable:true)
      ipaddr(nullable:true)
      uri(nullable:true)
      realuser_id(nullable:true)
    }

    String controller
    String action
    String params
    String useragent
    String ipaddr
    String uri
    Integer user_id
    Integer realuser_id
    Date date
}
