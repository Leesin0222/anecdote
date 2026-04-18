package com.yongjincompany.anecdote.config

public class PolicyConfig internal constructor(
    public val thresholdSuspected: Int,
    public val thresholdBlocked: Int,
    public val maxEvaluationsPerSession: Int,
    public val disabledRegionMccs: Set<Int>,
) {
    public class Builder {
        public var thresholdSuspected: Int = 40
        public var thresholdBlocked: Int = 70
        public var maxEvaluationsPerSession: Int = 3
        public var disabledRegionMccs: Set<Int> = setOf(MCC_CHINA)

        internal fun build(): PolicyConfig = PolicyConfig(
            thresholdSuspected = thresholdSuspected,
            thresholdBlocked = thresholdBlocked,
            maxEvaluationsPerSession = maxEvaluationsPerSession,
            disabledRegionMccs = disabledRegionMccs,
        )
    }

    public companion object {
        public const val MCC_CHINA: Int = 460
    }
}
