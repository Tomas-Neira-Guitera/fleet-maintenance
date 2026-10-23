# CAM-117 / CAM-118 — Configuración de avisos y avisos de vencimiento

Acompaña a `openapi.yaml`. Reemplaza el alcance de CAM-19 (email de vencimientos)
y CAM-24 (configuración de alertas).

## CAM-117 — Configuración por tipo de aviso

`GET /api/notification-settings` y `PUT|DELETE /api/notification-settings/{type}`,
solo ADMIN. Pantalla "Notificaciones" del menú del admin.

### 1. Catálogo fijo, no reglas libres
Los tipos son los que el sistema sabe detectar (`NotificationCatalog`). El admin
no crea avisos nuevos: activa o apaga cada uno, elige canales y, donde aplica,
destinatarios. Un tipo nuevo es código (su evento y su listener) más una entrada
en el catálogo.

### 2. Sin configuración guardada rige el default
Activo, campanita + push, a los admins (lo que hacía la base de CAM-96/CAM-119), y
anticipación de 7 días / 1000 km para vencimientos. Una fila en
`notification_settings` lo pisa; `DELETE` la borra y vuelve al default.

### 3. Dos clases de aviso
- **owner**: le llega a la persona involucrada (el técnico de la OT, el chofer del
  viaje). Se configura activo y canales; los destinatarios no se eligen.
- **configurable**: le llega a los roles elegidos más los usuarios elegidos
  (solo los activos).

### 4. Canales
`in_app` (campanita), `push` (CAM-119) y `email`. Activo pide al menos uno.

### 5. Email
Spring Mail con el SMTP de `spring.mail` (en local, Mailpit: SMTP en 1025 y la
bandeja en http://localhost:8025). Un email por destinatario, a quien tenga
`email` cargado en Usuarios (campo opcional nuevo); quien no lo tiene recibe los
otros canales. `fleetguard.mail.enabled: false` lo apaga. Un SMTP caído queda en
el log y no frena nada.

## CAM-118 — Avisos de vencimiento de planes

### 6. Dos avisos por ciclo
Uno al entrar en la anticipación (`maintenance_due_soon`) y otro al vencer
(`maintenance_overdue`). Nada más hasta el próximo ciclo. El ciclo es el próximo
vencimiento de la asignación (`nextDueDate` / `nextDueKm`): al registrar el
mantenimiento cambia y los avisos vuelven a empezar. Lo enviado se guarda en
`maintenance_alert_log` (único por asignación, etapa y ciclo).

### 7. Cuándo se revisa
- Por fecha: una vez por día (`fleetguard.maintenance-alerts.cron`, 08:00 de
  Argentina).
- Por km: cada vez que sube el km del vehículo (inspecciones, OTs finalizadas,
  mantenimientos hechos, "Cargar km"), con el evento `OdometerChangedEvent`.
  Desde CAM-90 el km cambia solo, así que esperar al chequeo diario llegaría tarde.

### 8. La anticipación no toca el dashboard
"Por vencer" del dashboard y el health score siguen con su umbral fijo (7 días /
1000 km, `MaintenanceStatusCalculator`). La anticipación del aviso es solo para
el aviso. "Vencido" es el mismo criterio en los dos lados.

### 9. Global con override por plan
`GET|PUT|DELETE /api/maintenance-plans/{id}/notifications`. Sin configuración
propia, el plan usa la general (anticipación de `maintenance_due_soon`, activo y
canales de cada tipo). Con configuración propia, el plan define activo,
anticipación y canales para sus dos avisos; los destinatarios son siempre los
generales. `leadDays` solo en planes por tiempo y `leadKm` solo en planes por km.

### 10. Vehículos dados de baja y planes inactivos no avisan.

## Fuera de alcance

Recordatorios repetidos (cada N días) y avisos después de vencido; resumen diario
por email (cada aviso es su propio email).
