# CAM-105 — Costo de mantenimiento de un vehículo

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

> Desde CAM-109 los totales y el costo por km también suman los gastos operativos: ya no son
> solo de mantenimiento. Ver `CAM-109-operating-expenses-contract.md`, decisión 6.

## Motivación

La pestaña Gastos (CAM-52) ya muestra el gasto de un vehículo si se lo filtra, pero
siempre dentro de un período. El detalle del vehículo necesita lo que hace falta para
decidir si conviene seguir reparándolo o reemplazarlo: cuánto costó en toda su vida,
cuánto en el último año y, sobre todo, cuánto cuesta por km, que es lo que permite
comparar un vehículo que anda mucho con uno que anda poco.

## Endpoint

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/vehicles/{id}/costs` | Totales de gasto del vehículo y su costo por km |

## Decisiones

### 1. Versión liviana, sin desglose por categoría
La card pedía también el total por categoría. Se dejó afuera a propósito: está a un
click, en la pestaña Gastos filtrada por el vehículo, que el detalle linkea. Este
endpoint trae solo lo que Gastos no tiene: el total de toda la vida del vehículo y el
costo por km.

### 2. Solo ADMIN
Exige el JWT de un ADMIN (401/403), igual que `/api/expenses`: son los mismos costos.

### 3. Qué gastos suman
Los de todas las OTs del vehículo, sin importar su estado, igual que `/api/expenses`.
Así el total coincide con lo que muestra Gastos para ese vehículo con el período "Todo".
"Últimos 12 meses" son los cargados desde la medianoche (hora argentina) del mismo día de
hace un año; un 29/02 cuenta desde el 28/02. Es la misma ventana con la que "Ver gastos" abre
la pestaña Gastos, así la cifra de la tarjeta y la de la pestaña coinciden.

### 4. Costo por km
`totalAmount / kmDriven`, con `kmDriven = currentKm − firstKnownKm`, redondeado a 2
decimales.

- **`firstKnownKm`** es el km del registro más antiguo entre las inspecciones del
  vehículo y sus mantenimientos hechos (`completedKm`; como solo guardan el día, cuentan
  desde el inicio de ese día en UTC). Es lo único con km que queda guardado: el vehículo
  solo guarda el km actual, y "Cargar km" o el km de cierre de una OT que no viene de un
  plan lo pisan sin dejar registro. Si dos registros caen en el mismo momento, gana el
  menor km.
- **El más antiguo, no el menor.** La primera versión tomaba el menor km, y falló con
  los datos de demo: una inspección cargada hoy con 310 km en un vehículo de 309.400 km
  (la inspección no rechaza un km menor al del vehículo) daba 309.090 km recorridos y un
  costo por km de $ 8,86. Un error de tipeo reciente no tiene que mover el punto de
  partida; uno en el registro más antiguo sí lo movería, y es un costo aceptado.
- **Sin registros, o con `kmDriven` ≤ 0**, `kmDriven` y `costPerKm` vienen en `null`
  y el front muestra "sin datos". Un número calculado sobre 3 km sería engañoso, pero
  no se fija un mínimo arbitrario: cualquier recorrido mayor a 0 da un valor.
- Los gastos cargados antes del primer registro de km también suman al total. Es una
  aproximación aceptada: lo normal es que un vehículo tenga una inspección antes de su
  primera OT.
