package com.sakena.user.infrastructure.security

import com.sakena.user.application.JwtTokenProvider
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority

class JwtAuthenticationFilterTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var filter: JwtAuthenticationFilter
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var chain: FilterChain

    @BeforeEach
    fun setup() {
        jwtTokenProvider = mockk()
        filter = JwtAuthenticationFilter(jwtTokenProvider)
        request = mockk()
        response = mockk()
        chain = mockk()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `doFilterInternal should authenticate when token is valid`() {
        val token = "valid.jwt.token"
        val username = "john"

        every { request.getHeader("Authorization") } returns "Bearer $token"
        every { jwtTokenProvider.validateToken(token) } returns true
        every { jwtTokenProvider.extractUsername(token) } returns username
        every { chain.doFilter(request, response) } just Runs

        filter.doFilterInternal(request, response, chain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertEquals(username, authentication.principal)
        assertTrue(authentication.authorities.contains(SimpleGrantedAuthority("ROLE_USER")))
        verify(exactly = 1) { chain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal should not set authentication when token is missing`() {
        every { request.getHeader("Authorization") } returns null
        every { chain.doFilter(request, response) } just Runs

        filter.doFilterInternal(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 1) { chain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal should not set authentication when token is invalid`() {
        val token = "invalid"
        every { request.getHeader("Authorization") } returns "Bearer $token"
        every { jwtTokenProvider.validateToken(token) } returns false
        every { chain.doFilter(request, response) } just Runs

        filter.doFilterInternal(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 1) { chain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal should not set authentication when header doesn't start with Bearer`() {
        every { request.getHeader("Authorization") } returns "Basic something"
        every { chain.doFilter(request, response) } just Runs

        filter.doFilterInternal(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 1) { chain.doFilter(request, response) }
    }
}
