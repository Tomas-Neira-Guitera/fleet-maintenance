# CAM-134 — Tiempo fuera de servicio y tiempo promedio de reparación

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Gastos dice cuánto cuesta cada vehículo, pero no cuánto tiempo estuvo parado. Esta
card mide, para un período, cuánto tiempo estuvo cada vehículo sin poder salir y
cuánto tarda en promedio una reparación, para detectar los vehículos que más paran y
si el taller (o el proveedor externo) se está demorando.

## Endpoint

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/downtime?from=&to=[&vehicleId=]` | Disponibilidad y reparaciones de la flota, o de un vehículo |

Solo ADMIN. `from` y `to` son obligatorios, con el mismo formato que `/api/expenses`
(instante ISO o fecha sola), y forman el período [from, to).

## Decisiones

### 1. Qué es "fuera de servicio"
Un vehículo está fuera de servicio mientras tiene **al menos un defecto bloqueante
abierto**: la misma regla que lo deja "No disponible" (CAM-49, CAM-89). Cada defecto
bloqueante aporta el intervalo que va desde que se reportó hasta que se resolvió (o
hasta ahora, si sigue abierto). Los intervalos de un mismo vehículo que se solapan o
se tocan se unen, así dos bloqueantes a la vez no cuentan doble.

La documentación vencida **no cuenta**: la card de vencimientos de documentación
(CAM-127) todavía no existe. Cuando exista, se suma como otra fuente de intervalos.

### 2. Cuándo se resolvió un defecto
Desde esta card, el defecto guarda `resolvedAt`: el momento en que se finaliza la OT
que lo resuelve. Los defectos resueltos antes no lo tienen; para ellos se usa la fecha
de finalización de su OT (la finalizada más antigua de ese defecto). Si tampoco hay OT,
el defecto no suma tiempo: no hay forma de saber cuánto duró. Si un defecto ya resuelto
recibe otra OT (por ejemplo, una programación vieja que se finaliza después), su fecha
de resolución no cambia: vale la primera.

### 3. El período se corta en "ahora"
Lo que todavía no pasó no cuenta. "Este mes" el día 10 mide del 1 al 10, y la
disponibilidad es sobre esos 10 días. Un período que todavía no empezó da
`availabilityPct` en null y 0 horas fuera de servicio.

### 4. Paradas
`stops` es la cantidad de intervalos (ya unidos) que tocan el período: cuántas veces
estuvo parado, aunque la parada haya empezado antes del período.

### 5. Disponibilidad
Por vehículo: `100 × (1 − horas fuera de servicio / horas del período)`, con un
decimal. De la flota: lo mismo sumando todos los vehículos, o sea el promedio
ponderado. Un período sin paradas da 100.

Sin vehículos (una flota activa vacía), la disponibilidad de la flota viene en null,
igual que con un período que todavía no empezó.

Las horas vienen redondeadas a un decimal, cada vehículo y el total por separado: la
suma de los vehículos puede diferir del total en 0,1 h.

Entran los vehículos **activos**. El vehículo no guarda su fecha de alta, así que se
asume que existió todo el período: uno dado de alta a mitad de mes cuenta como
disponible desde el día 1. Con `vehicleId` se puede pedir uno dado de baja.

### 6. Tiempo promedio de reparación
Promedio de horas entre que se creó y se finalizó cada OT **finalizada dentro del
período** (por `finalizedAt`), de cualquier origen: defecto, mantenimiento o manual.
Para toda la flota, por vehículo y por tipo de ejecución (interna o externa). Sin OTs
finalizadas, el promedio viene en null y el front muestra "Sin datos", no 0.

Costo aceptado: mide desde que se creó la OT, no desde que empezó el trabajo. Una OT
que se reusa al replanificar un mantenimiento conserva su fecha de creación, así que
ahí el promedio incluye la espera.

### 7. Orden
`vehicles` viene de más horas fuera de servicio a menos, y a igual tiempo, por patente.
El Resumen muestra solo el primero (el que más tiempo estuvo parado), que cambia con el período.
