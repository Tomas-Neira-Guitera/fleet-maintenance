# CAM-25 — ABM de vehículos, decisiones de contrato

`POST/PATCH/DELETE /api/vehicles[/{id}]` — alta, edición y baja de vehículos.
El resto del recurso (`GET`, `?view=fleet-status`, `/odometer`) ya existía
desde CAM-11/CAM-40; ver `openapi.yaml` para la forma completa.

## 1. Baja lógica, no DELETE físico

Un vehículo real casi siempre tiene inspecciones, viajes, defectos o
asignaciones de mantenimiento enganchados. Borrar la fila de verdad rompe
esas relaciones o exige un cascade que se lleva puesto historial real.
`DELETE /api/vehicles/{id}` responde `204` pero internamente solo pone
`active=false` — mismo patrón que ya usa
`DELETE /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}`.
No hay una segunda operación para "borrar de verdad".

## 2. Reactivar es un PATCH, no un endpoint aparte

`PATCH /api/vehicles/{id}` con `{ "active": true }` reactiva un vehículo
dado de baja — mismo patrón que `UpdateMaintenancePlanRequest.active` en el
catálogo de planes. No existe `POST /api/vehicles/{id}/reactivate` ni
similar.

## 3. Baja bloqueada si el vehículo está en viaje

Si tiene un viaje abierto (`Trip.status = OPEN`), la baja devuelve `409
VEHICLE_ON_TRIP` en vez de dar de baja igual. Mismo código de error que ya
documentaba `VehicleStateConflictException` desde CAM-11, no se inventa uno
nuevo.

## 4. Patente única

`POST`/`PATCH` chequean duplicado de `plate` (case-insensitive, excluyendo
al propio vehículo en el `PATCH`) antes de guardar → `409 DUPLICATE_PLATE`.
La columna además tiene un constraint `unique` en la base como segunda
barrera.

## 5. `active` filtra los listados existentes, default `true`

`GET /api/vehicles` y `GET /api/vehicles?view=fleet-status` ganan un
parámetro `active` (default `true`). Un vehículo dado de baja deja de
aparecer:

- para el chofer, al elegir vehículo para una inspección nueva
  (`VehicleList.tsx` no cambia de código, hereda el filtro por venir del
  default del parámetro);
- en "Estado de la flota" del dashboard de admin (CAM-40).

El admin ve los vehículos dados de baja pasando `active=false` explícito
desde la pantalla "Vehículos".

## 6. Campos obligatorios vs. opcionales en el alta

`plate`, `brand`, `model` obligatorios (ya lo eran antes de CAM-25).
`vehicleType`, `year`, `chassisNumber`, `odometerKm` opcionales — la card
(CAM-25) los pide como parte de "cargar la información del vehículo", pero
forzarlos hoy rompería los vehículos ya cargados sin esos datos. `year`, si
viene, se valida en el rango 1980–(año actual + 1); fuera de ese rango es
`422` (típico error de tipeo, no un caso real).

## 7. `odometerKm` en el alta, pero no en la edición

El alta puede sembrar un kilometraje inicial (`odometerKm` opcional en
`CreateVehicleRequest`, default 0 si se omite — la card lo pide
explícitamente: "...y su kilometraje inicial"). La corrección del
kilometraje después de creado sigue siendo exclusiva de
`PATCH /api/vehicles/{id}/odometer` (ya existe, con su propia validación
anti-regresión) — `UpdateVehicleRequest` no tiene campo `odometerKm` a
propósito, para no abrir un segundo camino que la sortee.

## Fuera de alcance

- La baja **no cascada** a las asignaciones de mantenimiento ni a la
  programación (`ScheduledMaintenance`) del vehículo — quedan como están,
  simplemente el vehículo deja de listarse por defecto.
- No hay historial de altas/bajas/ediciones (quién y cuándo).
