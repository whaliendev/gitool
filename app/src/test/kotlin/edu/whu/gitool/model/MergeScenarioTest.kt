package edu.whu.gitool.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.security.InvalidParameterException

class MergeScenarioTest {

    @Test
    fun `Invalid commit id should throw InvalidParameterException`() {
        assertThrows<InvalidParameterException> {
            MergeScenario(null, "abcdefgh", "f2238e717b31cf7a2ccec1ec82df0dc0f696ec47")
        }
        assertThrows<InvalidParameterException> {
            MergeScenario(null, "f2238e717b31cf7a2ccec1ec82df0dc0f696ec47", "kkkk")
        }
        assertDoesNotThrow {
            MergeScenario(null, "f2238e717b31cf", "7ce82e")
        }
        assertThrows<InvalidParameterException> {
            val ms = MergeScenario(null, "f2238e717b31cf7a2ccec1ec82df0dc0f696ec47", "7ce82e")
            MergeTriple(ms, "uvw")
        }
        assertDoesNotThrow {
            val ms = MergeScenario(null, "f2238e717b31cf7a2ccec1ec82df0dc0f696ec47", "7ce82e")
            MergeTriple(ms, "00fd3d92c6e31a0498527af83f2f510f529dbd9d")
        }
        assertThrows<InvalidParameterException> {
            val ms = MergeScenario(null, "f2238e717b31cf7a2ccec1ec82df0dc0f696ec47", "7ce82e")
            val mt = MergeTriple(ms, "00fd3d92c6e31a0498527af83f2f510f529dbd9d")
            MergeQuadruple(mt, "jjj")
        }
        assertDoesNotThrow {
            val ms = MergeScenario(null, "f2238e717b31cf7a2ccec1ec82df0dc0f696ec47", "7ce82e")
            val mt = MergeTriple(ms, "00fd3d92c6e31a0498527af83f2f510f529dbd9d")
            MergeQuadruple(mt, "63f439dc1e2b9f568c615c1d1329b8ace58928a6")
        }
    }
}
