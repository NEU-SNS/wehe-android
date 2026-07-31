package mobi.meddle.wehe.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RequestSetTest {

    @Test
    fun defaultConstructor_hasExpectedDefaults() {
        val rs = RequestSet()

        assertThat(rs.cSPair).isNull()
        assertThat(rs.timestamp).isEqualTo(0.0)
        assertThat(rs.payload).isNull()
        assertThat(rs.responseLen).isEqualTo(-1)
        assertThat(rs.responseHash).isNull()
        assertThat(rs.end).isFalse()
    }

    @Test
    fun isUDP_trueWhenResponseLenIsDefaultMinusOne() {
        // isUDP() is defined purely as `responseLen == -1` (RequestSet.kt line 15)
        val rs = RequestSet()
        assertThat(rs.isUDP()).isTrue()
    }

    @Test
    fun isUDP_falseWhenResponseLenIsSetToNonNegative() {
        val rs = RequestSet(responseLen = 100)
        assertThat(rs.isUDP()).isFalse()
    }

    @Test
    fun isUDP_trueOnlyForExactlyMinusOne_notForOtherNegativeValues() {
        // isUDP() checks equality to -1, not "< 0" - so another negative sentinel like -2 would
        // NOT be treated as UDP. This documents the exact contract rather than assuming any
        // negative value means UDP.
        val rs = RequestSet(responseLen = -2)
        assertThat(rs.isUDP()).isFalse()
    }

    @Test
    fun allArgsConstructor_setsAllFields() {
        val payload = byteArrayOf(1, 2, 3)
        val rs = RequestSet(
            cSPair = "1.2.3.4.1000-5.6.7.8.2000",
            timestamp = 12.5,
            payload = payload,
            responseLen = 42,
            responseHash = "abcdef",
            end = true
        )

        assertThat(rs.cSPair).isEqualTo("1.2.3.4.1000-5.6.7.8.2000")
        assertThat(rs.timestamp).isEqualTo(12.5)
        assertThat(rs.payload).isSameInstanceAs(payload)
        assertThat(rs.responseLen).isEqualTo(42)
        assertThat(rs.responseHash).isEqualTo("abcdef")
        assertThat(rs.end).isTrue()
    }

    @Test
    fun toString_includesCSPairResponseLenAndTimestamp_butNotPayloadOrHashOrEnd() {
        val rs = RequestSet(
            cSPair = "csp",
            timestamp = 1.0,
            payload = byteArrayOf(9),
            responseLen = 7,
            responseHash = "hash",
            end = true
        )
        val s = rs.toString()

        assertThat(s).contains("cSPair=csp")
        assertThat(s).contains("responseLen=7")
        assertThat(s).contains("timestamp=1.0")
        // custom toString() intentionally omits payload/responseHash/end - verifying that
        // omission is real, not just an oversight in this test.
        assertThat(s).doesNotContain("hash")
        assertThat(s).doesNotContain("payload")
    }

    @Test
    fun equals_isFalseForEqualContentDifferentArrayInstances() {
        // LATENT GOTCHA: RequestSet is a Kotlin `data class` with a ByteArray property. Kotlin's
        // generated equals()/hashCode() use `==` per-property, and for arrays `==` is reference
        // equality (not content equality; that requires `contentEquals`/`contentHashCode`).
        // So two RequestSets holding byte-for-byte identical payloads in different array objects
        // compare as NOT equal - which could surprise anyone assuming data class equals() means
        // full structural equality (e.g. de-duplication or test assertions relying on equals()).
        val rs1 = RequestSet(cSPair = "x", payload = byteArrayOf(1, 2, 3))
        val rs2 = RequestSet(cSPair = "x", payload = byteArrayOf(1, 2, 3))

        assertThat(rs1.payload).isNotSameInstanceAs(rs2.payload)
        assertThat(rs1).isNotEqualTo(rs2)
    }

    @Test
    fun equals_isTrueWhenSameArrayInstanceIsShared() {
        val sharedPayload = byteArrayOf(1, 2, 3)
        val rs1 = RequestSet(cSPair = "x", payload = sharedPayload)
        val rs2 = RequestSet(cSPair = "x", payload = sharedPayload)

        assertThat(rs1).isEqualTo(rs2)
    }

    @Test
    fun mutableFields_canBeReassigned() {
        // RequestSet uses `var` for every property, so instances are fully mutable after
        // construction - unusual for a "data" class typically expected to be immutable.
        val rs = RequestSet()
        rs.cSPair = "new-pair"
        rs.timestamp = 99.0
        rs.responseLen = 5

        assertThat(rs.cSPair).isEqualTo("new-pair")
        assertThat(rs.timestamp).isEqualTo(99.0)
        assertThat(rs.responseLen).isEqualTo(5)
    }
}
