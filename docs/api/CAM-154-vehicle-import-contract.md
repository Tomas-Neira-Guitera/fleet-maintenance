# CAM-154 — Importar la flota desde un Excel o CSV

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Endpoint

`POST /api/vehicles/import`, solo ADMIN. Con `dryRun=true` devuelve la vista previa
sin guardar nada; sin `dryRun` crea las filas válidas. La respuesta es la misma en
los dos casos: el resultado de cada fila y cuántas se crearon.

## Decisiones

### 1. El archivo lo lee el navegador
El frontend lee el CSV (separado por `,` o `;`, que es lo que exporta Excel en
español) o el `.xlsx`, y manda las filas como JSON. Así el backend no suma una
librería de planillas y valida igual que siempre.

### 2. Mismas reglas que el alta manual
Cada fila pasa por `VehicleService.validateNew`, la validación del alta (CAM-25):
patente, marca y modelo obligatorios y el año entre 1980 y el año que viene. Se
sumó que los km no pueden ser negativos, que vale también para el alta manual.

Antes de validar, se sacan los espacios de más y la patente queda en mayúsculas y
sin espacios.

### 3. Patentes repetidas
- Una patente que ya existe en la flota (activa o dada de baja) es un error de esa
  fila: no se duplica.
- Una patente que se repite en el archivo vale la primera vez; las siguientes
  filas se marcan con error, indicando la fila de la primera.

### 4. Se crean solo las válidas
Al confirmar, las filas con errores se saltean y el resto se crea en una sola
transacción. El admin ve antes, en la vista previa, cuáles van a quedar afuera y
por qué.

### 5. Límite
Entre 1 y 500 filas por archivo: alcanza para una flota de hasta 200 vehículos con
margen.
