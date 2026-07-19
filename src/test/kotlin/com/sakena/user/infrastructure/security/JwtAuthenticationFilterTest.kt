package com.sakena.user.infrastructure.security

import com.sakena.user.application.JwtTokenProvider
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.Instant

class JwtAuthenticationFilterTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var userRepository: UserRepository
    private lateinit var filter: JwtAuthenticationFilter
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var chain: FilterChain

    @BeforeEach
    fun setup() {
        jwtTokenProvider = mockk()
        userRepository = mockk()
        filter = JwtAuthenticationFilter(jwtTokenProvider, userRepository)
        request = mockk()
        response = mockk()
        chain = mockk()
        SecurityContextHolder.clearContext()
    }

    private fun createUser(username: String = "john", active: Boolean = true): User {
        return User.reconstitute(
            id = UserId.generate(),
            username = username,
            email = "$username@example.com",
            passwordHash = "hashed",
            role = Role.RESIDENT,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = active
        )
    }

    @Test
    fun `doFilterInternal should authenticate when token is valid and user is active`() {
        val token = "valid.jwt.token"
        val username = "john"

        every { request.getHeader("Authorization") } returns "Bearer $token"
        every { jwtTokenProvider.validateToken(token) } returns true
        every { jwtTokenProvider.extractUsername(token) } returns username
        every { userRepository.findByUsername(username) } returns createUser(username)
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

    @Test
    fun `doFilterInternal should not set authentication when user is deactivated`() {
        val token = "valid.jwt.token"
        val username = "john"

        every { request.getHeader("Authorization") } returns "Bearer $token"
        every { jwtTokenProvider.validateToken(token) } returns true
        every { jwtTokenProvider.extractUsername(token) } returns username
        every { userRepository.findByUsername(username) } returns createUser(username, active = false)
        every { chain.doFilter(request, response) } just Runs

        filter.doFilterInternal(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 1) { chain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal should not set authentication when user no longer exists`() {
        val token = "valid.jwt.token"
        val username = "ghost"

        every { request.getHeader("Authorization") } returns "Bearer $token"
        every { jwtTokenProvider.validateToken(token) } returns true
        every { jwtTokenProvider.extractUsername(token) } returns username
        every { userRepository.findByUsername(username) } returns null
        every { chain.doFilter(request, response) } just Runs

        filter.doFilterInternal(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 1) { chain.doFilter(request, response) }
    }
}
