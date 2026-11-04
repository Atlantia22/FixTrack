package com.fixtrack.person.service;

import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.person.dto.ChangePasswordRequest;
import com.fixtrack.person.dto.LoginRequest;
import com.fixtrack.person.dto.LoginResponse;

public interface AuthService {

	AuthenticatedUser authenticate(String email, String password);

	LoginResponse login(LoginRequest request, String clientIp);

	// Devuelve un token nuevo: los emitidos antes del cambio dejan de servir
	LoginResponse changePassword(AuthenticatedUser actor, ChangePasswordRequest request);
}
