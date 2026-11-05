-- FixTrack: hora estimada de entrega (5 de noviembre de 2026)
-- La orden guarda la hora acordada con el cliente junto a la fecha que ya existía; la reprogramación guarda
-- la hora anterior y la nueva. Las columnas aceptan NULL: las órdenes y reprogramaciones anteriores quedan
-- con su fecha y sin hora. Con ddl-auto: update Hibernate las crea solo; este script es para aplicarlo a mano
-- (por ejemplo, con ddl-auto: validate o en otro ambiente). Se puede correr una sola vez.

ALTER TABLE intervention
  ADD COLUMN estimated_delivery_time TIME NULL AFTER estimated_delivery_date;

ALTER TABLE delivery_date_change
  ADD COLUMN previous_time TIME NULL AFTER previous_date,
  ADD COLUMN new_time TIME NULL AFTER new_date;

-- Reversión (solo si hay que deshacer el cambio):
-- ALTER TABLE delivery_date_change DROP COLUMN new_time, DROP COLUMN previous_time;
-- ALTER TABLE intervention DROP COLUMN estimated_delivery_time;
