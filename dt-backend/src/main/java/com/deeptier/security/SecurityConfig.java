package com.deeptier.security;

import com.deeptier.config.InternalKeyFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            InternalKeyFilter internalKeyFilter,
            JwtAuthFilter jwtAuthFilter,
            @Value("${deeptier.auth.enabled:true}") boolean authEnabled
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(internalKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, InternalKeyFilter.class);

        if (!authEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}"
                            );
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"FORBIDDEN\",\"message\":\"Insufficient role permissions\"}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/approvals/*/approve",
                                "/api/approvals/*/reject",
                                "/api/rfqs/*/push-sap"
                        ).hasAnyRole("APPROVER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/seed/reset").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/approvals/**", "/api/rfqs/**")
                        .hasAnyRole("ANALYST", "APPROVER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/approvals")
                        .hasAnyRole("ANALYST", "APPROVER", "ADMIN")
                        .requestMatchers(
                                "/api/agent/**",
                                "/api/logistics/**",
                                "/api/whatif/**",
                                "/api/supply-chain/**",
                                "/api/audit/**",
                                "/api/tier-map/**"
                        ).hasAnyRole("ANALYST", "APPROVER", "ADMIN")
                        .requestMatchers("/api/auth/me").authenticated()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
