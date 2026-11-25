package br.vendas.app.domain

/**
 * Brazilian WhatsApp numbers. Customers are stored in E.164 format
 * (+5511999998888) so the same number typed in different ways matches.
 */
object PhoneNumbers {

    /**
     * Converts a number typed in any common format ("11 99999-8888",
     * "(11) 99999-8888", "+55 11 99999-8888", "011999998888") to E.164.
     * Mobile numbers written without the leading 9 get it added, as WhatsApp
     * often reports them that way. Returns null when the number has no valid
     * area code (DDD).
     */
    fun normalize(input: String): String? {
        var digits = input.filter { it.isDigit() }.trimStart('0')

        if (digits.length in 12..13 && digits.startsWith("55")) {
            digits = digits.substring(2)
        }
        if (digits.length !in 10..11) return null

        val areaCode = digits.substring(0, 2)
        if ('0' in areaCode) return null

        var subscriber = digits.substring(2)
        when (subscriber.length) {
            9 -> if (subscriber[0] != '9') return null
            8 -> if (subscriber[0] in '6'..'9') subscriber = "9$subscriber"
        }
        return "+55$areaCode$subscriber"
    }

    /** "+5511999998888" becomes "(11) 99999-8888". Other values are returned as they are. */
    fun format(e164: String): String {
        if (!e164.startsWith("+55")) return e164
        val national = e164.removePrefix("+55")
        if (national.length !in 10..11) return e164
        val areaCode = national.substring(0, 2)
        val subscriber = national.substring(2)
        val split = subscriber.length - 4
        return "($areaCode) ${subscriber.substring(0, split)}-${subscriber.substring(split)}"
    }

    /** Digits used in wa.me links: "+5511999998888" becomes "5511999998888". */
    fun waMeDigits(e164: String): String = e164.removePrefix("+")
}
