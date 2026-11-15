# CAM-146 — Daños ocurridos durante el viaje

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Qué cambia

`GET /api/defects` y `GET /api/vehicles/{id}/history` suman a cada defecto el campo
`occurredOnTrip` (schema `OccurredOnTrip`): el viaje, sus fechas y el pre-viaje en
que ese control dio OK. Es null cuando no se puede decir que el defecto apareció en
el viaje.

## Decisiones

### 1. La regla
Un defecto del **post-viaje** ocurrió en el viaje si el mismo control dio OK en el
**pre-viaje del mismo viaje** (`Trip`). Qué controles son el mismo lo define
`ChecklistCatalog.controlKey` (CAM-144): Luces y Fugas.

- `post-danos` ("Daños nuevos en la carrocería") siempre se marca: su pregunta ya
  es si apareció algo en el viaje.
- Un control del post-viaje sin equivalente en el pre-viaje no se marca: no hay
  contra qué comparar.
- Un defecto del pre-viaje nunca se marca.

### 2. Se calcula, no se guarda
Sale de las respuestas ya guardadas, así que los defectos viejos se marcan igual.
`TripDamageService` resuelve todo el listado con una sola consulta (los pre-viajes
de los viajes involucrados, con sus respuestas).

### 3. Quién lo ve
Es información sensible sobre el chofer, así que el frontend la muestra solo en el
panel del admin. `GET /api/defects` todavía no exige token (CAM-73): cuando se
proteja, el campo queda protegido con él.

### 4. Fuera de alcance
Asignar responsabilidad o costos al chofer: el sistema muestra cuándo pasó, no de
quién fue la culpa.
