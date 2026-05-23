// ═══════════════════════════════════════════════════════════════════
//  ATRIO Construcciones y Proyectos S.A.S
//  Backend — LeadRepository.java
//  Archivo: src/main/java/co/atrioconstrucciones/repository/LeadRepository.java
//
//  Spring Data JPA genera automáticamente las queries SQL a partir
//  de los nombres de los métodos. No hay que escribir SQL manual.
// ═══════════════════════════════════════════════════════════════════
package co.atrioconstrucciones.repository;

import co.atrioconstrucciones.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio JPA para operaciones CRUD sobre la tabla 'leads'.
 *
 * Spring Data JPA implementa todos los métodos automáticamente.
 * No es necesario escribir ninguna implementación.
 */
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    /**
     * Busca leads por estado para el pipeline comercial.
     * Query generada: SELECT * FROM leads WHERE estado = ? ORDER BY creado_en DESC
     */
    List<Lead> findByEstadoOrderByCreadoEnDesc(Lead.EstadoLead estado);

    /**
     * Busca leads por servicio de interés.
     * Útil para asignar leads al especialista correcto del equipo.
     */
    List<Lead> findByServicioOrderByCreadoEnDesc(String servicio);

    /**
     * Verifica si ya existe un lead con ese email en las últimas 24h.
     * Evita duplicados si alguien envía el formulario varias veces.
     */
    boolean existsByEmailAndCreadoEnAfter(String email, LocalDateTime desde);

    /**
     * Leads de las últimas N horas para el dashboard del equipo.
     * JPQL: Java Persistence Query Language (similar a SQL pero sobre entidades)
     */
    @Query("SELECT l FROM Lead l WHERE l.creadoEn >= :desde ORDER BY l.creadoEn DESC")
    List<Lead> findLeadsRecientes(LocalDateTime desde);

    /**
     * Conteo por servicio para estadísticas de marketing.
     * Retorna: [["construccion", 15], ["diseno_arquitectonico", 8], ...]
     */
    @Query("SELECT l.servicio, COUNT(l) FROM Lead l GROUP BY l.servicio ORDER BY COUNT(l) DESC")
    List<Object[]> contarPorServicio();
}
