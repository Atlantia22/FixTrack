package com.fixtrack.infra.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fixtrack.person.entity.Role;

// Usuario interno autenticado que Spring Security guarda en el contexto de cada petición
public record AuthenticatedUser(Long id, String fullName, String email, Role role, String passwordHash,
		boolean enabled, Instant passwordChangedAt) implements UserDetails {

	public AuthenticatedUser(Long id, String fullName, String email, Role role, String passwordHash, boolean enabled) {
		this(id, fullName, email, role, passwordHash, enabled, null);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	// Sin el hash de la contraseña, para que no termine en los logs
	@Override
	public String toString() {
		return "AuthenticatedUser[id=" + id + ", email=" + email + ", role=" + role + "]";
	}
}
