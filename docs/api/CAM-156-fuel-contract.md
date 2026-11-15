# CAM-156 — Combustible: cargas, consumo por vehículo y aviso de consumo anormal

## Qué cambia

| Dónde | Cambio |
|---|---|
| `POST /api/fuel-loads` (ADMIN o CHOFER) | Nueva: registra una carga |
| `PUT /api/fuel-loads/{id}`, `DELETE /api/fuel-loads/{id}` (ADMIN) | Nuevas: corregir o borrar una carga |
| `GET /api/vehicles/{id}/fuel?months=` (ADMIN) | Nueva: consumo de los últimos meses y las cargas |
| `GET /api/expenses` | Las cargas aparecen con `category: combustible` y `origin: fuel` (solo lectura acá) |
| `POST /api/expenses` | `category: combustible` da `422`: el combustible se carga como carga. Los gastos de combustible anteriores se siguen editando |
| `GET /api/vehicles/{id}/costs`, `GET /api/vehicles/comparison` | Las cargas suman como gasto operativo |
| Avisos | Tipo nuevo `fuel_consumption_anomaly`, configurable, por defecto al admin (in-app y push) |

## 1. La carga

`fuel_loads`: vehículo, `loadedAt` (fecha y hora), `liters` (numeric 10,2), `amount` (numeric 12,2),
`odometerKm`, `fullTank` y quién la cargó (`created_by`, el usuario del token).

- `loadedAt` es opcional: sin valor, ahora. Más de 5 minutos en el futuro da `422`; un poco adelantado
  (el reloj del celular) se toma como ahora.
- `liters` y `amount` mayores a 0, con hasta 2 decimales; hasta 2.000 litros por carga.
- `fullTank` es opcional, `true` por defecto (lo más común es llenar el tanque).
- El vehículo tiene que existir (`404 VEHICLE_NOT_FOUND`) y estar activo (`409 VEHICLE_INACTIVE`).
- **Km en orden**: la carga anterior por fecha no puede tener más km, ni la siguiente menos. Una carga
  con menos km que la anterior da `422` con `field: odometerKm` y el km y la fecha de la anterior. Igual
  km se acepta (dos cargas en la misma parada).
- Al cargarla, su km mueve el odómetro del vehículo con la regla de CAM-90 (nunca baja ni salta más de
  `max-jump-km`). Editarla no lo mueve.

## 2. Consumo: método de tanque lleno

Un **tramo** va de una carga de tanque lleno a la siguiente de tanque lleno. Los litros del tramo son los
de todas las cargas después de la primera: las parciales del medio y la que cierra. Los km, la diferencia
de odómetro. L/100 km = litros × 100 / km. Costo por km = monto del tramo / km.

- Las cargas parciales antes del primer tanque lleno no cuentan: no se sabe desde qué nivel arrancó.
- Dos tanques llenos en el mismo km no son un tramo: el siguiente arranca desde el último.
- El consumo de varios tramos juntos es litros totales sobre km totales, no el promedio de cada tramo
  (un tramo corto no pesa lo mismo que uno largo).
- Se calcula, no se guarda: editar o borrar una carga recalcula todo.

Cada carga del listado trae `consumption` con el tramo que cierra (`km`, `liters`, `litersPer100Km`,
`costPerKm`), o `null` si no cierra ninguno.

## 3. Aviso de consumo anormal

Al **crear** una carga de tanque lleno que cierra un tramo, se compara su consumo con el de los últimos
10 tramos anteriores del vehículo. Hacen falta al menos 3. Si supera 1,3 veces ese promedio (un 30% más)
la carga queda marcada (`alert: { litersPer100Km, averageLitersPer100Km }`) y, después del commit, se
avisa a los destinatarios de `fuel_consumption_anomaly`. El link lleva a la pestaña Combustible del
vehículo (`/vehiculos/{id}/combustible`). Configurable en `application.yml`:

```yaml
fleetguard:
  fuel-alerts:
    min-segments: 3
    recent-segments: 10
    ratio: 1.3
```

Se usa un 30% y no "el doble" como en los km del viaje (CAM-152) porque el consumo de un vehículo varía
mucho menos que la distancia de sus viajes. Editar una carga no vuelve a avisar.

Ejemplo: "AB123CD consumió 45,0 L cada 100 km en los últimos 500 km, un 47% más que su promedio de 30,7 L."

## 4. `GET /api/vehicles/{id}/fuel?months=6`

`months` entre 1 y 24 (6 por defecto), contando el mes actual (hora de Argentina).

- `summary`: cantidad de cargas, litros y monto de las cargas del período; km medidos, L/100 km y costo
  por km de los tramos que **cierran** en el período (una carga de antes del período puede abrir uno).
- `monthly`: un elemento por mes, también los vacíos, con lo mismo por mes.
- `loads`: las cargas del período, de la más reciente a la más antigua.

## 5. Gastos

En `GET /api/expenses` cada carga es un gasto con `category: combustible`, `origin: fuel`, la fecha y
hora de la carga y la descripción "Carga de 85,5 L a los 152.300 km" (con " (parcial)" si no fue tanque
lleno). Se editan y borran desde Combustible, no desde Gastos (`PUT/DELETE /api/expenses/{id}` da 404).
En la comparación de vehículos (CAM-140) y los costos del vehículo (CAM-105) cuentan como gasto operativo.

## 6. Base de datos ya creada

Igual que en CAM-152, `ddl-auto: update` crea la tabla nueva pero no actualiza el `CHECK` del tipo de
aviso en una base que ya existe. Si no se corrió ya:

```sql
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notification_settings DROP CONSTRAINT IF EXISTS notification_settings_type_check;
```

## Fuera de alcance

- Cargar combustible sin conexión (la carga del chofer necesita señal).
- Foto del ticket.
- Detectar robo comparando litros contra la capacidad del tanque (no se guarda la capacidad).
