package com.trailify.backend.security

import com.trailify.backend.account.ProfileLookup
import com.trailify.backend.admin.AdminController
import com.trailify.backend.admin.AdminService
import com.trailify.backend.poi.PoiController
import com.trailify.backend.poi.PoiService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Kto ma dostęp do czego (SecurityConfig) dla endpointów ciekawych punktów i panelu administratora.
 * Bez bazy: serwisy są atrapami, sprawdzamy tylko odpowiedzi 200/401/403 warstwy HTTP.
 */
@WebMvcTest(controllers = [PoiController::class, AdminController::class])
@Import(SecurityConfig::class, ProfileJwtAuthenticationConverter::class)
class EndpointSecurityTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var pois: PoiService

    @MockitoBean
    private lateinit var admin: AdminService

    @MockitoBean
    private lateinit var profiles: ProfileLookup

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private fun user() = jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))
    private fun administrator() = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    // --- publiczne odczyty (security: [] w openapi.yaml) ---

    @Test
    fun `lista punktow na mapie jest publiczna`() {
        mvc.get("/api/v1/pois?bbox=22.45,51.20,22.65,51.30").andExpect { status { isOk() } }
    }

    @Test
    fun `szczegoly punktu sa publiczne`() {
        mvc.get("/api/v1/pois/1").andExpect { status { isOk() } }
    }

    // --- wymagają logowania ---

    @Test
    fun `zgloszenie punktu bez tokena daje 401`() {
        mvc.post("/api/v1/pois") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"A","description":"B","latitude":51.0,"longitude":22.0}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `zbieranie punktu bez tokena daje 401`() {
        mvc.post("/api/v1/pois/1/collect") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"latitude":51.0,"longitude":22.0}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `kolekcja bez tokena daje 401`() {
        mvc.get("/api/v1/me/poi-collection").andExpect { status { isUnauthorized() } }
    }

    // --- panel administratora ---

    @Test
    fun `zwykly uzytkownik nie ma dostepu do moderacji punktow`() {
        mvc.get("/api/v1/admin/pois") { with(user()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `zwykly uzytkownik nie moze zatwierdzac punktow`() {
        mvc.post("/api/v1/admin/pois/1/approve") { with(user()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `zwykly uzytkownik nie widzi listy kont`() {
        mvc.get("/api/v1/admin/users") { with(user()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `panel administratora bez tokena daje 401`() {
        mvc.get("/api/v1/admin/users").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `administrator ma dostep do moderacji i kont`() {
        mvc.get("/api/v1/admin/pois?status=PENDING") { with(administrator()) }.andExpect { status { isOk() } }
        mvc.get("/api/v1/admin/users?q=anna") { with(administrator()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `odrzucenie punktu dziala bez tresci zadania`() {
        // RejectRequest jest opcjonalny (powód można pominąć)
        mvc.post("/api/v1/admin/pois/1/reject") { with(administrator()) }.andExpect { status { isOk() } }
    }
}
