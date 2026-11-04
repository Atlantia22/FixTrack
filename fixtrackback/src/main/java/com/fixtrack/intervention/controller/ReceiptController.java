package com.fixtrack.intervention.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixtrack.intervention.dto.ReceiptFile;
import com.fixtrack.intervention.service.ReceiptService;

import lombok.RequiredArgsConstructor;

// El de ingreso se abre en el navegador para imprimirlo o descargarlo; el de entrega solo se descarga
@RestController
@RequestMapping("/api/interventions/{id}/receipts")
@RequiredArgsConstructor
public class ReceiptController {

	private final ReceiptService receiptService;

	@GetMapping("/intake")
	@PreAuthorize("hasRole('ADVISOR')")
	public ResponseEntity<byte[]> intake(@PathVariable Long id,
			@RequestHeader(name = "X-Access-Key", required = false) String accessKey) {
		return pdf(receiptService.intakeReceipt(id, accessKey, false), ContentDisposition.inline());
	}

	@GetMapping("/delivery")
	@PreAuthorize("hasRole('ADVISOR')")
	public ResponseEntity<byte[]> delivery(@PathVariable Long id) {
		return pdf(receiptService.deliveryReceipt(id, false), ContentDisposition.attachment());
	}

	private ResponseEntity<byte[]> pdf(ReceiptFile file, ContentDisposition.Builder disposition) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(file.fileName()).build().toString())
				.body(file.content());
	}
}
