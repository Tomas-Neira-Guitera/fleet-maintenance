# CAM-11 — Contrato de API para la carga de inspecciones DVIR

Este documento acompaña a `openapi.yaml` (mismo directorio) con las decisiones y reglas
de negocio que el spec OpenAPI no puede expresar bien por sí solo. Referencia: historia
CAM-11 en Jira, checklist refinado v3, y el frontend ya implementado en `fleet-maintenance-fe`
(`src/types/domain.ts`, `src/services/*`) — este contrato está pensado para que el día que
se conecte la API real, el reemplazo de la capa mock del frontend sea mínimo.

## Endpoints (resumen)

| Método | Endpoint | Qué hace |
|---|---|---|
| GET | `/api/vehicles` | Lista de vehículos con estado (disponible/en viaje) |
| POST | `/api/photos` | Sube una foto de defecto, devuelve una URL (paso 1 de 2) |
| GET | `/api/photos/{photoId}` | Sirve el binario de una foto subida (a lo que apunta la photoUrl) |
| POST | `/api/inspections/{vehicleId}` | Envía una inspección pre-trip o post-trip (paso 2) |
| GET | `/api/defects` | Lista de defectos reportados, para mantenimiento (CAM-13) |

### División de recursos

`/api/vehicles` y `/api/inspections` quedan como recursos separados, no uno anidado
dentro del otro:

- **`/api/vehicles`** es la entidad vehículo — CAM-11 solo necesita `GET /api/vehicles`
  (lista con estado), pero el recurso queda reservado para el resto del
  CRUD y operaciones de flota que va a definir la historia de gestión de flota (alta,
  baja, edición, ficha completa, etc.) sin que eso choque con esto.
- **`/api/inspections`** concentra todo lo relacionado a crear (y, más adelante,
  consultar) inspecciones. Toma `{vehicleId}` como parámetro de path porque toda
  inspección pertenece a un vehículo, pero no vive colgada de `/api/vehicles` — así
  el recurso de inspecciones puede crecer con sus propios endpoints (por ejemplo, a
  futuro, `GET /api/inspections/{vehicleId}` para el historial de un vehículo, o
  `GET /api/inspections/{id}` para el detalle de una) sin mezclarse con el CRUD de
  vehículos ni depender de su forma.

## Decisiones clave

### 1. Identidad y hora del chofer: nunca confiar en el body
El chofer y el timestamp de la inspección **no se toman del JSON que manda el cliente**.
El chofer sale del token de autenticación (`Authorization: Bearer <token>`); el timestamp
lo pone el reloj del servidor al recibir el POST. Esto evita que alguien pueda enviar una
inspección "como" otro chofer, o con una fecha/hora manipulada — algo que sí sería posible
si confiáramos en lo que manda la app. La app mobile del chofer no necesita mandar ninguno
de los dos campos.

### 2. El servidor es dueño de la definición del checklist
El cliente manda solo `itemId` + lo que el chofer completó (`outcome`, `numberValue`,
`defect`). El servidor resuelve label/sección/tipo/obligatoriedad de cada ítem contra su
propio catálogo (una lista fija; la expansión por accesorios del vehículo quedó
fuera de alcance de esta historia, ver sección "Fuera de alcance").
Esto es lo que permite, más adelante, cambiar o agregar ítems del checklist sin tocar el
frontend ni romper inspecciones ya enviadas con la definición vieja.

### 3. Fotos: subida en dos pasos
1. El chofer saca la foto → `POST /api/photos` (multipart) → devuelve `photoUrl`.
2. Esa URL se referencia en `defect.photoUrl` dentro del envío final de la inspección,
   que es JSON liviano (sin binarios).

Se eligió este enfoque en vez de un único POST multipart con todo junto porque el chofer
está en movimiento, con conexión inestable — subir la foto apenas se saca, y poder
reintentar solo esa subida sin perder el resto del formulario completado, es más robusto
que perder toda la inspección si falla la subida de una imagen pesada.

### 4. Reglas de validación (422)
Espejo de los criterios de aceptación de CAM-11:

- Si no hay ningún defecto reportado, no se exige ningún campo adicional.
- `defect.severity = "blocking"` → `description` y `photoUrl` son obligatorios.
- `defect.severity = "non-blocking"` → `description` obligatoria, `photoUrl` opcional.
- Todo ítem marcado `required` en el catálogo del servidor (kilómetros, ítems base) debe
  tener respuesta; si falta, se devuelve en `details` con su `itemId`.
- `numberValue` de kilómetros no actualiza el odómetro oficial del vehículo — queda
  únicamente en el registro de la inspección (decisión de producto ya tomada en CAM-11 v2).

### 5. Reglas de estado del vehículo/viaje (409)
- `type: "pre-trip"` sobre un vehículo con viaje abierto → `409 VEHICLE_ON_TRIP`.
- `type: "post-trip"` sobre un vehículo sin viaje abierto → `409 NO_OPEN_TRIP`.
- Un pre-trip exitoso abre un `Trip` (`status: open`) y lo asocia al vehículo.
- Un post-trip exitoso cierra ese `Trip` (`status: closed`, `endedAt` seteado) y el
  vehículo vuelve a `available`.

### 6. Formato de error uniforme
Todas las respuestas de error devuelven `{ error: "<CÓDIGO>", message: "<texto>" }`,
y las de validación (422) suman `details: [{ itemId, message }]`. Mantener esto consistente
en todos los endpoints del backend, no solo estos tres, para que el frontend pueda manejar
errores de forma genérica.

### 7. Defectos como dato consumido por mantenimiento (CAM-13)
Cada respuesta con `outcome: "defect"` genera del lado del servidor un registro de
"defecto" (tabla `defects`, relacionada 1 a 1 con `inspection_answers`). `GET /api/defects`
expone ese listado para el equipo de mantenimiento, ordenado por severidad (blocking
primero) y luego por fecha más reciente. Usa la misma taxonomía de dos niveles
(`blocking`/`non-blocking`) que las reglas de validación de la sección 4 — no la de tres
niveles (bajo/medio/alto) que se evaluó y se descartó para no romper esas reglas.

## Sugerencia de esquema de datos (Postgres, orientativo)

No es parte formal del contrato — el backend ya lo implementa vía JPA/Hibernate
(`ddl-auto: update`) sobre este mismo modelo:

```sql
vehicles(id, plate, brand, model)
trips(id, vehicle_id, status, started_at, ended_at)
inspections(id, trip_id, vehicle_id, driver_id, type, "timestamp", odometer_km, notes, has_blocking_defect)
inspection_answers(id, inspection_id, item_id, outcome, number_value)
defects(id, inspection_answer_id, severity, description, photo_url, created_at, status)
```

## Fuera de alcance de este contrato

- Login/roles y el mecanismo real detrás de `Authorization: Bearer` (historia aparte).
- Órdenes de trabajo de mantenimiento y todo lo que consuma `GET /api/defects` más allá de listarlo (feature 5.3).
- Checklist configurable por accesorios del vehículo (ítems extra por faja/traca/grúa/
  rampa): se evaluó para esta historia pero se decidió diferirlo a una historia futura;
  el checklist queda con la lista fija únicamente por ahora.
