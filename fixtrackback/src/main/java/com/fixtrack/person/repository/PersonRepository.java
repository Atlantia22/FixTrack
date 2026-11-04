package com.fixtrack.person.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fixtrack.person.dto.TechnicianSummary;
import com.fixtrack.person.entity.DocumentType;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;

public interface PersonRepository extends JpaRepository<Person, Long> {

	Optional<Person> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

	boolean existsByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);

	boolean existsByDocumentTypeAndDocumentNumberAndIdNot(DocumentType documentType, String documentNumber, Long id);

	Optional<Person> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);

	boolean existsByRoleAndActiveTrue(Role role);

	long countByRoleAndActiveTrue(Role role);

	// Búsqueda por nombre, documento, correo o celular, con filtros de rol y estado; staff deja solo al personal interno.
	// pattern ya llega con los comodines escapados y entre %
	@Query("""
			SELECT p FROM Person p
			WHERE p.active = :active
			  AND (:role IS NULL OR p.role = :role)
			  AND (:staff = false OR p.role <> com.fixtrack.person.entity.Role.CUSTOMER)
			  AND (:pattern IS NULL
			       OR collate(p.fullName as utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!'
			       OR p.documentNumber LIKE :pattern ESCAPE '!'
			       OR collate(p.email as utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!'
			       OR p.phone LIKE :pattern ESCAPE '!')
			""")
	Page<Person> search(@Param("pattern") String pattern, @Param("role") Role role, @Param("staff") boolean staff,
			@Param("active") boolean active, Pageable pageable);

	// Técnicos activos con su disponibilidad, para el panel de Inicio
	@Query("""
			SELECT new com.fixtrack.person.dto.TechnicianSummary(p.id, p.fullName, p.availability)
			FROM Person p
			WHERE p.role = com.fixtrack.person.entity.Role.TECHNICIAN AND p.active = true
			ORDER BY p.fullName
			""")
	List<TechnicianSummary> findActiveTechnicians();
}
