package com.novaaforgee.novacapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaCaptureUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun capturePackage_isValid() {
        val packageName = "com.novaaforgee.novacapture"
        assertTrue(packageName.startsWith("com.novaaforgee"))
    }
}
