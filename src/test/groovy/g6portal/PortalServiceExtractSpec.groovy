package g6portal

import spock.lang.Specification
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PortalService.extract is the one place module packages are unpacked, by both the module
 * import screen and the bundled-package importer. Entry names come from whoever built the
 * archive, so the containment check is the thing standing between an uploaded zip and an
 * arbitrary file write as the account the portal runs as.
 *
 * A plain unit spec: extract is static and touches nothing but the filesystem.
 */
class PortalServiceExtractSpec extends Specification {

    File work
    File destination

    def setup() {
        work = File.createTempDir('extract_', '_spec')
        destination = new File(work, 'dest/module')
    }

    def cleanup() {
        work?.deleteDir()
    }

    /** Build a zip whose entries are exactly the names given. */
    private File zipWithEntries(Map<String, String> entries) {
        File zip = new File(work, 'package.zip')
        new ZipOutputStream(new FileOutputStream(zip)).withCloseable { zos ->
            entries.each { name, content ->
                zos.putNextEntry(new ZipEntry(name))
                zos.write(content.bytes)
                zos.closeEntry()
            }
        }
        zip
    }

    void "ordinary entries are written inside the destination"() {
        given:
            File zip = zipWithEntries(['settinglist.json': '[]', 'pages/home.gsp': 'hello'])

        when:
            PortalService.extract(zip, destination)

        then:
            new File(destination, 'settinglist.json').text == '[]'
            new File(destination, 'pages/home.gsp').text == 'hello'
    }

    void "an entry climbing out of the destination is refused"() {
        given: "the shape that reached start.sh before this check existed"
            File zip = zipWithEntries(['../../escaped.txt': 'owned'])
            File escapee = new File(work, 'escaped.txt')

        when:
            PortalService.extract(zip, destination)

        then:
            IOException e = thrown()
            e.message.contains('points outside the destination')

        and: "and nothing was written there"
            !escapee.exists()
    }

    void "a deep climb is refused too"() {
        given:
            File zip = zipWithEntries(['../../../../../../tmp/g6portal-zipslip-probe.txt': 'owned'])
            File escapee = new File('/tmp/g6portal-zipslip-probe.txt')
            escapee.delete()

        when:
            PortalService.extract(zip, destination)

        then:
            thrown(IOException)

        and:
            !escapee.exists()

        cleanup:
            escapee.delete()
    }

    void "an absolute entry name stays inside the destination"() {
        given: "harmless under the old concatenation, and it must stay harmless now"
            File zip = zipWithEntries(['/etc/passwd': 'not really'])

        when:
            PortalService.extract(zip, destination)

        then:
            noExceptionThrown()
            new File(destination, 'etc/passwd').exists()
            new File(destination, 'etc/passwd').text == 'not really'
    }

    void "a refused entry does not leave the earlier good ones half written"() {
        given: "the traversal entry comes after a legitimate one"
            File zip = zipWithEntries(['settinglist.json': '[]', '../escaped.txt': 'owned'])

        when:
            PortalService.extract(zip, destination)

        then:
            thrown(IOException)

        and: "the caller aborts the import, and nothing escaped"
            !new File(destination.parentFile, 'escaped.txt').exists()
    }
}
