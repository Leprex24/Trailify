package com.trailify.backend.account

import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProfileUpdateTest {

    @Test
    fun `pusty body nic nie zmienia`() {
        val update = parseProfileUpdate(emptyMap())
        assertThat(update.displayName).isNull()
        assertThat(update.changeAvatar).isFalse()
    }

    @Test
    fun `nazwa jest przycinana`() {
        assertThat(parseProfileUpdate(mapOf("displayName" to "  Jan  ")).displayName).isEqualTo("Jan")
    }

    @Test
    fun `za krotka lub za dluga nazwa jest odrzucana`() {
        assertThatThrownBy { parseProfileUpdate(mapOf("displayName" to "A")) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { parseProfileUpdate(mapOf("displayName" to "a".repeat(51))) }
            .isInstanceOf(ApiException::class.java)
        assertThat(parseProfileUpdate(mapOf("displayName" to "a".repeat(50))).displayName).hasSize(50)
    }

    @Test
    fun `nazwa, ktora nie jest tekstem, jest odrzucana`() {
        assertThatThrownBy { parseProfileUpdate(mapOf("displayName" to 5)) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { parseProfileUpdate(mapOf("displayName" to null)) }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `avatar null usuwa avatar`() {
        val update = parseProfileUpdate(mapOf("avatarUrl" to null))
        assertThat(update.changeAvatar).isTrue()
        assertThat(update.avatarUrl).isNull()
    }

    @Test
    fun `avatar musi byc adresem https`() {
        val ok = parseProfileUpdate(mapOf("avatarUrl" to "https://example.com/a.jpg"))
        assertThat(ok.avatarUrl).isEqualTo("https://example.com/a.jpg")
        assertThatThrownBy { parseProfileUpdate(mapOf("avatarUrl" to "http://example.com/a.jpg")) }
            .isInstanceOf(ApiException::class.java)
        assertThatThrownBy { parseProfileUpdate(mapOf("avatarUrl" to "to nie adres")) }
            .isInstanceOf(ApiException::class.java)
        assertThatThrownBy { parseProfileUpdate(mapOf("avatarUrl" to "javascript:alert(1)")) }
            .isInstanceOf(ApiException::class.java)
    }
}
