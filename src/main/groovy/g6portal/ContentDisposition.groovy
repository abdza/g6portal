package g6portal

/**
 * Builds a Content-Disposition header value that browsers accept for any filename.
 *
 * Downloads used to write the name bare - "attachment;filename=" + name - which breaks on
 * a comma: Chrome and Edge read "USD 230,430.77.docx" as two header values and refuse the
 * whole response with ERR_RESPONSE_HEADERS_MULTIPLE_CONTENT_DISPOSITION. A quote or
 * semicolon in the name breaks it the same way, and non-ASCII names came out garbled.
 *
 * Per RFC 6266 the value carries both forms: a quoted ASCII fallback in filename= for old
 * clients, and the exact name UTF-8 percent-encoded in filename*=, which every current
 * browser prefers.
 */
class ContentDisposition {

    static String attachment(String filename) {
        return build('attachment', filename)
    }

    static String inline(String filename) {
        return build('inline', filename)
    }

    static String build(String type, String filename) {
        def name = (filename ?: 'download').replaceAll('[\\r\\n]', ' ')
        // Fallback: printable ASCII only, and nothing that could end the quoted string early.
        def ascii = name.replaceAll('[^\\x20-\\x7E]', '_').replace('"', '_').replace('\\', '_')
        // URLEncoder is form encoding: space becomes '+', which filename*= would keep literally.
        def encoded = URLEncoder.encode(name, 'UTF-8').replace('+', '%20')
        return "${type}; filename=\"${ascii}\"; filename*=UTF-8''${encoded}".toString()
    }
}
