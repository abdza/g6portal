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
      module(nullable:true)
      slug(nullable:true)
      duration_ms(nullable:true)
      cpu_ms(nullable:true)
      status(nullable:true)
      error(nullable:true,maxSize:1024)
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
    // Performance columns, filled in by PortalAuditWriter.finishFrame. module and slug come
    // from the request params, so POSTs (whose params are not stored) can still be grouped by
    // page/tracker. duration_ms is wall-clock time including view rendering; cpu_ms is CPU used
    // by the request thread, so duration_ms - cpu_ms is mostly time waiting on the database.
    String module
    String slug
    Long duration_ms
    Long cpu_ms
    Integer status
    String error
}
