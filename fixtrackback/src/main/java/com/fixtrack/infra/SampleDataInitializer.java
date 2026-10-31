package com.fixtrack.infra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.cash.entity.CashClosing;
import com.fixtrack.cash.entity.Payment;
import com.fixtrack.cash.entity.PaymentMethod;
import com.fixtrack.cash.entity.PaymentType;
import com.fixtrack.cash.repository.CashClosingRepository;
import com.fixtrack.cash.repository.PaymentRepository;
import com.fixtrack.device.entity.Brand;
import com.fixtrack.device.entity.Device;
import com.fixtrack.device.entity.DeviceModel;
import com.fixtrack.device.entity.DeviceType;
import com.fixtrack.device.repository.BrandRepository;
import com.fixtrack.device.repository.DeviceModelRepository;
import com.fixtrack.device.repository.DeviceRepository;
import com.fixtrack.intervention.entity.ApprovalStatus;
import com.fixtrack.intervention.entity.FindingOrigin;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionFinding;
import com.fixtrack.intervention.entity.InterventionSparePart;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.entity.InterventionStatusHistory;
import com.fixtrack.intervention.entity.RepairOutcome;
import com.fixtrack.intervention.entity.ServiceType;
import com.fixtrack.intervention.repository.InterventionFindingRepository;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.intervention.repository.InterventionSparePartRepository;
import com.fixtrack.intervention.repository.InterventionStatusHistoryRepository;
import com.fixtrack.inventory.entity.PartQuality;
import com.fixtrack.inventory.entity.PartRequest;
import com.fixtrack.inventory.entity.PartRequestStatus;
import com.fixtrack.inventory.entity.PartType;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.SparePartCompatibility;
import com.fixtrack.inventory.entity.StockMovement;
import com.fixtrack.inventory.entity.StockMovementType;
import com.fixtrack.inventory.entity.Supplier;
import com.fixtrack.inventory.entity.SupplierSparePart;
import com.fixtrack.inventory.repository.PartRequestRepository;
import com.fixtrack.inventory.repository.PartTypeRepository;
import com.fixtrack.inventory.repository.SparePartCompatibilityRepository;
import com.fixtrack.inventory.repository.SparePartRepository;
import com.fixtrack.inventory.repository.StockMovementRepository;
import com.fixtrack.inventory.repository.SupplierRepository;
import com.fixtrack.inventory.repository.SupplierSparePartRepository;
import com.fixtrack.person.entity.Availability;
import com.fixtrack.person.entity.DocumentType;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.PersonSpecialty;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.entity.Specialty;
import com.fixtrack.person.repository.PersonRepository;
import com.fixtrack.person.repository.PersonSpecialtyRepository;
import com.fixtrack.person.repository.SpecialtyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Inicializa datos de prueba completos si la base de datos está vacía: catálogos, repuestos, clientes y órdenes
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class SampleDataInitializer implements ApplicationRunner {

	private final BrandRepository brandRepository;
	private final DeviceModelRepository deviceModelRepository;
	private final DeviceRepository deviceRepository;
	private final SpecialtyRepository specialtyRepository;
	private final PersonSpecialtyRepository personSpecialtyRepository;
	private final PersonRepository personRepository;
	private final PartTypeRepository partTypeRepository;
	private final SparePartRepository sparePartRepository;
	private final SparePartCompatibilityRepository compatibilityRepository;
	private final SupplierRepository supplierRepository;
	private final SupplierSparePartRepository supplierSparePartRepository;
	private final StockMovementRepository stockMovementRepository;
	private final InterventionRepository interventionRepository;
	private final InterventionFindingRepository findingRepository;
	private final InterventionSparePartRepository usageRepository;
	private final InterventionStatusHistoryRepository historyRepository;
	private final PartRequestRepository partRequestRepository;
	private final PaymentRepository paymentRepository;
	private final CashClosingRepository cashClosingRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	@Value("${fixtrack.seed-data:true}")
	private boolean seedDataEnabled;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!seedDataEnabled) {
			log.info("Carga de datos de prueba desactivada (fixtrack.seed-data=false)");
			return;
		}

		if (brandRepository.count() > 0) {
			log.info("La base de datos ya contiene catálogos; se omite la carga de datos de prueba");
			return;
		}

		log.info("Iniciando población de datos de prueba en la base de datos de FixTrack...");

		LocalDate today = LocalDate.now(clock);
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime yesterday = now.minusDays(1);
		LocalDate yesterdayDate = today.minusDays(1);

		// 1. Personal operativo
		Person admin = getOrCreateUser("admin@fixtrack.local", "Administrador Principal", Role.ADMIN, "0000000000");
		Person coordinator = getOrCreateUser("coordinador@fixtrack.local", "Carlos Andrés Coordinador", Role.COORDINATOR, "1000000001");
		Person advisor = getOrCreateUser("asesor.prueba@fixtrack.local", "Mariana Gómez Asesora", Role.ADVISOR, "1000000002");
		Person technician1 = getOrCreateUser("tecnico@fixtrack.local", "Alejandro Torres Técnico", Role.TECHNICIAN, "1000000003");
		Person technician2 = getOrCreateUser("javier.tecnico@fixtrack.local", "Javier Ramos Técnico", Role.TECHNICIAN, "1000000004");

		// 2. Especialidades
		Specialty specScreens = specialtyRepository.save(Specialty.builder().name("Pantallas y Cristales").build());
		Specialty specSoldering = specialtyRepository.save(Specialty.builder().name("Microsoldadura y Placa Base").build());
		Specialty specBattery = specialtyRepository.save(Specialty.builder().name("Baterías y Alimentación").build());
		Specialty specPorts = specialtyRepository.save(Specialty.builder().name("Puertos y Conectividad").build());
		Specialty specSoftware = specialtyRepository.save(Specialty.builder().name("Software y Respaldo").build());

		personSpecialtyRepository.save(new PersonSpecialty(technician1, specScreens));
		personSpecialtyRepository.save(new PersonSpecialty(technician1, specBattery));
		personSpecialtyRepository.save(new PersonSpecialty(technician2, specSoldering));
		personSpecialtyRepository.save(new PersonSpecialty(technician2, specPorts));
		personSpecialtyRepository.save(new PersonSpecialty(technician2, specSoftware));

		// 3. Marcas
		Brand apple = brandRepository.save(Brand.builder().name("Apple").build());
		Brand samsung = brandRepository.save(Brand.builder().name("Samsung").build());
		Brand xiaomi = brandRepository.save(Brand.builder().name("Xiaomi").build());
		Brand motorola = brandRepository.save(Brand.builder().name("Motorola").build());

		// 4. Modelos de equipos
		DeviceModel ip11 = deviceModelRepository.save(DeviceModel.builder().brand(apple).name("iPhone 11").deviceType(DeviceType.PHONE).build());
		DeviceModel ip12 = deviceModelRepository.save(DeviceModel.builder().brand(apple).name("iPhone 12").deviceType(DeviceType.PHONE).build());
		DeviceModel ip13 = deviceModelRepository.save(DeviceModel.builder().brand(apple).name("iPhone 13").deviceType(DeviceType.PHONE).build());
		DeviceModel ip14Pro = deviceModelRepository.save(DeviceModel.builder().brand(apple).name("iPhone 14 Pro").deviceType(DeviceType.PHONE).build());
		DeviceModel ipad9 = deviceModelRepository.save(DeviceModel.builder().brand(apple).name("iPad 9na Generación").deviceType(DeviceType.TABLET).build());
		DeviceModel appleWatch8 = deviceModelRepository.save(DeviceModel.builder().brand(apple).name("Apple Watch Series 8").deviceType(DeviceType.SMARTWATCH).build());

		DeviceModel s21 = deviceModelRepository.save(DeviceModel.builder().brand(samsung).name("Galaxy S21").deviceType(DeviceType.PHONE).build());
		DeviceModel s22Ultra = deviceModelRepository.save(DeviceModel.builder().brand(samsung).name("Galaxy S22 Ultra").deviceType(DeviceType.PHONE).build());
		DeviceModel s23 = deviceModelRepository.save(DeviceModel.builder().brand(samsung).name("Galaxy S23").deviceType(DeviceType.PHONE).build());
		DeviceModel a54 = deviceModelRepository.save(DeviceModel.builder().brand(samsung).name("Galaxy A54 5G").deviceType(DeviceType.PHONE).build());
		DeviceModel tabS8 = deviceModelRepository.save(DeviceModel.builder().brand(samsung).name("Galaxy Tab S8").deviceType(DeviceType.TABLET).build());

		DeviceModel rn11 = deviceModelRepository.save(DeviceModel.builder().brand(xiaomi).name("Redmi Note 11").deviceType(DeviceType.PHONE).build());
		DeviceModel rn12 = deviceModelRepository.save(DeviceModel.builder().brand(xiaomi).name("Redmi Note 12").deviceType(DeviceType.PHONE).build());
		DeviceModel mi13Pro = deviceModelRepository.save(DeviceModel.builder().brand(xiaomi).name("Xiaomi 13 Pro").deviceType(DeviceType.PHONE).build());
		DeviceModel pocoX5 = deviceModelRepository.save(DeviceModel.builder().brand(xiaomi).name("Poco X5 Pro 5G").deviceType(DeviceType.PHONE).build());

		DeviceModel g52 = deviceModelRepository.save(DeviceModel.builder().brand(motorola).name("Moto G52").deviceType(DeviceType.PHONE).build());
		DeviceModel g84 = deviceModelRepository.save(DeviceModel.builder().brand(motorola).name("Moto G84 5G").deviceType(DeviceType.PHONE).build());
		DeviceModel edge40 = deviceModelRepository.save(DeviceModel.builder().brand(motorola).name("Edge 40 Neo").deviceType(DeviceType.PHONE).build());

		// 5. Tipos de repuestos
		PartType typeScreen = partTypeRepository.save(PartType.builder().name("Pantalla Completa").build());
		PartType typeBattery = partTypeRepository.save(PartType.builder().name("Batería").build());
		PartType typeChargingPort = partTypeRepository.save(PartType.builder().name("Puerto de Carga Flex").build());
		PartType typeCamera = partTypeRepository.save(PartType.builder().name("Módulo de Cámaras").build());
		PartType typeHousing = partTypeRepository.save(PartType.builder().name("Tapa Trasera de Vidrio").build());
		PartType typeSpeaker = partTypeRepository.save(PartType.builder().name("Altavoz y Auricular").build());

		// 6. Proveedores
		Supplier supTech = supplierRepository.save(Supplier.builder()
				.name("Importadora Móvil Tech S.A.S.")
				.taxId("901234567-1")
				.contactName("Germán Castro")
				.phone("3104567890")
				.email("ventas@moviltech.co")
				.build());

		Supplier supExpress = supplierRepository.save(Supplier.builder()
				.name("Repuestos y Partes Express")
				.taxId("900876543-2")
				.contactName("Adriana Pérez")
				.phone("3159876543")
				.email("pedidos@partesexpress.com")
				.build());

		Supplier supGlobal = supplierRepository.save(Supplier.builder()
				.name("Soluciones Globales Celulares")
				.taxId("901555666-3")
				.contactName("Roberto Silva")
				.phone("3201112233")
				.email("distribucion@solucionesglobales.co")
				.build());

		// 7. Repuestos de Inventario
		SparePart partScreenIp13 = createSparePart("Pantalla iPhone 13 OLED (OEM)", "APP-SCR-IP13-OEM",
				typeScreen, ip13, PartQuality.OEM, 8, 2, money(180000), money(320000), coordinator);
		SparePart partBatteryIp13 = createSparePart("Batería iPhone 13 3240mAh (Original)", "APP-BAT-IP13-ORI",
				typeBattery, ip13, PartQuality.ORIGINAL, 12, 3, money(90000), money(170000), coordinator);
		SparePart partScreenS23 = createSparePart("Pantalla Samsung Galaxy S23 Dynamic AMOLED", "SAM-SCR-S23-ORI",
				typeScreen, s23, PartQuality.ORIGINAL, 5, 2, money(350000), money(580000), coordinator);
		SparePart partBatteryS23 = createSparePart("Batería Samsung Galaxy S23 (OEM)", "SAM-BAT-S23-OEM",
				typeBattery, s23, PartQuality.OEM, 10, 2, money(80000), money(150000), coordinator);
		SparePart partScreenRn12 = createSparePart("Pantalla Redmi Note 12 AMOLED (OEM)", "XIA-SCR-RN12-OEM",
				typeScreen, rn12, PartQuality.OEM, 15, 3, money(120000), money(230000), coordinator);
		SparePart partBatteryRn12 = createSparePart("Batería Redmi Note 12 5000mAh (Original)", "XIA-BAT-RN12-ORI",
				typeBattery, rn12, PartQuality.ORIGINAL, 14, 3, money(65000), money(130000), coordinator);
		SparePart partScreenG52 = createSparePart("Pantalla Moto G52 OLED (Genérica)", "MOT-SCR-G52-GEN",
				typeScreen, g52, PartQuality.GENERIC, 7, 2, money(95000), money(190000), coordinator);
		SparePart partBatteryG52 = createSparePart("Batería Moto G52 (OEM)", "MOT-BAT-G52-OEM",
				typeBattery, g52, PartQuality.OEM, 9, 2, money(55000), money(110000), coordinator);
		SparePart partPortIp13 = createSparePart("Puerto de Carga Flex iPhone 13 (OEM)", "APP-CHG-IP13-OEM",
				typeChargingPort, ip13, PartQuality.OEM, 6, 2, money(35000), money(85000), coordinator);
		SparePart partCamIp13 = createSparePart("Módulo Cámara Trasera iPhone 13 (Original)", "APP-CAM-IP13-ORI",
				typeCamera, ip13, PartQuality.ORIGINAL, 3, 1, money(140000), money(260000), coordinator);

		// Compatibilidades adicionales
		compatibilityRepository.save(new SparePartCompatibility(partBatteryIp13, ip13));
		compatibilityRepository.save(new SparePartCompatibility(partScreenIp13, ip13));

		// Catálogo de proveedores
		addSupplierPart(supTech, partScreenIp13, "TECH-SCR-13", money(175000), 2, true);
		addSupplierPart(supExpress, partBatteryIp13, "EXP-BAT-13", money(88000), 1, true);
		addSupplierPart(supGlobal, partScreenS23, "GLB-S23-DISP", money(340000), 3, true);
		addSupplierPart(supTech, partScreenRn12, "TECH-RN12", money(115000), 2, true);

		// 8. Clientes
		Person client1 = createCustomer("Carlos Mendoza", "1017234567", "3105551234", "carlos.mendoza@gmail.com");
		Person client2 = createCustomer("María Paula Gómez", "1020345678", "3126667890", "mp.gomez@hotmail.com");
		Person client3 = createCustomer("Andrés Felipe Morales", "1032456789", "3157778901", "andres.morales@yahoo.es");
		Person client4 = createCustomer("Laura Restrepo", "1045567890", "3188889012", "laura.restrepo@outlook.com");
		Person client5 = createCustomer("David Quintero", "1056678901", "3209990123", "david.quintero@gmail.com");
		Person client6 = createCustomer("Valentina Henao", "1067789012", "3012223344", "valentina.henao@gmail.com");

		// 9. Equipos de los clientes
		Device dev1 = createDevice(client1, ip13, "356789102345671", "F2LX90KLMD6R", "Azul medianoche");
		Device dev2 = createDevice(client2, s23, "358901234567892", "R5CW30ABX9Z", "Negro fantasma");
		Device dev3 = createDevice(client3, rn12, "864567051234563", "28491/20GA001", "Gris grafito");
		Device dev4 = createDevice(client4, ip14Pro, "359123456789014", "MQ0X3LL/A-81", "Morado oscuro");
		Device dev5 = createDevice(client5, g52, "353456098765434", "ZY22G38KLP", "Blanco");
		Device dev6 = createDevice(client6, ip12, "354567890123455", "DX4W80LKMD5Q", "Negro");

		// 10. Cierre de caja de ayer para tener datos históricos
		CashClosing closingYesterday = cashClosingRepository.save(CashClosing.builder()
				.closedBy(advisor)
				.closingDate(yesterdayDate)
				.expectedCash(money(250000))
				.expectedTransfer(money(180000))
				.expectedCard(BigDecimal.ZERO.setScale(2))
				.countedCash(money(250000))
				.notes("Cierre de caja sin novedades, cuadre exacto de efectivo.")
				.build());

		// 11. Órdenes de Reparación en Diversos Estados

		// ORDEN 1: RECEIVED (Recién ingresado con abono en efectivo hoy)
		Intervention ord1 = interventionRepository.save(Intervention.builder()
				.device(dev1)
				.advisor(advisor)
				.status(InterventionStatus.RECEIVED)
				.problemDescription("Pantalla parpadea con líneas verdes tras caída fuerte.")
				.physicalCondition("Bisel raspado en borde superior derecho. Vidrio astillado.")
				.unlockCode("1234")
				.estimatedPrice(money(380000))
				.estimatedDeliveryDate(today.plusDays(2))
				.accessKeyHash(passwordEncoder.encode("1234"))
				.build());
		recordStatusHistory(ord1, null, InterventionStatus.RECEIVED, advisor, "Ingreso del equipo al taller.");
		findingRepository.save(InterventionFinding.builder()
				.intervention(ord1)
				.serviceType(ServiceType.SCREEN_REPLACEMENT)
				.description("Cambio de módulo de pantalla OLED completa")
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(advisor)
				.build());
		paymentRepository.save(Payment.builder()
				.intervention(ord1)
				.amount(money(100000))
				.method(PaymentMethod.CASH)
				.type(PaymentType.DEPOSIT)
				.receivedBy(advisor)
				.build());

		// ORDEN 2: IN_DIAGNOSIS (En diagnóstico con técnico asignado)
		Intervention ord2 = interventionRepository.save(Intervention.builder()
				.device(dev2)
				.advisor(advisor)
				.technician(technician1)
				.status(InterventionStatus.IN_DIAGNOSIS)
				.problemDescription("No enciende ni muestra carga tras sumergirse accidentalmente.")
				.physicalCondition("Detector de humedad activado, sin daños físicos externos.")
				.unlockCode("Patrón en L")
				.estimatedPrice(money(150000))
				.estimatedDeliveryDate(today.plusDays(3))
				.accessKeyHash(passwordEncoder.encode("7890"))
				.build());
		recordStatusHistory(ord2, null, InterventionStatus.RECEIVED, advisor, "Ingreso de equipo.");
		recordStatusHistory(ord2, InterventionStatus.RECEIVED, InterventionStatus.IN_DIAGNOSIS, technician1, "Desensamble inicial y limpieza por ultrasonido.");
		findingRepository.save(InterventionFinding.builder()
				.intervention(ord2)
				.serviceType(ServiceType.WATER_DAMAGE)
				.description("Limpieza química por ultrasonido y verificación de sulfatación en placa")
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(advisor)
				.build());

		// ORDEN 3: WAITING_PARTS (Esperando repuesto con solicitud de compra creada)
		Intervention ord3 = interventionRepository.save(Intervention.builder()
				.device(dev4)
				.advisor(advisor)
				.technician(technician2)
				.status(InterventionStatus.WAITING_PARTS)
				.problemDescription("Cámara trasera no enfoca de cerca y emite zumbido al enfocar.")
				.physicalCondition("Vidrio del lente sin rayones, carcasa impecable.")
				.unlockCode("2580")
				.estimatedPrice(money(320000))
				.estimatedDeliveryDate(today.plusDays(4))
				.accessKeyHash(passwordEncoder.encode("9012"))
				.build());
		recordStatusHistory(ord3, null, InterventionStatus.RECEIVED, advisor, "Ingreso para servicio de cámara.");
		recordStatusHistory(ord3, InterventionStatus.RECEIVED, InterventionStatus.IN_DIAGNOSIS, technician2, "Diagnóstico: actuador OIS de cámara principal defectuoso.");
		recordStatusHistory(ord3, InterventionStatus.IN_DIAGNOSIS, InterventionStatus.WAITING_PARTS, technician2, "En espera de módulo de cámara de repuesto.");
		findingRepository.save(InterventionFinding.builder()
				.intervention(ord3)
				.serviceType(ServiceType.CAMERA)
				.description("Sustitución de módulo de cámara con estabilizador óptico")
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(technician2)
				.build());
		partRequestRepository.save(PartRequest.builder()
				.intervention(ord3)
				.requestedBy(technician2)
				.supplier(supTech)
				.description("Módulo de cámara trasera iPhone 14 Pro Original")
				.quantity(1)
				.status(PartRequestStatus.ORDERED)
				.estimatedCost(money(160000))
				.build());

		// ORDEN 4: IN_REPAIR (En proceso de reparación con repuesto ya consumido)
		Intervention ord4 = interventionRepository.save(Intervention.builder()
				.device(dev3)
				.advisor(advisor)
				.technician(technician1)
				.status(InterventionStatus.IN_REPAIR)
				.problemDescription("Batería hinchada, levanta la tapa trasera y dura menos de 2 horas.")
				.physicalCondition("Tapa trasera levemente levantada en el lateral izquierdo.")
				.unlockCode("0000")
				.estimatedPrice(money(180000))
				.laborCost(money(40000))
				.estimatedDeliveryDate(today.plusDays(1))
				.accessKeyHash(passwordEncoder.encode("8901"))
				.build());
		recordStatusHistory(ord4, null, InterventionStatus.RECEIVED, advisor, "Ingreso de equipo.");
		recordStatusHistory(ord4, InterventionStatus.RECEIVED, InterventionStatus.IN_DIAGNOSIS, technician1, "Confirmada hinchazón de celda de batería.");
		recordStatusHistory(ord4, InterventionStatus.IN_DIAGNOSIS, InterventionStatus.IN_REPAIR, technician1, "Instalando batería nueva y calibrando ciclos.");
		findingRepository.save(InterventionFinding.builder()
				.intervention(ord4)
				.serviceType(ServiceType.BATTERY_REPLACEMENT)
				.description("Cambio de celda de batería de alta capacidad")
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(advisor)
				.build());
		usageRepository.save(InterventionSparePart.builder()
				.intervention(ord4)
				.sparePart(partBatteryRn12)
				.quantity(1)
				.unitPrice(partBatteryRn12.getSalePrice())
				.costPrice(partBatteryRn12.getCostPrice())
				.createdBy(technician1)
				.build());

		// ORDEN 5: READY (Reparado, listo para entrega, con abono previo y saldo pendiente)
		Intervention ord5 = interventionRepository.save(Intervention.builder()
				.device(dev5)
				.advisor(advisor)
				.technician(technician2)
				.status(InterventionStatus.READY)
				.problemDescription("Pantalla rota sin táctil.")
				.physicalCondition("Cristal totalmente estrellado.")
				.unlockCode("5544")
				.estimatedPrice(money(240000))
				.laborCost(money(50000))
				.totalPrice(money(240000))
				.repairOutcome(RepairOutcome.REPAIRED)
				.outcomeReason("Se reemplazó pantalla OLED y se verificó touch, brillo y sensores.")
				.estimatedDeliveryDate(today)
				.readyAt(now.minusHours(2))
				.accessKeyHash(passwordEncoder.encode("0123"))
				.build());
		recordStatusHistory(ord5, null, InterventionStatus.RECEIVED, advisor, "Ingreso de equipo.");
		recordStatusHistory(ord5, InterventionStatus.RECEIVED, InterventionStatus.IN_DIAGNOSIS, technician2, "Revisión completada.");
		recordStatusHistory(ord5, InterventionStatus.IN_DIAGNOSIS, InterventionStatus.IN_REPAIR, technician2, "Cambio de pantalla.");
		recordStatusHistory(ord5, InterventionStatus.IN_REPAIR, InterventionStatus.READY, technician2, "Equipo listo para entregar.");
		findingRepository.save(InterventionFinding.builder()
				.intervention(ord5)
				.serviceType(ServiceType.SCREEN_REPLACEMENT)
				.description("Instalación de módulo de pantalla")
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(advisor)
				.build());
		usageRepository.save(InterventionSparePart.builder()
				.intervention(ord5)
				.sparePart(partScreenG52)
				.quantity(1)
				.unitPrice(partScreenG52.getSalePrice())
				.costPrice(partScreenG52.getCostPrice())
				.createdBy(technician2)
				.build());
		paymentRepository.save(Payment.builder()
				.intervention(ord5)
				.cashClosing(closingYesterday)
				.amount(money(50000))
				.method(PaymentMethod.TRANSFER)
				.type(PaymentType.DEPOSIT)
				.receivedBy(advisor)
				.build());

		// ORDEN 6: DELIVERED (Entregada ayer con pago total y garantía de 90 días activa)
		Intervention ord6 = interventionRepository.save(Intervention.builder()
				.device(dev6)
				.advisor(advisor)
				.technician(technician1)
				.status(InterventionStatus.DELIVERED)
				.problemDescription("Batería al 68%, se apaga súbitamente al llegar al 20%.")
				.physicalCondition("Signos leves de uso normal.")
				.estimatedPrice(money(180000))
				.laborCost(money(40000))
				.totalPrice(money(180000))
				.repairOutcome(RepairOutcome.REPAIRED)
				.outcomeReason("Batería sustituida con éxito.")
				.estimatedDeliveryDate(yesterdayDate)
				.readyAt(yesterday.minusHours(4))
				.deliveredAt(yesterday)
				.warrantyDays(90)
				.warrantyUntil(today.plusDays(90))
				.accessKeyHash(passwordEncoder.encode("3344"))
				.deliveryNotes("Se entrega equipo funcionando y probado con el cliente.")
				.build());
		recordStatusHistory(ord6, null, InterventionStatus.RECEIVED, advisor, "Ingreso inicial.");
		recordStatusHistory(ord6, InterventionStatus.RECEIVED, InterventionStatus.IN_DIAGNOSIS, technician1, "Diagnóstico batería.");
		recordStatusHistory(ord6, InterventionStatus.IN_DIAGNOSIS, InterventionStatus.IN_REPAIR, technician1, "Instalación de batería.");
		recordStatusHistory(ord6, InterventionStatus.IN_REPAIR, InterventionStatus.READY, technician1, "Equipo listo.");
		recordStatusHistory(ord6, InterventionStatus.READY, InterventionStatus.DELIVERED, advisor, "Entrega al cliente.");
		findingRepository.save(InterventionFinding.builder()
				.intervention(ord6)
				.serviceType(ServiceType.BATTERY_REPLACEMENT)
				.description("Cambio de batería iPhone 12")
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(advisor)
				.build());
		usageRepository.save(InterventionSparePart.builder()
				.intervention(ord6)
				.sparePart(partBatteryIp13) // Compatible
				.quantity(1)
				.unitPrice(money(140000))
				.costPrice(money(75000))
				.createdBy(technician1)
				.build());
		paymentRepository.save(Payment.builder()
				.intervention(ord6)
				.cashClosing(closingYesterday)
				.amount(money(80000))
				.method(PaymentMethod.CASH)
				.type(PaymentType.DEPOSIT)
				.receivedBy(advisor)
				.build());
		paymentRepository.save(Payment.builder()
				.intervention(ord6)
				.cashClosing(closingYesterday)
				.amount(money(100000))
				.method(PaymentMethod.TRANSFER)
				.type(PaymentType.FINAL)
				.receivedBy(advisor)
				.build());

		// ORDEN 7: GARANTÍA DELIVERED (Asociada a orden 6 con precio $0)
		Intervention ord7Warranty = interventionRepository.save(Intervention.builder()
				.device(dev6)
				.advisor(advisor)
				.technician(technician1)
				.status(InterventionStatus.DELIVERED)
				.problemDescription("Garantía: la tapa trasera tenía un ligero desajuste.")
				.physicalCondition("Mismo estado de entrega.")
				.estimatedPrice(BigDecimal.ZERO.setScale(2))
				.laborCost(BigDecimal.ZERO.setScale(2))
				.totalPrice(BigDecimal.ZERO.setScale(2))
				.repairOutcome(RepairOutcome.REPAIRED)
				.outcomeReason("Reajuste y sellado estanco realizado.")
				.estimatedDeliveryDate(today)
				.readyAt(now.minusHours(1))
				.deliveredAt(now)
				.warrantyOf(ord6)
				.warrantyNumber(1)
				.warrantyDays(30)
				.warrantyUntil(today.plusDays(30))
				.accessKeyHash(passwordEncoder.encode("3344"))
				.deliveryNotes("Revisión de garantía entregada a satisfacción a costo $0.")
				.build());
		recordStatusHistory(ord7Warranty, null, InterventionStatus.RECEIVED, advisor, "Ingreso por garantía de orden FT-00006.");
		recordStatusHistory(ord7Warranty, InterventionStatus.RECEIVED, InterventionStatus.DELIVERED, advisor, "Reajuste y entrega inmediata al cliente.");

		log.info("Población de datos de prueba completada exitosamente.");
	}

	private Person getOrCreateUser(String email, String fullName, Role role, String docNumber) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		return personRepository.findByEmail(normalizedEmail)
				.orElseGet(() -> personRepository.save(Person.builder()
						.documentType(DocumentType.CC)
						.documentNumber(docNumber)
						.fullName(fullName)
						.email(normalizedEmail)
						.password(passwordEncoder.encode("Password123*"))
						.role(role)
						.availability(role == Role.TECHNICIAN ? Availability.AVAILABLE : null)
						.active(true)
						.build()));
	}

	private Person createCustomer(String fullName, String docNumber, String phone, String email) {
		return personRepository.save(Person.builder()
				.documentType(DocumentType.CC)
				.documentNumber(docNumber)
				.fullName(fullName)
				.phone(phone)
				.email(email)
				.role(Role.CUSTOMER)
				.active(true)
				.build());
	}

	private Device createDevice(Person owner, DeviceModel model, String imei, String serial, String color) {
		return deviceRepository.save(Device.builder()
				.owner(owner)
				.deviceModel(model)
				.imei(imei)
				.serialNumber(serial)
				.color(color)
				.build());
	}

	private SparePart createSparePart(String name, String sku, PartType type, DeviceModel model,
			PartQuality quality, int stock, int minStock, BigDecimal cost, BigDecimal sale, Person createdBy) {
		SparePart part = sparePartRepository.save(SparePart.builder()
				.name(name)
				.sku(sku)
				.partType(type)
				.deviceModel(model)
				.quality(quality)
				.stock(stock)
				.minStock(minStock)
				.costPrice(cost)
				.salePrice(sale)
				.active(true)
				.build());

		stockMovementRepository.save(StockMovement.builder()
				.sparePart(part)
				.type(StockMovementType.IN_PURCHASE)
				.quantity(stock)
				.balanceAfter(stock)
				.unitCost(cost)
				.reason("Inventario inicial de apertura")
				.createdBy(createdBy)
				.build());

		return part;
	}

	private void addSupplierPart(Supplier supplier, SparePart part, String sku, BigDecimal cost, int leadDays, boolean pref) {
		SupplierSparePart link = new SupplierSparePart(supplier, part);
		link.setSupplierSku(sku);
		link.setLastCost(cost);
		link.setLeadTimeDays(leadDays);
		link.setPreferred(pref);
		supplierSparePartRepository.save(link);
	}

	private void recordStatusHistory(Intervention intervention, InterventionStatus from, InterventionStatus to,
			Person changedBy, String note) {
		historyRepository.save(InterventionStatusHistory.builder()
				.intervention(intervention)
				.fromStatus(from)
				.toStatus(to)
				.changedBy(changedBy)
				.note(note)
				.build());
	}

	private BigDecimal money(long amount) {
		return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
	}
}

