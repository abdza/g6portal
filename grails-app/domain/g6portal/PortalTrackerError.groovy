package g6portal

import groovy.sql.Sql
import static grails.util.Holders.config

class PortalTrackerError {

    static belongsTo = [field:PortalTrackerField]

    static constraints = {
        field()
        description(nullable:true)
        error_type(inList:['Unique','E-mail','Format','Not Empty','Custom'])
        format(nullable:true)
        error_msg(nullable:true,widget:'textarea')
        error_function(nullable:true,widget:'textarea')
        allow_submission(nullable:true)
    }

    static mapping = {
        error_msg type: 'text'
        error_function type: 'text'
    }

    String description
    String error_type
    String format
    String error_msg
    String error_function
    Boolean allow_submission

    String toString() {
        if(description) {
            return description
        }
        else {
            return error_type
        }
    }
    
}
