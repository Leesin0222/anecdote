package com.yongjincompany.anecdote.signal

/**
 * Why a probe was marked unreachable. Lets the evaluator tell a deliberate block (sinkhole,
 * NXDOMAIN on a live ad domain) apart from a flaky network (timeout).
 */
public enum class ProbeFailureReason {
    /** DNS answered with a sinkhole address (0.0.0.0, loopback, or a private/LAN address). */
    DNS_SINKHOLE,

    /** DNS answered "no such host" (or an empty answer). */
    DNS_NXDOMAIN,

    /** DNS lookup timed out or failed at the transport level. */
    DNS_FAILURE,

    /** Host resolved but the TLS handshake failed — typical of interception/blocking proxies. */
    TLS_FAILURE,

    /** Host resolved but the HTTP connection failed (refused, reset, timeout). */
    HTTP_UNREACHABLE,
}
