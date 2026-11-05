package com.fixtrack.person.events;

// La persona perdió el acceso que tenía su sesión (baja, cambio de rol o de contraseña): su canal en tiempo real se corta
public record PersonAccessRevokedEvent(Long personId) {
}
