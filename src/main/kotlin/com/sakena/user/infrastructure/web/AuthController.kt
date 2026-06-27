package com.sakena.user.infrastructure.web

import com.sakena.user.application.AuthService
import com.sakena.user.application.LoginCommand
import com.sakena.user.application.RegisterCommand
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
        val token = authService.login(LoginCommand(user.username, request.password))
        return AuthResponse(token, user.username, user.role.name)
    }

    @PostMapping("/login")
    @Operation(summary = "login")
    fun login(@RequestBody @Valid request: LoginRequest): AuthResponse {
        val command = LoginCommand(request.username, request.password)
        val token = authService.login(command)
        return AuthResponse(token, request.username, "USER")
    }
}
