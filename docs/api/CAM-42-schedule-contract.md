# CAM-42 / CAM-50 / CAM-51 — Contrato de API para programación de mantenimientos

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no puede
expresar bien por sí solo. Doc espejo en el Project de Claude:
`claude/CAM-42-programacion-mantenimientos.md`.

## Motivación

Estas tres historias comparten un concepto que no existía en el modelo de CAM-40: la
fecha en que el jefe de mantenimiento **planea** hacer un mantenimiento o resolver un
defecto -- distinta de cuándo *corresponde* (`nextDueDate`/`nextDueKm` en
`VehicleMaintenanceAssignment`, calculado) y de cuándo *ya se hizo*
(`MaintenanceCompletion`, historial).

- **CAM-42** -- calendario semanal en el Resumen, alimentado por `GET /api/maintenance-schedule`.
- **CAM-50** -- modal de "Estado de la flota" con botón "planificar" por asignación → `POST /api/maintenance-schedule` con `sourceType: assignment`.
- **CAM-51** -- botón "planificar" en "Defectos abiertos recientes" → `Ver todos` → mismo endpoint con `sourceType: defect`.

## Decisiones clave

### 1. Una tabla nueva, no un campo en cada origen
`scheduled_maintenances` es una entidad separada de `vehicle_maintenance_assignments` y
de `defects`, porque el calendario semanal necesita listar ambos orígenes de forma
unificada sin tener que hacer un UNION de dos tablas con forma distinta en cada request.

### 2. Un único endpoint genérico de creación
`POST /api/maintenance-schedule` recibe `{ sourceType, sourceId, scheduledAt }`. El
servidor resuelve `vehicleId` y `title` a partir de `sourceType`+`sourceId` (nunca se
confían del cliente, mismo criterio server-owned de CAM-11/CAM-40). El cliente no
necesita conocer la forma interna de `assignment` vs `defect`.

### 2.1. `sourceType: manual` -- programación suelta desde el calendario
El calendario semanal (CAM-42) permite agendar un mantenimiento al pasar el cursor
sobre un día, sin que nazca de una asignación ni de un defecto. Se resuelve con un
tercer valor de `sourceType`, no con una tabla ni un endpoint nuevo:
- El cliente manda `{ sourceType: "manual", vehicleId, title, scheduledAt, notes? }`
  directo -- no hay `sourceId` porque no hay plan ni defecto del que resolver
  `vehicleId`/`title`.
- `assignmentId` y `defectId` quedan `null` en la fila creada.
- **No aplica el upsert de la decisión 3**: cada `POST` manual crea una fila nueva
  (`201` siempre), porque no existe una clave de origen única de la que dedupear --
  a diferencia de assignment/defect, donde `sourceId` identifica una única
  programación activa posible.
- Se cierra igual que un `defect`: a mano vía `PATCH` con `{ status: done }` (no hay
  evento de "completion" al que engancharse, como sí lo hay para `assignment`).

### 3. Upsert sobre una sola fila activa por origen
Si ya existe una fila `status=scheduled` para ese `sourceId`, el `POST` actualiza
`scheduledAt` en esa fila (devuelve `200`) en vez de crear una nueva (`201`). No se
guarda historial de reprogramaciones -- se pisa, mismo criterio que
`lastDone*`/`nextDue*` en `VehicleMaintenanceAssignment`. El historial real de cuándo se
hizo un mantenimiento sigue viviendo, sin cambios, en `maintenance_completions`.

### 4. Cierre automático solo quiere decir enganchar el evento que ya existe
- `sourceType=assignment`: al registrar un completion en esa asignación
  (`POST /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}/completions`,
  ya existente en CAM-40), el servidor marca `done` la programación activa de esa
  asignación en la misma transacción -- el cliente no llama un segundo endpoint. Por
  eso `PATCH /api/maintenance-schedule/{id}` con `status: done` sobre una programación
  de origen `assignment` devuelve `409 USE_COMPLETION_ENDPOINT`: ese cierre pasa siempre
  por el completion, nunca a mano.
- `sourceType=defect`: no existe todavía un endpoint de "cerrar" un defecto (depende de
  órdenes de trabajo, feature 5.3, fuera de alcance). Se marca `done` a mano vía
  `PATCH /api/maintenance-schedule/{id}` con `{ status: done }`. Cuando exista 5.3, se
  puede enganchar el cierre automático ahí también sin romper este contrato.

### 5. Fecha y hora, no solo fecha
`scheduledAt` es un timestamp ISO-8601 completo, a diferencia de `nextDueDate`/
`completedAt` en CAM-40 (que son solo fecha). El endpoint de listado (`GET`) acepta
también fechas simples (`YYYY-MM-DD`, tomadas como inicio del día UTC) en `from`/`to`
para que armar el rango de una semana desde el frontend sea directo.

### 6. Validaciones (422) y conflictos (409)
- `422 PAST_DATE` si `scheduledAt` es anterior a ahora.
- `404 ASSIGNMENT_NOT_FOUND` / `DEFECT_NOT_FOUND` si `sourceId` no existe.
- `422 vehicleId`/`title` faltantes si `sourceType: manual`.
- `409 ASSIGNMENT_INACTIVE` / `DEFECT_RESOLVED` -- no tiene sentido programar algo que ya
  está cerrado.
- `404 SCHEDULE_NOT_FOUND` en el `PATCH` si el id no existe.
- `409 USE_COMPLETION_ENDPOINT` -- ver decisión 4.

### 7. Preview de "qué más hay programado ese día" en el modal de Planificar
El modal de "Planificar" (`SchedulePickerModal`, CAM-50/51/manual) muestra, apenas se
elige fecha y hora, todo lo que ya está programado **para ese mismo día** -- sin filtrar
por vehículo. La lógica de negocio detrás es capacidad de taller (un mecánico/box a la
vez), no un choque específico de un vehículo consigo mismo: lo que le interesa al jefe de
mantenimiento es si ese día ya está ocupado con otra cosa, sea del vehículo que sea. Se
resuelve con el mismo `GET /api/maintenance-schedule?from=&to=` ya existente (rango de un
día), sin necesidad de un endpoint nuevo. Es solo un preview informativo -- el backend no
bloquea ni valida colisiones de horario, la decisión de si se pisa o no queda en manos
del jefe de mantenimiento.

`GET /api/maintenance-schedule` acepta además un `vehicleId` opcional (filtro de uso
general, pensado por ejemplo para una futura vista de calendario de un solo vehículo) --
no lo usa este preview.

## Fuera de alcance

- Historial de reprogramaciones (ver decisión 3).
- Cierre automático de una programación de origen `defect` (depende de 5.3, fuera de
  alcance -- ver decisión 4).
- Notificaciones/alertas sobre mantenimientos programados (distinto de las alertas de
  vencimiento de 5.6, ya fuera de alcance de CAM-40).
- Recurrencia -- cada programación es un evento puntual.
