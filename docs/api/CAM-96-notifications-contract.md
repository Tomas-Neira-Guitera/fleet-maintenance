# CAM-96 — Avisos in-app (campanita)

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Cada rol tiene algo que le importa enterarse sin estar mirando la pantalla
correcta (épica CAM-95): el admin, un defecto bloqueante o una OT finalizada; el
técnico, que le asignaron o le sacaron una OT; el chofer, que se olvidó de cerrar
el viaje. CAM-96 es la base que usan todos esos avisos: cómo se originan, cómo se
reparten, dónde se guardan, cómo los lee cada usuario y la campanita del header.

## Avisos que existen

| Tipo | Card | A quién | Link |
|---|---|---|---|
| `blocking_defect_reported` | CAM-97 | Configurable (por defecto, admins) | `/vehiculos/{id}` |
| `recurring_defect` | CAM-135 | Configurable (por defecto, admins) | `/vehiculos/{id}` |
| `work_order_finalized` | CAM-98 | Configurable (por defecto, admins) | `/ordenes-trabajo/{id}` |
| `maintenance_due_soon`, `maintenance_overdue` | CAM-118 | Configurable (por defecto, admins) | `/vehiculos/{id}` |
| `work_order_assigned`, `work_order_unassigned`, `work_order_cancelled` | CAM-99, CAM-100 | El técnico de la OT | `/` |
| `trip_open_reminder` | CAM-101 | El chofer que abrió el viaje | `/` |
| `document_due_soon`, `document_expired` | CAM-127 | Configurable (por defecto, admins) | `/vehiculos/{id}/documentacion` |
| `vehicle_unavailable` | CAM-102 | El chofer que tiene el vehículo en viaje (solo si lo bloqueó un documento vencido) | `/inspeccion/{vehicleId}` |
| `vehicle_available` | CAM-103 | Quienes reportaron los bloqueantes resueltos y a quienes se avisó del bloqueo | `/inspeccion/{vehicleId}` |

Cómo se configura cada uno (activo, canales, destinatarios): ver
`CAM-117-118-notification-settings-contract.md`.

## Piezas

- **Origen**: el servicio que hace el cambio publica un evento de dominio; un
  listener `@TransactionalEventListener` (AFTER_COMMIT) arma el texto y llama a
  `NotificationService`. Lo que nace del paso del tiempo (CAM-101, vencimientos
  por fecha de CAM-118) lo pide una tarea `@Scheduled`.
- **`NotificationService`**: lo único que conocen los avisos. `notify` (a la
  persona involucrada) y `notifyConfiguredRecipients` (a los destinatarios de la
  configuración).
- **`DefaultNotificationService`**: lee la configuración del tipo y reparte a
  cada canal (`NotificationSender`): `InboxNotificationSender` (la tabla
  `notifications`), `PushNotificationSender` (CAM-119) y
  `EmailNotificationSender`.
- **`NotificationDispatcher`**: cada envío en su propia transacción.
- **`NotificationController`** / **`NotificationInboxService`**: la campanita.

## Decisiones

### 1. Los avisos nacen de eventos de dominio
Así la lógica de negocio no sabe nada de avisos, y si la operación falla y se
deshace no queda un aviso de algo que no pasó.

### 2. Cada aviso va en su propia transacción
En AFTER_COMMIT la transacción del pedido ya se confirmó: un `save` sumado a ella
no se escribiría nunca. Por eso nadie llama a `NotificationService` directo: se
pasa por `NotificationDispatcher`, que corre cada envío con REQUIRES_NEW.

### 3. Un aviso que falla no rompe la operación
`NotificationDispatcher` atrapa cualquier error afuera de la transacción del
aviso, lo deja en el log y sigue. En una tarea que manda varios avisos, uno que
falla no se lleva puestos a los demás.

### 4. La push y el email salen recién con el commit del aviso
Y en segundo plano (`@Async`): si el aviso se deshace no se manda nada, y un
servicio externo lento no demora a nadie.

### 5. El texto lo arma el backend
`title` y `message` llegan listos para mostrar, iguales en los tres canales. Un
tipo nuevo no necesita cambios en el front.

### 6. `link` es una ruta del front
Para el admin, una ruta de su vista. Chofer y técnico viven en `/` (sus flujos no
están en la URL), así que sus avisos llevan `/`. El email lo convierte en link
absoluto con `fleetguard.frontend-url`.

### 7. Descartar no borra
La X del panel (`PATCH /{id}/dismiss`) marca `dismissedAt` y también `readAt`:
el aviso sale de la campanita y del contador, pero queda en la base. Tocar el
aviso lo marca leído y navega a su link.

### 8. El aviso de otro usuario es 404
Leer o descartar un aviso ajeno responde lo mismo que uno que no existe, para no
confirmar que el id existe.

### 9. Sin paginación ni borrado
`GET /api/notifications` trae los últimos `limit` sin descartar (30 por defecto,
100 como máximo). Los avisos viejos no se borran todavía.

### 10. Quien finaliza una OT también recibe su aviso
`/api/work-orders` todavía no exige JWT (CAM-73), así que el backend no sabe
quién finalizó. Hasta entonces, el aviso de CAM-98 le llega a todos los
destinatarios, incluido el admin que la finalizó.

## Fuera de alcance

Preferencias por usuario (cada uno elige sus canales), WebSockets (la campanita
refresca cada 60 segundos), WhatsApp/Telegram.
