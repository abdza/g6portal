package g6portal

class UrlMappings {

    static mappings = {


	"/login" {
        controller = "user"
        action = "login"
    }

	"/setup" {
        controller = "portalPage"
        action = "setup"
    }

	"/download/$module/$slug" {
        controller = "FileLink"
        action = "download"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
        }
    }

	"/download/$slug" {
        controller = "FileLink"
        module = "portal"
        action = "download"
        constraints {
            slug(matches:/[a-zA-Z0-9]+.*$/)
        }
    }

	"/view/$module/$slug/$arg1/$arg2/$arg3" {
        controller = "portalPage"
        action = "display"
        constraints {
            module(matches:/[a-zA-Z0-9_]+.*$/)
            slug(matches:/[a-zA-Z0-9_]+.*$/)
            arg1(matches:/[a-zA-Z0-9_ ]+.*$/)
            arg2(matches:/[a-zA-Z0-9_ ]+.*$/)
            arg3(matches:/[a-zA-Z0-9_ ]+.*$/)
        }
    }

	"/view/$module/$slug/$arg1/$arg2" {
        controller = "portalPage"
        action = "display"
        constraints {
            module(matches:/[a-zA-Z0-9_]+.*$/)
            slug(matches:/[a-zA-Z0-9_]+.*$/)
            arg1(matches:/[a-zA-Z0-9_ ]+.*$/)
            arg2(matches:/[a-zA-Z0-9_ ]+.*$/)
        }
    }

	"/view/$module/$slug/$arg1" {
        controller = "portalPage"
        action = "display"
        constraints {
            module(matches:/[a-zA-Z0-9_]+.*$/)
            slug(matches:/[a-zA-Z0-9_]+.*$/)
            arg1(matches:/[a-zA-Z0-9_ ]+.*$/)
        }
    }

	"/view/$module/$slug" {
        controller = "portalPage"
        action = "display"
        constraints {
            module(matches:/[a-zA-Z0-9_]+.*$/)
            slug(matches:/[a-zA-Z0-9_]+.*$/)
        }
    }

	"/run/$module/$slug/$arg1/$arg2/$arg3" {
        controller = "portalPage"
        action = "runpage"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            arg1(matches:/[a-zA-Z0-9_ ]+.*$/)
            arg2(matches:/[a-zA-Z0-9_ ]+.*$/)
            arg3(matches:/[a-zA-Z0-9_ ]+.*$/)
        }
    }

	"/run/$module/$slug/$arg1/$arg2" {
        controller = "portalPage"
        action = "runpage"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            arg1(matches:/[a-zA-Z0-9_ ]+.*$/)
            arg2(matches:/[a-zA-Z0-9_ ]+.*$/)
        }
    }

	"/run/$module/$slug/$arg1" {
        controller = "portalPage"
        action = "runpage"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            arg1(matches:/[a-zA-Z0-9_ ]+.*$/)
        }
    }

	"/run/$module/$slug" {
        controller = "portalPage"
        action = "runpage"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
        }
    }

	"/run/$slug" {
        controller = "portalPage"
        module = "portal"
        action = "runpage"
        constraints {
            slug(matches:/[a-zA-Z0-9]+.*$/)
        }
    }

	"/$module/$slug/list" {
        controller = "portalTracker"
        action = "list"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
        }
    }

	"/$module/$slug/create" {
        controller = "portalTracker"
        action = "create_data"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
        }
    }

	"/$module/$slug/display/$id" {
        controller = "portalTracker"
        action = "display_data"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            id(matches:/[0-9]+.*$/)
        }
    }

	"/$module/$slug/updaterecord/$id?" {
        controller = "portalTracker"
        action = "update_record"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            id(matches:/[0-9]+.*$/)
        }
    }

	"/$module/$slug/$transition/$id?" {
        method = "GET"
        controller = "portalTracker"
        action = "transition"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            transition(matches:/[a-zA-Z]+.*$/)
            id(matches:/[0-9]+.*$/)
        }
    }

	"/$module/$slug/$transition/$id?" {
        method = "POST"
        controller = "portalTracker"
        action = "transition_submit"
        constraints {
            module(matches:/[a-zA-Z0-9]+.*$/)
            slug(matches:/[a-zA-Z0-9]+.*$/)
            transition(matches:/[a-zA-Z]+.*$/)
            id(matches:/[0-9]+.*$/)
        }
    }

    "/$controller/$action?/$id?(.$format)?"{
        constraints {
            // apply constraints here
        }
    }

    "/" {
	    controller = "portalPage"
	    action = "display"
	    module = "portal"
	    slug = "home"
    }

        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
