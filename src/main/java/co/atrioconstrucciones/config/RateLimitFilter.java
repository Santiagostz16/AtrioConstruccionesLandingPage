// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — RateLimitFilter.java
//  Archivo: src/main/java/co/atrioconstrucciones/config/RateLimitFilter.java
//
//  PROPÓSITO: Limitar solicitudes por IP para prevenir:
//   - Ataques de fuerza bruta al formulario
//   - Spam automatizado de leads falsos
//   - Ataques DDoS a nivel de aplicación
//
//  ALGORITMO: Token Bucket simplificado
//   Cada IP tiene un "cubo" de tokens. Cada request consume 1 token.
//   Los tokens se recargan cada 60 segundos.
//   Si el cubo está vacío → 429 Too Many Requests.
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filtro de Servlet que implementa rate limiting por IP.
 *
 * PARÁMETROS CONFIGURABLES:
 *  MAX_REQUESTS_POR_MINUTO: cuántas veces puede llamar una IP en 60 segundos
 *  VENTANA_MS: período de tiempo de la ventana deslizante (ms)
 *
 * HILO LIMPIADOR:
 *  Un ScheduledExecutorService limpia el mapa cada 5 minutos para
 *  liberar memoria y no acumular IPs antiguas indefinidamente.
 */
@Component
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // ── Parámetros del rate limiter ──
    private static final int  MAX_REQUESTS  = 10;           // Máx. requests por ventana
    private static final long VENTANA_MS    = 60_000L;      // Ventana de 60 segundos
    private static final int  MAX_IPS       = 10_000;       // Límite del mapa (memoria)

    /**
     * Mapa concurrente: IP → [contador de requests, timestamp de inicio de ventana]
     * ConcurrentHashMap es thread-safe para accesos simultáneos.
     */
    private final Map<String, long[]> contadores = new ConcurrentHashMap<>();

    // Contador total de IPs bloqueadas (para monitoreo)
    private final AtomicInteger ipsBloquedas = new AtomicInteger(0);

    /**
     * Constructor: inicia el hilo limpiador periódico.
     */
    public RateLimitFilter() {
        // Limpiar el mapa cada 5 minutos para evitar memory leaks
        ScheduledExecutorService limpiador = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleaner");
            t.setDaemon(true); // No bloquea el apagado de la JVM
            return t;
        });
        limpiador.scheduleAtFixedRate(this::limpiarContadoresExpirados, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Punto de entrada del filtro. Se ejecuta en CADA request HTTP.
     *
     * Solo aplica rate limiting al endpoint POST /api/leads.
     * Las solicitudes GET (la landing page) no están limitadas.
     */
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String metodo = request.getMethod();
        String uri    = request.getRequestURI();

        // Aplicar rate limiting SOLO a POST /api/leads
        if ("POST".equalsIgnoreCase(metodo) && uri.startsWith("/api/leads")) {

            String ip = obtenerIpReal(request);

            if (!permitirRequest(ip)) {
                // Bloquear: retornar 429 con headers estándar de retry
                log.warn("[RATE-LIMIT] IP bloqueada | ip={} | bloqueadas_total={}",
                         ip, ipsBloquedas.incrementAndGet());

                response.setStatus(429); // 429 Too Many Requests
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", "60"); // Reintentar en 60 segundos
                response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("X-RateLimit-Reset", String.valueOf(
                    (System.currentTimeMillis() + VENTANA_MS) / 1000
                ));
                response.getWriter().write(
                    "{\"status\":\"error\",\"message\":\"Demasiadas solicitudes. Espere 60 segundos.\"}"
                );
                return; // Cortar la cadena: no pasar al controlador
            }
        }

        // Request permitido: continuar con el siguiente filtro/controlador
        chain.doFilter(req, res);
    }

    /**
     * Verifica si una IP puede hacer un nuevo request.
     * Implementa ventana deslizante (sliding window) simplificada.
     *
     * @param ip Dirección IP del cliente
     * @return true si el request es permitido, false si debe bloquearse
     */
    private boolean permitirRequest(String ip) {
        // Protección de memoria: si el mapa crece mucho, permitir (fail-open)
        // En producción, usar Redis para estado distribuido
        if (contadores.size() > MAX_IPS) {
            log.warn("[RATE-LIMIT] Mapa lleno ({} IPs). Permitiendo request.", MAX_IPS);
            return true;
        }

        long ahora = System.currentTimeMillis();

        // compute es atómico en ConcurrentHashMap: thread-safe sin synchronized
        long[] estado = contadores.compute(ip, (k, v) -> {
            if (v == null || (ahora - v[1]) > VENTANA_MS) {
                // Primera vez o ventana expirada: reiniciar contador
                return new long[]{1, ahora};
            } else {
                // Dentro de la ventana: incrementar contador
                v[0]++;
                return v;
            }
        });

        return estado[0] <= MAX_REQUESTS;
    }

    /**
     * Elimina del mapa las IPs cuya ventana de tiempo ya expiró.
     * Llamado cada 5 minutos por el ScheduledExecutorService.
     */
    private void limpiarContadoresExpirados() {
        long ahora = System.currentTimeMillis();
        int antes = contadores.size();
        contadores.entrySet().removeIf(e -> (ahora - e.getValue()[1]) > VENTANA_MS);
        int eliminadas = antes - contadores.size();
        if (eliminadas > 0) {
            log.debug("[RATE-LIMIT] Limpieza: {} IPs eliminadas del mapa", eliminadas);
        }
    }

    /**
     * Extrae la IP real considerando proxies (Nginx, CloudFlare).
     */
    private String obtenerIpReal(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
