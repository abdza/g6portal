# Module menus (`menulist.json`)

A module package can declare its menu entries. The portal wires them into
navigation on import, regenerates the declaration on export, and a package dropped
in `bundled-modules/` wires itself up at boot with nobody there to click anything.

Before this, a module was reachable only by URL until somebody hand-built nodes in
`/portalTree`.

## Declaring a menu

Add `menulist.json` to the package root, beside `pagelist.json`:

```json
{
    "label": "Live Quiz",
    "icon": "play-circle",
    "items": [
        { "name": "Run a session", "link": "page:livequiz:console", "icon": "play-circle", "group": null },
        { "name": "Sessions",      "link": "tracker:livequiz:event", "icon": "card-list", "group": "Authoring" }
    ]
}
```

| Key | Meaning |
|---|---|
| `label` | Heading for the module. Defaults to the module name. |
| `icon` | A [Bootstrap Icons](https://icons.getbootstrap.com/) name, without the `bi-` prefix. |
| `items[].name` | What the entry reads as. |
| `items[].link` | Where it goes — the same slug spec tree nodes already use (below). |
| `items[].icon` | Optional, per entry. |
| `items[].group` | Optional. Entries sharing a group are collected under one heading; `null` puts the entry directly under the module. |
| `items[].mainrole` / `hiderole` | Optional, passed to the node for role-gated entries. |

`link` accepts the forms `TreeTagLib.node_url` already parses:

```
page:<module>:<slug>[:arg1[:arg2[:arg3]]]     a portal page
tracker:<module>:<slug>                       a tracker's list screen
run:<module>:<slug>                           a runable page
file:<module>:<slug>                          a file download
link:/any/path                                anything else
```

## What gets built

Two trees, both under module **`portal`** — the taglibs give no choice, since
`TreeTagLib.side_menu` hardcodes `findByModuleAndName('portal', …)` and `main.gsp`
calls `<g:tree_menu module='portal' name='main_menu'/>`:

| Tree | What the module gets |
|---|---|
| `portal/main_menu` | one column in the header megamenu, with a heading per group |
| `portal/<module>_menu` | a sidebar: ungrouped entries as links, one collapsible per group |

The sidebar only shows on pages that ask for it. Set `side_menu` to the **module
name** on the pages and trackers that should carry it, in their own
`pagelist.json` / `trackerlist.json` — the taglib appends `_menu` itself.

## Ownership

Every node the portal creates from a declaration is stamped `module=<name>` in the
node's `data` field. That stamp is what makes the entries *owned* rather than
merely present:

- Matching is by stamp, never by name, so renaming an entry updates it rather than
  leaving a duplicate behind.
- Re-importing rebuilds the module's own nodes to match the declaration, **including
  deleting entries the declaration has dropped**, so the menu always reflects the
  installed version. Reordering `items` reorders the menu.
- Nodes an admin added by hand carry no stamp and are never touched. Neither is any
  other module's column.

## Installing

**Through the import screens.** The preview shows an *Add this module's menu
entries* checkbox, ticked by default. Untick it to take the module without touching
a curated menu; the import log then records that the entries were left out. The
diff always includes `menulist.json` regardless, so it compares like with like.

**From `bundled-modules/`.** Wired up automatically — there is no one to ask at
boot, and a preconfigured instance that ships a module but leaves it unreachable is
not preconfigured. Note the bundled path cannot grant module roles (nobody is
logged in), so after a bundled install grant yourself `Developer` on the module at
`/userRole/create` or the module's own pages will bounce you.

**Exporting.** Tick *Menus* on the module's export form and `menulist.json` is
regenerated from the nodes the module owns, so a module exported from one portal
carries its menu to the next.

## Two things it does not do

**Menu visibility is not access control.** An entry shows to anyone the node's
`mainrole`/`hiderole` allow; whether the page behind it opens is decided by that
page's own `allowedroles`. Gate the page, not just the menu.

**A removed module keeps its menu.** Deleting a `PortalModule` row does not sweep up
the nodes it contributed — remove them in `/portalTree`, or re-import with an empty
`items` list.
