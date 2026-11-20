package com.yongjincompany.anecdote.config

public class PolicyConfig internal constructor(
    public val thresholdSuspected: Int,
    public val thresholdBlocked: Int,
    public val maxEvaluationsPerSession: Int,
    public val disabledRegionMccs: Set<Int>,
    public val weights: SignalWeights,
    public val minAdProbeSamples: Int,
    public val minControlProbeSamples: Int,
) {
    /** Returns a builder pre-filled with this config's values. */
    public fun toBuilder(): Builder = Builder().also {
        it.thresholdSuspected = thresholdSuspected
        it.thresholdBlocked = thresholdBlocked
        it.maxEvaluationsPerSession = maxEvaluationsPerSession
        it.disabledRegionMccs = disabledRegionMccs
        it.weights = weights
        it.minAdProbeSamples = minAdProbeSamples
        it.minControlProbeSamples = minControlProbeSamples
    }

    public class Builder {
        public var thresholdSuspected: Int = 40
        public var thresholdBlocked: Int = 70
        public var maxEvaluationsPerSession: Int = 3
        public var disabledRegionMccs: Set<Int> = setOf(MCC_CHINA)
        public var weights: SignalWeights = SignalWeights.DEFAULT

        /**
         * Ad-domain probe results needed in the window before the probe signal counts at
         * full weight. Below this the probe contribution is scaled down proportionally, so a
         * single failed probe can't push the verdict to Blocked on its own.
         */
        public var minAdProbeSamples: Int = 4

        /** Control-domain probe results needed before the probe signal counts at full weight. */
        public var minControlProbeSamples: Int = 2

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
            require(minAdProbeSamples > 0) {
                "minAdProbeSamples must be positive, got $minAdProbeSamples"
            }
            require(minControlProbeSamples > 0) {
                "minControlProbeSamples must be positive, got $minControlProbeSamples"
            }
            return PolicyConfig(
                thresholdSuspected = thresholdSuspected,
                thresholdBlocked = thresholdBlocked,
                maxEvaluationsPerSession = maxEvaluationsPerSession,
                disabledRegionMccs = disabledRegionMccs,
                weights = weights,
                minAdProbeSamples = minAdProbeSamples,
                minControlProbeSamples = minControlProbeSamples,
            )
        }
    }

    public companion object {
        public const val MCC_CHINA: Int = 460
    }
}
