// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — Lead.java  (Entidad JPA)
//  Archivo: src/main/java/co/atrioconstrucciones/model/Lead.java
//
//  INSTRUCCIÓN ECLIPSE:
//  Clic derecho en paquete 'model' → New → Class → pegue este código
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA mapeada a la tabla 'leads' de MySQL.
 * Hibernate genera las queries SQL automáticamente.
 */
@Entity
@Table(name = "leads")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Columnas mapeadas 1:1 con la tabla SQL
    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(length = 100)
    private String empresa;

    @Column(nullable = false, length = 50)
    private String servicio;

    // TEXT en MySQL = sin límite de longitud en la entidad
    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(length = 50)
    private String origen;

    // Enum de estados del pipeline comercial
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoLead estado = EstadoLead.NUEVO;

    @Column(name = "ip_origen", length = 45)
    private String ipOrigen;

    // Timestamps automáticos de auditoría
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Callbacks JPA para timestamps automáticos
    @PrePersist
    protected void onCreate() {
        this.creadoEn     = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    // ─── Enum de estados ────────────────────────────────────────────
    public enum EstadoLead { NUEVO, CONTACTADO, COTIZADO, GANADO, PERDIDO }

    // ─── Getters y Setters ──────────────────────────────────────────
    public Long getId()           { return id;           }
    public String getNombre()     { return nombre;       }
    public String getEmail()      { return email;        }
    public String getTelefono()   { return telefono;     }
    public String getEmpresa()    { return empresa;      }
    public String getServicio()   { return servicio;     }
    public String getMensaje()    { return mensaje;      }
    public String getOrigen()     { return origen;       }
    public EstadoLead getEstado() { return estado;       }
    public String getIpOrigen()   { return ipOrigen;     }
    public LocalDateTime getCreadoEn() { return creadoEn; }

    public void setNombre(String n)       { this.nombre   = n; }
    public void setEmail(String e)        { this.email    = e; }
    public void setTelefono(String t)     { this.telefono = t; }
    public void setEmpresa(String em)     { this.empresa  = em; }
    public void setServicio(String s)     { this.servicio = s; }
    public void setMensaje(String m)      { this.mensaje  = m; }
    public void setOrigen(String o)       { this.origen   = o; }
    public void setEstado(EstadoLead est) { this.estado   = est; }
    public void setIpOrigen(String ip)    { this.ipOrigen = ip; }
}
