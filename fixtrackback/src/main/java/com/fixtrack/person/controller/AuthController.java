package com.fixtrack.person.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixtrack.infra.security.CurrentUserProvider;
import com.fixtrack.person.dto.ChangePasswordRequest;
import com.fixtrack.person.dto.LoginRequest;
import com.fixtrack.person.dto.LoginResponse;
import com.fixtrack.person.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final CurrentUserProvider currentUserProvider;

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return authService.login(request, http.getRemoteAddr());
	}

	@PatchMapping("/password")
	@PreAuthorize("isAuthenticated()")
	public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		return authService.changePassword(currentUserProvider.require(), request);
	}
}
