# CAM-31 — Checklist DVIR configurable por vehículo

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Hasta ahora el checklist era fijo para toda la flota (`ChecklistCatalog`). CAM-31 permite
que el admin agregue o quite ítems para un vehículo puntual (por ejemplo, un camión con
acoplado tiene controles que una camioneta no). Reemplaza la idea anterior de "ítems por
accesorios" (faja/traca/grúa/rampa), que se había dejado para más adelante: con ítems
libres por vehículo el admin carga esos mismos controles sin un catálogo de accesorios.

## Decisiones

### 1. Base − desactivados + extras, solo en pre-trip
- `ChecklistCatalog` sigue siendo el checklist base, definido en código.
- `vehicle_disabled_checklist_items(vehicle_id, base_item_id)`: ítems base que no aplican
  a ese vehículo.
- `vehicle_checklist_items(id, vehicle_id, label, type, section, active, created_at)`:
  ítems extra. `type` es `check` o `number`; `section` es `exterior` o `interior`.
- El checklist del chofer para un vehículo es base − desactivados + extras activos. Los
  extras van al final de su sección.
- **El post-trip no se configura**: sigue siendo la lista corta fija de CAM-11.

### 2. Ids de ítems extra
Un ítem extra se identifica como `extra-<uuid>`. El prefijo evita choques con los ids del
catálogo base (`ext-luces`, `int-km`, ...) y le permite al servidor saber de qué tabla sale
sin otra consulta.

### 3. Obligatorios no se quitan
Los ítems base con `required=true` (hoy `int-km`, kilómetros actuales) vuelven como
`locked: true` y un `PATCH { enabled: false }` sobre ellos responde `409 ITEM_LOCKED`.
Los ítems extra nunca son obligatorios: uno numérico se puede dejar vacío.

### 4. Validación contra el checklist del vehículo
`InspectionValidator` valida contra el checklist resuelto para ese vehículo, no contra el
catálogo global. Se mantiene la regla de CAM-11: un `itemId` que no está en ese checklist
se ignora (por ejemplo, un ítem que el admin quitó mientras el chofer tenía el formulario
abierto). Un ítem extra marcado con falla genera un defecto igual que un ítem base.

### 5. Historial
- Quitar un ítem extra es baja lógica (`active=false`), nunca un borrado.
- `inspection_answers.item_label` (nuevo, nullable) guarda el nombre del ítem al momento
  de responder. Así las inspecciones viejas mantienen el nombre aunque el checklist del
  vehículo cambie después. Las respuestas previas a CAM-31 quedan con `item_label` null:
  su `itemId` sigue resolviendo contra el catálogo base.

### 6. Endpoints
- `GET /api/vehicles/{vehicleId}/checklist?type=pre-trip|post-trip`: lo que ve el
  chofer (default `pre-trip`).
- `GET /api/vehicles/{vehicleId}/checklist-items`: configuración para el admin (todos los
  ítems base con `enabled` más los extras vigentes; `origin: base|extra`).
- `POST /api/vehicles/{vehicleId}/checklist-items`: agrega un extra (`label` de hasta 60
  caracteres, `type`, `section`) y devuelve 201.
- `PATCH /api/vehicles/{vehicleId}/checklist-items/{itemId}` con `{ "enabled": bool }`:
  quita o vuelve a activar un ítem, sea base o extra.

Errores: `404 VEHICLE_NOT_FOUND` / `CHECKLIST_ITEM_NOT_FOUND`, `409 ITEM_LOCKED`, `422`
con `details[].field` para `label`/`type`/`section`/`enabled`.

## Fuera de alcance

- Plantillas de checklist por tipo de vehículo (se puede evaluar si hay muchos vehículos
  parecidos).
- Configurar el post-trip.
- Editar el nombre de un ítem extra (se quita y se agrega otro).
- Protección por rol: igual que el resto de la API, el JWT todavía no se valida.
