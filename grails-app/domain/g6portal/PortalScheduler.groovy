package g6portal

import groovy.sql.Sql
import org.springframework.transaction.annotation.Transactional
import grails.plugins.mail.MailService

class PortalScheduler {

    static constraints = {
        name()
        module()
        slugs(nullable:true,widget:'textarea',maxSize:500000)
        hour_of_day(nullable:true)
        day_of_week(nullable:true)
        day_of_month(nullable:true)
        lastrun(nullable:true)
        enabled(nullable:true)
    }

    static mapping = {
        slugs type: 'text'
        cache true
    }

    transient MailService mailService
    String module
    String name
    String slugs
    String hour_of_day
    String day_of_week
    String day_of_month
    Boolean enabled
    Date lastrun
}
