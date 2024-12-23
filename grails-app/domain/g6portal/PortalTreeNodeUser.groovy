package g6portal

class PortalTreeNodeUser {

    static belongsTo = [user:User,node:PortalTreeNode]

    static constraints = {
        user(nullable:true)
    }

    static mapping = {
        cache true
    }

    String role

    String toString(){
        // return role 
        def drole = role
        if(node.tree.name in PortalSetting.namedefault('rd_trees',['RD_2024','RD_2020','RD_2015','RD_2016','RD'])){
            drole = role + ' of ' + node.name
        }
        else if(node.tree.name=='HOD' && role in ['Admin','HOD']){
            drole = role + ' of ' + node.name
        }
        return drole
        /* else if(ddomain){
            if(this.node.domain=='csdportal.Branch'){
                drole = role+' of Branch ' + ddomain.name
            }
            else if(this.node.domain=='csdportal.Area'){
                if(role=='ARM'){
                    drole = 'ARM' + ddomain.number
                }
                else {
                    drole = role + ' of ARM ' + ddomain.number
                }
            }
            else if(this.node.domain=='csdportal.Region'){
                if(role=='RD'){
                    drole = 'RD' + ddomain.number
                }
                else {
                    drole = role + ' of RD ' + ddomain.number
                }
            }
            else if(this.node.domain=='csdportal.Department'){
                if(role=='HOD'){
                    drole = 'Head of Department ' + ddomain.name
                }
            }
            else if(this.node.domain=='csdportal.PortalTreeReport'){
                drole = role + ' of Module ' + ddomain.title
            }
            else {
                drole = role + ' of ' + ddomain
            }
        }
        else {
            drole = role + ' ' + node.name
        }
        return drole */
    }
}
