package com.quipu.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PersonKeyTest {

    @Test
    fun sameKey_ignoringCaseSpacesAndAccents() {
        val expected = PersonKey.keyOf("jose perez")
        assertEquals(expected, PersonKey.keyOf("  José   PÉREZ "))
        assertEquals(expected, PersonKey.keyOf("JOSE PEREZ"))
    }

    @Test
    fun detailInTheName_createsDistinctPeople() {
        assertNotEquals(PersonKey.keyOf("Juan"), PersonKey.keyOf("Juan (primo)"))
        assertNotEquals(PersonKey.keyOf("Juan P."), PersonKey.keyOf("Juan Q."))
    }

    @Test
    fun displayName_trimsAndCollapsesSpaces() {
        assertEquals("María López", PersonKey.displayName("  María    López "))
    }

    @Test
    fun enie_isTreatedLikeAnAccent() {
        // Regla documentada: se ignoran las marcas diacríticas, incluida la virgulilla de la ñ.
        assertEquals(PersonKey.keyOf("Munoz"), PersonKey.keyOf("Muñoz"))
    }
}
