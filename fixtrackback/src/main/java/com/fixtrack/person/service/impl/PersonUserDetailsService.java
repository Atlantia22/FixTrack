package com.fixtrack.person.service.impl;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.repository.PersonRepository;

import lombok.RequiredArgsConstructor;

// Carga por correo a los usuarios internos para Spring Security; los clientes nunca inician sesión
@Service
@RequiredArgsConstructor
public class PersonUserDetailsService implements UserDetailsService {

	private final PersonRepository personRepository;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		return personRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.filter(person -> person.getRole() != Role.CUSTOMER && person.getPassword() != null)
				.map(person -> new AuthenticatedUser(person.getId(), person.getFullName(), person.getEmail(),
						person.getRole(), person.getPassword(), person.isActive(), person.getPasswordChangedAt()))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
	}
}
