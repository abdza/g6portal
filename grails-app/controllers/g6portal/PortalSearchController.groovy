package g6portal

class PortalSearchController {

    def index() {
        def curuser = session.curuser
        def users = []
        def pages = []
        def trackers = []
        
        try {
            // Validate and sanitize search query
            def query = params.query?.trim()
            if(!query || query.length() < 2) {
                flash.message = "Search query must be at least 2 characters long"
                return [users:users,curuser:curuser,pages:pages,trackers:trackers]
            }
            
            // Limit query length to prevent abuse
            if(query.length() > 100) {
                query = query.substring(0, 100)
            }
            
            // Remove potentially harmful characters but keep basic wildcards
            query = query.replaceAll(/[%_]/, '\\\\$0') // Escape existing wildcards
                        .replaceAll(/[<>'"&]/, '') // Remove potentially harmful chars
            
            def dparam = '%' + query.replace(' ','%') + '%'
            
            // Search users with error handling
            try {
                users = User.createCriteria().list() {
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
            } catch(Exception e) {
                PortalErrorLog.record(params, curuser, 'search', 'user_search_error', 
                    "Error searching users: " + e.toString(), null, null)
                users = []
            }
            
            // Search pages with error handling
            try {
                pages = PortalPage.createCriteria().list() {
                    or{
                        'ilike'('title',dparam)
                        'ilike'('slug',dparam)
                        'ilike'('module',dparam)
                    }
                    'eq'('published',true)
                    maxResults(20)
                    order("id","asc")
                }
            } catch(Exception e) {
                PortalErrorLog.record(params, curuser, 'search', 'page_search_error', 
                    "Error searching pages: " + e.toString(), null, null)
                pages = []
            }
            
            // Search trackers with error handling
            try {
                trackers = PortalTracker.createCriteria().list() {
                    or{
                        'ilike'('name',dparam)
                        'ilike'('slug',dparam)
                        'ilike'('module',dparam)
                    }
                    maxResults(20)
                    order("id","asc")
                }
            } catch(Exception e) {
                PortalErrorLog.record(params, curuser, 'search', 'tracker_search_error', 
                    "Error searching trackers: " + e.toString(), null, null)
                trackers = []
            }
            
        } catch(Exception e) {
            PortalErrorLog.record(params, curuser, 'search', 'general_search_error', 
                "General search error: " + e.toString(), null, null)
            flash.error = "An error occurred during search"
        }
        
        [users:users,curuser:curuser,pages:pages,trackers:trackers]
    }
}
