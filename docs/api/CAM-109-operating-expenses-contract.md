# CAM-109 — Gastos operativos del vehículo

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Hasta acá un gasto pertenecía siempre a una orden de trabajo, así que solo se conocía
el costo de mantener cada vehículo. Combustible, seguro, patente, VTV y peajes no
salen de una reparación, y sin ellos no se sabe cuánto cuesta tenerlo.

## Endpoints

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/expenses` | Los dos tipos de gasto juntos. Suma el filtro `origin` |
| POST | `/api/expenses` | Carga un gasto operativo |
| PUT | `/api/expenses/{id}` | Edita un gasto operativo |
| DELETE | `/api/expenses/{id}` | Borra un gasto operativo |

Todos son solo para ADMIN.

## Decisiones

### 1. Entidad aparte, no una generalización de `WorkOrderExpense`
`VehicleExpense` es una tabla nueva, y `GET /api/expenses` une las dos fuentes. La
alternativa, una sola tabla con la OT opcional, obligaba a migrar `work_order_expenses`
a mano en cada base (con `ddl-auto` no hay migraciones) y a tocar las OTs, la actividad
de usuarios (CAM-91) y los costos. Así los gastos de OT quedan exactamente como estaban.

### 2. FK real al vehículo
A diferencia de `WorkOrder.vehicleId`, es un `@ManyToOne`. Además de la integridad, hace
que el `truncate vehicles cascade` de `seed-demo.sql` limpie también estos gastos: los
vehículos del seed tienen ids fijos, y sin la FK quedarían colgados de los nuevos.

### 3. Una fecha propia, sin hora
`VehicleExpense.date` es un día: es lo que dice una factura, y permite cargarla después.
No puede ser futura (hora argentina). `createdAt` guarda aparte cuándo se cargó.

En `GET /api/expenses` sale como la medianoche argentina de ese día, para que `date` sea
siempre un instante y el período `[from, to)` trate igual a los dos tipos.

### 4. Forma del listado
Un gasto operativo tiene `origin: operating`, y vienen en null `workOrderId`,
`workOrderTitle`, `workOrderStatus`, `executionType` y el comprobante. Es un cambio del
contrato de CAM-52, donde esos campos no podían ser null: **el frontend que recorra
gastos tiene que contemplarlo**.

`otro` es la misma categoría en los dos tipos: filtrar por `otro` trae los dos.

### 5. Los gastos de OT no se tocan desde acá
`PUT` y `DELETE` con el id de un gasto de OT dan 404 `EXPENSE_NOT_FOUND`. Esos se manejan
desde la OT, que además no deja cambiarlos si está cerrada (CAM-75).

### 6. El costo del vehículo pasa a ser total
`GET /api/vehicles/{id}/costs` suma los dos tipos en el total, los últimos 12 meses, la
cantidad y el costo por km. Es lo que pidió la card: el costo de tener el vehículo. El
costo por km deja de ser solo de mantenimiento, y **no es comparable con el de antes**.

### 7. Sin comprobante
La card pide vehículo, fecha, categoría, descripción y monto. El número y la foto de
CAM-107 se pueden sumar después sin romper el contrato: ya viajan en el listado, en null.
