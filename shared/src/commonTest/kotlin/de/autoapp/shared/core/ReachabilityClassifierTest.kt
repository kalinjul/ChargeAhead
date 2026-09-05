package de.autoapp.shared.core

import de.autoapp.shared.domain.Reachability
import kotlin.test.Test
import kotlin.test.assertEquals

class ReachabilityClassifierTest {

    private val range = 200.0

    @Test
    fun clearlyWithin_isReachable() {
        assertEquals(Reachability.REACHABLE, ReachabilityClassifier.classify(100.0, range))
    }

    @Test
    fun exactlyAtTheComfortThreshold_isStillReachable() {
        assertEquals(Reachability.REACHABLE, ReachabilityClassifier.classify(170.0, range))
    }

    @Test
    fun justOverTheComfortThreshold_isMarginal() {
        assertEquals(Reachability.MARGINAL, ReachabilityClassifier.classify(170.1, range))
    }

    @Test
    fun exactlyAtTheRange_isStillMarginal() {
        assertEquals(Reachability.MARGINAL, ReachabilityClassifier.classify(200.0, range))
    }

    @Test
    fun aboveThat_isUnreachable() {
        assertEquals(Reachability.UNREACHABLE, ReachabilityClassifier.classify(200.1, range))
    }

    @Test
    fun withoutRange_nothingIsReachable() {
        // Below the reserve, range is zero — then everything but the vehicle's
        // own location is unreachable.
        assertEquals(Reachability.UNREACHABLE, ReachabilityClassifier.classify(1.0, 0.0))
        assertEquals(Reachability.REACHABLE, ReachabilityClassifier.classify(0.0, 0.0))
    }
}
