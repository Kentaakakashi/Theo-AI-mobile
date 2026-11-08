package com.example.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsernamePolicyTest {
    @Test
    fun normalizesUsernameBeforeReservation() {
        assertEquals("naren_k", UsernamePolicy.normalize("  Naren_K  "))
    }

    @Test
    fun acceptsValidUsernames() {
        assertNull(UsernamePolicy.validationError("theo_user_16"))
    }

    @Test
    fun rejectsShortOrUnsafeUsernames() {
        assertNotNull(UsernamePolicy.validationError("ab"))
        assertNotNull(UsernamePolicy.validationError("not valid"))
        assertNotNull(UsernamePolicy.validationError("name.with.dot"))
    }
}
