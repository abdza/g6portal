package g6portal

import org.grails.gsp.*
import groovy.text.Template
import grails.plugins.mail.MailService

class PortalTrackerEmail {

    transient MailService mailService

    static belongsTo = [tracker:PortalTracker,transition:PortalTrackerTransition,status:PortalTrackerStatus]

    static constraints = {
        transition(nullable:true)
        status(nullable:true)
        tracker(nullable:true)
        name()
        emailto(widget:'textarea')
        emailcc(nullable:true,widget:'textarea')
        body(nullable:true)
    }

    static mapping = {
        emailto type: 'text'
        emailcc type: 'text'
        cache true
    }

    PortalTrackerTransition transition
    PortalTrackerStatus status
    PortalTracker tracker
    String name
    String emailto
    String emailcc
    PortalPage body

    String toString() {
        return name 
    }

    def evalbody(datas,groovyPagesTemplateEngine){
        def toeval = this.body?.content
        def bodyoutput = new StringWriter()
        def titleoutput = new StringWriter()
        def bodyreturn = null
        try{
            def lastdate = 	new java.text.SimpleDateFormat('yyyyMMddHHmmss').format(this.body?.lastUpdated)  
            def location = "page_" + this.id + lastdate
            Template template = groovyPagesTemplateEngine.createTemplate(toeval,location)
            template.make([datas:datas]).writeTo(bodyoutput)                
            bodyreturn = bodyoutput.toString()
        }
        catch(Exception e){
            println 'There was an error in role page ' + this + ' eval: ' + toeval + ':' + e
            def emailreporterror = PortalSetting.findByName("emailrolepageerror")
            if(emailreporterror){
                mailService.sendMail {
                    to emailreporterror.value().trim()
                    subject "Role Rule Error"
                    body '''There was an error in role page ' + this + ' prosessing object eval: ' + toeval + ':' + e
Params: ''' + params
                }
            }
            PortalErrorLog.record(datas,null,'trackerEmail','evalbody',e.toString(),this.transition.tracker.slug)
            return ''
        }

        toeval = this.body?.title
        def titlereturn = null
        try{
            def lastdate = 	new java.text.SimpleDateFormat('yyyyMMddHHmmss').format(this.body?.lastUpdated)  
            def location = "title_" + this.id + lastdate
            Template template = groovyPagesTemplateEngine.createTemplate(toeval,location)
            template.make([datas:datas]).writeTo(titleoutput)                
            titlereturn = titleoutput.toString()
        }
        catch(Exception e){
            println 'There was an error in role page ' + this + ' eval: ' + toeval + ':' + e
            def emailreporterror = PortalSetting.findByName("emailrolepageerror")
            if(emailreporterror){
                mailService.sendMail {
                    to emailreporterror.value().trim()
                    subject "Role Rule Error"
                    body '''There was an error in role page ' + this + ' prosessing object eval: ' + toeval + ':' + e
Params: ''' + params
                }
            }
            PortalErrorLog.record(datas,null,'trackerEmail','evalbody',e.toString(),this.transition.tracker.slug)
            return ''
        }
        return ['title':titlereturn,'body':bodyreturn]
    }
}
