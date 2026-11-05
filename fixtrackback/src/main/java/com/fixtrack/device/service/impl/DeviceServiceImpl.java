package com.fixtrack.device.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.device.dto.DeviceRequest;
import com.fixtrack.device.dto.DeviceResponse;
import com.fixtrack.device.entity.Device;
import com.fixtrack.device.entity.DeviceModel;
import com.fixtrack.device.entity.DeviceType;
import com.fixtrack.device.mapper.DeviceMapper;
import com.fixtrack.device.repository.DeviceRepository;
import com.fixtrack.device.service.DeviceModelService;
import com.fixtrack.device.service.DeviceService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.device.events.DeviceEventPublisher;
import com.fixtrack.infra.realtime.RealtimeEventType;

import lombok.RequiredArgsConstructor;

// Equipos de los clientes: IMEI obligatorio en teléfonos y reutilización por IMEI o por modelo y serie
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

	private final DeviceRepository deviceRepository;
	private final DeviceModelService deviceModelService;
	private final DeviceMapper deviceMapper;
	private final DeviceEventPublisher deviceEvents;

	@Override
	@Transactional(readOnly = true)
	public List<DeviceResponse> findByOwner(Long ownerId) {
		return deviceRepository.findByOwnerIdOrderByIdDesc(ownerId).stream().map(deviceMapper::toResponse).toList();
	}

	// Si el equipo ya existe debe ser del mismo cliente y modelo; cambiar el dueño reescribiría el historial
	@Override
	@Transactional
	public Device registerOrReuse(Person owner, DeviceRequest request) {
		if (request.deviceId() != null) {
			Device device = deviceRepository.findById(request.deviceId())
					.orElseThrow(() -> new NotFoundException("No existe el equipo con id " + request.deviceId()));
			if (!device.getOwner().getId().equals(owner.getId())) {
				throw new ConflictException("El equipo ya está registrado a nombre de otro cliente");
			}
			return device;
		}
		if (request.deviceModelId() == null) {
			throw new BusinessRuleException("Elija un equipo del cliente o el modelo del equipo nuevo");
		}

		DeviceModel model = deviceModelService.getEntity(request.deviceModelId());
		String imei = blankToNull(request.imei());
		String serialNumber = blankToNull(request.serialNumber());
		if (model.getDeviceType() == DeviceType.PHONE && imei == null) {
			throw new BusinessRuleException("El IMEI es obligatorio para los teléfonos");
		}

		Optional<Device> existing = imei != null
				? deviceRepository.findByImei(imei)
				: serialNumber != null
						? deviceRepository.findFirstByDeviceModelIdAndSerialNumber(model.getId(), serialNumber)
						: Optional.empty();

		if (existing.isPresent()) {
			Device device = existing.get();
			if (!device.getOwner().getId().equals(owner.getId())) {
				throw new ConflictException("El equipo ya está registrado a nombre de otro cliente");
			}
			if (!device.getDeviceModel().getId().equals(model.getId())) {
				throw new ConflictException("El equipo ya está registrado como " + device.getDeviceModel().getBrand().getName()
						+ " " + device.getDeviceModel().getName());
			}
			if (serialNumber != null) {
				device.setSerialNumber(serialNumber);
			}
			if (blankToNull(request.color()) != null) {
				device.setColor(request.color().trim());
			}
			if (blankToNull(request.observations()) != null) {
				device.setObservations(request.observations().trim());
			}
			deviceEvents.deviceChanged(RealtimeEventType.UPDATED, device);
			return device;
		}

		Device created = deviceRepository.save(Device.builder()
				.owner(owner)
				.deviceModel(model)
				.imei(imei)
				.serialNumber(serialNumber)
				.color(blankToNull(request.color()))
				.observations(blankToNull(request.observations()))
				.build());
		deviceEvents.deviceChanged(RealtimeEventType.CREATED, created);
		return created;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
