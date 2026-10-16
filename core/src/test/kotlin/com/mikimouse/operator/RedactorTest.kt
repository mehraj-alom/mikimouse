package com.mikimouse.operator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedactorTest {
    @Test fun masksPii() {
        val s = Redactor.mask("mail me at joe@x.com or +1 555-123-4567, OTP 482910")
        assertTrue("joe@x.com" !in s && "482910" !in s && "555" !in s)
        assertTrue(Redactor.looksSensitive("pin 1234"))
        assertEquals("hello world", Redactor.mask("hello world"))
    }
}
