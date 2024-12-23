package g6portal

import grails.plugins.mail.MailService
import grails.validation.ValidationException


class PortalEmail {

    transient MailService mailService

    static constraints = {
        title()
        module(nullable:true)
        emailto(widget:'textarea')
        emailcc(nullable:true,widget:'textarea')
        body(nullable:true,widget:'textarea')
        deliveryTime(nullable:true)
        emailSent(nullable:true)
        timeSent(nullable:true)
    }

    static mapping = {
        emailto type: 'text'
        emailcc type: 'text'
        body type: 'text'
        sort deliveryTime: "desc"
        cache true
    }

    String title
    String module
    String emailto
    String emailcc
    String body
    Date deliveryTime
    Date timeSent
    Boolean emailSent

    String toString() {
        return title 
    }

    def send(mailService) {
      println "In send email service"
      def emailfrom = PortalSetting.namedefault("portal.emailfrom","portal@portal.com")
      def sendto = this.emailto?.tokenize(',')
      def ccto = this.emailcc?.tokenize(',')
      try {
        mailService.sendMail {
            to sendto
            if(ccto?.size()) {
              cc ccto
            }
            from emailfrom
            subject this.title
            html this.body
        }
        this.timeSent = new Date()
        this.emailSent = true
        this.save(flush:true)
      } catch (ValidationException e) {
          PortalErrorLog.record(params,null,controllerName,actionName,e.toString(),email.title,email.module)
          respond portalEmail.errors, view:'create'
          return
      }
    }

}
