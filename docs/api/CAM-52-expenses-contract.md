# CAM-52 — Gastos de mantenimiento de la flota

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Desde CAM-63 los gastos se cargan dentro de cada orden de trabajo (`WorkOrderExpense`,
con categoría y monto), pero solo se veían abriendo cada OT. `GET /api/expenses` los
devuelve todos juntos para la pestaña "Gastos" del admin.

## Endpoint

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/expenses` | Gastos de todas las OTs, con filtros opcionales `vehicleId`, `category`, `from` y `to` |

## Decisiones

### 1. Solo lectura, sin modelo nuevo
Los gastos siguen viviendo en la OT: se cargan y se borran con
`POST`/`DELETE /api/work-orders/{id}/expenses`. Este endpoint es otra forma de leer
esas mismas filas, con el vehículo y la OT de cada una.

### 2. Solo ADMIN
Exige el JWT de un ADMIN (401/403), igual que `/api/users`. El resto de la API todavía
no valida el token (CAM-73), pero un endpoint nuevo con los costos de toda la flota no
se publicó abierto.

### 3. Los totales los calcula el cliente
La respuesta es el listado (`{ items: [...] }`), del más reciente al más antiguo. El
total del período, el total por vehículo y los cortes de los gráficos salen de sumar ese
listado en el frontend: así listado, totales y gráficos no pueden desacordarse, y un
filtro nuevo no necesita otro endpoint. Con el volumen esperado (cientos de gastos por
año) el listado entero es chico.

### 4. Período [from, to)
`from` y `to` son opcionales; sin uno, el período queda abierto de ese lado.

- Con un **instante ISO completo** (`2026-01-01T03:00:00Z`): `from` se incluye y `to` no.
- Con una **fecha sola** (`2026-12-31`): se toma en UTC, y `to` incluye ese día entero.

El frontend manda instantes completos con la medianoche **local**: en Argentina (UTC-3)
un gasto cargado un 31 a las 22:00 tiene fecha UTC del día siguiente, y con fechas solas
caería en el período equivocado (mismo problema que describe CAM-88 para el calendario).

Un valor que no es ni una cosa ni la otra es un 422 con `details[].field` = `from` o `to`.

### 5. La fecha de un gasto es la de su carga
`date` es `WorkOrderExpense.createdAt`. No hay una "fecha de factura" aparte: si hiciera
falta imputar un gasto a otro día, es un campo nuevo en la carga del gasto (CAM-63).

### 6. Entran los gastos de todas las OTs, cualquiera sea su estado
También los de OTs en curso o canceladas: es plata que se gastó. Cada gasto trae
`workOrderStatus` por si la vista quiere distinguirlos.

### 7. `origin`: preventivo o correctivo
Cada gasto dice de dónde nació su OT: `plan` (un plan de mantenimiento: gasto
preventivo), `defect` (un defecto: correctivo) o `manual` (OT suelta). No es el
`sourceType` de la OT: una OT creada desde el calendario tiene
`sourceType = scheduled_maintenance`, pero conserva el plan o el defecto que la originó,
y eso es lo que importa para saber si la flota gasta más en prevenir o en arreglar.

### 8. Filtros que no encuentran nada
Un `vehicleId` malformado o inexistente, o una `category` que no existe, devuelven una
lista vacía, no un error (mismo criterio que los filtros de `GET /api/work-orders`).

## Fuera de alcance
Exportar a planilla, presupuestos y alertas de gasto, gastos que no salen de una OT
(combustible, peajes, seguros).
