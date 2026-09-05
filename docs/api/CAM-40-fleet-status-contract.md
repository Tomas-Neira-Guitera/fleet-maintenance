# CAM-40 — Contrato de API para mantenimiento preventivo y estado de flota

Acompaña a `claude/CAM-40-modelo-mantenimiento-preventivo.md` (modelo de datos) y al
diagrama de flujo publicado en esa conversación. Sigue el mismo estilo y convenciones que
`claude/CAM-11-dvir-api-contract.md`: servidor dueño de la lógica de negocio, formato de
error uniforme — con una diferencia deliberada respecto de CAM-11, ver división de
recursos más abajo.

## Endpoints (resumen)

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/vehicles?view=fleet-status` | Fila por vehículo para el componente de CAM-40 |
| GET | `/api/maintenance-plans` | Catálogo de planes de mantenimiento |
| POST | `/api/maintenance-plans` | Crear un plan nuevo en el catálogo |
| PATCH | `/api/maintenance-plans/{id}` | Editar nombre/intervalo/categoría de un plan |
| DELETE | `/api/maintenance-plans/{id}` | Borrar un plan del catálogo |
| GET | `/api/vehicles/{vehicleId}/maintenance-assignments` | Planes asignados a un vehículo, con estado calculado |
| POST | `/api/vehicles/{vehicleId}/maintenance-assignments` | Asignar un plan del catálogo a este vehículo |
| PATCH | `/api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}` | Activar/desactivar o corregir una asignación |
| DELETE | `/api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}` | Desasignar un plan de este vehículo |
| GET | `/api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}/completions` | Historial de veces que se hizo ese mantenimiento |
| POST | `/api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}/completions` | Registrar que el mantenimiento se hizo (dispara recálculo) |
| PATCH | `/api/vehicles/{id}/odometer` | Cargar el kilometraje actual del vehículo (feature 5.5) |

### División de recursos

Dos recursos, no uno por tabla:

- **`/api/maintenance-plans`** es el catálogo (tabla `maintenance_plans`). Es el único
  que amerita ser un recurso propio: no pertenece a ningún vehículo, se administra desde
  una pantalla de configuración aparte, y tiene sentido consultarlo o editarlo sin
  contexto de ningún vehículo puntual.
- **Todo lo que opera sobre la relación vehículo↔plan vive bajo `/api/vehicles/{vehicleId}/...`**,
  incluyendo el historial de completions. A diferencia de la primera versión de este
  contrato, acá **no** hay un recurso `vehicle-maintenance-assignments` a nivel raíz: una
  asignación no tiene identidad ni utilidad fuera del vehículo al que pertenece (nadie
  necesita pedir "la asignación X" sin saber de qué vehículo es), así que se modela como
  parte del agregado `vehicle`, no como una tabla más con su propio controller. Esto es
  intencionalmente distinto del criterio de CAM-11 con `/api/inspections` — ahí sí
  interesa poder crecer ese recurso de forma independiente (historial global, detalle por
  id) porque una inspección es un documento con entidad propia; una asignación de plan es,
  en cambio, un dato de configuración *del vehículo*.
- **`GET /api/vehicles?view=fleet-status`** tampoco es un recurso nuevo — es una vista
  agregada sobre `/api/vehicles`, reservado en CAM-11 para el resto del CRUD y
  operaciones de flota. Se usa un query param en vez de un endpoint paralelo para no
  duplicar la entidad vehículo en dos lugares del contrato.

Internamente, la tabla que registra la asignación puede seguir llamándose
`vehicle_maintenance_assignments` (o el nombre que el equipo de backend prefiera) — el
punto de esta sección es que **no se expone como su propio recurso de API**, se accede
siempre a través del vehículo.

## Decisiones clave

### 1. El servidor calcula todo lo derivado, el cliente nunca manda `next_due_*`, `status` ni `healthScore`
Igual que en CAM-11 (el servidor es dueño del checklist), acá el servidor es dueño del
cálculo de vencimientos y estado. El cliente manda hechos (`lastDoneKm`, `completedAt`,
kilometraje cargado); el servidor calcula y devuelve `nextDueKm`, `nextDueDate`, `status`
y `healthScore`. Esto evita que el frontend implemente la lógica de umbrales dos veces
(una en el back para alertas por email, feature 5.6, y otra en el front) y que se
desincronicen.

### 2. `completedAt` sí lo manda el cliente, a diferencia de las inspecciones DVIR
En CAM-11 el timestamp de la inspección nunca se confía del body, porque lo carga el
chofer en el momento. Acá es distinto: quien registra un completion (mantenimiento o
jefe de taller) muchas veces está cargando algo que **ya pasó** — cerrar una orden de
trabajo de la semana pasada, o cargar el historial previo a usar el sistema. Por eso
`completedAt` es un campo del body, no el reloj del servidor — pero el servidor valida
que no sea una fecha futura (`422 FUTURE_DATE`) y que no sea anterior al completion previo
de la misma asignación (`422 OUT_OF_ORDER_COMPLETION` — no tiene sentido registrar un
cambio de aceite "antes" del último ya registrado).

### 3. Borrar un plan y desasignarlo son soft-delete, nunca se pierde historial
- `DELETE /api/maintenance-plans/{id}` solo funciona si el plan **no tiene ninguna
  asignación** (activa o inactiva, en ningún vehículo) — si tiene, devuelve
  `409 PLAN_IN_USE` y hay que desactivarlo (`PATCH { active: false }`) en su lugar. Esto
  evita huérfanos: una asignación o un completion no pueden quedar apuntando a un plan
  que ya no existe.
- `DELETE /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}` no borra la
  fila: pone `active: false`. Los `completions` ya registrados quedan intactos para el
  historial del vehículo (feature 5.8), y si mañana se reasigna el mismo plan al mismo
  vehículo, no se pierde el último dato de cuándo se hizo.

### 4. No se permite asignar el mismo plan activo dos veces al mismo vehículo
`POST /api/vehicles/{vehicleId}/maintenance-assignments` con un `maintenancePlanId` que
ya tiene una asignación **activa** en ese vehículo devuelve
`409 DUPLICATE_ACTIVE_ASSIGNMENT`. Si existe una asignación inactiva del mismo plan (se
había desasignado antes), la reasigna en vez de crear una fila nueva — así no se duplica
el historial de completions.

### 5. Reglas de validación por tipo de intervalo (422)
Un plan (`intervalType: 'km' | 'time' | 'both'`) exige campos coherentes en cascada,
desde el catálogo hasta el completion:

| `intervalType` | En el plan del catálogo | Al asignar (`lastDone*` inicial) | Al registrar completion |
|---|---|---|---|
| `km` | `intervalKm` obligatorio, `intervalDays` debe venir null | `lastDoneKm` obligatorio | `completedKm` obligatorio |
| `time` | `intervalDays` obligatorio, `intervalKm` debe venir null | `lastDoneDate` obligatorio | `completedAt` obligatorio (siempre lo es, ver más abajo) |
| `both` | ambos obligatorios | ambos obligatorios | `completedKm` obligatorio (además de `completedAt`, que siempre va) |

`completedAt` viaja siempre en todo completion (es el evento en sí), independientemente
del `intervalType` — lo condicional es `completedKm`.

### 6. Formato de error uniforme
Mismo formato que CAM-11: `{ "error": "<CÓDIGO>", "message": "<texto>" }`, y los `422` de
validación suman `"details": [{ "field", "message" }]`.

## Endpoints en detalle

### `GET /api/vehicles?view=fleet-status`

Query params: `page` (default 1), `pageSize` (default 20), `status` (filtro opcional:
`al_dia` | `por_vencer` | `vencido`), `sort` (default `status_severity_desc`).

**200 Response**
```json
{
  "page": 1,
  "pageSize": 20,
  "total": 47,
  "items": [
    {
      "vehicleId": "veh_101",
      "plate": "AD 789 GH",
      "brand": "Iveco",
      "model": "Daily",
      "vehicleType": "furgon",
      "odometerKm": 156900,
      "healthScore": 28,
      "status": "vencido",
      "nextMaintenance": {
        "assignmentId": "asg_501",
        "name": "VTV",
        "status": "vencido",
        "dueDate": "2026-08-15",
        "dueKm": null,
        "remainingDays": -5,
        "remainingKm": null
      }
    }
  ]
}
```
`nextMaintenance` es `null` si el vehículo no tiene ningún plan activo asignado (caso a
mostrar distinto en el componente — "sin mantenimiento configurado", no confundir con
"al día").

### `GET /api/maintenance-plans`

Query params: `active` (default `true`), `category`.

**200 Response**
```json
{
  "items": [
    {
      "id": "mp_1",
      "name": "Cambio de aceite",
      "category": "motor",
      "intervalType": "km",
      "intervalKm": 10000,
      "intervalDays": null,
      "active": true
    },
    {
      "id": "mp_2",
      "name": "VTV",
      "category": "documentacion",
      "intervalType": "time",
      "intervalKm": null,
      "intervalDays": 365,
      "active": true
    }
  ]
}
```

### `POST /api/maintenance-plans`

**Request**
```json
{
  "name": "Correa de distribución",
  "category": "motor",
  "intervalType": "both",
  "intervalKm": 60000,
  "intervalDays": 1460
}
```
**201 Response** → el mismo objeto con `id` y `active: true`.

**422** si `intervalType` no es coherente con los campos presentes (ver tabla de la
decisión 5), o si `name` está vacío, o si `intervalKm`/`intervalDays` son ≤ 0.

### `PATCH /api/maintenance-plans/{id}`

**Request** (campos parciales)
```json
{ "intervalKm": 12000 }
```
**200 Response** → el plan actualizado. Nota: cambiar el intervalo de un plan del
catálogo **no** recalcula automáticamente `nextDue*` de las asignaciones existentes en
este alcance — eso queda para cuando exista un job de recálculo masivo (fuera de alcance,
sección de abajo); por ahora el nuevo intervalo aplica desde el próximo completion
registrado en cada asignación.

**404** si el plan no existe. **409 `PLAN_INACTIVE`** si se intenta editar un plan con
`active: false` (hay que reactivarlo primero con un PATCH explícito `{ "active": true }`).

### `DELETE /api/maintenance-plans/{id}`

**204** si no tiene ninguna asignación (activa ni inactiva) en ningún vehículo.
**409 `PLAN_IN_USE`** si tiene asignaciones — mensaje sugiere desactivar en su lugar.

### `GET /api/vehicles/{vehicleId}/maintenance-assignments`

Lista los planes asignados a este vehículo puntual, con su estado ya calculado. Es lo que
alimenta la ficha de detalle del vehículo (fuera de alcance de CAM-40 en sí, pero
comparte este mismo endpoint).

Query params: `active` (default `true`).

**200 Response**
```json
{
  "items": [
    {
      "id": "asg_501",
      "vehicleId": "veh_101",
      "maintenancePlanId": "mp_2",
      "planName": "VTV",
      "intervalType": "time",
      "lastDoneKm": null,
      "lastDoneDate": "2025-08-15",
      "nextDueKm": null,
      "nextDueDate": "2026-08-15",
      "status": "vencido",
      "active": true
    }
  ]
}
```

### `POST /api/vehicles/{vehicleId}/maintenance-assignments`

Asigna un plan del catálogo a este vehículo.

**Request**
```json
{
  "maintenancePlanId": "mp_1",
  "lastDoneKm": 72000,
  "lastDoneDate": null
}
```
Si el vehículo es nuevo y nunca se le hizo ese mantenimiento, se puede omitir
`lastDoneKm`/`lastDoneDate`: el servidor los siembra con el kilometraje/fecha actual del
vehículo al momento de la asignación (asume "recién hecho" como punto de partida
conservador).

**201 Response** → la asignación creada, con `nextDueKm`/`nextDueDate`/`status` ya
calculados (mismo shape que un item de la lista de arriba).

**404** `VEHICLE_NOT_FOUND` o `MAINTENANCE_PLAN_NOT_FOUND`.
**409** `DUPLICATE_ACTIVE_ASSIGNMENT` (ver decisión 4).
**422** si faltan los campos que exige el `intervalType` del plan (ver tabla decisión 5),
o si `maintenancePlanId` referencia un plan con `active: false`.

### `PATCH /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}`

Dos usos: desactivar/reactivar (`{ "active": false }`), o corregir un dato mal cargado
sin que cuente como un completion nuevo (`{ "lastDoneKm": 71500 }` — por ejemplo, un
error de tipeo). Esta segunda vía **no** inserta una fila en `completions`; es
explícitamente una corrección administrativa, distinta de "se hizo el mantenimiento".

**200 Response** → la asignación actualizada con `nextDue*`/`status` recalculados.
**404** `ASSIGNMENT_NOT_FOUND` (incluye el caso de que exista pero pertenezca a otro
`vehicleId`).

### `DELETE /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}`

**204**. Soft-delete (`active: false`), ver decisión 3. Los `completions` no se tocan.

### `GET /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}/completions`

**200 Response**
```json
{
  "items": [
    {
      "id": "mc_9001",
      "completedAt": "2026-06-02",
      "completedKm": 72000,
      "workOrderId": null,
      "notes": "Cambio de aceite y filtro, taller Norte"
    },
    {
      "id": "mc_8888",
      "completedAt": "2025-11-20",
      "completedKm": 62100,
      "workOrderId": "wo_442",
      "notes": null
    }
  ]
}
```
Orden: más reciente primero. Sin paginar en esta primera versión (el volumen por
asignación es bajo — a lo sumo un puñado de completions por año).

### `POST /api/vehicles/{vehicleId}/maintenance-assignments/{assignmentId}/completions`

**Request**
```json
{
  "completedAt": "2026-09-01",
  "completedKm": 84320,
  "workOrderId": null,
  "notes": "Cambio de aceite programado"
}
```
Al crearse, el servidor recalcula y actualiza `lastDone*`/`nextDue*` de la asignación
usando **este** completion (siempre que sea el más reciente por fecha — ver decisión 2
sobre `OUT_OF_ORDER_COMPLETION`).

**201 Response**
```json
{
  "id": "mc_9002",
  "assignmentId": "asg_501",
  "completedAt": "2026-09-01",
  "completedKm": 84320,
  "workOrderId": null,
  "notes": "Cambio de aceite programado",
  "updatedAssignment": {
    "id": "asg_501",
    "nextDueKm": 94320,
    "nextDueDate": null,
    "status": "al_dia"
  }
}
```
Devolver `updatedAssignment` inline evita que el frontend tenga que pedir la asignación
de nuevo solo para refrescar el chip de estado tras registrar el completion.

**404** `ASSIGNMENT_NOT_FOUND`.
**409** `ASSIGNMENT_INACTIVE` (no se puede registrar un completion sobre una asignación
desactivada — hay que reactivarla primero).
**422** `FUTURE_DATE`, `OUT_OF_ORDER_COMPLETION`, o falta `completedKm` cuando el
`intervalType` del plan lo exige (ver decisión 5).

### `PATCH /api/vehicles/{id}/odometer`

Comparte dueño con la feature 5.5 (carga semanal de kilometraje); se incluye acá porque
es uno de los tres disparadores del recálculo de estado (ver diagrama, figura 1).

**Request**
```json
{ "odometerKm": 84950 }
```
**200 Response** → `{ "vehicleId": "veh_1", "odometerKm": 84950, "updatedAt": "2026-09-03T14:00:00Z" }`

Este endpoint **no** toca `nextDueKm` de ningún plan (eso solo cambia con un completion)
— solo actualiza la referencia contra la que se compara en el momento de calcular
`status`.

**422** `ODOMETER_REGRESSION` si `odometerKm` es menor al valor actual guardado (no tiene
sentido que el kilometraje baje).

## Fuera de alcance de este contrato

- Recalcular en bloque `nextDue*` de asignaciones existentes cuando se edita el intervalo
  de un plan del catálogo (decisión 5, `PATCH /api/maintenance-plans/{id}`) — por ahora
  el cambio aplica desde el próximo completion.
- Asignación masiva de un plan a todos los vehículos de un tipo (alternativa de modelado
  descartada por ahora, ver sección 8 de `CAM-40-modelo-mantenimiento-preventivo.md`).
- Disparar automáticamente un completion al cerrar una orden de trabajo (feature 5.3,
  historia aparte) — el campo `workOrderId` en el completion ya deja el enganche listo
  para cuando esa historia exista, pero hoy el completion se crea siempre a mano vía este
  endpoint.
- Alertas por email de vencimiento (feature 5.6) — consume el mismo `status` calculado
  acá, pero el disparo y la configuración de a quién avisar son de otra historia.
