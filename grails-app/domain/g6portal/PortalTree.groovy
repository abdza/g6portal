package g6portal

class PortalTree {

    static hasMany = [nodes:PortalTreeNode]

    static constraints = {
        module(nullable:true)
        name()
        root(nullable:true)
        expire(nullable:true)
        valid(nullable:true)
    }

    static mapping = {
        cache true
    }

    String module
    String name
    PortalTreeNode root
    Date valid
    Date expire

    String toString(){
        name
    }

    def static load_tree(name,module='portal') {
        def tokens = name.tokenize(':')
        name = tokens[0]
        if(tokens.size()==2) {
            module = tokens[1]
        }
        return PortalTree.findByModuleAndName(module,name)
    }

    def userbyrole(role) {
        def dnode = PortalTreeNodeUser.findByNodeInListAndRole(this.nodes,role,[cache:true])
        return dnode
    }

    def alluserbyrole(role) {
        def dnode = PortalTreeNodeUser.findAllByNodeInListAndRole(this.nodes,role,[cache:true])
        return dnode
    }

    def useris(role,curuser){
        def dnode = PortalTreeNodeUser.findAll("from PortalTreeNodeUser as tnu where tnu.node in :dnode and tnu.role=:drole and tnu.user=:duser",[dnode:this.nodes,drole:role,duser:curuser],[cache:true])
        return dnode
    }

    static def rdtree(user=null){
        def toret = PortalTree.findByName('RD_2024',[cache:true])
        if(!toret){
            toret = PortalTree.findByName('RD_2020',[cache:true])
        }
        if(!toret){
            toret = PortalTree.findByName('RD_2016',[cache:true])
        }
        if(user){
            def valid=validtrees(user)
            if(toret in valid){
                return toret
            }
            else{
                return PortalTree.findByName('RD_2020',[cache:true])
            }
        }
        return toret
    }

    static def validtrees(user){
        def tc = PortalTree.createCriteria()
        def results = tc.list {
            and {
                or{
                    lt("valid",user.treesdate)
                    isNull("valid")
                }
                or{
                    gt("expire",user.treesdate)
                    isNull("expire")
                }
                not{
                    'in'("name",PortalSetting.namedefault("not_user_trees",["ReportModule"]))
                }
            }
        }
        return results
    }

}
