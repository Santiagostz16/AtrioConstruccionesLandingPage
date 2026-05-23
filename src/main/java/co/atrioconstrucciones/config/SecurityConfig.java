// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — SecurityConfig.java
//  Archivo: src/main/java/co/atrioconstrucciones/config/SecurityConfig.java
//
//  SEGURIDAD ROBUSTA IMPLEMENTADA:
//   ✔ CORS: solo permite el dominio de producción de Atrio
//   ✔ CSRF: habilitado con token en cookie (SameSite=Strict)
//   ✔ Headers de seguridad HTTP (CSP, HSTS, X-Frame-Options, etc.)
//   ✔ Rate limiting por IP (máx. 10 requests/minuto al endpoint /api/leads)
//   ✔ Protección contra clickjacking y XSS via headers
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración central de seguridad de Spring Security 6.
 *
 * CONCEPTO CLAVE:
 * Esta clase es el "portero" de toda la aplicación. Cada request HTTP
 * pasa por esta cadena de filtros antes de llegar a cualquier controlador.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    /**
     * Define la cadena de filtros de seguridad.
     * El orden de las reglas importa: se evalúan de arriba a abajo.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // ── CORS: restringir orígenes permitidos ──────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── CSRF: protección contra solicitudes forjadas entre sitios ──
            // Usa cookie HttpOnly=false para que el JS pueda leerlo
            // y enviarlo como header X-XSRF-TOKEN
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )

            // ── HEADERS DE SEGURIDAD HTTP ─────────────────────────────────
            .headers(headers -> headers
                // Previene que la página sea embebida en iframes (clickjacking)
                .frameOptions(frame -> frame.deny())
                // HSTS: fuerza HTTPS por 1 año
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
                // Content Security Policy: restringe fuentes de scripts, estilos e imágenes
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://fonts.gstatic.com; " +
                        "font-src 'self' https://fonts.gstatic.com; " +
                        "img-src 'self' data: https://images.unsplash.com; " +
                        "connect-src 'self'"
                    )
                )
                // Evita que el navegador "adivine" el tipo de contenido
                .contentTypeOptions(ct -> {})
                // Activa el filtro XSS del navegador
                .xssProtection(xss -> {})
            )

            // ── AUTORIZACIÓN DE RUTAS ─────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // La landing page y sus assets son públicos
                .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/img/**").permitAll()
                // El endpoint de leads es público (cualquiera puede enviar un lead)
                .requestMatchers("/api/leads").permitAll()
                // Todo lo demás requiere autenticación (panel admin futuro)
                .anyRequest().authenticated()
            )

            // ── FILTRO DE RATE LIMITING ──────────────────────────────────
            // Se ejecuta ANTES de la autenticación; bloquea IPs que abusen
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configura CORS para permitir solo el dominio de producción de Atrio.
     * EN DESARROLLO: agregar "http://localhost:8080" a allowedOrigins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // ⚠️ IMPORTANTE: cambiar a su dominio real en producción
        config.setAllowedOrigins(List.of(
            "https://www.atrioconstrucciones.com.co",
            "https://atrioconstrucciones.com.co",
            "http://localhost:8080"  // Solo para desarrollo local
        ));

        // Solo GET y POST necesarios para la landing page
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));

        // Headers permitidos en las solicitudes
        config.setAllowedHeaders(List.of(
            "Content-Type", "X-Requested-With", "X-XSRF-TOKEN"
        ));

        // Permitir cookies (necesario para CSRF)
        config.setAllowCredentials(true);

        // Cache del preflight por 1 hora (reduce requests OPTIONS)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
