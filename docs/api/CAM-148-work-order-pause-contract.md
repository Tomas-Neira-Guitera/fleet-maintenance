# CAM-148 — Pausar una orden de trabajo con un motivo

Una OT que se frena porque falta un repuesto ya no se ve igual que una en la que se está
trabajando. Pasa a `en_espera`, con un motivo, y se retoma cuando se puede seguir.

## Qué cambia en la API

| Dónde | Qué |
|---|---|
| `WorkOrderStatus` | Valor nuevo, `en_espera` |
| `PATCH /api/work-orders/{id}` | Transiciones nuevas. `pauseReason` y `pauseComment` en el body |
| `WorkOrder` (todas las respuestas) | `pause` (motivo, comentario, desde cuándo) y `pausedSeconds` |
| `GET /api/work-orders?status=en_espera` | El filtro existente acepta el estado nuevo |
| `GET /api/scheduled-maintenance` | `workOrder.status` puede ser `en_espera` |

Pausar:

```json
PATCH /api/work-orders/{id}
{ "status": "en_espera", "pauseReason": "esperando_repuesto", "pauseComment": "Pastillas pedidas a Bosch" }
```

Retomar:

```json
PATCH /api/work-orders/{id}
{ "status": "en_proceso" }
```

## Decisiones

### 1. Transiciones

| Desde | Hacia | |
|---|---|---|
| `en_proceso` | `en_espera` | Exige `pauseReason` |
| `en_espera` | `en_proceso` | Retomar: cierra la pausa |
| `en_espera` | `cancelada` | Cierra la pausa en el momento de cancelar |
| `en_espera` | `finalizada` | **409 `INVALID_STATUS_TRANSITION`**: primero se retoma |

Una OT asignada, finalizada o cancelada no se puede pausar (409). Las transiciones de antes no
cambian.

### 2. El motivo

`pauseReason` es obligatorio y puede ser `esperando_repuesto`, `esperando_proveedor`,
`esperando_vehiculo` u `otro` (422 `pauseReason` si falta o no es uno de esos). Con `otro`,
`pauseComment` también es obligatorio, porque si no, no dice nada (422 `pauseComment`). El comentario
se recorta y tiene un máximo de 500 caracteres.

### 3. Quién pausa

Cualquiera que pueda cambiar el estado de la OT, como hoy con "Iniciar" y "Finalizar": el técnico
desde su vista, y el admin desde el detalle. El admin también tiene que poder, porque una OT de un
taller externo no tiene técnico.

### 4. En espera es una OT abierta

`WorkOrderStatus.OPEN` (asignada, en proceso y en espera) es el único lugar que define qué es una OT
abierta. De ahí sale:

- se puede seguir editando y cargando gastos y fotos;
- impide abrir otra OT para el mismo origen;
- cuenta como pendiente en la actividad del técnico (`UserStatsService`);
- aparece como la OT de su programación en el calendario.

Al cancelar una programación, una OT en espera pide confirmación, igual que una en proceso, porque ya
se empezó a trabajar en ella.

El defecto sigue abierto mientras la OT está en espera, así que un vehículo con un defecto bloqueante
sigue "No disponible".

### 5. El tiempo en espera

- **El registro de cada pausa:** cada una queda en `work_order_pauses`, con motivo, comentario,
  inicio y fin. La OT lleva la pausa actual (`pause_reason`, `pause_comment`, `paused_at`) y los
  segundos de las pausas ya terminadas (`paused_seconds`, null es 0), para mostrarlos en el listado sin
  una consulta por OT.
- **Qué devuelve la API:** `pausedSeconds` suma las pausas terminadas más la actual hasta el momento de
  la respuesta.
- **Quién cierra la pausa:** la OT, venga de donde venga el cambio. Así se cierra también cuando el
  calendario cancela la OT.
- **La reparación promedio de CAM-134 no cambia:** sigue contando desde que se crea la OT hasta que se
  finaliza, con el tiempo en espera adentro. Descontarlo es otra decisión, que no se tomó en esta card.

### 6. Base de datos ya creada

`ddl-auto: update` crea `work_order_pauses` y las columnas nuevas de `work_orders`, pero **no
actualiza el `CHECK` de `work_orders.status`** en una base que ya existe. Es el mismo problema que
`users.role` (2026-09-16) y los tipos de aviso (CAM-152).

Sin arreglarlo, pausar responde 500. Hay que correr esto una vez en cada base:

```sql
ALTER TABLE work_orders DROP CONSTRAINT IF EXISTS work_orders_status_check;
```

Se borra y no se recrea con el valor nuevo: `ddl-auto` no lo vuelve a crear en una columna que ya
existe, y el estado lo sigue validando el enum de Java.
