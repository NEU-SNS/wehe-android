package mobi.meddle.wehe.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Result is a pure Kotlin data class holder (all primitive/String fields, no ByteArray, no
 * custom logic) - so only a light construction/getter/equals/copy sanity check is warranted.
 */
class ResultTest {

    private fun sample() = Result(
        isPortTest = true,
        resultNameText = "Netflix",
        appImage = "netflix.png",
        dateText = "2024-01-01",
        differentiationText = "Differentiation detected",
        appThroughput = 12.5,
        nonAppThroughput = 10.0,
        ipType = "IPv4",
        server = "server1",
        carrier = "Verizon",
        isTomography = false,
        differentiationNetwork = "cellular"
    )

    @Test
    fun constructor_setsAllFields() {
        val r = sample()

        assertThat(r.isPortTest).isTrue()
        assertThat(r.resultNameText).isEqualTo("Netflix")
        assertThat(r.appImage).isEqualTo("netflix.png")
        assertThat(r.dateText).isEqualTo("2024-01-01")
        assertThat(r.differentiationText).isEqualTo("Differentiation detected")
        assertThat(r.appThroughput).isEqualTo(12.5)
        assertThat(r.nonAppThroughput).isEqualTo(10.0)
        assertThat(r.ipType).isEqualTo("IPv4")
        assertThat(r.server).isEqualTo("server1")
        assertThat(r.carrier).isEqualTo("Verizon")
        assertThat(r.isTomography).isFalse()
        assertThat(r.differentiationNetwork).isEqualTo("cellular")
    }

    @Test
    fun equals_and_hashCode_areStructural() {
        val r1 = sample()
        val r2 = sample()
        assertThat(r1).isEqualTo(r2)
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode())
    }

    @Test
    fun copy_overridesOnlySpecifiedField() {
        val original = sample()
        val copy = original.copy(appThroughput = 99.0)

        assertThat(copy.appThroughput).isEqualTo(99.0)
        assertThat(copy.resultNameText).isEqualTo(original.resultNameText)
        assertThat(copy).isNotEqualTo(original)
    }
}
