package com.mazesys.zerobite.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextFoldingTest {
    @Test
    fun `folds accented Latin letters`() {
        assertEquals("Creme fraiche", TextFolding.foldDiacritics("Crème fraîche"))
        assertEquals("Gruyere, jalapeno, Emmental", TextFolding.foldDiacritics("Gruyère, jalapeño, Emmental"))
        assertEquals("O’Brien Fine Foods", TextFolding.foldDiacritics("O’Brien Fine Foods"))
    }
}
