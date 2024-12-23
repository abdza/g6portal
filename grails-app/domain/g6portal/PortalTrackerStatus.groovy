package g6portal

class PortalTrackerStatus {

  static belongsTo = [tracker:PortalTracker]

  static constraints = {
    name()
    tracker()
    displayfields(nullable:true)
    editroles(nullable:true)
    editfields(nullable:true)
    updateable(nullable:true)
    attachable(nullable:true)
    emailonupdate(nullable:true)
    updateallowedroles(nullable:true)
    suppressupdatebutton(nullable:true)
    flow(nullable:true)
    actiontransitions(nullable:true)
  }

  static mapping = {
    displayfields type: 'text'
    editfields type: 'text'
    editroles type: 'text'
    cache true
  }

  String name
  PortalTracker tracker
  String displayfields
  String updateallowedroles
  String editroles
  String editfields
  Integer flow
  Boolean updateable
  Boolean attachable
  Boolean suppressupdatebutton
  Boolean actiontransitions
  PortalTrackerEmail emailonupdate

  String toString() {
    return name
  }

  Boolean checkupdateable(user_roles) {
    if(!updateable){
        return false
    }
    def toreturn = true
    if(updateallowedroles){
      toreturn = false
      def roles = updateallowedroles.tokenize(';')*.trim()
      roles.each { role->
        def parts = role.tokenize('->')*.trim()
        if(parts[0].trim() in user_roles){
            toreturn = true
        }
      }
    }
    return toreturn
  }

  def findpath(target,curpath=[]){
    if(this==target){
      return [this]
    }
    else{
      if(this in curpath){
        return null
      }
      else{
        def curout = PortalTrackerTransition.createCriteria().list {
          'eq'('tracker',this.tracker)
          'eq'('prev_status',this)
        }
        if(curout){
          def validpaths = []
          curout.each { cpath->
            curpath << this
            def testpath = cpath.next_status.findpath(target,curpath)
            if(testpath){
              validpaths << [this] + testpath
            }
          }
          if(validpaths.size()){
            return validpaths
          }
          else{
            return null
          }
        }
        else{
          return null
        }
      }
    }
  }

  def pathtostatus(target) {
    println 'cur stat:' + this

    def curout = PortalTrackerTransition.createCriteria().list {
      'eq'('tracker',this.tracker)
      'eq'('prev_status',this)
    }

    if(curout){
      def testpaths = []
      curout.each { cpath->
        println 'trans:' + cpath
        def testpath = cpath.next_status.findpath(target)
        if(testpath){
          println 'curtestpath:' + testpath
          testpaths << testpath
        }
        else{
          println 'curpath failed:' + cpath
          return null
        }
      }
      println 'testpaths:' + testpaths
      if(testpaths.size()){
        def shortest = null
        testpaths.each { ctp->
          println 'testsing size of path:' + ctp
          if(shortest==null){ 
            shortest = ctp
          }
          if(ctp.size()<shortest.size()){
            shortest = ctp
          }
        }
        return shortest.flatten()
      }
      return null
    }
    else{
      return null
    }
  }
}
