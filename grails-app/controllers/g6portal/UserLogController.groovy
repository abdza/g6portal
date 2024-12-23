package g6portal

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.*

class UserLogController {

    UserLogService userLogService

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
	def dparam = [max:params.max?:10,offset:params.offset?:0]
	params.max = dparam.max
	if(params.q) {
		def query = '%' + params.q + '%'
		def thelist = userLogService.list(query,dparam)
		respond thelist, model:[userLogCount: userLogService.count(query), params:params]
	}
	else {
		def thelist = userLogService.list(dparam)
		respond thelist, model:[userLogCount: userLogService.count(), params:params]
	}
    }

    def show(Long id) {
        respond userLogService.get(id)
    }

    def create() {
        respond new UserLog(params)
    }

    def save(UserLog userLog) {
        if (userLog == null) {
            notFound()
            return
        }

        try {
            userLogService.save(userLog)
        } catch (ValidationException e) {
            respond userLog.errors, view:'create'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'userLog.label', default: 'UserLog'), userLog.id])
                redirect userLog
            }
            '*' { respond userLog, [status: CREATED] }
        }
    }

    def edit(Long id) {
        respond userLogService.get(id)
    }

    def update(UserLog userLog) {
        if (userLog == null) {
            notFound()
            return
        }

        try {
            userLogService.save(userLog)
        } catch (ValidationException e) {
            respond userLog.errors, view:'edit'
            return
        }

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'userLog.label', default: 'UserLog'), userLog.id])
                redirect userLog
            }
            '*'{ respond userLog, [status: OK] }
        }
    }

    def delete(Long id) {
        if (id == null) {
            notFound()
            return
        }

        userLogService.delete(id)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'userLog.label', default: 'UserLog'), id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'userLog.label', default: 'UserLog'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
