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
        // nullable so the column can be added to existing rows; null means "before we tracked it"
        lastUpdated(nullable:true)
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
    // Bounds the missed-run check: a slot from before the schedule was last edited was due
    // under the old settings, so it is not reported as missed under the new ones. lastrun is
    // therefore written with a bulk update, which does not touch this.
    Date lastUpdated

    /**
     * One of hour / day_of_week / day_of_month against a value: '*' matches anything, else a
     * comma list compared exactly. Null or blank never matches - the same as run() has always
     * treated them. day_of_week is Date.getDay(), 0 = Sunday.
     */
    static boolean fieldMatches(String spec, int value) {
        if(spec == null || !spec.trim()) return false
        if(spec.trim() == '*') return true
        return spec.tokenize(',').any { it.trim() == value.toString() }
    }

    boolean isDue(Date when) {
        if(!enabled) return false
        def c = Calendar.getInstance()
        c.setTime(when)
        return fieldMatches(hour_of_day, c.get(Calendar.HOUR_OF_DAY)) &&
               fieldMatches(day_of_week, c.get(Calendar.DAY_OF_WEEK) - 1) &&
               fieldMatches(day_of_month, c.get(Calendar.DAY_OF_MONTH))
    }

    static Date truncateToHour(Date d) {
        def c = Calendar.getInstance()
        c.setTime(d)
        c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.getTime()
    }

    /** Hour slots in [from, to) at which this schedule was due, oldest first. */
    List<Date> dueSlots(Date from, Date to) {
        def slots = []
        def c = Calendar.getInstance()
        c.setTime(truncateToHour(from))
        if(c.getTime() < from) c.add(Calendar.HOUR_OF_DAY, 1)
        while(c.getTime() < to) {
            if(isDue(c.getTime())) slots << c.getTime()
            c.add(Calendar.HOUR_OF_DAY, 1)
        }
        return slots
    }

    /** The next hour slot at or after `from` this schedule is due, looking up to ~2 months ahead. */
    Date nextDue(Date from) {
        if(!enabled) return null
        def c = Calendar.getInstance()
        c.setTime(truncateToHour(from))
        if(c.getTime() < from) c.add(Calendar.HOUR_OF_DAY, 1)
        for(int i = 0; i < 24 * 62; i++) {
            if(isDue(c.getTime())) return c.getTime()
            c.add(Calendar.HOUR_OF_DAY, 1)
        }
        return null
    }
}
