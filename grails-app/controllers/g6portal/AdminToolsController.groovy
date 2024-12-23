package g6portal

class AdminToolsController {

    def mailService

    def index() { }

    def mailForm() {
        if(params.send) {
            println "Sending email"
            mailService.sendMail {
                to params.to
                subject params.subject
                html params.content
            }
        }
    }
}
