package com.mazesys.zerobite.core.text

/**
 * Strips accents from Latin letters ("Crème" → "Creme"). A lookup table rather than
 * java.text.Normalizer so :core stays portable to Kotlin Multiplatform.
 */
object TextFolding {

    private val FOLDS: Map<Char, Char> = buildMap {
        fun add(base: Char, accented: String) = accented.forEach { put(it, base) }
        add('a', "àáâãäåāăą"); add('A', "ÀÁÂÃÄÅĀĂĄ")
        add('c', "çćĉċč"); add('C', "ÇĆĈĊČ")
        add('d', "ďđ"); add('D', "ĎĐ")
        add('e', "èéêëēĕėęě"); add('E', "ÈÉÊËĒĔĖĘĚ")
        add('g', "ĝğġģ"); add('G', "ĜĞĠĢ")
        add('i', "ìíîïĩīĭįı"); add('I', "ÌÍÎÏĨĪĬĮİ")
        add('l', "ĺļľŀł"); add('L', "ĹĻĽĿŁ")
        add('n', "ñńņň"); add('N', "ÑŃŅŇ")
        add('o', "òóôõöøōŏő"); add('O', "ÒÓÔÕÖØŌŎŐ")
        add('r', "ŕŗř"); add('R', "ŔŖŘ")
        add('s', "śŝşš"); add('S', "ŚŜŞŠ")
        add('t', "ţťŧ"); add('T', "ŢŤŦ")
        add('u', "ùúûüũūŭůűų"); add('U', "ÙÚÛÜŨŪŬŮŰŲ")
        add('y', "ýÿŷ"); add('Y', "ÝŸŶ")
        add('z', "źżž"); add('Z', "ŹŻŽ")
    }

    fun foldDiacritics(text: String): String = buildString(text.length) {
        for (c in text) {
            // Decomposed accents ("e" + U+0300) arrive as separate combining marks; drop them.
            if (c in '\u0300'..'\u036F') continue
            append(FOLDS[c] ?: c)
        }
    }

    /**
     * Turns non-breaking and other Unicode spaces into plain spaces, one for one, so regex `\s`
     * behaves like iOS (ICU) on every platform. Line breaks are kept.
     */
    fun normalizeSpaces(text: String): String = buildString(text.length) {
        for (c in text) append(if (c != '\n' && c != '\r' && c.isWhitespace()) ' ' else c)
    }
}
