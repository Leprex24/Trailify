package com.trailify.backend.security

import com.trailify.backend.account.ProfileLookup
import com.trailify.backend.admin.AdminController
import com.trailify.backend.admin.AdminService
import com.trailify.backend.poi.PoiController
import com.trailify.backend.poi.PoiService
import com.trailify.backend.route.RouteService
import com.trailify.backend.route.SavedRouteController
import com.trailify.backend.social.SocialController
import com.trailify.backend.social.SocialService
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Kto ma dostęp do czego (SecurityConfig) dla endpointów ciekawych punktów i panelu administratora.
 * Bez bazy: serwisy są atrapami, sprawdzamy tylko odpowiedzi 200/401/403 warstwy HTTP.
 */
@WebMvcTest(
    controllers = [PoiController::class, AdminController::class, SavedRouteController::class, SocialController::class],
)
@Import(SecurityConfig::class, ProfileJwtAuthenticationConverter::class)
class EndpointSecurityTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockitoBean
    private lateinit var pois: PoiService

    @MockitoBean
    private lateinit var admin: AdminService

    @MockitoBean
    private lateinit var routes: RouteService

    @MockitoBean
    private lateinit var social: SocialService

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

    // --- zapisane trasy (gwiazdka): wszystko wymaga logowania ---

    @Test
    fun `zapisanie trasy bez tokena daje 401`() {
        mvc.put("/api/v1/routes/1/saved").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `odznaczenie trasy bez tokena daje 401`() {
        mvc.delete("/api/v1/routes/1/saved").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `stan zapisu trasy bez tokena daje 401`() {
        mvc.get("/api/v1/routes/1/saved").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `lista zapisanych tras bez tokena daje 401`() {
        mvc.get("/api/v1/me/saved-routes").andExpect { status { isUnauthorized() } }
    }

    // --- cudze profile i obserwowanie (wszystko wymaga logowania) ---

    private val someUser = "/api/v1/users/6f1c2d3e-0000-4000-8000-000000000001"

    @Test
    fun `cudzy profil bez tokena daje 401`() {
        mvc.get(someUser).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `obserwowanie bez tokena daje 401`() {
        mvc.put("$someUser/follow").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `odobserwowanie bez tokena daje 401`() {
        mvc.delete("$someUser/follow").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `listy obserwujacych i obserwowanych bez tokena daja 401`() {
        mvc.get("$someUser/followers").andExpect { status { isUnauthorized() } }
        mvc.get("$someUser/following").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `trasy i punkty cudzego profilu bez tokena daja 401`() {
        mvc.get("$someUser/routes").andExpect { status { isUnauthorized() } }
        mvc.get("$someUser/poi-collection").andExpect { status { isUnauthorized() } }
    }
}
