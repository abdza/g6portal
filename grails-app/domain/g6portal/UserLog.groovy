package g6portal

class UserLog {

    static constraints = {
        user(nullable:true)
        targetuser(nullable:true)
        date(nullable:true)
        ipaddr(nullable:true)
        category(nullable:true)
        description(nullable:true,maxSize:1024)
    }

    String ipaddr
    String category
    String description
    User user
    User targetuser
    Date date

    static UserLog record(targetuser,category,description=null,curuser=null) {
        def violationlog = new UserLog()
        violationlog.date = new Date()
        violationlog.user = curuser
        violationlog.targetuser = targetuser
        violationlog.category = category
        violationlog.description = description
// Need to get the read remote ip
        violationlog.ipaddr = '0.0.0.0'
        violationlog.save()
        return violationlog
    }
}
