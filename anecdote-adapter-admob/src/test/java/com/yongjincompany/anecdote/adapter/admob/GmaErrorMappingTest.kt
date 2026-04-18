package com.yongjincompany.anecdote.adapter.admob

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.yongjincompany.anecdote.signal.ErrorType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class GmaErrorMappingTest {

    private fun errorWithCode(code: Int): LoadAdError = mockk {
        every { this@mockk.code } returns code
    }

    @Test
    fun `ERROR_CODE_INTERNAL_ERROR maps to INTERNAL_ERROR`() {
        assertEquals(
            ErrorType.INTERNAL_ERROR,
            errorWithCode(AdRequest.ERROR_CODE_INTERNAL_ERROR).toAnecdoteErrorType(),
        )
    }

    @Test
    fun `ERROR_CODE_INVALID_REQUEST maps to INVALID_REQUEST`() {
        assertEquals(
            ErrorType.INVALID_REQUEST,
            errorWithCode(AdRequest.ERROR_CODE_INVALID_REQUEST).toAnecdoteErrorType(),
        )
    }

    @Test
    fun `ERROR_CODE_NETWORK_ERROR maps to NETWORK_ERROR`() {
        assertEquals(
            ErrorType.NETWORK_ERROR,
            errorWithCode(AdRequest.ERROR_CODE_NETWORK_ERROR).toAnecdoteErrorType(),
        )
    }

    @Test
    fun `ERROR_CODE_NO_FILL maps to NO_FILL`() {
        assertEquals(
            ErrorType.NO_FILL,
            errorWithCode(AdRequest.ERROR_CODE_NO_FILL).toAnecdoteErrorType(),
        )
    }

    @Test
    fun `unrecognized code maps to UNKNOWN`() {
        assertEquals(ErrorType.UNKNOWN, errorWithCode(-999).toAnecdoteErrorType())
        assertEquals(ErrorType.UNKNOWN, errorWithCode(42).toAnecdoteErrorType())
    }
}
