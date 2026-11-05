package com.fixtrack.infra.realtime;

// Topics del broker STOMP; quién puede suscribirse a cada uno lo decide StompAuthChannelInterceptor
public final class RealtimeTopics {

	public static final String PREFIX = "/topic";
	public static final String SPARE_PARTS = PREFIX + "/spare-parts";
	public static final String STOCK_ALERTS = PREFIX + "/alerts/stock";
	public static final String INTERVENTIONS = PREFIX + "/interventions";
	public static final String PART_REQUESTS = PREFIX + "/part-requests";
	public static final String DEVICES = PREFIX + "/devices";
	public static final String PERSONS = PREFIX + "/persons";
	public static final String ORDERS_PREFIX = PREFIX + "/orders/";

	// Colas privadas: el servidor publica en /queue/... para un usuario y él se suscribe a /user/queue/...
	public static final String USER_PREFIX = "/user";
	public static final String MY_INTERVENTIONS = "/queue/interventions";
	public static final String MY_PART_REQUESTS = "/queue/part-requests";

	private RealtimeTopics() {
	}

	// Canal público de una orden en el portal, por el código de la orden original (FT-00001)
	public static String order(String originalCode) {
		return ORDERS_PREFIX + originalCode;
	}
}
