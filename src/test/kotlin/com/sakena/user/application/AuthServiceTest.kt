package com.sakena.user.application

import com.sakena.user.domain.*
import com.sakena.user.domain.exceptions.InvalidCredentialsException
import com.sakena.user.domain.exceptions.TokenInvalidException
import com.sakena.user.domain.exceptions.UserAlreadyExistsException
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var resetTokenRepository: PasswordResetTokenRepository
    private lateinit var emailSender: EmailSender
    private lateinit var authService: AuthService

    private val frontendUrl = "http://frontend.com"
    private val tokenExpMinutes = 60L

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        passwordEncoder = mockk()
        jwtTokenProvider = mockk()
        resetTokenRepository = mockk()
        emailSender = mockk()
        authService = AuthService(
            userRepository,
            passwordEncoder,
            jwtTokenProvider,
            resetTokenRepository,
            emailSender,
            frontendUrl,
            tokenExpMinutes
        )
    }

    // --- Helper to create a User without validation (using reconstitute) ---
    private fun createUser(
        username: String = "john",
        email: String = "john@example.com",
        passwordHash: String = "hashed",
        role: Role = Role.RESIDENT,
        active: Boolean = true
    ): User {
        return User.reconstitute(
            id = UserId.generate(),
            username = username,
            email = email,
            passwordHash = passwordHash,
            role = role,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            active = active
        )
    }

    // ===== REGISTER TESTS =====

    @Test
    fun `register should save and return user when username and email are unique`() {
        val command = RegisterCommand("john", "john@example.com", "password123", "MANAGER")

        every { userRepository.existsByUsername(command.username) } returns false
        every { userRepository.existsByEmail(command.email) } returns false
        every { passwordEncoder.encode(command.password) } returns "encodedPassword"

        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = authService.register(command)

        assertEquals("john", result.username)
        assertEquals("john@example.com", result.email)
        assertEquals("encodedPassword", result.passwordHash)
        assertEquals(Role.MANAGER, result.role)
        assertTrue(result.active)

        verify(exactly = 1) { userRepository.save(any()) }
        val savedUser = savedUserSlot.captured
        assertEquals("john", savedUser.username)
        assertEquals("encodedPassword", savedUser.passwordHash)
        assertEquals(Role.MANAGER, savedUser.role)
    }

    @Test
    fun `register should throw UserAlreadyExistsException if username exists`() {
        val command = RegisterCommand("john", "john@example.com", "password123", null)
        every { userRepository.existsByUsername(command.username) } returns true
        assertThrows<UserAlreadyExistsException> { authService.register(command) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `register should throw UserAlreadyExistsException if email exists`() {
        val command = RegisterCommand("john", "john@example.com", "password123", null)
        every { userRepository.existsByUsername(command.username) } returns false
        every { userRepository.existsByEmail(command.email) } returns true
        assertThrows<UserAlreadyExistsException> { authService.register(command) }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ===== LOGIN TESTS =====

    @Test
    fun `login should return JWT when credentials are valid`() {
        val command = LoginCommand("john", "correct")
        val user = createUser(passwordHash = "hashed_correct")
        every { userRepository.findByUsername(command.username) } returns user
        every { passwordEncoder.matches(command.password, user.passwordHash) } returns true
        every { jwtTokenProvider.generateToken(user.username, user.role.name) } returns "jwt-token"

        val token = authService.login(command)
        assertEquals("jwt-token", token)
        verify(exactly = 1) { jwtTokenProvider.generateToken(any(), any()) }
    }

    @Test
    fun `login should throw InvalidCredentialsException when user not found`() {
        every { userRepository.findByUsername("unknown") } returns null
        assertThrows<InvalidCredentialsException> {
            authService.login(LoginCommand("unknown", "pass"))
        }
    }

    @Test
    fun `login should throw InvalidCredentialsException when password does not match`() {
        val user = createUser(passwordHash = "hashed_correct")
        every { userRepository.findByUsername("john") } returns user
        every { passwordEncoder.matches("wrong", user.passwordHash) } returns false
        assertThrows<InvalidCredentialsException> {
            authService.login(LoginCommand("john", "wrong"))
        }
    }

    @Test
    fun `login should throw DomainException when user is inactive`() {
        val user = createUser(active = false)
        every { userRepository.findByUsername("john") } returns user
        every { passwordEncoder.matches("pass", user.passwordHash) } returns true
        assertThrows<com.sakena.shared.domain.DomainException> {
            authService.login(LoginCommand("john", "pass"))
        }
    }

    // ===== FORGOT PASSWORD TESTS =====

    @Test
    fun `forgotPassword should create token and send email when user exists`() {
        val user = createUser(email = "john@example.com")
        every { userRepository.findByEmail("john@example.com") } returns user
        every { resetTokenRepository.deleteByUserId(user.id) } just Runs
        every { resetTokenRepository.save(any()) } answers { firstArg() }
        every { emailSender.sendPasswordResetEmail(any(), any()) } just Runs

        authService.forgotPassword(ForgotPasswordCommand("john@example.com"))

        verify(exactly = 1) { resetTokenRepository.deleteByUserId(user.id) }
        verify(exactly = 1) { resetTokenRepository.save(any()) }
        verify(exactly = 1) { emailSender.sendPasswordResetEmail(eq(user.email), any()) }
    }

    @Test
    fun `forgotPassword should do nothing if email not found (security)`() {
        every { userRepository.findByEmail("nonexistent@example.com") } returns null
        authService.forgotPassword(ForgotPasswordCommand("nonexistent@example.com"))
        verify(exactly = 0) { resetTokenRepository.deleteByUserId(any()) }
        verify(exactly = 0) { resetTokenRepository.save(any()) }
        verify(exactly = 0) { emailSender.sendPasswordResetEmail(any(), any()) }
    }

    // ===== RESET PASSWORD TESTS =====

    @Test
    fun `resetPassword should update password and mark token used when token is valid`() {
        val userId = UserId.generate()
        val user = createUser(passwordHash = "old_hash")
        val tokenEntity = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = userId,
            token = "valid-token",
            expiresAt = Instant.now().plusSeconds(60),
            used = false
        )
        val command = ResetPasswordCommand("valid-token", "newPassword123")

        every { resetTokenRepository.findByToken(command.token) } returns tokenEntity
        every { userRepository.findById(userId) } returns user
        every { passwordEncoder.encode(command.newPassword) } returns "new_hash"
        every { userRepository.save(any()) } returns user
        every { resetTokenRepository.save(any()) } returns tokenEntity.markUsed()

        authService.resetPassword(command)

        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { resetTokenRepository.save(any()) }
        val savedUser = slot<User>()
        verify { userRepository.save(capture(savedUser)) }
        assertEquals("new_hash", savedUser.captured.passwordHash)
        val savedToken = slot<PasswordResetToken>()
        verify { resetTokenRepository.save(capture(savedToken)) }
        assertTrue(savedToken.captured.used)
    }

    @Test
    fun `resetPassword should throw TokenInvalidException if token not found`() {
        every { resetTokenRepository.findByToken("unknown") } returns null
        assertThrows<TokenInvalidException> {
            authService.resetPassword(ResetPasswordCommand("unknown", "newPass"))
        }
    }

    @Test
    fun `resetPassword should throw TokenInvalidException if token is expired`() {
        val tokenEntity = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = UserId.generate(),
            token = "expired",
            expiresAt = Instant.now().minusSeconds(1),
            used = false
        )
        every { resetTokenRepository.findByToken("expired") } returns tokenEntity
        assertThrows<TokenInvalidException> {
            authService.resetPassword(ResetPasswordCommand("expired", "newPass"))
        }
    }

    @Test
    fun `resetPassword should throw TokenInvalidException if token already used`() {
        val tokenEntity = PasswordResetToken(
            id = PasswordResetTokenId.generate(),
            userId = UserId.generate(),
            token = "used",
            expiresAt = Instant.now().plusSeconds(60),
            used = true
        )
        every { resetTokenRepository.findByToken("used") } returns tokenEntity
        assertThrows<TokenInvalidException> {
            authService.resetPassword(ResetPasswordCommand("used", "newPass"))
        }
    }
}
