package g6portal

import org.grails.gsp.*
import groovy.text.Template
import grails.plugins.mail.MailService

class PortalTrackerRole {

    // transient GroovyPagesTemplateEngine groovyPagesTemplateEngine
    transient MailService mailService
    transient PortalService portalService

    static belongsTo = [tracker:PortalTracker]

    static constraints = {
        name()
        tracker()
        role_type(inList:["User Role","Data Compare"])
        role_rule(nullable:true,widget:'textarea')
        role_desc(nullable:true,widget:'textarea')
        lastUpdated(nullable:true)
    }

    PortalTracker tracker
    String name
    String role_type
    String role_rule
    String role_desc
    Date lastUpdated

    String toString() {
        return name
    }

    static mapping = {
        role_rule type: 'text'
        cache true
    }

    def evalrole(curuser,datas){
        def toeval = this.role_rule
        def output = new StringWriter()
        def toreturn = null
        try{
            def location = "rule_" + this.id + this.lastUpdated
            def gpte = new GroovyPagesTemplateEngine()
            gpte.afterPropertiesSet()

            Template template = gpte.createTemplate(toeval,location)
            template.make([curuser:curuser,datas:datas,portalService:portalService]).writeTo(output)                
            toreturn = output.toString()
            return toreturn
        }
        catch(Exception e){
            def errormsg = 'There was an error in role rule ' + this + ' prosessing object ' + curuser + ' in tracker:' + this.tracker + ' eval: ' + toeval + ':' + e
            println("error:" + errormsg)
            def emailreporterror = PortalSetting.findByName("emailroleruleerror")
            if(emailreporterror){
                mailService.sendMail {
                    to emailreporterror.value().trim()
                    subject "Role Rule Error"
                    body '''There was an error in role rule ' + this + ' prosessing object ' + curuser + ' eval: ' + toeval + ':' + e
Params: ''' + params
                }
            }
            PortalErrorLog.record(datas,curuser,'trackerRole','evalrole',e.toString() + '<br/>' + errormsg)
            return ''
        }
    }
}
