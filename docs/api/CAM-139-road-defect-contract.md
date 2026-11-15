# CAM-139 — Reportar un defecto durante el viaje

Con un viaje abierto, el chofer reporta un problema del vehículo en el momento en que aparece, sin
esperar al post-viaje. El admin lo ve enseguida en Defectos, marcado "Reportado en viaje", y si es
bloqueante le llega el aviso de CAM-97 y el vehículo queda no disponible. El viaje sigue abierto.

## Qué cambia en la API

| Dónde | Qué |
|---|---|
| `POST /api/vehicles/{id}/road-defects` | Nuevo, solo CHOFER. Recibe `RoadDefectRequest` y devuelve el defecto (`DefectSummary`, 201) |
| `GET /api/defects`, `GET /api/vehicles/{id}/history` | `defects[].source`: `pre-trip`, `post-trip` u `on-road` |
| Aviso `blocking_defect_reported` | Con un bloqueante en viaje, el texto dice "Reportado durante el viaje" |

```json
POST /api/vehicles/3f0c.../road-defects
{
  "itemId": "ext-luces",
  "defect": {
    "severity": "blocking",
    "description": "Sin luz de freno",
    "details": "Se apagó de golpe en la ruta 2.",
    "photoUrl": "http://localhost:8080/api/photos/9a1b..."
  }
}
```

## Decisiones

### 1. Se guarda como una inspección `ON_ROAD`

`Defect.inspectionAnswer` es obligatorio, y todo lo que necesita saber de qué vehículo, chofer o viaje
es un defecto (disponibilidad, tiempo fuera de servicio, recurrentes, OTs, calendario, listados) llega
por ahí. El reporte se guarda como una inspección de un tipo nuevo, `ON_ROAD`, dentro del viaje
abierto, con una sola respuesta: la del control que falla, con su defecto. Así nada de eso cambia.

Una inspección `ON_ROAD` no tiene km (`odometerKm: null`), no tiene notas y no cierra el viaje. Como no
es una inspección para el chofer:

- `POST /api/inspections/{vehicleId}` no acepta `type: on-road` (sigue siendo pre-trip o post-trip);
- no cuenta en la actividad del chofer (CAM-91, `GET /api/users/stats`);
- no aparece en `inspections` del historial del vehículo ni en el impreso (CAM-130); su defecto sí, en
  `defects`.

Las lecturas de km (CAM-90, CAM-137, CAM-140, CAM-152) ya ignoran las inspecciones sin km.

### 2. Qué falla: un control del checklist, u "Otro problema"

`itemId` es uno de los controles de tipo sí/no del pre-viaje del vehículo, con los suyos propios
(CAM-31) y sin los desactivados: los mismos ids, así que una falla de luces en viaje se suma a las del
pre-viaje en los defectos recurrentes (CAM-135). El frontend arma la lista con
`GET /api/vehicles/{vehicleId}/checklist` (pre-trip), sin el de km.

Si no es ninguno, `road-otro` ("Otro problema"). Ese no cuenta para recurrentes: no es un control, y
dos problemas distintos no son una falla que se repite: `RecurringDefectService` lo deja afuera del
listado y del aviso. Un `itemId` que no es ninguno de esos da 422
con el detalle en `itemId`.

### 3. Las reglas del defecto son las del checklist

Se validan con el mismo `InspectionValidator`: gravedad `blocking` o `non-blocking`, título obligatorio
de hasta 30 caracteres (CAM-79), detalle opcional de hasta 1.000 (CAM-32) y foto obligatoria si es
bloqueante. Los errores vuelven en `details[]` con el `itemId` del control, igual que en
el checklist.

### 4. Quién puede reportar

Solo el chofer que abrió el viaje (`Trip.openedById`). Sin viaje abierto, 409 `NO_OPEN_TRIP`: el
problema se reporta en la inspección, como siempre. Con el viaje de otro chofer, o con uno abierto
antes de CAM-87 (sin `openedById`), 409 `NOT_YOUR_TRIP`. La ruta exige el JWT de un CHOFER desde el
principio (`WebConfig`), aunque el resto de `/api/vehicles` todavía no lo pida (CAM-73).

### 5. Un bloqueante en viaje

Hace lo mismo que uno del checklist: avisa al admin (CAM-97, con el texto "Reportado durante el viaje
por …; el viaje sigue abierto, y el vehículo no puede volver a salir hasta resolverlo"), y registra
desde cuándo el vehículo no está disponible (CAM-102/103). El viaje sigue abierto y el chofer lo
cierra con el post-viaje: un post-viaje nunca se rechaza por un defecto bloqueante (CAM-89).

Un defecto no bloqueante no avisa, igual que en el checklist.

El tiempo fuera de servicio (CAM-134) se cuenta desde que se reporta el bloqueante, aunque el vehículo
siga en la ruta hasta el post-viaje. Es coherente con "queda no disponible", pero suma esas horas
de viaje. Queda junto a la decisión abierta de CAM-134 sobre el tiempo en espera de CAM-148.

### Límite conocido: reporte y post-viaje al mismo tiempo

El viaje abierto se lee sin bloquearlo. Si el reporte y el post-viaje llegan casi juntos (dos pestañas,
un reintento), el reporte puede quedar dentro de un viaje que se acaba de cerrar. No cambia ninguna
cuenta, porque no tiene km y no es una inspección del chofer, así que no se agregó un lock.

### 6. `source` y la marca de CAM-146

`source` dice dónde se reportó el defecto: `pre-trip`, `post-trip` u `on-road`. La marca "Reportado
en viaje" sale de `on-road`. Es otra cosa que `occurredOnTrip` de CAM-146 ("Ocurrió en el viaje"), que
se calcula comparando el post-viaje con el pre-viaje: esa sigue siendo solo de los defectos del
post-viaje. Si algún día un defecto tuviera las dos, se muestra solo "Reportado en viaje".

## Base de datos

Sin tablas ni columnas nuevas. `ddl-auto` no actualiza el CHECK que Hibernate arma para
`inspections.type` en una base que ya existe, así que guardar un `ON_ROAD` da 500 hasta borrarlo:

```sql
ALTER TABLE inspections DROP CONSTRAINT IF EXISTS inspections_type_check;
```

`seed-demo.sql` ya lo hace.
