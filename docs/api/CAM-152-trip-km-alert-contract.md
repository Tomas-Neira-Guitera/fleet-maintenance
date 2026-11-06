# CAM-152 — Aviso de kilometraje fuera de lo normal en un viaje

Al cerrar un viaje con el post-viaje, el servidor revisa si el chofer cargó un km muy distinto al
habitual del vehículo o menor al que ya tenía. Si es así, marca el viaje y le avisa al admin. Sirve
para detectar errores de carga o un uso indebido del camión. No cambia ningún endpoint de escritura.

## Qué cambia en la API

| Dónde | Qué |
|---|---|
| `GET /api/notifications`, configuración de avisos | Tipo nuevo `trip_km_anomaly`, configurable, que por defecto le llega al admin (in-app y push) |
| `GET /api/vehicles/{id}/history` | `inspections[].kmAlert`: la marca del viaje en su post-viaje, o `null` |

`kmAlert` (schema `TripKmAlert` de `openapi.yaml`):

```json
{
  "reason": "above_average",
  "tripKm": 1250,
  "averageKm": 400,
  "declaredKm": 51250,
  "vehicleKm": 50000
}
```

## Decisiones

### 1. Los km de un viaje

Son el km del post-viaje menos el del pre-viaje del mismo viaje, los dos en su parte entera, como
en CAM-90 y CAM-137. Un viaje que se cerró en el pre-viaje por un defecto bloqueante (CAM-86) no
tiene post-viaje y no cuenta.

En el viaje que se cierra, el km del pre-viaje solo se usa si es el que tiene el vehículo antes del
post-viaje, es decir, el que dejó el pre-viaje. Si es otro, CAM-90 lo descartó por mal cargado (menor
al actual, o un salto de más de 5.000 km), y restarlo culparía al post-viaje de un error del
pre-viaje: un 400.000 tipeado daría "un viaje de -90.400 km". En ese caso no hay km del viaje
(`tripKm: null`) y no se compara con el promedio. También pasa si alguien usó "Cargar km" durante el
viaje.

### 2. El promedio del vehículo

- **De dónde sale:** de los últimos 10 viajes cerrados anteriores, por fecha de cierre, que tengan
  km en el pre-viaje y en el post-viaje.
- **Qué viajes no entran:**
  - los de 0 km o menos, porque el vehículo no se movió o hay un error;
  - los de más de `fleetguard.odometer.max-jump-km` (5.000 km). Es la misma regla con que el
    vehículo ignora una lectura (CAM-90) y CAM-137 descarta un salto.
- **Cuántos hacen falta:** 3 viajes que sirvan. Con menos no hay promedio y no se avisa por
  promedio.
- **Configuración:** las tres cifras están en `fleetguard.trip-km-alerts` (`min-trips`,
  `recent-trips`, `ratio`). El texto del aviso dice "el doble" y "la mitad", así que va con
  `ratio: 2`.

### 3. Cuándo se avisa

Se revisa en el orden siguiente, y gana el primer motivo que se cumple:

1. **`below_vehicle_km`:** el km del post-viaje es menor al que tenía el vehículo antes del
   post-viaje. CAM-90 ignora esa lectura sin avisar, y acá se avisa. Gana sobre el promedio, porque
   con un km así los km del viaje tampoco son confiables. Se avisa aunque no haya promedio o
   pre-viaje con km.
2. **`above_average`:** el viaje recorrió más del doble del promedio.
3. **`below_average`:** el viaje recorrió menos de la mitad del promedio.

Más detalles:

- **Justo el doble o justo la mitad no avisan.** Se compara contra el promedio ya redondeado, que es el
  mismo número que muestra el aviso.
- **Sin km del viaje** (sin pre-viaje con km, o con uno que CAM-90 descartó) solo se revisa el primer
  motivo.
- **Un viaje de 0 km avisa** como `below_average`, aunque los viajes de 0 km no cuentan para el
  promedio. Puede ser un post-viaje que copió el km del pre-viaje, que es un error de carga.
- **Un post-viaje con un salto enorme** (1.500.000 en vez de 150.000) avisa como `above_average` si hay
  promedio. Sin promedio no avisa: no hay contra qué compararlo. Que CAM-90 ignore esa lectura sin
  avisar es lo que muestra CAM-115.
- **El chofer del aviso** es el que cerró el viaje y cargó el km, que puede ser otro que el que lo abrió
  (CAM-87). Se nombra con su username, como en el aviso de defecto bloqueante (CAM-97).

### 4. La marca queda en el viaje

Las columnas nuevas de `trips` son `km_alert_reason`, `km_alert_trip_km`, `km_alert_average_km`,
`km_alert_declared_km` y `km_alert_vehicle_km`. Guardan los números del momento del cierre:

- el promedio no se recalcula después;
- los viajes cerrados antes de CAM-152 no se revisan para atrás.

El historial muestra la marca en el post-viaje, que es el que cerró el viaje y cargó el km.

### 5. El aviso

- **Cuándo y cuántos:** uno por viaje, después del commit (`TripKmAnomalyListener`, el mismo
  esquema que CAM-97). Lleva al detalle del vehículo (`/vehiculos/{id}`).
- **Si falla:** el post-viaje se guarda igual (`NotificationDispatcher`).
- **Qué dice:** el vehículo, el chofer, los km del viaje y el promedio. Si el motivo es
  `below_vehicle_km`, dice el km declarado y el que tenía el vehículo.

Ejemplos:

- "chofer1 cerró un viaje de 1.250 km con AB123CD, más del doble de su promedio de 400 km por viaje."
- "chofer1 cerró el viaje de AB123CD con 49.000 km, menos de los 50.000 km que ya tenía el vehículo."

### 6. Base de datos ya creada

`ddl-auto: update` agrega las columnas nuevas de `trips`, pero **no actualiza el `CHECK` de un enum
en una base que ya existe**. El problema ya se vio con `users.role` el 2026-09-16.

En la base local de Guido pasó: `notifications_type_check` rechaza `TRIP_KM_ANOMALY`. El viaje y su
marca se guardan igual, pero el aviso no, y queda en el log como un error de `NotificationDispatcher`
(SQLState 23514). En una base nueva no pasa. En una que ya existe, hay que correr esto una vez:

```sql
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notification_settings DROP CONSTRAINT IF EXISTS notification_settings_type_check;
```

Se borra y no se recrea con el valor nuevo: `ddl-auto: update` no lo vuelve a crear en una columna que
ya existe, así que el próximo tipo de aviso tampoco choca. El tipo lo sigue validando el enum de Java.
