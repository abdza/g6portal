package g6portal

class PortalSearchController {

    def index() {
        // def curuser = User.get(session.userid)
        def curuser = session.curuser
        def dparam = '%' + params.query?.trim()?.replace(' ','%') + '%'
        def users = User.createCriteria().list() {
            or{
                'ilike'('name',dparam)
                'ilike'('userID',dparam)
                'ilike'('email',dparam)
                'ilike'('lanid',dparam)
                'ilike'('role',dparam)
            }
            'eq'('isActive',true)
            maxResults(20)
            order("id","asc")
        }
        def pages = PortalPage.createCriteria().list() {
            or{
                'ilike'('title',dparam)
                'ilike'('slug',dparam)
                'ilike'('module',dparam)
            }
            'eq'('published',true)
            maxResults(20)
            order("id","asc")
        }
        def trackers = PortalTracker.createCriteria().list() {
            or{
                'ilike'('name',dparam)
                'ilike'('slug',dparam)
                'ilike'('module',dparam)
            }
            maxResults(20)
            order("id","asc")
        }
        [users:users,curuser:curuser,pages:pages,trackers:trackers]
    }
}
