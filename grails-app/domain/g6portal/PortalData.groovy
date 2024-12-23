package g6portal

import groovy.sql.Sql
import org.springframework.transaction.annotation.Transactional
import grails.plugins.mail.MailService

class PortalData {

    transient MailService mailService

    def curdatas = [:]
    def curtree = null
    def curnode = null
    def typename = ""
    PortalTracker tracker
    String module
    String slug
    Integer id

    def prop(field,default_data = null) {
      if(field in curdatas) {
          return curdatas[field]
      }
      return default_data
    }

    def getnode(tree_name = null) {
      if(!tree_name) {
          tree_name = PortalSetting.namedefault('portal:default_tree','RD_2024')
          def btoken = tree_name.tokenize(':')
          if(btoken.size()>1){
              this.curtree = PortalTree.findByModuleAndName(btoken[0],btoken[1])
          }
          else {
              this.curtree = PortalTree.findByName(btoken[0])
          }
          if(this.curtree) {
              this.curnode = PortalTreeNode.findByTreeAndDomainAndDomainid(this.curtree,this.typename,this.id)
          }
      }

    }

    def fnd(search, lookfor, default_data = null) {
        def toreturn = []
        if('users_by_roles' in search) {
            if(!this.curnode) {
                this.getnode()
            }
            if(this.curnode) {
                lookfor.each { role ->
                    toreturn += PortalTreeNodeUser.findAllByNodeAndRole(this.curnode,role)
                }
            }
            return toreturn*.user
        }
        if('users_by_role' in search) {
            if(!this.curnode) {
                this.getnode()
            }
            if(this.curnode) {
                toreturn += PortalTreeNodeUser.findAllByNodeAndRole(this.curnode,lookfor)
            }
            if(toreturn) {
                return toreturn[0].user
            }
            else {
                return null
            }
        }
        if('user_by_role' in search) {
            if(!this.curnode) {
                this.getnode()
            }
            if(this.curnode) {
                toreturn += PortalTreeNodeUser.findByNodeAndRole(this.curnode,lookfor)
            }
            if(toreturn) {
                return toreturn[0].user
            }
            else {
                return null
            }
        }
    }

}
