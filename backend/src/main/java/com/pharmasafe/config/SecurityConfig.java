package com.pharmasafe.config;

import com.pharmasafe.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,https://pharmasafe-frontend.onrender.com}")
    private String allowedOrigins;

    @Value("${server.servlet.session.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean cookieSecure;

    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, com.pharmasafe.repository.UserRepository users, com.pharmasafe.administration.AdministrativeAudit administrativeAudit) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // Resolve tokens only when a mutation or /auth/csrf needs one. Eager
        // token creation on concurrent anonymous GETs races their Set-Cookie
        // responses and can invalidate the first registration POST.
        requestHandler.setCsrfRequestAttributeName("_csrf");

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(builder -> {
            builder.sameSite(cookieSameSite);
            if (cookieSecure) {
                builder.secure(true);
            }
        });

        http.addFilterBefore(new com.pharmasafe.security.CurrentAccountFilter(users),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(requestHandler)
                .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/csrf", "/api/health/**", "/actuator/**", "/error")
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(2)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request,response,error)->{var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();if(a!=null&&request.getRequestURI().startsWith("/api/admin"))administrativeAudit.denied(a.getName(),null,request.getRequestURI());response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Access denied: insufficient permissions\"}");})
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/health/**",
                    "/actuator/**",
                    "/error",
                    "/api/auth/access-roles",
                    "/api/auth/role-selection",
                    "/api/auth/register",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/login",
                    "/api/auth/csrf"
                ).permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/processing-teams/invitations/*", "/api/processing-teams/invitations/code/*").permitAll()
                .requestMatchers("/api/pv-analyses/**", "/api/pv-analyses").hasAnyRole("ADMIN","PV_ANALYST","SAFETY_MANAGER","SCOPED_ADMIN")
                .requestMatchers("/api/processing-teams/**").hasAnyRole("ADMIN","SCOPED_ADMIN","CASE_TEAM_LEAD","CASE_PROCESSOR","USER")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/cases").hasAnyRole("ADMIN", "CASE_PROCESSOR", "USER")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/cases/*").hasAnyRole("ADMIN", "CASE_PROCESSOR", "USER")
                .requestMatchers("/api/administration/assignments/**", "/api/administration/eligible-administrators").hasRole("ADMIN")
                .requestMatchers("/api/administration/**").hasAnyRole("ADMIN", "SCOPED_ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/**", "/api/profile", "/api/profile/**").authenticated()
                .requestMatchers("/api/reports/*/evidence", "/api/reports/*/documents", "/api/reports/*/documents/*/pdf", "/api/reports/*/acknowledgements").hasAnyRole("ADMIN", "SAFETY_MANAGER", "REPORTING_SPECIALIST", "AUDITOR", "SCOPED_ADMIN")
                .anyRequest().hasAnyRole("ADMIN", "CASE_PROCESSOR", "USER", "MEDICAL_REVIEWER",
                        "QUALITY_REVIEWER", "SAFETY_MANAGER", "REPORTING_SPECIALIST", "AUDITOR", "PV_ANALYST")
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .map(origin -> origin.replaceAll("/+$", ""))
            .filter(origin -> !origin.isEmpty())
            .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With", 
            "Accept", 
            "X-XSRF-TOKEN", 
            "X-CSRF-TOKEN"
        ));
        configuration.setExposedHeaders(Arrays.asList("Set-Cookie", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
