package com.fixtrack.intervention.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fixtrack.infra.security.CurrentUserProvider;
import com.fixtrack.intervention.dto.InterventionPartsResponse;
import com.fixtrack.intervention.dto.NewPartUsageRequest;
import com.fixtrack.intervention.dto.SparePartUsageRequest;
import com.fixtrack.intervention.dto.UpdateSparePartUsageRequest;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.inventory.dto.SparePartCandidateResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/interventions/{interventionId}/spare-parts")
@RequiredArgsConstructor
public class InterventionSparePartController {

	private final InterventionSparePartService interventionSparePartService;
	private final CurrentUserProvider currentUserProvider;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public InterventionPartsResponse list(@PathVariable Long interventionId) {
		return interventionSparePartService.findByIntervention(interventionId, currentUserProvider.require());
	}

	@GetMapping("/candidates")
	@PreAuthorize("hasRole('TECHNICIAN')")
	public List<SparePartCandidateResponse> candidates(@PathVariable Long interventionId,
			@RequestParam(required = false) Long partTypeId, @RequestParam(required = false) Long deviceModelId) {
		return interventionSparePartService.findCandidates(interventionId, partTypeId, deviceModelId,
				currentUserProvider.require());
	}

	@PostMapping
	@PreAuthorize("hasRole('TECHNICIAN')")
	@ResponseStatus(HttpStatus.CREATED)
	public InterventionPartsResponse register(@PathVariable Long interventionId,
			@Valid @RequestBody SparePartUsageRequest request) {
		return interventionSparePartService.register(interventionId, request, currentUserProvider.require());
	}

	@PostMapping("/new-part")
	@PreAuthorize("hasRole('TECHNICIAN')")
	@ResponseStatus(HttpStatus.CREATED)
	public InterventionPartsResponse registerNewPart(@PathVariable Long interventionId,
			@Valid @RequestBody NewPartUsageRequest request) {
		return interventionSparePartService.registerNewPart(interventionId, request, currentUserProvider.require());
	}

	@PatchMapping("/{usageId}")
	@PreAuthorize("hasRole('TECHNICIAN')")
	public InterventionPartsResponse correctQuantity(@PathVariable Long interventionId, @PathVariable Long usageId,
			@Valid @RequestBody UpdateSparePartUsageRequest request) {
		return interventionSparePartService.correctQuantity(interventionId, usageId, request,
				currentUserProvider.require());
	}

	@DeleteMapping("/{usageId}")
	@PreAuthorize("hasRole('TECHNICIAN')")
	public InterventionPartsResponse remove(@PathVariable Long interventionId, @PathVariable Long usageId) {
		return interventionSparePartService.remove(interventionId, usageId, currentUserProvider.require());
	}
}
