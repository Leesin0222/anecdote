package com.yongjincompany.anecdote.adapter.admob

import com.google.android.ump.ConsentInformation
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentCheckerTest {

    @Test
    fun `UmpConsentChecker flags issue when canRequestAds is false`() {
        val info = mockk<ConsentInformation> {
            every { canRequestAds() } returns false
        }
        assertTrue(UmpConsentChecker(info).isConsentIssue())
    }

    @Test
    fun `UmpConsentChecker clears issue when canRequestAds is true`() {
        val info = mockk<ConsentInformation> {
            every { canRequestAds() } returns true
        }
        assertFalse(UmpConsentChecker(info).isConsentIssue())
    }
}
