package com.fixtrack.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// Orden de los listados paginados: solo campos permitidos por cada servicio (nombre del front → propiedad de la entidad).
// Así ningún cliente ordena por columnas internas (hashes, textos cifrados) y un campo desconocido responde 400
public final class SortWhitelist {

	private SortWhitelist() {
	}

	public static Pageable restrict(Pageable pageable, Map<String, String> allowed) {
		List<Sort.Order> orders = new ArrayList<>();
		for (Sort.Order order : pageable.getSort()) {
			String property = allowed.get(order.getProperty());
			if (property == null) {
				throw new BusinessRuleException("No se puede ordenar por " + order.getProperty() + "; use uno de "
						+ String.join(", ", new TreeSet<>(allowed.keySet())));
			}
			orders.add(new Sort.Order(order.getDirection(), property));
		}
		// El id desempata para que la paginación sea estable cuando varios registros comparten el valor
		if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
			orders.add(Sort.Order.asc("id"));
		}
		return pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders))
				: Pageable.unpaged(Sort.by(orders));
	}
}
