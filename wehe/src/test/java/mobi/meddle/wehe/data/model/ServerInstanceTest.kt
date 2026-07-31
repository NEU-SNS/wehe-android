package mobi.meddle.wehe.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * ServerInstance is a plain (non-data) class holder: `server` is a mutable var, `port` is a
 * read-only val, no equals/hashCode/toString overrides (uses default Object identity semantics).
 */
class ServerInstanceTest {

    @Test
    fun constructor_setsServerAndPort() {
        val instance = ServerInstance("meddle.mobi", "443")

        assertThat(instance.server).isEqualTo("meddle.mobi")
        assertThat(instance.port).isEqualTo("443")
    }

    @Test
    fun server_isMutable() {
        val instance = ServerInstance("meddle.mobi", "443")
        instance.server = "other-server.mobi"
        assertThat(instance.server).isEqualTo("other-server.mobi")
    }

    @Test
    fun equals_usesDefaultIdentityNotStructuralEquality() {
        // ServerInstance is a plain `class`, not a `data class`, so it inherits Object's identity
        // equals()/hashCode() - two instances with identical server/port values are NOT equal.
        // Documenting this so callers don't assume structural equality (e.g. when deduplicating
        // a list of ServerInstance via a Set or equals-based contains()).
        val a = ServerInstance("meddle.mobi", "443")
        val b = ServerInstance("meddle.mobi", "443")

        assertThat(a).isNotEqualTo(b)
        assertThat(a == a).isTrue()
    }
}
