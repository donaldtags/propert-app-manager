package com.example.primenestprop.security;

import com.example.primenestprop.auth.AuthService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000,http://127.0.0.1:3001}")
    private String[] allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Total-Count"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthService authService,
            RateLimitingFilter rateLimitingFilter,
            RestAuthEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/logs/frontend").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai/property-search").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai/affordability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/properties/*/inquiries").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/properties", "/api/v1/properties/*", "/api/v1/properties/*/passport").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/investments/reits").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/featured-listings/settings").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/featured-listings/settings").hasAuthority("PERM_FEATURED_LISTINGS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/subscriptions/plans").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/subscriptions/plans/*").hasAuthority("PERM_SUBSCRIPTION_PLANS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/market/reits/zw").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/neighbourhoods").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/neighbourhoods").hasAuthority("PERM_NEIGHBOURHOOD_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/vendors").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/vendors").hasAuthority("PERM_VENDOR_MANAGE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/vendors/*/verify").hasAuthority("PERM_VENDOR_MANAGE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/vendors/*/deactivate").hasAuthority("PERM_VENDOR_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ratings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/landlord-passport").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/investments/reits").hasAuthority("PERM_INVESTMENT_MANAGE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/investments/reits/*/properties").hasAuthority("PERM_INVESTMENT_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").hasAuthority("PERM_USER_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users").hasAuthority("PERM_USER_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/search").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/admin-requests").hasAuthority("PERM_USER_MANAGE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/admin-requests/*").hasAuthority("PERM_USER_MANAGE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/verify").hasAuthority("PERM_USER_VERIFY")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/*/verify-business").hasAuthority("PERM_USER_VERIFY")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/properties/*/verify").hasAuthority("PERM_PROPERTY_VERIFY")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/escrows/*/release").hasAuthority("PERM_ESCROW_RELEASE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/escrows/admin").hasAuthority("PERM_ESCROW_ADMIN_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/v1/kyc/submissions").hasAuthority("PERM_KYC_REVIEW")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/kyc/submissions/*/review").hasAuthority("PERM_KYC_REVIEW")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboards/admin").hasAuthority("PERM_DASHBOARD_ADMIN_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/fraud-signals").hasAuthority("PERM_FRAUD_SIGNALS_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/v1/messages/admin/**").hasAuthority("PERM_MESSAGES_ADMIN_VIEW")
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new TokenAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
