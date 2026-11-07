# CAM-155 — Indicadores de cumplimiento del mantenimiento

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Endpoint

`GET /api/maintenance-compliance?from&to`, solo ADMIN. Lo muestra el Resumen del
admin, con el mismo filtro de período que Gastos.

## Decisiones

### 1. "A tiempo" se reconstruye con el historial
El historial (`maintenance_completions`) guarda cuándo y a qué km se hizo cada
mantenimiento, pero no cuándo vencía en ese momento. Se reconstruye: el vencimiento
de un mantenimiento es el anterior del mismo plan en el mismo vehículo más el
intervalo del plan.

- Es a tiempo si se hizo hasta la fecha de vencimiento y hasta el km de
  vencimiento. En un plan por km y tiempo tienen que cumplirse los dos.
- Si falta el km de alguno de los dos, decide solo la fecha. Si no hay con qué
  comparar (un plan por km sin km cargados), no cuenta.
- **El primero de cada plan no cuenta**: no tiene anterior. Se eligió así para que
  funcione con los datos que ya hay, en lugar de guardar el vencimiento desde
  ahora (que arrancaría vacío).
- Se usa el intervalo actual del plan: si se cambió, los mantenimientos viejos se
  miden con el nuevo.

### 2. Tiempo de resolución de defectos
Promedio de horas entre que se reportó y que se resolvió, de los defectos resueltos
dentro del período (`resolvedAt`, CAM-134). Los resueltos antes de CAM-134 no tienen
fecha de resolución y no entran. El período se corta en el momento actual.

### 3. OTs abiertas por antigüedad
Las asignadas o en proceso hoy, separadas por días desde que se crearon: menos de 7,
de 7 a 30 y más de 30. Es la foto de hoy, sin el filtro de período.
