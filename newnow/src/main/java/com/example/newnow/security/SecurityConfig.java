package com.example.newnow.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // K2 — javni auth endpointi
                        .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/test").permitAll()

                        // K1 — neregistrovan korisnik šalje zahtev za registraciju
                        .requestMatchers(HttpMethod.POST, "/api/account-requests").permitAll()

                        // A1 — samo admin obrađuje zahteve
                        .requestMatchers("/api/account-requests/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/search/locations/*/pdf").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/search/reindex").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/files/images").hasAnyRole("ADMIN", "MANAGER")

                        // S1 — pretraga, PDF download i MinIO fajlovi javni
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()

                        // Javni read endpointi
                        .requestMatchers(
                                HttpMethod.GET, "/api/locations/**",
                                "/api/events/**",
                                "/api/home/**"
                        ).permitAll()

                        // K9/K10 — profil i lozinka za ulogovane
                        .requestMatchers("/api/users/profile", "/api/users/change-password").authenticated()

                        // A2 — lista korisnika samo admin (dodatna provera u kontroleru)
                        .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")

                        // Ostali users endpointi — zabranjeni
                        .requestMatchers("/api/users/**").authenticated()

                        .requestMatchers(
                                "/api/reviews/**",
                                "/api/comments/**",
                                "/api/manager/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
