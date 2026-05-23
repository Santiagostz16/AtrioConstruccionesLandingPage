// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — LeadRequestDTO.java
//  Archivo: src/main/java/co/atrioconstrucciones/dto/LeadRequestDTO.java
//
//  DTO (Data Transfer Object): recibe y VALIDA los datos del formulario
//  Bean Validation (jakarta.validation) aplica las reglas ANTES de que
//  lleguen al servicio. Si falla alguna, Spring retorna 400 automáticamente.
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de entrada para la creación de un lead.
 * Toda validación de formato se hace aquí, NUNCA en la entidad JPA.
 */
public class LeadRequestDTO {

    // ── Nombre: obligatorio, 2-100 caracteres, solo letras y espacios ──
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Pattern(
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s''-]{2,100}$",
        message = "El nombre solo puede contener letras y espacios"
    )
    private String nombre;

    // ── Email: formato válido, máximo 120 caracteres ──
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene formato válido")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres")
    private String email;

    // ── Teléfono: formato colombiano ──
    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(
        regexp = "^(\\+?57)?[\\s\\-]?[0-9]{7,10}$",
        message = "Ingrese un número de teléfono colombiano válido"
    )
    private String telefono;

    // ── Empresa: opcional, máximo 100 caracteres ──
    @Size(max = 100, message = "El nombre de empresa no puede superar 100 caracteres")
    private String empresa;

    // ── Servicio: debe ser uno de los valores permitidos ──
    @NotBlank(message = "El servicio de interés es obligatorio")
    @Pattern(
        regexp = "^(construccion|diseno_arquitectonico|mantenimiento_electrico|mantenimiento_general|interventoria|otro)$",
        message = "Servicio no válido"
    )
    private String servicio;

    // ── Mensaje: opcional, máximo 1000 caracteres ──
    @Size(max = 1000, message = "El mensaje no puede superar 1000 caracteres")
    private String mensaje;

    // ── Origen: de dónde vino el lead (landing, WhatsApp, etc.) ──
    @Size(max = 50)
    private String origen;

    // ─── Getters y Setters ───────────────────────────────────────────

    public String getNombre()   { return nombre;   }
    public String getEmail()    { return email;    }
    public String getTelefono() { return telefono; }
    public String getEmpresa()  { return empresa;  }
    public String getServicio() { return servicio; }
    public String getMensaje()  { return mensaje;  }
    public String getOrigen()   { return origen;   }

    public void setNombre(String nombre)     { this.nombre   = nombre != null ? nombre.trim() : null;     }
    public void setEmail(String email)       { this.email    = email  != null ? email.trim().toLowerCase() : null; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public void setEmpresa(String empresa)   { this.empresa  = empresa; }
    public void setServicio(String servicio) { this.servicio = servicio; }
    public void setMensaje(String mensaje)   { this.mensaje  = mensaje; }
    public void setOrigen(String origen)     { this.origen   = origen;  }
}
