package com.fixtrack.intervention.service.impl;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.cash.dto.PaymentResponse;
import com.fixtrack.cash.dto.PaymentSummaryResponse;
import com.fixtrack.cash.entity.PaymentType;
import com.fixtrack.cash.service.PaymentService;
import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.Money;
import com.fixtrack.device.entity.Device;
import com.fixtrack.intervention.dto.ReceiptFile;
import com.fixtrack.intervention.entity.ApprovalStatus;
import com.fixtrack.intervention.entity.FindingOrigin;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionFinding;
import com.fixtrack.intervention.entity.InterventionSparePart;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.intervention.repository.InterventionFindingRepository;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.intervention.service.ReceiptService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.setting.entity.SettingKey;
import com.fixtrack.setting.service.SettingService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;

// Comprobantes en PDF: el de ingreso (con código y clave de consulta) y el de entrega (servicios, pagos y garantía)
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

	private static final String NOT_AVAILABLE = "NT";
	private static final String NO_CONTACT_WARNING = "Conserve este comprobante, no podremos contactarlo";
	private static final List<String> DEFAULT_TERMS = List.of(
			"El equipo se recibe en el estado físico descrito en este comprobante; la tienda no responde por fallas no reportadas al ingreso.",
			"El presupuesto es inicial. Si aparecen daños adicionales que lo superen, se le consultará antes de repararlos.",
			"La fecha de entrega es estimada y puede reprogramarse; en ese caso se le informará el motivo.",
			"La garantía cubre solo el servicio realizado y los repuestos instalados; no aplica por golpes, humedad o manipulación de terceros.",
			"Para retirar el equipo presente este comprobante o su documento de identidad.");

	private static final Locale COLOMBIA = Locale.of("es", "CO");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", COLOMBIA);
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", COLOMBIA);
	private static final Color PRIMARY = new Color(30, 64, 175);
	private static final Color MUTED = new Color(100, 116, 139);
	private static final Color HIGHLIGHT = new Color(239, 246, 255);
	private static final Color WARNING = new Color(180, 83, 9);
	private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PRIMARY);
	private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PRIMARY);
	private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
	private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 9);
	private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
	private static final Font KEY_LABEL = FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED);
	private static final Font KEY_VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, PRIMARY);
	private static final Font ALERT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, WARNING);

	private final InterventionPricingService pricingService;
	private final InterventionSparePartService sparePartService;
	private final InterventionFindingRepository findingRepository;
	private final PaymentService paymentService;
	private final SettingService settingService;
	private final PasswordEncoder passwordEncoder;

	@Value("${fixtrack.portal-url}")
	private String portalUrl;

	// La clave solo se guarda con hash: se imprime la que llega si coincide con la vigente (recién creada o regenerada)
	@Override
	@Transactional(readOnly = true)
	public ReceiptFile intakeReceipt(Long id, String accessKey) {
		Intervention intervention = pricingService.getEntity(id);
		Intervention keyOwner = intervention.getWarrantyOf() != null ? intervention.getWarrantyOf() : intervention;
		if (accessKey == null || accessKey.isBlank()) {
			throw new BusinessRuleException("Envíe la clave de consulta vigente o genere una nueva para reimprimir el comprobante");
		}
		if (keyOwner.getAccessKeyHash() == null || !passwordEncoder.matches(accessKey.trim(), keyOwner.getAccessKeyHash())) {
			throw new BusinessRuleException("La clave de consulta no es la vigente; genere una nueva para reimprimir el comprobante");
		}

		String code = InterventionCode.of(intervention);
		String lookupCode = InterventionCode.of(keyOwner);
		Person customer = intervention.getDevice().getOwner();
		PaymentSummaryResponse payments = paymentService.findByIntervention(id);
		BigDecimal deposits = payments.payments().stream()
				.filter(payment -> payment.type() == PaymentType.DEPOSIT)
				.map(PaymentResponse::amount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		// Daños reportados al recibir, en el orden en que se registraron
		List<String> initialServices = findingRepository.findByInterventionIdOrderByCreatedAtAscIdAsc(id).stream()
				.filter(finding -> finding.getOrigin() == FindingOrigin.INITIAL)
				.map(finding -> ReceiptLabels.SERVICE_TYPES.get(finding.getServiceType()))
				.toList();

		byte[] pdf = render(document -> {
			header(document, "Comprobante de ingreso", code, intervention.getReceivedAt());

			// 1. Código y clave destacados, con las instrucciones del portal
			PdfPTable keys = new PdfPTable(2);
			keys.setWidthPercentage(100);
			keys.addCell(keyCell("Código de la orden", lookupCode));
			keys.addCell(keyCell("Clave de consulta", accessKey.trim()));
			document.add(keys);
			String instructions = "Consulte el estado de su equipo en " + portalUrl + " con el código " + lookupCode
					+ " y la clave de consulta. Guarde la clave: por seguridad no se puede recuperar; si la pierde, "
					+ "pida en la tienda un nuevo comprobante y la anterior dejará de servir.";
			if (intervention.getWarrantyOf() != null) {
				instructions = "Garantía " + code + " de la orden " + lookupCode + ". " + instructions;
			}
			document.add(paragraph(instructions, SMALL, 4));

			// 2. Cliente y contactos; sin ninguno se advierte que no podremos contactarlo
			section(document, "Cliente");
			document.add(rows(
					"Nombre", customer.getFullName(),
					"Documento", ReceiptLabels.DOCUMENT_TYPES.get(customer.getDocumentType()) + " " + customer.getDocumentNumber(),
					"Celular", orNotAvailable(customer.getPhone()),
					"Correo", orNotAvailable(customer.getEmail())));
			if (customer.getPhone() == null && customer.getEmail() == null) {
				document.add(paragraph(NO_CONTACT_WARNING, ALERT, 4));
			}

			// 3. Equipo y servicio
			section(document, "Equipo");
			Device device = intervention.getDevice();
			document.add(rows(
					"Equipo", deviceName(device),
					"Tipo", ReceiptLabels.DEVICE_TYPES.get(device.getDeviceModel().getDeviceType()),
					"IMEI", orDash(device.getImei()),
					"Número de serie", orDash(device.getSerialNumber()),
					"Color", orDash(device.getColor()),
					"Estado físico", intervention.getPhysicalCondition()));

			section(document, "Servicio");
			document.add(rows(
					"Daños reportados", initialServices.isEmpty() ? "—" : String.join(", ", initialServices),
					"Problema reportado", intervention.getProblemDescription(),
					"Presupuesto inicial", money(intervention.getEstimatedPrice()),
					"Fecha estimada de entrega", formatDate(intervention.getEstimatedDeliveryDate()),
					"Abono recibido", Money.format(deposits)));

			// 4. Condiciones del servicio y firma
			section(document, "Condiciones del servicio");
			for (String term : serviceTerms()) {
				document.add(paragraph("• " + term, SMALL, 1));
			}
			signature(document);
		});
		return new ReceiptFile("ingreso-" + code + ".pdf", pdf);
	}

	// Solo de órdenes entregadas: fechas, resultado, servicios, repuestos sin precio, total, pagos y garantía
	@Override
	@Transactional(readOnly = true)
	public ReceiptFile deliveryReceipt(Long id) {
		Intervention intervention = pricingService.getEntity(id);
		String code = InterventionCode.of(intervention);
		if (intervention.getStatus() != InterventionStatus.DELIVERED) {
			throw new ConflictException("La orden " + code + " aún no se ha entregado");
		}
		Person customer = intervention.getDevice().getOwner();
		List<InterventionFinding> services = findingRepository.findByInterventionIdOrderByCreatedAtAscIdAsc(id).stream()
				.filter(finding -> finding.getApprovalStatus() == ApprovalStatus.APPROVED)
				.toList();
		List<InterventionSparePart> parts = sparePartService.findUsages(id);
		PaymentSummaryResponse payments = paymentService.findByIntervention(id);

		byte[] pdf = render(document -> {
			header(document, "Comprobante de entrega", code, intervention.getDeliveredAt());
			if (intervention.getWarrantyOf() != null) {
				document.add(paragraph("Garantía de la orden " + InterventionCode.of(intervention.getWarrantyOf()),
						LABEL, 4));
			}

			section(document, "Cliente y equipo");
			document.add(rows(
					"Cliente", customer.getFullName(),
					"Documento", ReceiptLabels.DOCUMENT_TYPES.get(customer.getDocumentType()) + " " + customer.getDocumentNumber(),
					"Equipo", deviceName(intervention.getDevice()),
					"Fecha estimada de entrega", formatDate(intervention.getEstimatedDeliveryDate()),
					"Fecha de entrega", formatDateTime(intervention.getDeliveredAt())));

			// 1. Resultado y servicios realizados
			section(document, "Servicios realizados");
			if (intervention.getRepairOutcome() != null) {
				String outcome = ReceiptLabels.OUTCOMES.get(intervention.getRepairOutcome());
				if (intervention.getOutcomeReason() != null) {
					outcome += ": " + intervention.getOutcomeReason();
				}
				document.add(rows("Resultado", outcome));
			}
			// Sin reparación solo se hizo el diagnóstico: lo solicitado se lista como no realizado
			boolean repaired = intervention.getRepairOutcome() == null || !intervention.getRepairOutcome().endsWithoutRepair();
			if (!repaired) {
				document.add(paragraph("Se realizó el diagnóstico; la reparación no se hizo. Servicio solicitado:", TEXT, 1));
			}
			for (InterventionFinding service : services) {
				document.add(paragraph("• " + ReceiptLabels.SERVICE_TYPES.get(service.getServiceType()) + ": "
						+ service.getDescription() + (repaired ? "" : " (no realizado)"), TEXT, 1));
			}
			if (intervention.getDiagnosis() != null) {
				document.add(rows("Diagnóstico", intervention.getDiagnosis()));
			}
			if (intervention.getSolution() != null) {
				document.add(rows("Solución", intervention.getSolution()));
			}

			// 2. Repuestos reemplazados, sin precios
			section(document, "Repuestos reemplazados");
			if (parts.isEmpty()) {
				document.add(paragraph("No se reemplazaron repuestos.", TEXT, 1));
			}
			for (InterventionSparePart part : parts) {
				document.add(paragraph("• " + part.getSparePart().getName() + " × " + part.getQuantity(), TEXT, 1));
			}

			// 3. Total y pagos
			section(document, "Pagos");
			PdfPTable table = new PdfPTable(new float[] { 2.2f, 2f, 2f, 2f });
			table.setWidthPercentage(100);
			table.setSpacingBefore(4);
			for (String heading : List.of("Fecha", "Tipo", "Medio")) {
				table.addCell(cell(heading, LABEL, Element.ALIGN_LEFT));
			}
			table.addCell(cell("Valor", LABEL, Element.ALIGN_RIGHT));
			for (PaymentResponse payment : payments.payments()) {
				table.addCell(cell(formatDateTime(payment.createdAt()), TEXT, Element.ALIGN_LEFT));
				table.addCell(cell(ReceiptLabels.PAYMENT_TYPES.get(payment.type()), TEXT, Element.ALIGN_LEFT));
				table.addCell(cell(ReceiptLabels.PAYMENT_METHODS.get(payment.method()), TEXT, Element.ALIGN_LEFT));
				String amount = (payment.type() == PaymentType.REFUND ? "−" : "") + Money.format(payment.amount());
				table.addCell(cell(amount, TEXT, Element.ALIGN_RIGHT));
			}
			document.add(table);
			document.add(rows(
					"Total", Money.format(payments.total()),
					"Pagado", Money.format(payments.paid())));

			// 4. Garantía desde el día de la entrega
			section(document, "Garantía");
			Integer days = intervention.getWarrantyDays();
			document.add(paragraph(days != null && days > 0 && intervention.getWarrantyUntil() != null
					? days + " días, hasta el " + formatDate(intervention.getWarrantyUntil())
							+ ". Cubre el servicio realizado y los repuestos instalados."
					: "Sin garantía.", TEXT, 2));
			signature(document);
		});
		return new ReceiptFile("entrega-" + code + ".pdf", pdf);
	}

	private byte[] render(Consumer<Document> content) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.LETTER, 42, 42, 36, 36);
		PdfWriter.getInstance(document, out);
		document.open();
		content.accept(document);
		document.close();
		return out.toByteArray();
	}

	private void header(Document document, String title, String code, LocalDateTime date) {
		PdfPTable table = new PdfPTable(new float[] { 3, 2 });
		table.setWidthPercentage(100);
		PdfPCell brand = new PdfPCell();
		brand.setBorder(Rectangle.NO_BORDER);
		brand.addElement(new Paragraph("FixTrack", TITLE));
		brand.addElement(new Paragraph(title, SECTION));
		table.addCell(brand);
		PdfPCell meta = new PdfPCell();
		meta.setBorder(Rectangle.NO_BORDER);
		Paragraph codeLine = new Paragraph(code, SECTION);
		codeLine.setAlignment(Element.ALIGN_RIGHT);
		meta.addElement(codeLine);
		Paragraph dateLine = new Paragraph(formatDateTime(date), SMALL);
		dateLine.setAlignment(Element.ALIGN_RIGHT);
		meta.addElement(dateLine);
		table.addCell(meta);
		table.setSpacingAfter(10);
		document.add(table);
	}

	private PdfPCell keyCell(String label, String value) {
		PdfPCell cell = new PdfPCell();
		cell.setBackgroundColor(HIGHLIGHT);
		cell.setBorderColor(PRIMARY);
		cell.setPadding(10);
		Paragraph labelLine = new Paragraph(label, KEY_LABEL);
		labelLine.setAlignment(Element.ALIGN_CENTER);
		cell.addElement(labelLine);
		Paragraph valueLine = new Paragraph(value, KEY_VALUE);
		valueLine.setAlignment(Element.ALIGN_CENTER);
		cell.addElement(valueLine);
		return cell;
	}

	private void section(Document document, String title) {
		document.add(paragraph(title, SECTION, 0, 10));
	}

	// Pares etiqueta-valor en dos columnas sin bordes
	private PdfPTable rows(String... labelsAndValues) {
		PdfPTable table = new PdfPTable(new float[] { 1.3f, 3 });
		table.setWidthPercentage(100);
		for (int i = 0; i < labelsAndValues.length; i += 2) {
			PdfPCell label = cell(labelsAndValues[i], LABEL, Element.ALIGN_LEFT);
			label.setBorder(Rectangle.NO_BORDER);
			PdfPCell value = cell(labelsAndValues[i + 1], TEXT, Element.ALIGN_LEFT);
			value.setBorder(Rectangle.NO_BORDER);
			table.addCell(label);
			table.addCell(value);
		}
		return table;
	}

	private PdfPCell cell(String text, Font font, int alignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font));
		cell.setHorizontalAlignment(alignment);
		cell.setPadding(3);
		cell.setBorderColor(new Color(226, 232, 240));
		return cell;
	}

	private void signature(Document document) {
		document.add(paragraph("\n\n______________________________", TEXT, 0, 18));
		document.add(paragraph("Firma del cliente", SMALL, 0));
	}

	private Paragraph paragraph(String text, Font font, float spacingAfter) {
		return paragraph(text, font, spacingAfter, 2);
	}

	private Paragraph paragraph(String text, Font font, float spacingAfter, float spacingBefore) {
		Paragraph paragraph = new Paragraph(text, font);
		paragraph.setSpacingBefore(spacingBefore);
		paragraph.setSpacingAfter(spacingAfter);
		return paragraph;
	}

	// Las condiciones salen del parámetro SERVICE_TERMS (una por línea) o, si está vacío, de las de por defecto
	private List<String> serviceTerms() {
		String configured = settingService.getValue(SettingKey.SERVICE_TERMS);
		if (configured == null || configured.isBlank()) {
			return DEFAULT_TERMS;
		}
		return configured.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
	}

	private String deviceName(Device device) {
		return device.getDeviceModel().getBrand().getName() + " " + device.getDeviceModel().getName();
	}

	private String money(BigDecimal amount) {
		return amount == null ? "—" : Money.format(amount);
	}

	private String formatDate(LocalDate date) {
		return date == null ? "—" : DATE.format(date);
	}

	private String formatDateTime(LocalDateTime dateTime) {
		return dateTime == null ? "—" : DATE_TIME.format(dateTime);
	}

	private String orNotAvailable(String value) {
		return value == null || value.isBlank() ? NOT_AVAILABLE : value;
	}

	private String orDash(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
}
