# CAM-140 — Comparar vehículos para decidir si conviene reparar o reemplazar

`GET /api/vehicles/comparison?from&to`, solo ADMIN: una fila por vehículo activo con lo que cuesta
mantenerlo, cuánto anduvo, cuánto estuvo parado y cuántas fallas se le repiten, más el promedio de la
flota. Los datos ya existían, pero de a un vehículo. El esquema completo está en `openapi.yaml`
(`VehicleComparison`).

## Decisiones

### 1. Cada número sale del mismo lugar que en el resto de la app

Así coincide con lo que muestra cada pantalla para el mismo período.

| Indicador | De dónde sale |
|---|---|
| `maintenanceCost`, `operatingCost` | `GET /api/expenses` con el mismo `from`/`to`. El de mantenimiento son los gastos de OTs (preventivo, correctivo y manual), y el operativo, los de CAM-109 |
| `availabilityPct`, `downtimeHours`, `stops` | `GET /api/downtime` (CAM-134), que además valida el período y lo recorta a hoy |
| `recurringControls` | `GET /api/defects/recurring` (CAM-135): cuántos controles del checklist le fallan seguido **hoy** |
| `kmDriven` | Las lecturas de km que usa la fecha estimada (CAM-137) |

`recurringControls` no depende del período. CAM-135 mira su propia ventana fija de 30 días, que es la
misma que muestra el detalle del vehículo, y "recurrente en un año entero" sería otra regla.

### 2. Los km del período

Del km al empezar el período al km al terminarlo:

- **El km al empezar** es la última lectura válida hasta el comienzo del período. Si no hay ninguna
  antes, es la primera dentro del período, y los km cuentan desde esa fecha. Pasa con un vehículo que
  empezó a cargar km a mitad del período, o con una flota que empezó a usar el sistema hace poco.
- **El km al terminar** es la última lectura válida hasta el final.
- **Qué lecturas valen:** las mismas que en CAM-137 (`KmPerDayEstimator.validReadings`). Se descartan
  las menores a una anterior, las mayores al km actual y las futuras. Como en CAM-137, también se
  descartan las primeras de la historia que tienen más de 5.000 km (`fleetguard.odometer.max-jump-km`)
  hasta la siguiente. Un vehículo dado de alta con 309.000 km cuya primera inspección se cargó con 310
  no anduvo 309.000 km en el período.
- **Sin dos lecturas distintas** (ninguna, una sola, o todas antes del período), `kmDriven` es `null`:
  no se sabe cuánto anduvo. Si no se movió, es `0`.

El costo por km (`maintenanceCostPerKm`, `totalCostPerKm`) divide por `kmDriven` solo los gastos del
mismo tramo: los que tienen fecha desde que empiezan a contar los km. Si las lecturas arrancan el 20/10,
una reparación del 5/10 no se divide por los km de después: daría un costo por km inflado y marcaría el
vehículo "Para revisar" sin motivo. Esa reparación sí cuenta en `maintenanceCost`, que es el gasto de
todo el período. El resultado va a dos decimales, y es `null` si `kmDriven` es `null` o `0`.

### 3. El promedio de la flota

Cada indicador se promedia sobre los vehículos que lo tienen, y es `null` si no lo tiene ninguno. Los
costos y los conteos cuentan los ceros: un vehículo sin gastos baja el promedio. Los km solo cuentan
los vehículos con km.

El costo por km de la flota no es el promedio de los costos por km de cada vehículo. Es el gasto de
los vehículos con km dividido por sus km. Con el promedio de los cocientes, un vehículo con 100 km y
un gasto grande subiría el de toda la flota hasta que ningún otro quede por encima.

La marca se decide contra el promedio sin redondear, y a la respuesta va redondeado: a dos decimales
la plata y a uno el resto.

El promedio es sobre toda la flota activa, no por tipo de vehículo. Comparar una camioneta con un
camión puede confundir, pero hoy el tipo de vehículo es texto libre y no alcanza para agrupar.

### 4. "Para revisar"

Un vehículo queda marcado si está peor que el promedio de la flota en al menos dos de estos tres
indicadores:

| `reasons` | Peor es |
|---|---|
| `maintenance_cost_per_km` | Un costo por km de mantenimiento más alto |
| `availability` | Una disponibilidad más baja |
| `recurring_defects` | Más controles con fallas recurrentes |

Un indicador sin dato, en el vehículo o en el promedio, no cuenta. `reasons` dice cuáles fueron, para
que la pantalla diga "Costo por km y disponibilidad peores que el promedio", y viene vacío si el
vehículo no está marcado. La marca solo señala qué mirar: la decisión es del admin.

### 5. Qué vehículos aparecen

Los activos. Los dados de baja no aparecen. Las filas vienen por patente, y la pantalla las reordena.
