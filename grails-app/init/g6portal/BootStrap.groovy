package g6portal

class BootStrap {

    def setupService

    def init = { servletContext ->
        // On a brand new database nothing can be reached until a system administrator
        // exists, so write and announce the one-time /setup token when there is none.
        setupService.announce()
    }
    def destroy = {
    }
}
