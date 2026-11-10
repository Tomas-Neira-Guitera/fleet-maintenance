# CAM-157 — Neumáticos: cubiertas por posición, km recorridos, dibujo y rotación

## Qué cambia

| Dónde | Cambio |
|---|---|
| `/api/tires` (ADMIN) | Nuevo: alta, edición, baja y borrado de cubiertas, y su detalle |
| `GET /api/tires/brand-comparison` (ADMIN) | Nuevo: costo por km por marca |
| `/api/vehicles/{id}/tires`, `/tire-layout`, `/tires/mount`, `/tires/unmount`, `/tires/rotate`, `/tire-treads` (ADMIN) | Nuevos: configuración de ejes, qué cubierta hay en cada posición, montar, sacar, rotar y medir |
| `GET /api/vehicles/{id}/tire-positions` (ADMIN o CHOFER) | Nuevo: posiciones montadas, para anotar el dibujo en el pre-viaje |
| `POST /api/inspections/{vehicleId}` | Acepta `treadReadings` opcional en el pre-viaje |
| `vehicles.tire_layout` | Columna nueva, nullable |
| Avisos | Tipo nuevo `tire_tread_low`, configurable, por defecto al admin (in-app y push) |

## 1. Configuración de ejes y posiciones

Cada vehículo tiene una configuración fija (`PUT /api/vehicles/{id}/tire-layout` con `{ "layout": ... }`).
Las posiciones salen de ahí, siempre con auxilio:

| `layout` | Posiciones |
|---|---|
| `4_ruedas` | `del-izq`, `del-der`, `tras-izq`, `tras-der`, `auxilio` |
| `6_ruedas` (trasero dual) | `del-izq`, `del-der`, `e2-izq-ext`, `e2-izq-int`, `e2-der-int`, `e2-der-ext`, `auxilio` |
| `10_ruedas` (dos ejes traseros duales) | las de `6_ruedas` más `e3-izq-ext`, `e3-izq-int`, `e3-der-int`, `e3-der-ext` |

El nombre que se muestra depende de la configuración: con un solo eje trasero se dice "Trasera derecha
interior"; con dos, "Eje 3 derecha interior". Cada posición trae `axle` (1 el delantero, 0 el auxilio),
`side` (`izq`/`der`) e `inner`, para dibujar el esquema.

`vehicleType` sigue siendo texto libre: la configuración la elige el admin. Cambiarla cuando quedan
cubiertas en posiciones que desaparecen da `409 TIRES_ON_REMOVED_POSITIONS`. Montar sin configuración da
`409 NO_TIRE_LAYOUT`; una posición que no es de la configuración, `422`.

## 2. Cubiertas

`tires`: marca y medida obligatorias; código (número de serie o el que pinta el taller), modelo y dibujo
inicial opcionales; fecha de compra (no futura) y costo. El código no se repite (`409 DUPLICATE_TIRE_CODE`).

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

## 5. Dibujo

- En el **pre-viaje**, el chofer puede anotar la profundidad de cada cubierta montada:
  `treadReadings: [{ position, depthMm }]`, opcional. Las posiciones salen de
  `GET /vehicles/{id}/tire-positions` (las que ruedan y tienen cubierta). Una posición sin cubierta o
  repetida, o un valor fuera de 0–30 mm o con más de un decimal, da `422` con
  `details[].itemId = "treadReadings[i].position"` o `".depthMm"`, y la inspección no se guarda. En el
  post-viaje se ignora. Viaja con la inspección, así que también sale de la cola sin conexión (CAM-153) con
  la hora en que se hizo.
- El admin también puede cargar mediciones (`POST /vehicles/{id}/tire-treads` con `{ readings: [...] }`).
- **Aviso**: una medición por debajo del mínimo avisa a los destinatarios de `tire_tread_low` (link a
  `/vehiculos/{id}/neumaticos`). Una sola vez por cubierta: se vuelve a avisar recién después de una
  medición buena o de sacarla del vehículo. No bloquea el vehículo. El mínimo se configura:

```yaml
fleetguard:
  tires:
    min-tread-mm: 1.6
```

1,6 mm es el mínimo legal en Argentina para autos; para camiones conviene subirlo.

Ejemplo: "La cubierta Michelin X Multi N° 1234 (Delantera izquierda) de AB123CD tiene 1,4 mm de dibujo,
menos del mínimo de 1,6 mm. Lo midió chofer1."

## 6. Costo por km por marca

`GET /api/tires/brand-comparison`: por marca, costo total sobre km totales de sus cubiertas, montadas o
dadas de baja. Entran solo las que llevan al menos 1.000 km: una recién montada daría un costo por km
engañoso. Ordenado del más barato por km al más caro, con cantidad de cubiertas, dadas de baja y km
promedio por cubierta.

## 7. Base de datos ya creada

`ddl-auto: update` crea las tablas y la columna nuevas, pero no actualiza el `CHECK` del tipo de aviso:

```sql
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notification_settings DROP CONSTRAINT IF EXISTS notification_settings_type_check;
```

## Fuera de alcance

- El costo de las cubiertas en Gastos: una cubierta en stock todavía no es gasto de ningún vehículo.
- Recapados y cubiertas compradas usadas (con km previos).
- Pasar una cubierta de un vehículo a otro en un solo paso (se saca al stock y se monta en el otro).
- Presión de inflado.
