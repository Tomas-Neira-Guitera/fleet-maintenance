# CAM-153 — Inspección sin conexión

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Qué cambia en la API

`POST /api/inspections/{vehicleId}` acepta dos campos opcionales más en el body
(`InspectionSubmission`). Los clientes que no los mandan siguen igual.

| Campo | Para qué |
|---|---|
| `performedAt` | La hora en que el chofer hizo la inspección. |
| `clientSubmissionId` | UUID que arma el celular para no guardar dos veces la misma inspección. |

## Decisiones

### 1. La inspección queda con la hora real
Sin señal, la inspección se guarda en el celular y se manda cuando vuelve la
conexión, a veces horas después. El viaje que abre o cierra, la inspección y sus
defectos quedan con `performedAt`, no con la hora de llegada. Si no, un viaje hecho
sin señal aparecería con horarios corridos y el tiempo fuera de servicio (CAM-134)
o los daños del viaje (CAM-146) saldrían mal.

- Sin `performedAt`, la hora del servidor (como antes).
- Hasta 5 minutos en el futuro se toma como ahora (reloj del celular apenas
  adelantado). Más es un 422.
- Un post-viaje anterior al inicio de su viaje se lleva al inicio del viaje.

### 2. Un reintento no duplica
Si el celular mandó la inspección pero se cortó antes de recibir la respuesta, la
vuelve a mandar con el mismo `clientSubmissionId`. El servidor encuentra la ya
guardada (`inspections.client_submission_id`, único) y devuelve la misma respuesta,
sin volver a validar el estado del vehículo: ese estado ya lo cambió la propia
inspección. Si el id es de otro chofer u otro vehículo, 409 `DUPLICATE_SUBMISSION`.

### 3. El estado del vehículo se valida al llegar
Las reglas de siempre (viaje abierto, vehículo bloqueado o dado de baja) se revisan
cuando la inspección llega. Si mientras tanto otro chofer se llevó el vehículo, la
inspección se rechaza con el 409 de siempre y el celular se lo muestra al chofer.

### 4. Orden
El celular manda las pendientes en el orden en que se hicieron: un pre-viaje y el
post-viaje del mismo vehículo, hechos los dos sin señal, llegan en ese orden.

### 5. Las fotos
La foto de un defecto se sube igual que siempre (`POST /api/photos`) antes de
mandar la inspección. Sin señal, el celular la guarda y la sube al reconectarse.

### 6. Alcance del frontend
Cubre quedarse sin señal con la app ya abierta: la flota y el checklist de cada
vehículo se guardan la última vez que se cargaron. Abrir la app por primera vez sin
conexión queda afuera.
