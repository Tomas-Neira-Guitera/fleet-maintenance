# CAM-85, CAM-86, CAM-87 y CAM-89 — Chofer real, quién tiene cada vehículo y bloqueo en el servidor

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo. Las cuatro cards tocan el mismo caso de uso
(`POST /api/inspections/{vehicleId}`) y se hicieron juntas.

## Motivación

- **CAM-85**: todas las inspecciones salían a nombre de un chofer fijo ("Carlos Gómez",
  headers `X-Driver-Id` / `X-Driver-Name`), sin importar quién se había logueado.
- **CAM-87**: el viaje (`Trip`) no guardaba chofer; para saber quién tenía un vehículo
  había que buscar el pre-viaje que lo abrió.
- **CAM-89**: "No disponible" lo calculaba solo el frontend. Un pedido directo a la API
  abría un viaje sobre un vehículo bloqueado o dado de baja.
- **CAM-86** (bug): un pre-viaje que reportaba un defecto bloqueante dejaba el viaje
  abierto sobre un vehículo que nadie podía elegir, así que no se cerraba nunca.

## Decisiones

### 1. El chofer es el usuario del JWT (CAM-85)
`POST /api/inspections/{vehicleId}` exige el JWT de un usuario activo con rol CHOFER
(401 `UNAUTHORIZED` sin token válido, 403 `FORBIDDEN` con otro rol). Reusa
`JwtAuthInterceptor`, que ahora recibe el rol por ruta (ver `CAM-23-users-contract.md`).

La inspección guarda `driver_id` = id del usuario y `driver_name` = su username.
`HeaderDriverResolver` y los headers `X-Driver-*` se eliminaron; si un cliente los
manda, se ignoran.

El token pasó a durar **12 horas** (`fleetguard.jwt.expiration-minutes: 720`): con 60
minutos, el chofer tenía que volver a iniciar sesión para hacer el post-viaje.

### 2. El nombre se resuelve al mostrarlo, no se copia
Donde se muestra un chofer (quién reportó un defecto, historial del vehículo, defecto
de una OT, viaje en curso) se usa el **nombre actual** del usuario, buscado por id
(`UserNameService`). El nombre guardado en la inspección o en el viaje es solo respaldo:
vale para lo anterior a CAM-85, donde `driver_id` no era el id de un usuario. Así,
renombrar a un usuario (CAM-91) actualiza todo sin tocar datos viejos.

### 3. El viaje registra quién lo abrió y quién lo cerró (CAM-87)
`trips` suma cuatro columnas opcionales: `opened_by_id`, `opened_by_name`,
`closed_by_id`, `closed_by_name`. Los viajes anteriores quedan sin dato y se siguen
viendo sin errores.

- `Trip` (respuesta del POST de inspección) expone `openedBy` y `closedBy` como
  `{ id, name }`, o `null` si no hay dato.
- `GET /api/vehicles` y `GET /api/vehicles/{id}` suman `currentTrip`
  (`{ startedAt, openedBy }`) cuando el vehículo está en viaje; `null` si está disponible.
- Hoy el post-viaje lo puede hacer otro chofer: el viaje queda abierto por uno y cerrado
  por otro. Impedirlo es CAM-33; el cierre forzado por el admin, CAM-55.

### 4. El servidor rechaza el pre-viaje de un vehículo que no puede salir (CAM-89)
En este orden, antes de validar las respuestas:

| Situación | Respuesta |
|---|---|
| Vehículo dado de baja | 409 `VEHICLE_INACTIVE` |
| Ya tiene un viaje abierto | 409 `VEHICLE_ON_TRIP` |
| Tiene un defecto bloqueante sin resolver | 409 `VEHICLE_BLOCKED` |

El **post-viaje no se rechaza** por bloqueo: un viaje abierto se tiene que poder cerrar.

"Bloqueado" es tener al menos un defecto `blocking` con `status = open`. Se destraba
cuando la OT de ese defecto se finaliza (el defecto pasa a `resuelto`).

Los vehículos suman el campo `blocked`. El listado del chofer lo usa y dejó de pedir
`GET /api/defects`, que era la única razón para darle a un chofer lectura sobre todos
los defectos de la flota.

### 5. Un pre-viaje con defecto bloqueante no deja el vehículo en viaje (CAM-86)
La inspección y los defectos se guardan, pero el vehículo no sale. Como
`inspections.trip_id` es obligatorio (y `ddl-auto: update` no relaja esa restricción),
el viaje se crea **ya cerrado**: `status = closed`, `endedAt` igual a `startedAt`, y el
mismo chofer en `openedBy` y `closedBy`. No hay estados nuevos.

- La respuesta del POST trae ese `trip` cerrado y `inspection.hasBlockingDefect = true`.
- El vehículo queda `available` y `blocked`: se puede dar de baja, y lo próximo que
  acepta, una vez resuelto el defecto, es un pre-viaje.
- Esos viajes de duración cero no cuentan como viajes en la actividad por chofer
  (`GET /api/users/stats`).
- Un post-viaje con defecto bloqueante cierra el viaje igual que siempre.

Para los viajes que ya habían quedado trabados antes del arreglo: el listado del chofer
deja elegir un vehículo en viaje aunque esté bloqueado, para hacerle el post-viaje.

## Datos demo
`docs/db/seed-demo.sql` se regeneró: las inspecciones guardan el id del usuario chofer y
los viajes, quién los abrió y cerró. Hay que volver a correrlo para ver choferes en el
detalle de los vehículos y datos en los gráficos de Usuarios.

## Fuera de alcance
Que solo el chofer que abrió el viaje lo pueda cerrar y "En uso por X" (CAM-33), cierre
forzado por el admin (CAM-55), proteger el resto de la API (CAM-73).
