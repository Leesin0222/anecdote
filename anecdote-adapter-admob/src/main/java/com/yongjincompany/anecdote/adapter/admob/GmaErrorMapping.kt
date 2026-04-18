package com.yongjincompany.anecdote.adapter.admob

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.yongjincompany.anecdote.signal.ErrorType

internal fun AdError.toAnecdoteErrorType(): ErrorType = when (code) {
    AdRequest.ERROR_CODE_INTERNAL_ERROR -> ErrorType.INTERNAL_ERROR
    AdRequest.ERROR_CODE_INVALID_REQUEST -> ErrorType.INVALID_REQUEST
    AdRequest.ERROR_CODE_NETWORK_ERROR -> ErrorType.NETWORK_ERROR
    AdRequest.ERROR_CODE_NO_FILL -> ErrorType.NO_FILL
    else -> ErrorType.UNKNOWN
}
