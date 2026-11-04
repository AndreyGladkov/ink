package io.github.andreygladkov.inkmessages.mms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VCardsTest {
    @Test fun readsTheFormattedName() {
        assertEquals("Иван Петров", VCards.displayName("BEGIN:VCARD\r\nVERSION:3.0\r\nN:Петров;Иван\r\nFN:Иван Петров\r\nEND:VCARD\r\n"))
    }

    @Test fun decodesQuotedPrintableNames() {
        val card = "BEGIN:VCARD\nVERSION:2.1\nFN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=D0=98=D0=B2=D0=B0=D0=BD\nEND:VCARD\n"
        assertEquals("Иван", VCards.displayName(card))
    }

    @Test fun joinsFoldedLines() {
        assertEquals("Анна Каренина", VCards.displayName("BEGIN:VCARD\nFN:Анна \n Каренина\nEND:VCARD\n"))
    }

    @Test fun aCardWithoutANameHasNone() {
        assertNull(VCards.displayName("BEGIN:VCARD\nTEL:+79001112233\nEND:VCARD\n"))
    }
}
