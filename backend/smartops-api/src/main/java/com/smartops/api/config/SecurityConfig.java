package com.smartops.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;

/**
 * Seguridad del API:
 * <ul>
 *   <li>Autenticación HTTP Basic sin sesión (stateless) → CSRF no aplica.</li>
 *   <li>Autorización por rol: OPERATOR registra y carga; AUDITOR consulta auditoría; ADMIN métricas.</li>
 *   <li>Errores 401/403 en formato RFC 7807, sin cabecera WWW-Authenticate (evita el popup del navegador).</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, ex) ->
                writeProblem(mapper, res, HttpStatus.UNAUTHORIZED, "Credenciales requeridas o inválidas", req.getRequestURI());
        AccessDeniedHandler forbidden = (req, res, ex) ->
                writeProblem(mapper, res, HttpStatus.FORBIDDEN, "Su rol no permite esta operación", req.getRequestURI());

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/transactions/**", "/api/v1/batch/**",
                                "/api/v1/reconciliations/**").hasRole("OPERATOR")
                        .requestMatchers("/api/v1/audit/**").hasRole("AUDITOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(SecurityProperties properties, PasswordEncoder encoder) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        properties.users().forEach(u -> manager.createUser(User.withUsername(u.username())
                .password(encoder.encode(u.password()))
                .roles(u.roles().toArray(String[]::new))
                .build()));
        return manager;
    }

    private static void writeProblem(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse res,
                                     HttpStatus status, String detail, String path) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(path));
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), problem);
    }
}
