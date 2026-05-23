// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — LeadController.java
//  Framework: Spring Boot 3.2 + Spring Security 6
//  Archivo: src/main/java/co/atrioconstrucciones/api/LeadController.java
//
//  INSTRUCCIÓN ECLIPSE:
//  1. Clic derecho en el paquete 'api' → New → Class
//  2. Pegue este código completo
//  3. Eclipse resolverá los imports automáticamente (Ctrl+Shift+O)
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.api;

import co.atrioconstrucciones.dto.LeadRequestDTO;
import co.atrioconstrucciones.model.Lead;
import co.atrioconstrucciones.service.LeadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para la recepción de leads desde la landing page.
 *
 * SEGURIDAD IMPLEMENTADA:
 *  - Validación de entrada con Bean Validation (@Valid + anotaciones en DTO)
 *  - Rate limiting por IP (ver RateLimitFilter.java)
 *  - CORS restringido al dominio de producción (ver SecurityConfig.java)
 *  - Protección CSRF activa en rutas de escritura
 *  - Logging de eventos de seguridad para auditoría
 *  - No se exponen stack traces al cliente (respuestas genéricas en error)
 */
@RestController
@RequestMapping("/api")
public class LeadController {

    // Logger estructurado para auditoría de seguridad
    private static final Logger log = LoggerFactory.getLogger(LeadController.class);

    // Inyección por constructor (mejor práctica sobre @Autowired en campo)
    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    /**
     * POST /api/leads
     * Recibe, valida y almacena un nuevo lead desde el formulario de la landing.
     *
     * @param dto     Datos del lead validados por Bean Validation
     * @param request Request HTTP para obtener la IP del cliente
     * @return 201 Created con confirmación, o 4xx/5xx con mensaje genérico
     */
    @PostMapping("/leads")
    public ResponseEntity<Map<String, String>> crearLead(
            @Valid @RequestBody LeadRequestDTO dto,
            HttpServletRequest request) {

        // Obtener IP real del cliente (considera proxies reversos como Nginx)
        String ipCliente = obtenerIpReal(request);

        // Loguear intento de creación (sin datos sensibles como email completo)
        log.info("[LEAD] Nuevo lead recibido | servicio={} | ip={} | origen={}",
                dto.getServicio(), ipCliente, dto.getOrigen());

        try {
            // Delegar toda la lógica de negocio al servicio
            Lead leadCreado = leadService.procesarLead(dto, ipCliente);

            log.info("[LEAD] Lead guardado exitosamente | id={}", leadCreado.getId());

            // Retornar 201 Created con mensaje de éxito (sin exponer el ID interno)
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Map.of(
                        "status",  "ok",
                        "message", "Solicitud recibida. Le contactaremos en menos de 24 horas."
                    ));

        } catch (Exception ex) {
            // Loguear el error internamente pero NO exponer detalles al cliente
            // (evita filtrar información del stack o estructura interna)
            log.error("[LEAD] Error al procesar lead | ip={} | causa={}", ipCliente, ex.getMessage(), ex);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "status",  "error",
                        "message", "Error interno. Por favor intente nuevamente o contáctenos por WhatsApp."
                    ));
        }
    }

    /**
     * Extrae la dirección IP real del cliente considerando proxies reversos.
     * Nginx/CloudFlare inyectan la IP original en el header X-Forwarded-For.
     *
     * @param request HttpServletRequest
     * @return IP del cliente como String
     */
    private String obtenerIpReal(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For puede tener múltiples IPs separadas por coma;
            // la primera es siempre la del cliente original
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
