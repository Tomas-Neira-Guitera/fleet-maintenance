# CAM-157 — Neumáticos: cubiertas por posición, km recorridos, estado y rotación

## Qué cambia

| Dónde | Cambio |
|---|---|
| `/api/tires` (ADMIN) | Nuevo: alta, edición, baja y borrado de cubiertas, y su detalle |
| `GET /api/tires/brand-comparison` (ADMIN) | Nuevo: costo por km por marca |
| `/api/vehicles/{id}/tires`, `/tires/mount`, `/tires/unmount`, `/tires/rotate` (ADMIN) | Nuevos: qué cubierta hay en cada posición, montar, sacar y rotar |
| `vehicleType` (alta, edición, importación) | Pasa a ser un catálogo cerrado y obligatorio al crear: define ejes y cubiertas |
| `/api/vehicles/{id}/coupling` (GET admin o chofer; PUT y DELETE admin) | Nuevo: enganche de un semirremolque o acoplado al que lo arrastra |
| `GET /api/vehicles/{id}/tire-positions` (ADMIN o CHOFER) | Nuevo: posiciones del vehículo, para marcar cubiertas con problemas en el pre-viaje |
| `POST /api/inspections/{vehicleId}` | Acepta `tireConditions` (con Neumáticos en defecto) y `trailerId` (pre-viaje), opcionales; rechaza remolques |
| `GET /api/vehicles` | Trae `coupledWith`: con quién está enganchado cada vehículo, para el listado de remolques |
| `POST /api/fuel-loads` | Rechaza remolques (`409 VEHICLE_IS_TRAILER`) |
| `trailer_couplings` | Tabla nueva |
| Avisos | Tipo nuevo `tire_problem_reported`, configurable, por defecto al admin (in-app y push) |
| `tire_condition_reports` | Tabla nueva: lo que reportó el chofer de cada cubierta |

## 1. Tipo de vehículo, ejes y posiciones

El tipo es un catálogo cerrado y **define las cubiertas** (S = rueda simple, D = dual), siempre con auxilio:

| `vehicleType` | Nombre | Ejes | Arrastra |
|---|---|---|---|
| `utilitario` | Utilitario (furgón, pickup, camioneta) | S · S | — |
| `camion_4x2` | Camión rígido 4x2 | S · D | acoplado |
| `camion_6x4` | Camión rígido 6x2 / 6x4 | S · D · D | acoplado |
| `tractor_4x2` | Tractor 4x2 | S · D | semirremolque |
| `tractor_6x4` | Tractor 6x4 | S · D · D | semirremolque |
| `semirremolque_2_ejes` | Semirremolque 2 ejes | D · D | — |
| `semirremolque_3_ejes` | Semirremolque 3 ejes | D · D · D | — |
| `acoplado` | Acoplado | D · D | — |

Posición = eje + lado (+ `ext`/`int` en una dual): `e1-izq`, `e2-der-int`, `e3-izq-ext`... y `auxilio`.
Cada una trae `axle` (1 el de adelante, 0 el auxilio), `side` (`izq`/`der`) y `wheel`
(`simple`/`exterior`/`interior`), para dibujar el vehículo visto de arriba.

El nombre que se muestra: en un vehículo con motor de 2 ejes, "Delantera" / "Trasera"; con 3 ejes o en un
remolque, "Eje n". La dual agrega "exterior"/"interior" ("Eje 3 derecha interior").

- Al crear (alta o importación) el tipo es **obligatorio**. Se acepta el código, el nombre o un alias
  (furgón, pickup, camioneta, van → `utilitario`; camión → `camion_4x2`; balancín → `camion_6x4`;
  tractor → `tractor_4x2`; semi → `semirremolque_3_ejes`). Otro valor da `422` en `vehicleType`.
- Cambiar el tipo con cubiertas en posiciones que el nuevo no tiene da `409 TIRES_ON_REMOVED_POSITIONS`;
  estando enganchado, `409 VEHICLE_COUPLED`.
- Un vehículo viejo cuyo tipo de texto libre no es del catálogo se lee como `null` ("Sin definir"): sin
  posiciones, y montar da `409 NO_VEHICLE_TYPE`. Una posición que no es de su tipo, `422`.

## 2. Cubiertas

`tires`: marca y medida obligatorias; código (número de serie o el que pinta el taller) y modelo
opcionales; fecha de compra (no futura) y costo. El código no se repite (`409 DUPLICATE_TIRE_CODE`).

Estado: `stock` (sin montar), `mounted` o `retired` (dada de baja, con fecha y motivo opcional).
- Una montada no se da de baja con `POST /api/tires/{id}/retire` (`409 TIRE_MOUNTED`): se saca del
  vehículo con `retire: true`.
- `DELETE` solo borra una cargada por error: si ya estuvo montada, `409 TIRE_HAS_HISTORY`.

## 3. Montar, sacar y rotar

`tire_mountings` guarda dónde estuvo cada cubierta: vehículo, posición, cuándo y con qué km del vehículo se
montó, y cuándo y con qué km se sacó. Es el historial.

- **Montar** (`POST /vehicles/{id}/tires/mount` con `{ tireId, position }`): la cubierta tiene que estar en
  stock (`409 TIRE_NOT_IN_STOCK`) y el vehículo activo. Si la posición ya tiene una, da `409 POSITION_OCCUPIED`,
  salvo que venga `replaced: "stock" | "retired"` (y `retiredReason` opcional): ahí la anterior se saca y la
  nueva se monta en la misma operación. Así se **cambia** una cubierta.
- **Sacar** (`POST /vehicles/{id}/tires/unmount` con `{ position, retire, reason }`): vuelve al stock o se da
  de baja. Sin cubierta, `409 POSITION_EMPTY`.
- **Rotar** (`POST /vehicles/{id}/tires/rotate` con `{ from, to }`): mueve la cubierta; si `to` tiene una, se
  intercambian. Cierra los montajes y abre otros con el km actual.

Las tres devuelven el vehículo como `GET /vehicles/{id}/tires`.

## 4. Km de cada cubierta

**Se calculan, no se guardan**: la suma, en todos sus montajes, del km del vehículo al sacarla (o el actual
si sigue puesta) menos el km al montarla. Rotar cierra un montaje y abre otro, así que los km siguen
sumando en la nueva posición sin lógica aparte. El **auxilio no suma km**: mientras está ahí no rueda.

El km del vehículo es el de CAM-90 (nunca baja), así que un montaje nunca resta.

## 5. Estado de las cubiertas (relevamiento visual)

Al principio se medía la profundidad del dibujo en mm, pero el chofer no anda con un medidor. Se reemplazó por
lo que se ve a simple vista; no hay mediciones en mm en ningún lado.

- Estados (`TireCondition`, del más grave al más leve): `se-ve-la-tela`, `bulto`, `corte-flanco`,
  `pinchada`, `baja-presion`, `desgaste-irregular`, `poco-dibujo`. Se pueden marcar varios por cubierta.
- En la **inspección**, solo si el control "Neumáticos" (`ext-neumaticos`) va en defecto:
  `tireConditions: [{ position, conditions: [...] }]`, opcional. Las posiciones salen de
  `GET /vehicles/{id}/tire-positions`: todas las del tipo, auxilio incluido, con `mounted`; solo se reportan
  las que tienen cubierta. Errores (`422`, la inspección no se guarda):
  - `tireConditions` sin "Neumáticos" en defecto: `itemId = "tireConditions"`.
  - Posición sin cubierta o repetida: `"tireConditions[i].position"`.
  - Sin estados, o con uno desconocido: `"tireConditions[i].conditions"`.

  Viaja con la inspección, así que también sale de la cola sin conexión (CAM-153).
- **Va con el defecto, no aparte**:
  - El resumen se suma al detalle del defecto de Neumáticos ("Cubiertas: Trasera derecha exterior: se ve la
    tela o el alambre."). Así entra al circuito de siempre: defecto → programación → OT.
  - La severidad la elige el chofer, como en cualquier defecto.
- `tire_condition_reports` guarda cada cubierta reportada, con su defecto. El reporte **vale** mientras el
  defecto esté abierto y la cubierta montada. Si se resuelve el defecto (al finalizar la OT) o se saca la
  cubierta, deja de valer y queda en el historial de la cubierta.
- `Tire.condition` es el último reporte que vale (null si no hay). En la vista de arriba del admin, la cubierta
  se pinta según su `severity` (bloqueante o no). `GET /api/tires/{id}` trae todos los `reports`.
- **Aviso** `tire_problem_reported`: uno por inspección, a los destinatarios configurados, con link a
  `/vehiculos/{id}/neumaticos`. Ejemplo: "chofer1 reportó en AB123CD: Trasera derecha exterior (Michelin
  X Multi D N° 1234): se ve la tela o el alambre, poco dibujo."

## 6. Costo por km por marca

`GET /api/tires/brand-comparison`: por marca, costo total sobre km totales de sus cubiertas, montadas o
dadas de baja. Entran solo las que llevan al menos 1.000 km: una recién montada daría un costo por km
engañoso. Ordenado del más barato por km al más caro, con cantidad de cubiertas, dadas de baja y km
promedio por cubierta.

## 7. Remolques y enganche

Semirremolques y acoplados son vehículos (tienen cubiertas, documentación, mantenimiento, órdenes de
trabajo), pero **solo los ve el admin**: no aparecen en el listado del chofer, no se inspeccionan desde la
app (`409 VEHICLE_IS_TRAILER`) ni cargan combustible (ídem).

- `PUT /vehicles/{trailerId}/coupling` con `{ towingId }` lo engancha. Un tractor arrastra semirremolques y
  un camión rígido acoplados (`409 CANNOT_TOW`); `{trailerId}` tiene que ser un remolque
  (`409 NOT_A_TRAILER`); los dos activos (`409 VEHICLE_INACTIVE`). Si el remolque estaba con otro, o el otro
  ya arrastraba uno, esos enganches se cierran. `DELETE` lo desengancha.
- `GET /vehicles/{id}/coupling` en un remolque trae `towedBy`; en un tractor o camión, `towing`. También
  `canTow` y el historial (`trailer_couplings`).
- En el **pre-viaje** de un tractor o camión el chofer elige qué remolque lleva: `trailerId` con un id lo
  engancha, `"none"` desengancha, sin el campo no cambia nada. Se valida antes de guardar la inspección.
- **Km del remolque**: no tiene odómetro. Cada km que suma el que arrastra (inspecciones, CAM-90) se le
  suma también al remolque enganchado. Así sus cubiertas suman km y su mantenimiento por km vence solo.

## 8. Base de datos ya creada

`ddl-auto: update` crea las tablas y columnas nuevas, pero no actualiza los `CHECK` de los enums ni borra lo
que ya no se usa. Con datos de versiones anteriores de esta rama (posiciones `del-*`/`tras-*`, `tire_layout` y
la medición del dibujo):

```sql
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notification_settings DROP CONSTRAINT IF EXISTS notification_settings_type_check;
ALTER TABLE tire_mountings DROP CONSTRAINT IF EXISTS tire_mountings_position_check;

UPDATE tire_mountings SET position = CASE position WHEN 'DEL_IZQ' THEN 'E1_IZQ' WHEN 'DEL_DER' THEN 'E1_DER'
  WHEN 'TRAS_IZQ' THEN 'E2_IZQ' WHEN 'TRAS_DER' THEN 'E2_DER' ELSE position END;

-- El tipo sale de la configuración que tenía; los demás se eligen desde la pantalla de edición.
UPDATE vehicles SET vehicle_type = CASE tire_layout WHEN 'CUATRO_RUEDAS' THEN 'utilitario'
  WHEN 'SEIS_RUEDAS' THEN 'camion_4x2' WHEN 'DIEZ_RUEDAS' THEN 'camion_6x4' END
  WHERE tire_layout IS NOT NULL;
ALTER TABLE vehicles DROP COLUMN IF EXISTS tire_layout;

-- La medición del dibujo se reemplazó por el estado visual: un tipo de aviso que ya no existe rompe la lectura.
DELETE FROM notifications WHERE type = 'TIRE_TREAD_LOW';
DELETE FROM notification_settings WHERE type = 'TIRE_TREAD_LOW';
DROP TABLE IF EXISTS tire_tread_readings;
ALTER TABLE tires DROP COLUMN IF EXISTS initial_tread_mm;
ALTER TABLE tires DROP COLUMN IF EXISTS tread_alerted_at;
```

El `vehicle_type` de texto libre anterior no hace falta migrarlo: se lee con los alias (`furgon` →
utilitario, `camion` → camión rígido 4x2) y se guarda con el código la próxima vez que se edita. El seed
demo (`docs/db/seed-demo.sql`) ya hace todo esto.

## Fuera de alcance

- El costo de las cubiertas en Gastos: una cubierta en stock todavía no es gasto de ningún vehículo.
- Recapados y cubiertas compradas usadas (con km previos).
- Pasar una cubierta de un vehículo a otro en un solo paso (se saca al stock y se monta en el otro).
- Presión de inflado medida (solo "baja presión o desinflada" a simple vista) y profundidad del dibujo en mm.
- Tipos con más ejes (bitrén, 8x4) o ruedas superanchas (single wide) en lugar de duales.
- El chofer no ve ni engancha remolques fuera del pre-viaje; el admin sí.
