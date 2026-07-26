package com.sakena.user.infrastructure.security

import com.sakena.user.domain.Role
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Authorization is enforced here as well as on the controllers, so an endpoint
 * is never left wide open just because someone forgot an annotation.
 *
 * Rules are evaluated in declaration order — first match wins — so the
 * resident/staff carve-outs must be declared before the broader manager rules
 * they sit underneath, for example facility bookings nested inside the
 * otherwise manager-only facilities path.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authRateLimitFilter: AuthRateLimitFilter,
) {

    private val manager = Role.MANAGER.name
    private val staff = Role.STAFF.name

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/actuator/health",
                        // The join screen reads the invitation before the invitee signs in.
                        "/api/v1/invitations/preview"
                    ).permitAll()

                    // ─── Carve-outs that sit under a manager-only prefix ───
                    // Residents book and cancel their own facility slots.
                    .requestMatchers("/api/v1/facilities/*/bookings/**").authenticated()
                    // Workers progress and complete the requests assigned to them.
                    .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/service-requests/*/start-progress",
                        "/api/v1/service-requests/*/complete"
                    ).hasAnyRole(staff, manager)

                    // Accepting an invitation only needs a signed-in user, not a manager.
                    .requestMatchers(HttpMethod.POST, "/api/v1/invitations/accept").authenticated()

                    // ─── Manager-only surface ───
                    .requestMatchers("/api/v1/dashboard/manager").hasRole(manager)
                    .requestMatchers("/api/v1/users/**").hasRole(manager)
                    .requestMatchers("/api/v1/invitations/**").hasRole(manager)
                    .requestMatchers("/api/v1/charge-periods/**").hasRole(manager)
                    .requestMatchers(HttpMethod.POST, "/api/v1/invoices/*/payments").hasRole(manager)
                    .requestMatchers("/api/v1/wallets/building/**").hasRole(manager)
                    .requestMatchers("/api/v1/wallets/settle/**").hasRole(manager)
                    .requestMatchers("/api/v1/service-requests/admin").hasRole(manager)
                    .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/service-requests/*/approve",
                        "/api/v1/service-requests/*/reject",
                        "/api/v1/service-requests/*/assign"
                    ).hasRole(manager)
                    .requestMatchers(HttpMethod.POST, "/api/v1/announcements").hasRole(manager)
                    .requestMatchers(HttpMethod.POST, "/api/v1/polls").hasRole(manager)
                    .requestMatchers(HttpMethod.POST, "/api/v1/polls/*/close").hasRole(manager)
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/buildings",
                        "/api/v1/apartments",
                        "/api/v1/facilities"
                    ).hasRole(manager)
                    .requestMatchers(
                        HttpMethod.PUT,
                        "/api/v1/buildings/**",
                        "/api/v1/apartments/**",
                        "/api/v1/facilities/**"
                    ).hasRole(manager)
                    .requestMatchers(
                        HttpMethod.DELETE,
                        "/api/v1/buildings/**",
                        "/api/v1/apartments/**",
                        "/api/v1/facilities/**"
                    ).hasRole(manager)

                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            // Throttling runs first, so a flood never reaches password hashing.
            .addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
