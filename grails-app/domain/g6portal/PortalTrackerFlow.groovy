package g6portal

class PortalTrackerFlow {

    static belongsTo = [tracker:PortalTracker]

    static constraints = {
        name()
        tracker()
        fields(nullable:true,widget:'textarea')
        transitions(nullable:true,widget:'textarea')
    }

    static mapping = {
        fields type: 'text'
        transitions type: 'text'
    }

    String name
    String fields
    String transitions
}
