package com.fixtrack.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

// Formato de pesos colombianos para mensajes y comprobantes ($ 45.000)
public final class Money {

	private static final Locale COLOMBIA = new Locale("es", "CO");

	private Money() {
	}

	public static String format(BigDecimal amount) {
		NumberFormat format = NumberFormat.getCurrencyInstance(COLOMBIA);
		format.setMaximumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
		format.setMinimumFractionDigits(format.getMaximumFractionDigits());
		return format.format(amount).replace('\u00a0', ' ');
	}
}
