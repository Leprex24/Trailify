package com.trailify.backend.social

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.http.HttpStatus

class SocialRulesTest {

    @Test
    fun `wlasny profil widac zawsze, takze prywatny`() {
        assertThat(canViewDetails(isOwnProfile = true, isPrivate = true)).isTrue()
        assertThat(canViewDetails(isOwnProfile = true, isPrivate = false)).isTrue()
    }

    @Test
    fun `cudzy profil publiczny widac, a prywatny nie`() {
        assertThat(canViewDetails(isOwnProfile = false, isPrivate = false)).isTrue()
        assertThat(canViewDetails(isOwnProfile = false, isPrivate = true)).isFalse()
    }

    @Test
    fun `szczegoly prywatnego profilu to 403 PRIVATE_PROFILE`() {
        val e = privateProfileError()
        assertThat(e.status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(e.code).isEqualTo(CODE_PRIVATE_PROFILE)
    }

    @Test
    fun `samego siebie nie mozna obserwowac`() {
        val e = followRejection(isSelf = true, targetIsPrivate = false, alreadyFollowing = false)!!
        assertThat(e.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(e.code).isEqualTo(CODE_SELF_FOLLOW)
    }

    @Test
    fun `profil prywatny mozna zaczac obserwowac tylko jesli juz sie go obserwuje`() {
        val first = followRejection(isSelf = false, targetIsPrivate = true, alreadyFollowing = false)!!
        assertThat(first.code).isEqualTo(CODE_PRIVATE_PROFILE)
        assertThat(followRejection(isSelf = false, targetIsPrivate = true, alreadyFollowing = true)).isNull()
    }

    @Test
    fun `profil publiczny mozna obserwowac`() {
        assertThat(followRejection(isSelf = false, targetIsPrivate = false, alreadyFollowing = false)).isNull()
    }

    // --- wyszukiwanie osób ---

    @Test
    fun `fraza wyszukiwania jest przycinana`() {
        assertThat(parseUserQuery("  Anna  ")).isEqualTo("Anna")
    }

    @Test
    fun `zbyt krotka albo zbyt dluga fraza to VALIDATION_ERROR`() {
        listOf(null, "", " a ", "x".repeat(USER_QUERY_MAX + 1)).forEach { raw ->
            assertThatThrownBy { parseUserQuery(raw) }
                .isInstanceOf(ApiException::class.java)
                .extracting("code").isEqualTo("VALIDATION_ERROR")
        }
    }

    @Test
    fun `znaki specjalne LIKE sa traktowane doslownie`() {
        assertThat(escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(escapeLike("100%")).isEqualTo("100\\%")
        assertThat(escapeLike("a\\b")).isEqualTo("a\\\\b")
        assertThat(escapeLike("Anna")).isEqualTo("Anna")
    }
}
