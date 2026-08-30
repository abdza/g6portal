package g6portal

import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import groovy.json.JsonOutput

/**
 * menulist.json lets a module declare its menu entries so the portal can wire them into
 * navigation on import. The entries land in trees belonging to the `portal` module - the
 * taglibs give no choice - so the thing that makes them the module's own is the
 * `module=<name>` stamp written into each node's data. Everything worth testing here is a
 * consequence of that stamp: a re-import may delete entries the declaration dropped, but
 * must not touch a node it did not create.
 *
 * Not @Rollback - importmodule manages its own transactions.
 */
@Integration
class PortalModuleMenuImportSpec extends Specification {

    static final String MODULE = 'menuspec'

    File migrationFolder

    def setup() {
        def root = PortalSetting.withNewSession {
            PortalSetting.namedefault('migrationfolder',
                System.getProperty('user.dir') + '/uploads/modulemigration')
        }
        migrationFolder = new File(root, MODULE)
        migrationFolder.deleteDir()
        migrationFolder.mkdirs()
        writemenu([
            [name: 'First',  link: 'link:/one',   icon: 'house', group: null],
            [name: 'Second', link: 'link:/two',   icon: null,    group: null],
            [name: 'Nested', link: 'link:/three', icon: null,    group: 'Tools'],
        ])
    }

    private void writemenu(List items) {
        new File(migrationFolder, 'menulist.json').write(
            JsonOutput.toJson([label: 'Menu Spec', icon: 'box', items: items]))
    }

    def cleanup() {
        migrationFolder?.deleteDir()
        PortalSetting.withNewTransaction {
            // Drop the module's own sidebar tree outright, and only this module's nodes
            // from the shared megamenu - another spec's or a developer's main_menu must
            // survive the cleanup.
            def side = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
            if(side) {
                // Release root before deleting the nodes, or the tree is left pointing at a
                // row that no longer exists and the next load throws EntityNotFound.
                side.root = null
                side.save(flush: true)
                deepestfirst(PortalTreeNode.findAllByTree(side))
                side.delete(flush: true)
            }
            def main = PortalTree.findByModuleAndName('portal', 'main_menu')
            if(main) {
                deepestfirst(PortalTreeNode.findAllByTree(main)
                                           .findAll { it.getdata('module', null) == MODULE })
            }
            PortalModule.findAllByName(MODULE)*.delete(flush: true)
        }
    }

    /** Children before parents, so beforeDelete never runs against a missing parent. */
    private void deepestfirst(List nodes) {
        nodes.findAll { !nodes.any { p -> p.id == it.parent?.id } }.each { leaf ->
            leaf.delete(flush: true)
        }
        def left = nodes.findAll { it.id && PortalTreeNode.get(it.id) }
        if(left) {
            deepestfirst(left)
        }
    }

    private PortalModule makeModule() {
        PortalModule.withNewTransaction {
            new PortalModule(name: MODULE).save(flush: true, failOnError: true)
        }
    }

    /** The module's entries in its own sidebar tree, in the order they render. */
    private List sidebaritems() {
        PortalTree.withNewSession {
            def tree = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
            if(!tree?.root) { return [] }
            PortalTreeNode.findAllByParent(tree.root)
                .sort { a, b -> (a.lft ?: 0) <=> (b.lft ?: 0) ?: a.id <=> b.id }
                .collect { it.name }
        }
    }

    void "a package that declares no menu leaves navigation alone"() {
        given:
            new File(migrationFolder, 'menulist.json').delete()
            def module = makeModule()

        when:
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        then:
            PortalTree.withNewSession { PortalTree.findByModuleAndName('portal', MODULE + '_menu') } == null
    }

    void "menu entries are only wired up when the import asks for them"() {
        given:
            def module = makeModule()

        when: "the default - the operator left the menu box unticked"
            PortalModule.withNewSession { module.importmodule(false, false, false) }

        then:
            sidebaritems() == []
    }

    void "an import that asks for menus builds both surfaces"() {
        given:
            def module = makeModule()

        when:
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        then: "ungrouped entries sit at the top level, grouped ones under their heading"
            sidebaritems() == ['First', 'Second', 'Tools']

        and: "and the module gets its own column in the shared megamenu"
            PortalTree.withNewSession {
                def main = PortalTree.findByModuleAndName('portal', 'main_menu')
                PortalTreeNode.findAllByParent(main.root)
                              .count { it.getdata('module', null) == MODULE }
            } == 1

        and: "each node carries the stamp that makes it the module's to manage"
            PortalTree.withNewSession {
                def tree = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
                PortalTreeNode.findAllByTree(tree).every { it.getdata('module', null) == MODULE }
            }
    }

    void "re-importing the same declaration changes nothing"() {
        given:
            def module = makeModule()
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }
            def first = sidebaritems()

        when:
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        then:
            sidebaritems() == first
    }

    void "a dropped entry is removed and a reordered one moves"() {
        given:
            def module = makeModule()
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        when: "the next version drops Second and puts Nested's group first"
            writemenu([
                [name: 'Nested', link: 'link:/three', icon: null, group: 'Tools'],
                [name: 'First',  link: 'link:/one',   icon: 'house', group: null],
            ])
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        then: "the menu matches the declaration rather than its own history"
            sidebaritems() == ['First', 'Tools']
    }

    void "dropping a whole group takes its entries with it"() {
        given: "a menu whose only grouped entry sits under Tools"
            def module = makeModule()
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }
            sidebaritems() == ['First', 'Second', 'Tools']

        when: "the group is dropped, in a session that has already read the tree"
            writemenu([
                [name: 'First',  link: 'link:/one', icon: 'house', group: null],
                [name: 'Second', link: 'link:/two', icon: null,    group: null],
            ])
            PortalModule.withNewSession {
                // Loading the collections first is the whole point. A web request has
                // rendered the menu through this same Hibernate session long before the
                // import runs, so the parent holds its children in an initialised
                // association - and that is what Hibernate refuses to flush a delete
                // against. An import into a session that has read nothing never sees it.
                def tree = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
                tree.nodes*.nodes*.size()
                module.importmodule(false, false, false, null, false, true)
            }

        then: "the heading and the entry under it are both gone"
            // Regression: deleting a group meant deleting a node that had children, and
            // the parent's loaded `nodes` collection still held them - which Hibernate
            // rejects as a deleted object it would have to re-save, aborting the whole
            // import. Every earlier case here dropped a leaf, so nothing caught it.
            sidebaritems() == ['First', 'Second']
    }

    void "a node the module did not create survives a re-import"() {
        given:
            def module = makeModule()
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        and: "somebody adds their own entry into the module's sidebar by hand"
            PortalTree.withNewTransaction {
                def tree = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
                new PortalTreeNode(tree: tree, parent: tree.root, name: 'Added by hand',
                                   slug: 'link:/byhand').save(flush: true, failOnError: true)
            }

        when: "a later version drops everything it used to declare"
            writemenu([[name: 'First', link: 'link:/one', icon: 'house', group: null]])
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        then: "the module prunes only its own, and the hand-added entry is still there"
            sidebaritems().contains('Added by hand')
            !sidebaritems().contains('Second')
    }

    void "another key in the node's data blob survives a re-import"() {
        given:
            def module = makeModule()
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        and: "somebody flags one of the entries with a key the declaration knows nothing about"
            PortalTree.withNewTransaction {
                def tree = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
                def node = PortalTreeNode.findAllByParent(tree.root).find { it.name == 'First' }
                node.data = node.data + ';adminonly=true'
                node.save(flush: true)
            }

        when:
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }

        then: "data is merged, not overwritten - only module and icon belong to the module"
            PortalTree.withNewSession {
                def tree = PortalTree.findByModuleAndName('portal', MODULE + '_menu')
                def node = PortalTreeNode.findAllByParent(tree.root).find { it.name == 'First' }
                [node.getdata('adminonly', null), node.getdata('module', null), node.getdata('icon', null)]
            } == ['true', MODULE, 'house']
    }

    void "exporting regenerates the declaration"() {
        given:
            def module = makeModule()
            PortalModule.withNewSession { module.importmodule(false, false, false, null, false, true) }
            def target = File.createTempDir('menuspec_', '_export')

        when:
            PortalModule.withNewSession { module.exportmodule(false, false, false, target.path, true) }

        then: "the round trip that treelist.json could never do, because the trees are portal's"
            def written = new groovy.json.JsonSlurper().parse(new File(target, 'menulist.json'))
            written.label == 'Menu Spec'
            written.items*.name == ['First', 'Second', 'Nested']
            written.items*.link == ['link:/one', 'link:/two', 'link:/three']
            written.items*.group == [null, null, 'Tools']

        cleanup:
            target.deleteDir()
    }
}
