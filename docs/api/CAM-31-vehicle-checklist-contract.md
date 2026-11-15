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
  ítems extra. `section` es `exterior` o `interior`; `active` indica si entra en el checklist.
  `type` es `check` salvo que se pida `number` por API: el panel de admin siempre crea `check`.
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

### 5. Quitar no es eliminar, e historial
Un ítem extra puede estar cargado en el vehículo sin formar parte del checklist del chofer:
- **Quitar / incluir** (`PATCH { enabled }`): el ítem sigue en el vehículo y en la configuración del
  admin, con `enabled=false` no aparece en la inspección. Se puede crear ya así con `enabled: false`.
- **Eliminar** (`DELETE`): borra el ítem extra del vehículo. Los ítems base no se eliminan
  (`409 BASE_ITEM_NOT_DELETABLE`), solo se quitan.
- `inspection_answers.item_label` (nuevo, nullable) guarda el nombre del ítem al momento de
  responder. Así las inspecciones viejas mantienen el nombre aunque el ítem se quite o se elimine
  después. Las respuestas previas a CAM-31 quedan con `item_label` null: su `itemId` sigue
  resolviendo contra el catálogo base.

### 6. Endpoints
- `GET /api/vehicles/{vehicleId}/checklist?type=pre-trip|post-trip`: lo que ve el
  chofer (default `pre-trip`).
- `GET /api/vehicles/{vehicleId}/checklist-items`: configuración para el admin (todos los
  ítems base y todos los extras, cada uno con `enabled`; `origin: base|extra`).
- `POST /api/vehicles/{vehicleId}/checklist-items`: agrega un extra (`label` de hasta 60
  caracteres, `section`, y opcionales `enabled` y `type`) y devuelve 201.
- `PATCH /api/vehicles/{vehicleId}/checklist-items/{itemId}` con `{ "enabled": bool }`:
  quita del checklist o vuelve a incluir un ítem, sea base o extra.
- `DELETE /api/vehicles/{vehicleId}/checklist-items/{itemId}`: elimina un ítem extra (204).

Errores: `404 VEHICLE_NOT_FOUND` / `CHECKLIST_ITEM_NOT_FOUND`, `409 ITEM_LOCKED` /
`BASE_ITEM_NOT_DELETABLE`, `422`
con `details[].field` para `label`/`type`/`section`/`enabled`.

### 7. Plantillas (CAM-150)

Una plantilla es un nombre y una lista de controles (`label`, `type`, `section`) que el admin
**copia** a varios vehículos. No queda vinculada: lo copiado son controles extra comunes del
vehículo, que se quitan o eliminan como cualquier otro. Editar o borrar la plantilla no cambia
los vehículos ni las inspecciones viejas. Se copia y no se vincula porque los defectos
recurrentes (CAM-135) se identifican por el id de cada control, y así editar una plantilla no
cambia de golpe el checklist de muchos vehículos.

- Cada control sigue las reglas de un extra (sección 6). Nombre de la plantilla obligatorio,
  de hasta 60 caracteres y único sin importar mayúsculas (`409 DUPLICATE_TEMPLATE_NAME`). De 1 a
  30 controles, sin repetir etiqueta y sección. Los errores de un control van como
  `items[i].label` / `items[i].type` / `items[i].section`.
- **Aplicar**: a cada vehículo se le agregan los controles que no tiene. Se saltea el que ya
  tiene con la misma etiqueta y sección, sin importar mayúsculas, acentos ni espacios de más,
  sea un ítem base o un extra (activo o quitado). Aplicar dos veces no duplica. Lo copiado
  entra activo.
- Un vehículo dado de baja no se puede elegir: `409 VEHICLE_INACTIVE` y no se copia nada a
  ningún vehículo del pedido. Un id inexistente da `404 VEHICLE_NOT_FOUND`. Hasta 200 vehículos
  por pedido; los ids repetidos cuentan una vez.
- **Alta con plantilla**: `POST /api/vehicles` acepta `checklistTemplateId` opcional. Se valida
  antes de crear el vehículo (`422` con `field: checklistTemplateId` si no existe) y se copia en
  la misma transacción del alta.

Endpoints (solo ADMIN; sin token `401`, otro rol `403`):
- `GET /api/checklist-templates` → `{ items: [ChecklistTemplate] }`, por nombre.
- `GET /api/checklist-templates/{id}`.
- `POST /api/checklist-templates` con `{ name, items: [{ label, type?, section }] }` → 201.
- `PUT /api/checklist-templates/{id}`: reemplaza nombre y controles.
- `DELETE /api/checklist-templates/{id}` → 204.
- `POST /api/checklist-templates/{id}/apply` con `{ vehicleIds: [...] }` →
  `{ templateId, added, skipped, vehicles: [{ vehicleId, plate, added, skipped, skippedLabels }] }`.

`404 CHECKLIST_TEMPLATE_NOT_FOUND` si la plantilla no existe.

## Fuera de alcance

- Desde una plantilla, quitar controles base, y sincronizar los vehículos cuando cambia (CAM-150).
- Configurar el post-trip.
- Editar el nombre de un ítem extra (se elimina y se agrega otro).
- Protección por rol del checklist de cada vehículo: queda para CAM-73. Las plantillas
  (CAM-150) sí piden ADMIN.
