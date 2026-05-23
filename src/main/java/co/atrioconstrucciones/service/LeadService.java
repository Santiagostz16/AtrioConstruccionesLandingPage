// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — LeadService.java
//  Archivo: src/main/java/co/atrioconstrucciones/service/LeadService.java
//
//  Lógica de negocio: Captura → Sanitización → Almacenamiento → Notificación
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.service;

import co.atrioconstrucciones.dto.LeadRequestDTO;
import co.atrioconstrucciones.model.Lead;
import co.atrioconstrucciones.repository.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio que orquesta el flujo completo de un nuevo lead:
 *  1. Sanitizar datos de entrada
 *  2. Mapear DTO → Entidad
 *  3. Persistir en base de datos (transaccional)
 *  4. Notificar al equipo de Atrio por correo electrónico
 *  5. (Opcional) Notificar por Telegram
 */
@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository  leadRepository;
    private final JavaMailSender  mailSender;

    // Email del equipo comercial de Atrio (configurado en application.properties)
    private static final String EMAIL_ATRIO = "juan.ramirez@atrioconstrucciones.com.co";

    public LeadService(LeadRepository leadRepository, JavaMailSender mailSender) {
        this.leadRepository = leadRepository;
        this.mailSender     = mailSender;
    }

    /**
     * Procesa y persiste un nuevo lead.
     *
     * @param dto      Datos validados del formulario
     * @param ipOrigen IP del cliente para auditoría
     * @return Lead persistido con su ID generado
     */
    @Transactional  // Si falla cualquier paso, hace rollback automático
    public Lead procesarLead(LeadRequestDTO dto, String ipOrigen) {

        // ── Paso 1: Sanitizar texto libre para prevenir XSS y inyecciones ──
        String mensajeSanitizado = sanitizarTexto(dto.getMensaje());
        String nombreSanitizado  = sanitizarTexto(dto.getNombre());

        // ── Paso 2: Mapear DTO a entidad JPA ──
        Lead lead = new Lead();
        lead.setNombre(nombreSanitizado);
        lead.setEmail(dto.getEmail().toLowerCase().trim());
        lead.setTelefono(dto.getTelefono().trim());
        lead.setEmpresa(sanitizarTexto(dto.getEmpresa()));
        lead.setServicio(dto.getServicio());
        lead.setMensaje(mensajeSanitizado);
        lead.setOrigen(dto.getOrigen() != null ? dto.getOrigen() : "landing_page");
        lead.setIpOrigen(truncarIpParaPrivacidad(ipOrigen)); // /24 para privacidad
        lead.setEstado(Lead.EstadoLead.NUEVO);

        // ── Paso 3: Persistir en MySQL ──
        Lead leadGuardado = leadRepository.save(lead);
        log.info("[LEAD] Persistido en DB | id={} | servicio={}", leadGuardado.getId(), lead.getServicio());

        // ── Paso 4: Notificación por email (asincrónica; no bloquea la respuesta) ──
        try {
            enviarNotificacionEmail(leadGuardado);
        } catch (Exception e) {
            // Si el email falla, NO revertir el lead ya guardado; solo loguear
            log.warn("[LEAD] Fallo notificación email | id={} | causa={}", leadGuardado.getId(), e.getMessage());
        }

        return leadGuardado;
    }

    /**
     * Envía un correo de alerta al equipo de Atrio con los datos del nuevo lead.
     * Configura SMTP en application.properties (ver sección de configuración).
     */
    private void enviarNotificacionEmail(Lead lead) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(EMAIL_ATRIO);
        msg.setSubject("🔔 Nuevo Lead — " + lead.getServicio() + " | " + lead.getNombre());
        msg.setText(String.format(
            "NUEVO LEAD RECIBIDO — ATRIO Construcciones\n" +
            "==========================================\n\n" +
            "Nombre:   %s\n" +
            "Empresa:  %s\n" +
            "Email:    %s\n" +
            "Teléfono: %s\n" +
            "Servicio: %s\n\n" +
            "Mensaje:\n%s\n\n" +
            "Origen:   %s\n" +
            "Fecha:    %s\n\n" +
            "⚡ Responda en menos de 24 horas para maximizar la conversión.\n" +
            "📱 WhatsApp: https://wa.me/%s",
            lead.getNombre(),
            lead.getEmpresa() != null ? lead.getEmpresa() : "No especificada",
            lead.getEmail(),
            lead.getTelefono(),
            lead.getServicio(),
            lead.getMensaje() != null ? lead.getMensaje() : "Sin mensaje",
            lead.getOrigen(),
            lead.getCreadoEn(),
            lead.getTelefono().replaceAll("[^0-9]", "")
        ));

        mailSender.send(msg);
        log.info("[EMAIL] Notificación enviada a {} | lead_id={}", EMAIL_ATRIO, lead.getId());
    }

    /**
     * Sanitiza texto libre para prevenir XSS básico.
     * Elimina etiquetas HTML y caracteres peligrosos.
     * En producción, considerar usar la librería OWASP Java HTML Sanitizer.
     *
     * @param texto Texto de entrada (puede ser null)
     * @return Texto limpio o null si la entrada era null
     */
    private String sanitizarTexto(String texto) {
        if (texto == null) return null;
        return texto
            .trim()
            .replaceAll("<[^>]*>", "")           // Eliminar tags HTML
            .replaceAll("[<>\"'%;()&+]", "")     // Eliminar chars especiales peligrosos
            .replaceAll("\\s{2,}", " ");          // Colapsar espacios múltiples
    }

    /**
     * Trunca una IP a /24 para preservar privacidad del usuario
     * según la Ley 1581 de Protección de Datos de Colombia.
     * Ej: 192.168.1.123 → 192.168.1.0
     *
     * @param ip Dirección IP completa
     * @return IP truncada a nivel de red /24
     */
    private String truncarIpParaPrivacidad(String ip) {
        if (ip == null) return null;
        String[] partes = ip.split("\\.");
        if (partes.length == 4) {
            return partes[0] + "." + partes[1] + "." + partes[2] + ".0";
        }
        return ip; // IPv6 u otro formato: retornar tal cual
    }
}
