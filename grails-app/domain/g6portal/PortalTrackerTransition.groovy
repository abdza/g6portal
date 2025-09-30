package g6portal

import grails.plugins.mail.MailService

class PortalTrackerTransition {

    transient MailService mailService

    static belongsTo = [tracker:PortalTracker]

    static hasMany = [emails:PortalTrackerEmail,prev_status:PortalTrackerStatus,roles:PortalTrackerRole]

    static constraints = {
      name()
      tracker()
      roles(nullable:true)
      editfields(nullable:true)
      displayfields(nullable:true)
      requiredfields(nullable:true)
      richtextfields(nullable:true)
      prev_status(nullable:true)
      same_status(nullable:true)
      next_status(nullable:true)
      enabledcondition(nullable:true)
      updatetrails(nullable:true,widget:'textarea')
      postprocess(nullable:true)
      redirect_after(nullable:true)
      gotoprevstatuslist(nullable:true)
      submitbuttontext(nullable:true)
      cancelbuttontext(nullable:true)
      display_name(nullable:true)
      cancelbutton(nullable:true)
      immediate_submission(nullable:true)
    }

    static mapping = {
      displayfields type: 'text'
      editfields type: 'text'
      requiredfields type: 'text'
      richtextfields type: 'text'
      enabledcondition type: 'text'
      updatetrails type: 'text'
      emails(cascade: "all-delete-orphan")
      cache true
    }

    String name
    String display_name
    PortalTracker tracker
    String editfields
    String displayfields
    String requiredfields
    String richtextfields
    String enabledcondition
    String updatetrails
    String submitbuttontext
    String cancelbuttontext
    String redirect_after
    Boolean gotoprevstatuslist
    Boolean same_status
    Boolean cancelbutton
    Boolean immediate_submission
    PortalPage postprocess
    PortalTrackerStatus next_status

    String toString() {
      if(display_name){
          return display_name
      }
      return name
    }

    def sendemails(params,session,sql,groovyPagesTemplateEngine=null,portalService=null) {
        if(!params.id){
            params.id = sql.firstRow("select top 1 id from " + tracker.data_table() + " order by id desc")[0]
        }

        def curdatas = sql.firstRow("select * from " + tracker.data_table() + " where id=" + params.id)

        Binding binding = new Binding()
        binding.setVariable("session",session)
        binding.setVariable("datas",curdatas)
        binding.setVariable("portalService",portalService)
        def shell = new GroovyShell(this.class.classLoader,binding)
        emails.each { email->
            def toccs = null
            def tosend = shell.evaluate(email.emailto)
            if(email.emailcc){
                toccs = shell.evaluate(email.emailcc)
            }
            def emailcontent = email.evalbody(curdatas,groovyPagesTemplateEngine,portalService)
            try {
                mailService.sendMail {
                    to tosend
                    if(toccs){
                        cc toccs
                    }
                    subject emailcontent['title']
                    html emailcontent['body']
                }
            }
            catch(Exception e){
                println 'Error with sending email ' + email?.body?.title + ' : ' + e.toString()
                def emailpagerror = PortalSetting.findByName("emailpagerror")
                if(emailpagerror){
                    try {
                        mailService.sendMail {
                            to emailpagerror.value().trim()
                            subject "Page Error"
                            body 'Error with sending email ' + email?.body?.title + ' : ' + e.toString() + '''
    Params: ''' + params
                        }
                    }
                    catch(Exception f){
                        println 'Error with sending error email ' + email?.body?.title + ' : ' + f.toString()
                    }
                }
            }
        }
    }

    def testenabled(session,datas){
      if(datas!=null && this.tracker.initial_status==this.next_status && this.prev_status.size()==0) {
          return false
      }
      if(this.roles) {
          if(session) {
              def curuser = session.curuser
              def foundrole = false
              if(curuser) {
                  def userroles = this.tracker.user_roles(curuser,datas)
                  if(userroles.size()){
                      userroles.each { urole->
                          if(urole in this.roles) {
                              foundrole = true
                          }
                      }
                  }
              }
              if(!foundrole) {
                  return foundrole
              }
          }
      }
      if(!enabledcondition){
          return true
      }
      Binding binding = new Binding()
      binding.setVariable("session",session)
      binding.setVariable("datas",datas)
      try {
        def shell = new GroovyShell(this.class.classLoader,binding)
        def toret = shell.evaluate(enabledcondition)
        return toret
      }
      catch(Exception f) {
          println "Error updating trail: " + f
          PortalErrorLog.record(null,null,'trackertransition','testenabled',f.toString())
      }
    }

    def updatetrail(session,datas,portalService=null){
        if(!updatetrails){
            return false
        }
        Binding binding = new Binding()
        binding.setVariable("session",session)
        binding.setVariable("datas",datas)
        binding.setVariable("portalService",portalService)
        try {
            def shell = new GroovyShell(this.class.classLoader,binding)
            return shell.evaluate(updatetrails)
        }
        catch(Exception f) {
            println "Error updating trail: " + f
            PortalErrorLog.record(null,null,'trackertransition','updatetrail',f.toString())
        }
    }
}
