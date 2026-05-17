package com.yongjincompany.anecdote.config

public class PolicyConfig internal constructor(
    public val thresholdSuspected: Int,
    public val thresholdBlocked: Int,
    public val maxEvaluationsPerSession: Int,
    public val disabledRegionMccs: Set<Int>,
    public val weights: SignalWeights,
) {
    public class Builder {
        public var thresholdSuspected: Int = 40
        public var thresholdBlocked: Int = 70
        public var maxEvaluationsPerSession: Int = 3
        public var disabledRegionMccs: Set<Int> = setOf(MCC_CHINA)
        public var weights: SignalWeights = SignalWeights.DEFAULT

        internal fun build(): PolicyConfig {
            require(thresholdSuspected in 0..100) {
                "thresholdSuspected must be in 0..100, got $thresholdSuspected"
            }
            require(thresholdBlocked in 0..100) {
                "thresholdBlocked must be in 0..100, got $thresholdBlocked"
            }
            require(thresholdSuspected <= thresholdBlocked) {
                "thresholdSuspected ($thresholdSuspected) must not exceed thresholdBlocked ($thresholdBlocked)"
            }
            require(maxEvaluationsPerSession > 0) {
                "maxEvaluationsPerSession must be positive, got $maxEvaluationsPerSession"
            }
            return PolicyConfig(
                thresholdSuspected = thresholdSuspected,
                thresholdBlocked = thresholdBlocked,
                maxEvaluationsPerSession = maxEvaluationsPerSession,
                disabledRegionMccs = disabledRegionMccs,
                weights = weights,
            )
        }
    }

    public companion object {
        public const val MCC_CHINA: Int = 460
    }
}
