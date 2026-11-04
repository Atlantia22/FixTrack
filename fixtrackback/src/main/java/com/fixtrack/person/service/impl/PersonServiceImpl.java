package com.fixtrack.person.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.ForbiddenException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.PageResponse;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.person.dto.CustomerContactRequest;
import com.fixtrack.person.dto.CustomerRequest;
import com.fixtrack.person.dto.PersonRequest;
import com.fixtrack.person.dto.PersonResponse;
import com.fixtrack.person.dto.TechnicianSummary;
import com.fixtrack.person.dto.SpecialtyResponse;
import com.fixtrack.person.entity.Availability;
import com.fixtrack.person.entity.DocumentType;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.PersonSpecialty;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.entity.Specialty;
import com.fixtrack.person.mapper.PersonMapper;
import com.fixtrack.person.mapper.SpecialtyMapper;
import com.fixtrack.person.repository.PersonRepository;
import com.fixtrack.person.repository.PersonSpecialtyRepository;
import com.fixtrack.person.repository.SpecialtyRepository;
import com.fixtrack.person.service.PersonService;

import lombok.RequiredArgsConstructor;

// Clientes y usuarios internos: documento único, reglas de contacto, contraseña, especialidades y disponibilidad
@Service
@RequiredArgsConstructor
public class PersonServiceImpl implements PersonService {

	// Comodines de LIKE que se escapan con ! antes de buscar
	private static final Pattern LIKE_SPECIAL = Pattern.compile("[!%_]");

	private final PersonRepository personRepository;
	private final PersonSpecialtyRepository personSpecialtyRepository;
	private final SpecialtyRepository specialtyRepository;
	private final PasswordEncoder passwordEncoder;
	private final PersonMapper personMapper;
	private final SpecialtyMapper specialtyMapper;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<PersonResponse> search(String search, Role role, boolean staff, boolean active, Pageable pageable) {
		String pattern = search == null || search.isBlank() ? null
				: "%" + LIKE_SPECIAL.matcher(search.trim()).replaceAll("!$0") + "%";
		Page<Person> page = personRepository.search(pattern, role, staff, active, pageable);
		Map<Long, List<SpecialtyResponse>> specialties = specialtiesByPerson(
				page.getContent().stream().map(Person::getId).toList());
		return PageResponse.from(page.map(person -> personMapper.toResponse(person,
				specialties.getOrDefault(person.getId(), List.of()))));
	}

	@Override
	@Transactional(readOnly = true)
	public PersonResponse findById(Long id) {
		return toResponse(findPerson(id));
	}

	// Búsqueda exacta por documento, con la misma normalización con que se guarda
	@Override
	@Transactional(readOnly = true)
	public PersonResponse findByDocument(DocumentType documentType, String documentNumber) {
		String normalized = normalizeDocument(documentNumber);
		return personRepository.findByDocumentTypeAndDocumentNumber(documentType, normalized)
				.map(this::toResponse)
				.orElseThrow(() -> new NotFoundException(
						"No hay una persona registrada con el documento " + documentType + " " + normalized));
	}

	@Override
	@Transactional
	public PersonResponse create(PersonRequest request) {
		Person person = Person.builder().build();
		applyRequest(person, request);
		personRepository.save(person);
		syncSpecialties(person, request.specialtyIds());
		return toResponse(person);
	}

	@Override
	@Transactional
	public PersonResponse update(Long id, PersonRequest request) {
		Person person = findPerson(id);
		if (!person.isActive()) {
			throw new ConflictException("La persona está dada de baja y no se puede editar");
		}
		if (request.role() != Role.ADMIN && isLastActiveAdmin(person)) {
			throw new ConflictException("No se puede quitar el rol de administrador al único administrador activo");
		}
		applyRequest(person, request);
		syncSpecialties(person, request.specialtyIds());
		return toResponse(person);
	}

	// Baja lógica: conserva su historial en órdenes, pagos y movimientos
	@Override
	@Transactional
	public void deactivate(Long id) {
		Person person = findPerson(id);
		if (isLastActiveAdmin(person)) {
			throw new ConflictException("No se puede dar de baja al único administrador activo");
		}
		person.setActive(false);
	}

	// El técnico cambia su propia disponibilidad; el administrador, la de cualquier técnico
	@Override
	@Transactional
	public PersonResponse changeAvailability(Long id, Availability availability, AuthenticatedUser actor) {
		if (!actor.isAdmin() && !actor.id().equals(id)) {
			throw new ForbiddenException("Solo puede cambiar su propia disponibilidad");
		}
		Person person = findPerson(id);
		if (person.getRole() != Role.TECHNICIAN) {
			throw new BusinessRuleException("Solo los técnicos tienen disponibilidad");
		}
		if (!person.isActive()) {
			throw new ConflictException("El técnico está dado de baja");
		}
		person.setAvailability(availability);
		return toResponse(person);
	}

	// La recepción corrige el celular y el correo de un cliente activo; la clave de consulta de sus órdenes no cambia
	@Override
	@Transactional
	public PersonResponse updateCustomerContact(Long id, CustomerContactRequest request) {
		Person person = findPerson(id);
		if (person.getRole() != Role.CUSTOMER) {
			throw new BusinessRuleException("Desde aquí solo se editan los datos de contacto de los clientes");
		}
		if (!person.isActive()) {
			throw new ConflictException("El cliente está dado de baja");
		}
		String phone = resolveContact(request.phone(), request.noPhone(), true, "el celular");
		String email = resolveContact(request.email(), request.noEmail(), true, "el correo");
		if (email != null) {
			email = email.toLowerCase(Locale.ROOT);
			if (personRepository.existsByEmailAndIdNot(email, person.getId())) {
				throw new ConflictException("Ya existe una persona con el correo " + email);
			}
		}
		person.setPhone(phone);
		person.setEmail(email);
		return toResponse(person);
	}

	// Si el documento ya existe se reutiliza la persona y solo se actualizan celular y correo; si no, se crea el cliente
	@Override
	@Transactional
	public Person registerOrUpdateCustomer(CustomerRequest request) {
		String documentNumber = normalizeDocument(request.documentNumber());
		Optional<Person> existing = personRepository.findByDocumentTypeAndDocumentNumber(request.documentType(),
				documentNumber);
		if (existing.isPresent()) {
			Person person = existing.get();
			if (!person.isActive()) {
				throw new ConflictException("La persona con documento " + request.documentType() + " " + documentNumber
						+ " está dada de baja");
			}
			updateContact(person, request);
			return person;
		}

		requireValidDocument(request.documentType(), documentNumber);
		if (request.fullName() == null || request.fullName().isBlank()) {
			throw new BusinessRuleException("El nombre es obligatorio para registrar un cliente nuevo");
		}
		String phone = resolveContact(request.phone(), request.noPhone(), true, "el celular");
		String email = resolveContact(request.email(), request.noEmail(), true, "el correo");
		if (email != null) {
			email = email.toLowerCase(Locale.ROOT);
			if (personRepository.existsByEmail(email)) {
				throw new ConflictException("Ya existe una persona con el correo " + email);
			}
		}
		return personRepository.save(Person.builder()
				.documentType(request.documentType())
				.documentNumber(documentNumber)
				.fullName(request.fullName().trim())
				.phone(phone)
				.email(email)
				.role(Role.CUSTOMER)
				.build());
	}

	@Override
	@Transactional(readOnly = true)
	public Person getActiveTechnician(Long id) {
		Person person = findPerson(id);
		if (person.getRole() != Role.TECHNICIAN || !person.isActive()) {
			throw new BusinessRuleException("La persona con id " + id + " no es un técnico activo");
		}
		return person;
	}

	@Override
	@Transactional(readOnly = true)
	public List<TechnicianSummary> findActiveTechnicians() {
		return personRepository.findActiveTechnicians();
	}

	@Override
	@Transactional(readOnly = true)
	public Person getEntity(Long id) {
		return findPerson(id);
	}

	// Cliente que regresa: el valor nuevo reemplaza, la marca «No tiene» vacía el dato y si no llega nada se conserva
	private void updateContact(Person person, CustomerRequest request) {
		if (hasText(request.phone()) && Boolean.TRUE.equals(request.noPhone())) {
			throw new BusinessRuleException("Envíe el celular o marque que no tiene, pero no ambos");
		}
		if (hasText(request.phone())) {
			person.setPhone(request.phone().trim());
		} else if (Boolean.TRUE.equals(request.noPhone())) {
			person.setPhone(null);
		}

		if (hasText(request.email()) && Boolean.TRUE.equals(request.noEmail())) {
			throw new BusinessRuleException("Envíe el correo o marque que no tiene, pero no ambos");
		}
		if (hasText(request.email())) {
			String email = request.email().trim().toLowerCase(Locale.ROOT);
			// El correo de un usuario interno es su usuario de inicio de sesión: desde el ingreso no se cambia
			if (person.getRole() != Role.CUSTOMER && !email.equals(person.getEmail())) {
				throw new BusinessRuleException(
						"Esta persona es un usuario interno; su correo solo lo cambia el administrador en Personal");
			}
			if (personRepository.existsByEmailAndIdNot(email, person.getId())) {
				throw new ConflictException("Ya existe una persona con el correo " + email);
			}
			person.setEmail(email);
		} else if (Boolean.TRUE.equals(request.noEmail())) {
			if (person.getRole() != Role.CUSTOMER) {
				throw new BusinessRuleException("El correo es obligatorio para los usuarios internos, porque con él inician sesión");
			}
			person.setEmail(null);
		}
	}

	// Aplica los datos de la petición validando documento, contacto, correo y contraseña según el rol
	private void applyRequest(Person person, PersonRequest request) {
		boolean customer = request.role() == Role.CUSTOMER;

		String documentNumber = normalizeDocument(request.documentNumber());
		requireValidDocument(request.documentType(), documentNumber);
		boolean duplicateDocument = person.getId() == null
				? personRepository.existsByDocumentTypeAndDocumentNumber(request.documentType(), documentNumber)
				: personRepository.existsByDocumentTypeAndDocumentNumberAndIdNot(request.documentType(), documentNumber,
						person.getId());
		if (duplicateDocument) {
			throw new ConflictException(
					"Ya existe una persona con el documento " + request.documentType() + " " + documentNumber);
		}

		String phone = resolveContact(request.phone(), request.noPhone(), customer, "el celular");
		String email = resolveContact(request.email(), request.noEmail(), customer, "el correo");
		if (email != null) {
			email = email.toLowerCase(Locale.ROOT);
			boolean duplicateEmail = person.getId() == null
					? personRepository.existsByEmail(email)
					: personRepository.existsByEmailAndIdNot(email, person.getId());
			if (duplicateEmail) {
				throw new ConflictException("Ya existe una persona con el correo " + email);
			}
		} else if (!customer) {
			throw new BusinessRuleException("El correo es obligatorio para los usuarios internos, porque con él inician sesión");
		}

		boolean hasPassword = request.password() != null && !request.password().isBlank();
		if (customer) {
			if (hasPassword) {
				throw new BusinessRuleException("Los clientes no inician sesión, así que no llevan contraseña");
			}
			person.setPassword(null);
		} else if (hasPassword) {
			person.setPassword(passwordEncoder.encode(request.password()));
			// Si el administrador cambia la contraseña, las sesiones abiertas de esa persona se cierran
			if (person.getId() != null) {
				person.setPasswordChangedAt(clock.instant().truncatedTo(ChronoUnit.SECONDS));
			}
		} else if (person.getPassword() == null) {
			throw new BusinessRuleException("La contraseña es obligatoria para los usuarios internos");
		}

		boolean technician = request.role() == Role.TECHNICIAN;
		if (!technician && request.specialtyIds() != null && !request.specialtyIds().isEmpty()) {
			throw new BusinessRuleException("Solo los técnicos tienen especialidades");
		}
		if (!technician) {
			person.setAvailability(null);
		} else if (person.getAvailability() == null) {
			person.setAvailability(Availability.AVAILABLE);
		}

		person.setDocumentType(request.documentType());
		person.setDocumentNumber(documentNumber);
		person.setFullName(request.fullName().trim());
		person.setPhone(phone);
		person.setEmail(email);
		person.setRole(request.role());
	}

	// Celular y correo del cliente: llega el valor o la marca de que no tiene; nunca ambos ni ninguno
	private String resolveContact(String value, Boolean noValue, boolean required, String label) {
		boolean hasValue = value != null && !value.isBlank();
		boolean marked = Boolean.TRUE.equals(noValue);
		if (hasValue && marked) {
			throw new BusinessRuleException("Envíe " + label + " o marque que no tiene, pero no ambos");
		}
		if (hasValue) {
			return value.trim();
		}
		if (required && !marked) {
			throw new BusinessRuleException("Indique " + label + " del cliente o marque que no tiene");
		}
		return null;
	}

	// Deja las especialidades pedidas: null no cambia nada; los que no son técnicos quedan sin especialidades
	private void syncSpecialties(Person person, List<Long> requestedIds) {
		List<PersonSpecialty> current = personSpecialtyRepository.findByPersonIdIn(List.of(person.getId()));
		if (person.getRole() != Role.TECHNICIAN) {
			personSpecialtyRepository.deleteAll(current);
			return;
		}
		if (requestedIds == null) {
			return;
		}

		Set<Long> wanted = new LinkedHashSet<>(requestedIds);
		List<Specialty> specialties = specialtyRepository.findAllById(wanted);
		if (specialties.size() != wanted.size()) {
			Set<Long> found = specialties.stream().map(Specialty::getId).collect(Collectors.toSet());
			wanted.removeAll(found);
			throw new NotFoundException("No existen las especialidades con id " + wanted);
		}

		Set<Long> currentIds = current.stream().map(link -> link.getSpecialty().getId()).collect(Collectors.toSet());
		personSpecialtyRepository.deleteAll(
				current.stream().filter(link -> !wanted.contains(link.getSpecialty().getId())).toList());
		personSpecialtyRepository.saveAll(specialties.stream()
				.filter(specialty -> !currentIds.contains(specialty.getId()))
				.map(specialty -> new PersonSpecialty(person, specialty))
				.toList());
	}

	private Map<Long, List<SpecialtyResponse>> specialtiesByPerson(List<Long> personIds) {
		if (personIds.isEmpty()) {
			return Map.of();
		}
		return personSpecialtyRepository.findByPersonIdIn(personIds).stream()
				.sorted(Comparator.comparing(link -> link.getSpecialty().getName()))
				.collect(Collectors.groupingBy(link -> link.getId().getPersonId(),
						Collectors.mapping(link -> specialtyMapper.toResponse(link.getSpecialty()), Collectors.toList())));
	}

	private PersonResponse toResponse(Person person) {
		return personMapper.toResponse(person,
				specialtiesByPerson(List.of(person.getId())).getOrDefault(person.getId(), List.of()));
	}

	private void requireValidDocument(DocumentType documentType, String documentNumber) {
		if (!documentType.isValidNumber(documentNumber)) {
			throw new BusinessRuleException(documentType.rule());
		}
	}

	private String normalizeDocument(String documentNumber) {
		return documentNumber.trim().toUpperCase(Locale.ROOT);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private boolean isLastActiveAdmin(Person person) {
		return person.getRole() == Role.ADMIN && person.isActive()
				&& personRepository.countByRoleAndActiveTrue(Role.ADMIN) == 1;
	}

	private Person findPerson(Long id) {
		return personRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("No existe la persona con id " + id));
	}
}
