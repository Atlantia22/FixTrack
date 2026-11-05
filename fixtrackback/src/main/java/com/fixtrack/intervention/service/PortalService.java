package com.fixtrack.intervention.service;

import com.fixtrack.intervention.dto.ReceiptFile;
import com.fixtrack.intervention.dto.portal.PortalLookupRequest;
import com.fixtrack.intervention.dto.portal.PortalOrderResponse;

// Consulta pública del cliente con código y clave; todo fallo responde el mismo mensaje genérico
public interface PortalService {

	PortalOrderResponse lookup(PortalLookupRequest request, String clientIp);

	ReceiptFile intakeReceipt(PortalLookupRequest request, String clientIp);

	ReceiptFile deliveryReceipt(PortalLookupRequest request, String clientIp);

	// Autoriza seguir la orden en tiempo real con las mismas reglas de la consulta; devuelve el código de la original
	String authorizeFollow(PortalLookupRequest request, String clientIp);
}
