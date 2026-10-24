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

    /**
     * The id of the user who holds row `tnuId` right now, read from the database. Not from
     * get(): this domain is in the second-level cache, and a bulk or out-of-band edit of the
     * table does not evict it - a cached row can name its previous holder for a long time.
     */
    static Long holderId(tnuId) {
        if(tnuId == null) return null
        def rows = PortalTreeNodeUser.executeQuery("select t.user.id from PortalTreeNodeUser t where t.id = :id",
                                                   [id: (tnuId as Long)])
        return rows ? (rows[0] as Long) : null
    }

    /**
     * Clear User.roletargetid for everyone still pointing at tree_node_user row `tnuId`,
     * except `keepUserId` (the row's holder now; null when the row is being deleted).
     *
     * Call it whenever a row changes hands (its user is replaced in place) or is deleted. The
     * old holder's roletargetid would otherwise keep pointing at a row that is now someone
     * else's, and they would act with that person's node and role. A cleared roletargetid is
     * re-picked from the user's own rows at their next login.
     *
     * Plain HQL bulk update: it also evicts the cached User entities, so the change is seen
     * at once. Returns the number of users released.
     */
    static int releaseRoleTargets(tnuId, keepUserId = null) {
        if(tnuId == null) return 0
        def tid = (tnuId as Long).intValue()
        // A bulk update needs a transaction; withTransaction joins the caller's when there is one.
        User.withTransaction {
            if(keepUserId != null) {
                return User.executeUpdate("update User u set u.roletargetid = null where u.roletargetid = :tid and u.id <> :uid",
                                          [tid: tid, uid: keepUserId as Long])
            }
            return User.executeUpdate("update User u set u.roletargetid = null where u.roletargetid = :tid", [tid: tid])
        } as int
    }

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
