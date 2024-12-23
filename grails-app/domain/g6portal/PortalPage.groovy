package g6portal

import org.grails.gsp.*
import groovy.text.Template

class PortalPage {

    static hasMany = [datasources:PortalPageData]

    transient sessionFactory
    transient mailService

    static constraints = {
        title()
        slug()
        module(nullable:true)
        published(nullable:true)
        requirelogin(nullable:true)
        allowedroles(nullable:true)
        runable(nullable:true)
        redirectafter(nullable:true)
        fullpage(nullable:true)
        render(nullable:true,inList:['Default','HTML','XML','JSON','File','XLSX'])
        lastUpdated(nullable:true)
        side_menu(nullable:true)
        content(nullable:true,widget:'textarea',maxSize:500000)
        preprocess(nullable:true,widget:'textarea',maxSize:500000)
    }

    static mapping = {
        content type: 'text'
        preprocess type: 'text'
        title type: 'text'
        datasources cascade: "all-delete-orphan"
        cache true
    }

    String title
    String slug
    String content
    String preprocess
    String allowedroles
    String module
    String side_menu
    Boolean requirelogin = true
    Boolean published = false
    Boolean runable = false
    Boolean fullpage = false
    String render
    String redirectafter
    Date lastUpdated

    String toString() {
        return title
    }

    def evaltitle(args){
        def bodyreturn = null
        try{
            def bodyoutput = new StringWriter()
            def location = "pagetitle_" + this.id + this.lastUpdated
            def groovyPagesTemplateEngine = new GroovyPagesTemplateEngine() 
            groovyPagesTemplateEngine.afterPropertiesSet()
            Template template = groovyPagesTemplateEngine.createTemplate(this.title,location)
            template.make(args).writeTo(bodyoutput)
            bodyreturn = bodyoutput.toString()
        }
        catch(Exception e){
            println 'There was an error in page evaltitle ' + this + ' eval: ' + this.title + ':' + e
            def emailreporterror = PortalSetting.findByName("emailpageerror")
            if(emailreporterror){
                mailService.sendMail {
                    to emailreporterror.value().trim()
                    subject "Page Content Error"
                    body '''There was an error in page evaltitle ' + this + ' prosessing title eval: ' + this.title + ':' + e + '''
                }
            }
            PortalErrorLog.record(args,null,'page','evaltitle',e.toString())
            return ''
        }
        return bodyreturn
    }

    def evalcontent(args){
        def bodyreturn = null
        try{
            def bodyoutput = new StringWriter()
            def location = "page_" + this.id + this.lastUpdated
            def groovyPagesTemplateEngine = new GroovyPagesTemplateEngine() 
            groovyPagesTemplateEngine.afterPropertiesSet()
            Template template = groovyPagesTemplateEngine.createTemplate(this.content,location)
            template.make(args).writeTo(bodyoutput)
            bodyreturn = bodyoutput.toString()
        }
        catch(Exception e){
            println 'There was an error in page evalcontent ' + this + ' eval: ' + this.content + ':' + e
            def emailreporterror = PortalSetting.findByName("emailpageerror")
            if(emailreporterror){
                mailService.sendMail {
                    to emailreporterror.value().trim()
                    subject "Page Content Error"
                    body '''There was an error in page evalcontent ${this} prosessing content eval: ${this.content} : ${e}'''
                }
            }
            PortalErrorLog.record(args,null,'page','evalcontent',e.toString())
            return ''
        }
        return bodyreturn
    }

    def runcontent(args) {
        Binding binding = new Binding()
        binding.setVariable("args",args)
        def shell = new GroovyShell(this.class.classLoader,binding)
        def results = shell.evaluate(this.content)
        return results
    }
}
