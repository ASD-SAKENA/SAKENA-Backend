package com.sakena.user.infrastructure.web

import com.sakena.user.application.AuthService
import com.sakena.user.application.ForgotPasswordCommand
import com.sakena.user.application.LoginCommand
import com.sakena.user.application.RegisterCommand
import com.sakena.user.application.ResetPasswordCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "register and login")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "register")
    fun register(@RequestBody @Valid request: RegisterRequest): AuthResponse {
        val command = RegisterCommand(
            username = request.username,
            email = request.email,
            password = request.password,
            role = request.role
        )
        val user = authService.register(command)
        val result = authService.login(LoginCommand(user.username, request.password))
        return AuthResponse(result.token, result.user.username, result.user.role.name)
    }

    @PostMapping("/login")
    @Operation(summary = "login")
    fun login(@RequestBody @Valid request: LoginRequest): AuthResponse {
        val result = authService.login(LoginCommand(request.username, request.password))
        return AuthResponse(result.token, result.user.username, result.user.role.name)
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Request password reset")
    fun forgotPassword(@RequestBody @Valid request: ForgotPasswordRequest) {
        authService.forgotPassword(ForgotPasswordCommand(request.email))
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reset password using token")
    fun resetPassword(@RequestBody @Valid request: ResetPasswordRequest): AuthResponse {
        authService.resetPassword(ResetPasswordCommand(request.token, request.newPassword))
        return AuthResponse("", "", "")
    }
}
