package com.fixtrack.intervention.service;

import com.fixtrack.intervention.dto.ReceiptFile;

public interface ReceiptService {

	// accessKey es la clave vigente de la orden (recién creada o regenerada); se imprime destacada.
	// masked oculta documento, contactos, IMEI y serie para la copia que se descarga desde el portal público
	ReceiptFile intakeReceipt(Long id, String accessKey, boolean masked);

	ReceiptFile deliveryReceipt(Long id, boolean masked);
}
