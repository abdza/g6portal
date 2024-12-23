package g6portal

import static grails.util.Holders.config

class TreeTagLib {

    def node_url(node) {
        def ptoken = node?.slug?.tokenize(':')
        def tolink = ""
        if(ptoken) {
            if(ptoken.size()>1) {
                def lparam = [module: ptoken[1], slug: ptoken[2]]
                if(ptoken.size()>3){
                    lparam['arg1'] = ptoken[3]
                }
                if(ptoken.size()>4){
                    lparam['arg2'] = ptoken[4]
                }
                if(ptoken.size()>5){
                    lparam['arg3'] = ptoken[5]
                }
                if(ptoken[0]=='page'){
                    tolink = createLink(controller: 'portalPage', action: 'display', params:lparam)
                }
                else if(ptoken[0]=='tracker'){
                    tolink = createLink(controller: 'portalTracker', action: 'list', params:lparam)
                }
                else if(ptoken[0]=='file'){
                    tolink = createLink(controller: 'fileLink', action: 'download', params:lparam)
                }
                else if(ptoken[0]=='link'){
                    def remainder = ptoken[1..-1].join(':')
                    tolink = createLink(url:remainder)
                }
            }
        }
        return tolink
    }

    def node_icon(node) {
        def icontag = ""
        def icondata = node?.getdata('icon',null)
        if(icondata) {
            icontag = "<i class='bi bi-" + icondata + "'></i> "
        }
        else {
            def imgdata = node?.getdata('img',null)
            if(imgdata) {
                icontag = "<img src='/download/portal/${imgdata}' class='img-fluid' style='max-height: 1.5em; max-width: 1.5em;'/>"
            }
        }
        return icontag
    }

    def side_menu = { attrs->
        def menutree = null
        if(attrs.module && attrs.slug) {
            def obj = null
            if(ControllerName=='portalPage') {
                obj = g6portal.PortalPage.findByModuleAndSlug(attrs.module,attrs.slug)
            }
            else if(ControllerName=='portalTracker') {
                obj = g6portal.PortalTracker.findByModuleAndSlug(attrs.module,attrs.slug)
            }
            def menuname = ""
            if(obj) {
                menuname = obj.side_menu + "_menu"
            }
            menutree = g6portal.PortalTree.findByModuleAndName('portal',menuname)
            if(menutree) {
                menutree.root?.nodes?.sort { it.lft } .each { menu_group ->
                    if(menu_group.nodes.size()==0) {
                            def tolink = node_url(menu_group)
                            def icon = node_icon(menu_group)

                            out << "<li class='nav-item'><a class='nav-link' href='${tolink}'>${icon} " + menu_group.name + "</a></li>"
                    }
                    else {
                        def groupicon = node_icon(menu_group)
                        def ulid = menu_group.name.replaceAll(' ','-').toLowerCase()
                        out << "<li class='nav-item'><a class='nav-link collapsed' data-bs-target='#${ulid}-nav' data-bs-toggle='collapse' href='#'>${groupicon} " + menu_group.name + "<i class='bi bi-chevron-down ms-auto'></i></a><ul class='nav-content collapse' data-bs-parent='#sidebar-nav' id='${ulid}-nav'>"
                        menu_group.nodes?.sort { it.lft }.each  { menu_item ->
                            def tolink = node_url(menu_item)
                            def icon = node_icon(menu_item)
                            out << "<li><a href='${tolink}'>${icon} " + menu_item.name + "</a></li>"
                        }
                        out << "</ul></li>"
                    }
                }
            }
        }
    }

    def tree_menu = { attrs->
        def menutree = null
        if(attrs.module && attrs.name) {
            if(config.grails?.domainlimit){
                menutree = g6portal.PortalTree.findByModuleAndName(attrs.module,session['url_domain'] + "_" + attrs.name)
            }
            if(!menutree) {
                menutree = g6portal.PortalTree.findByModuleAndName(attrs.module,attrs.name)
            }
            if(menutree) {
                menutree.root?.nodes?.sort { it.lft } .each { menu_columns ->
                    out << "<div class='col-lg-3 col-6'><div class='col-megamenu p-2'>"
                    menu_columns.nodes.sort { it.lft } .each { menu_group ->
                        if(menu_group.nodes.size()==0) {
                                def tolink = node_url(menu_group)
                                def icon = node_icon(menu_group)

                                out << "<li><a href='${tolink}'>${icon} " + menu_group.name + "</a></li>"
                        }
                        else {
                            out << "<h3 class='title'>" + menu_group.name + "</h3><ul class='list-unstyled'>"
                            menu_group.nodes.sort { it.lft }.each  { menu_item ->
                                def tolink = node_url(menu_item)
                                def icon = node_icon(menu_item)
                                out << "<li><a href='${tolink}'>${icon} " + menu_item.name + "</a></li>"
                            }
                            out << "</ul><hr/>"
                        }
                    }
                    out << "</div></div>"
                }
            }
        }
    }

    def treenodes = { attrs->
        if(attrs.node){
            def id=attrs.node.id
            if(attrs.id){
                id=attrs.id
            }
            out << "<div id='" + id + "' class='jstree'>"
            out << "<ul class='jstree-drop'>"
            out << nodesli(attrs.node)
            out << "</ul>"
            out << "</div>"
        }
    }

    def nodesli(node) {
        def output = "<li class='jstree-drop' id='" + node.id + "'><a href='#'>" + node.name
        if(node.users){
            if(node.users.size()==1){
                node.users.each { duser->
                    output += " - " + duser.role + ": " + duser.user.name + " (" + duser.user.userID + ")"
                }
            }
        }
        output +="</a>"
            if(node.nodes){
                output += "<ul>"
                    node.nodes.sort { it.lft } .each {
                        output += nodesli(it)
                    }
                output += "</ul>"
            }
        return output + "</li>"
    }
}
