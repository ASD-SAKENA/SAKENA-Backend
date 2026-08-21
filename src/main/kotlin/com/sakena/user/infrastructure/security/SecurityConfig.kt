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
    private val resident = Role.RESIDENT.name
    private val staff = Role.STAFF.name
    private val admin = Role.ADMIN.name

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
                        // Actuator listens on its own port (management.server.port),
                        // which the public ingress does not route — so these are
                        // reachable from inside the cluster only: kubelet probes
                        // and the in-cluster Prometheus scraper.
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
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

                    // Residents submit claims and read only their own payment views.
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasRole(resident)
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/payments",
                        "/api/v1/payments/submissions"
                    ).hasRole(resident)
                    // The application service additionally checks resident ownership.
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/*/receipt")
                    .hasAnyRole(resident, manager)

                    // Residents may fund only the personal wallet bound to their identity.
                    .requestMatchers(HttpMethod.POST, "/api/v1/wallets/me/top-ups").hasRole(resident)

                    // ─── Admin-only surface ───
                    .requestMatchers("/api/v1/users/**").hasRole(admin)

                    // ─── Manager-only surface ───
                    .requestMatchers("/api/v1/dashboard/manager").hasRole(manager)
                    .requestMatchers("/api/v1/staff").hasRole(manager)
                    .requestMatchers("/api/v1/invitations/**").hasRole(manager)
                    .requestMatchers("/api/v1/charge-periods/**").hasRole(manager)
                    .requestMatchers(HttpMethod.POST, "/api/v1/invoices/*/payments").hasRole(manager)
                    .requestMatchers(HttpMethod.GET, "/api/v1/invoices/*/items").hasRole(resident)
                    .requestMatchers(HttpMethod.GET, "/api/v1/payments/pending").hasRole(manager)
                    .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/payments/*/confirm",
                        "/api/v1/payments/*/reject"
                    ).hasRole(manager)
                    .requestMatchers("/api/v1/wallets/building/**").hasRole(manager)
                    .requestMatchers("/api/v1/wallets/settle/**").hasRole(manager)
                    .requestMatchers("/api/v1/service-requests/admin").hasRole(manager)
                    .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/service-requests/*/approve",
                        "/api/v1/service-requests/*/reject",
                        "/api/v1/service-requests/*/assign",
                        "/api/v1/service-requests/*/cost-responsibility"
                    ).hasRole(manager)
                    // Support tickets are a resident-to-manager conversation:
                    // staff are refused here as well as in the service.
                    .requestMatchers(HttpMethod.POST, "/api/v1/support-tickets").hasRole(resident)
                    .requestMatchers(HttpMethod.GET, "/api/v1/support-tickets/mine").hasRole(resident)
                    .requestMatchers(HttpMethod.GET, "/api/v1/support-tickets").hasRole(manager)
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/support-tickets/*/answer").hasRole(manager)
                    .requestMatchers("/api/v1/support-tickets/**")
                    .hasAnyRole(resident, manager)
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
