package g6portal

/**
 * Masking for personal data that leaves the portal - above all in e-mails, which are neither
 * access-controlled nor recallable once sent. The full values stay on the record in the portal
 * for anyone entitled to see them; a notification only needs enough to recognise the case.
 *
 * Callable from any page or e-mail template:
 *     ${g6portal.Mask.account(datas['account_number'])}   ->  ******2698
 *     ${g6portal.Mask.name(datas['name_of_customer'])}    ->  AHMAD R. B. Z. A.
 *
 * Nothing here is organisation-specific.
 */
class Mask {

    /**
     * Only the last four characters survive: '10110000102698' -> '**********2698'.
     * Four characters or fewer are masked whole rather than shown intact - revealing a short
     * value completely is exactly what this is for. Blank -> '-'.
     */
    static String account(value) {
        def s = value?.toString()?.trim()
        if(!s) return '-'
        return (s.length() <= 4) ? ('*' * s.length()) : ('*' * (s.length() - 4)) + s[-4..-1]
    }

    /**
     * First word kept, every later word reduced to its initial:
     * 'AHMAD RITHAUDDIN BIN ZAINAL AHMAD' -> 'AHMAD R. B. Z. A.'.
     * A one-word name keeps only its first letter ('ROSLAN' -> 'R*****'), since keeping the
     * first word would otherwise show it in full. Blank -> '-'.
     */
    static String name(value) {
        def words = value?.toString()?.trim()?.split(/\s+/)?.findAll { it } ?: []
        if(!words) return '-'
        if(words.size() == 1) {
            def w = words[0]
            return w.length() <= 1 ? '*' : w[0] + ('*' * (w.length() - 1))
        }
        return ([words[0]] + words[1..-1].collect { it[0] + '.' }).join(' ')
    }
}
