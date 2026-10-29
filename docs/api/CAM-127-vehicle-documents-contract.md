# CAM-127 / CAM-102 / CAM-103 — Documentación del vehículo y avisos de disponibilidad

Acompaña a `openapi.yaml` (1.14). Cubre la documentación de cada vehículo con sus
vencimientos (CAM-127), qué deja a un vehículo "No disponible", y los avisos al chofer
cuando su vehículo deja de estar disponible o vuelve a estarlo (CAM-102, CAM-103).

## Endpoints

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/vehicles/{id}/documents` | Vigentes, historial y obligatorios que faltan |
| POST | `/api/vehicles/{id}/documents` | Cargar un documento |
| POST | `/api/vehicles/{id}/documents/{documentId}/renewals` | Renovar el vigente |
| PATCH | `/api/vehicles/{id}/documents/{documentId}` | Corregir un dato del vigente |
| DELETE | `/api/vehicles/{id}/documents/{documentId}` | Baja lógica |
| POST | `/api/photos?purpose=document` | Subir el comprobante (JPG, PNG o PDF) |

Todos los de `/documents` exigen el JWT de un ADMIN (`WebConfig`). El comprobante se sube
como las fotos de defectos (dos pasos), y su URL va en `fileUrl`. `GET /api/photos/{id}`
lo sirve con su tipo (también `application/pdf`); igual que el resto de las fotos, sin
autenticación todavía (CAM-73).

## Decisiones

### 1. Nombre libre; el tipo sale del nombre
El admin carga cada documento con el nombre que quiera (hasta 60 caracteres): "VTV", "Póliza
La Segunda", "Permiso municipal". El backend reconoce el tipo por palabras clave del nombre
(`VehicleDocumentType.infer`, sin tildes ni mayúsculas): `vtv` (vtv, rto, revisión técnica),
`seguro` (seguro, póliza), `cedula_azul`, `cedula_verde`, `habilitacion_carga`, `titulo`; lo que
no reconoce es `otro`. El tipo es lo que usan los obligatorios y el vínculo con los planes.
`type` en el alta es opcional y lo fuerza. Corregir el nombre vuelve a reconocer el tipo. El
número también va hasta 60.

### 2. Obligatorios que no bloquean
VTV y seguro son obligatorios: si falta alguno vigente, `missingRequired` lo dice y la
pantalla lo marca. **No bloquea**: si bloqueara, el día del deploy toda la flota quedaría
"No disponible" por no tener nada cargado.

### 3. Renovar es una fila nueva
Renovar crea un documento nuevo y deja el anterior en el historial (`replacedById`). El
vigente de cada tipo es el activo sin reemplazo. Cargar un tipo reconocido que ya tiene
vigente, o un `otro` con el mismo nombre, es `409 DOCUMENT_ALREADY_LOADED` (se renueva o se
corrige). Corregir
(`PATCH`) solo toca el vigente (`409 DOCUMENT_REPLACED` sobre uno del historial) y no
cuenta como renovación. Borrar es baja lógica.

### 4. Vencido = desde el día siguiente
El documento vale hasta el día de `expiresAt` inclusive. Desde el día siguiente está
`vencido` y el vehículo queda "No disponible". `por_vencer` si faltan hasta la
anticipación de "Documento por vencer" (15 días por defecto). Sin `expiresAt`:
`sin_vencimiento`, no bloquea ni avisa.

### 5. Un solo lugar decide si el vehículo puede salir
`VehicleAvailabilityService`: un defecto bloqueante abierto o un documento vigente
vencido. Lo usan el listado y el detalle de vehículos (`blocked` + `blockReasons`), la
tabla de flota y el pre-viaje (`409 VEHICLE_BLOCKED`, con los motivos en el mensaje:
"AB123CD no puede salir: VTV / RTO: venció el 12/10/2026."). El post-viaje nunca se
rechaza por bloqueo (CAM-89).

### 6. Avisos al admin
Tipos `document_due_soon` y `document_expired` en el catálogo de CAM-117 (configurables,
por defecto a los admins por campanita y push). Tres avisos por documento, una vez cada
uno (`document_alert_log`, por fecha de vencimiento: si se corrige la fecha, vuelven a
empezar):

- al entrar en la anticipación: "VTV / RTO de AB123CD: vence el 08/11/2026 (faltan 10 días).";
- el día que vence: "…vence hoy (…). Desde mañana el vehículo queda no disponible…";
- al vencer: "…venció el … El vehículo queda no disponible hasta que se cargue la renovación."

Se revisan todos los días a las 08:05 de Argentina (`fleetguard.document-alerts.cron`) y,
para ese vehículo, después de cargar o corregir un documento. La anticipación se
configura en Notificaciones (solo días), no en `application.yml`.

### 7. Plan vinculado a un documento
Un plan por tiempo puede renovar un tipo de documento (`maintenance_plans.document_type`;
solo `vtv`, `seguro`, `cedula_azul` y `habilitacion_carga`). Un plan vinculado no puede
pasar a km.

- **El documento manda sobre la fecha**: si el vehículo tiene el documento vigente con
  vencimiento, `nextDueDate` del plan es ese vencimiento, no "última vez + intervalo"
  (`dueDateFromDocument: true` en la asignación). Sin documento rige el plan.
- **Registrar el mantenimiento pide el documento**: el completion y la finalización de
  una OT de ese plan aceptan `document` (`expiresAt` obligatorio, `number` y `fileUrl`
  opcionales). Se guarda como renovación, emitida el día del mantenimiento. En un plan sin
  vínculo, u OT sin plan, es `422` en `document`.
- **Cargar el documento registra el mantenimiento**: cargar o renovar el documento a mano
  registra el completion del plan vinculado con la fecha de emisión (o hoy), salvo que ya
  haya uno en esa fecha o después.
- **Sin avisos repetidos**: un plan que vence con su documento no manda los avisos de
  CAM-118; avisa el documento.

### 8. Avisos al chofer (CAM-102 / CAM-103)
`VehicleAvailabilityService.sync` guarda desde cuándo el vehículo está "No disponible"
(`vehicles.unavailable_since`) y publica `VehicleAvailabilityChangedEvent` solo cuando
cambia. Se llama al reportar un bloqueante, al finalizar la OT que resuelve un defecto, al
cambiar un documento y en la corrida diaria (los documentos vencen solos).

- **No disponible** (`vehicle_unavailable`): solo si lo bloqueó un documento vencido, al
  chofer que tiene el vehículo en viaje. Si lo bloqueó un defecto, no se avisa: lo reportó
  el propio chofer.
- **Disponible de nuevo** (`vehicle_available`): a quienes reportaron los bloqueantes que
  se resolvieron mientras estuvo bloqueado (`defects.resolved_at`) y a quienes se les
  avisó del bloqueo. Si lo liberó una OT, el mensaje la nombra.
- Un segundo bloqueante sobre un vehículo ya bloqueado no avisa de nuevo, y si le queda
  otro motivo, liberarse uno no avisa.
- El link es `/inspeccion/{vehicleId}`, la ruta del QR (CAM-133).

## Fuera de alcance

- Licencia de conducir del chofer (CAM-128).
- Que un obligatorio faltante bloquee.
- Recordatorios repetidos después de vencido.
- Proteger `/api/photos` y el resto de la API con JWT (CAM-73).
