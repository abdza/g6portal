package g6portal

/**
 * Audit log of module imports. Stores the unified diff between the module
 * state before the import (temporary export) and the imported migration
 * files, together with the importing user's remarks.
 */
class PortalModuleImportLog {

    String module
    String staffid
    String staffname
    String remarks
    String diff
    Date dateCreated

    static constraints = {
        module()
        staffid(nullable:true)
        staffname(nullable:true)
        remarks(nullable:true,widget:'textarea')
        diff(nullable:true,widget:'textarea')
    }

    static mapping = {
        remarks type: 'text'
        diff type: 'text'
        sort dateCreated: 'desc'
    }
}
