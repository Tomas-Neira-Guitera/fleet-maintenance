# CAM-60 — Técnico a cargo de una orden de trabajo

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

CAM-60 ("Dashboard de taller con órdenes de trabajo", épica CAM-59) necesita saber
**qué técnico está a cargo de cada OT**. Con eso queda armada la cadena
técnico → OTs → vehículos, que después va a usar la vista del técnico en
`TechnicianShell`. Hasta ahora `WorkOrder.assignee` era texto libre, sin relación
con los usuarios del sistema.

Esta primera tanda cubre solo el vínculo y asignarlo desde el panel de admin. La
vista "mis OTs" del técnico va en una tanda siguiente.

## Decisiones

### 1. Campo nuevo `technicianId`, no reemplazar `assignee`
`work_orders.technician_id` (UUID, nullable) apunta a `users.id`. Es una columna UUID
suelta, mismo criterio que `vehicle_id`: sin `@ManyToOne`.

`assignee` sigue existiendo como texto libre, por dos motivos:
- En una OT **externa** no hay usuario del sistema a quien asignar. Ahí `assignee`
  pasa a ser el contacto en el proveedor.
- Las OTs ya cargadas tienen `assignee` con texto. Así no hay que migrar nada.

### 2. Solo técnicos, y solo en OTs internas
- `technicianId` tiene que ser un usuario con rol `TECNICO`. Si no existe, no es
  técnico o el id está malformado, se responde 422 con `details[].field = "technicianId"`.
- Solo vale para `executionType = interno`. Mandarlo en una OT externa es 422.
- Si una OT interna con técnico pasa a `externo` (PATCH), el técnico se desasigna
  solo: no se rechaza el cambio.
- Al revés, si una OT externa pasa a `interno`, se limpian `assignee` y
  `externalProvider`: el contacto del proveedor no debe quedar mostrándose como
  responsable de una OT interna.

### 3. Semántica del PATCH
Igual que el resto de los campos parciales del PATCH: si `technicianId` no viene,
no se toca. Además:
- `"technicianId": "<id>"` asigna (o reasigna).
- `"technicianId": ""` desasigna.

### 4. `technicianUsername` en la respuesta
`WorkOrder` devuelve `technicianId` + `technicianUsername`, resuelto por el servidor,
para que el frontend lo muestre sin una segunda llamada. Mismo criterio que `plate`.

### 4b. `defect` en la respuesta
Si la OT viene de un defecto (`defectId` no nulo, directo o vía una programación de
origen defecto), `WorkOrder.defect` trae el defecto con la misma forma que
`GET /api/defects` (`DefectSummary`): gravedad, descripción, foto, quién lo reportó y
cuándo. Así el técnico ve el contexto (y la lista se ordena por gravedad) sin una
segunda llamada por OT.

`defect` puede venir `null` aunque `defectId` no lo sea: `defectId` es un UUID suelto,
sin FK, y si el defecto no se encuentra el servidor no falla. El cliente pregunta por
`defect`, no deduce su presencia de `defectId`.

### 5. Filtro `GET /api/work-orders?technicianId=`
Es el vínculo técnico → vehículos: cada OT trae `vehicleId`/`plate`. Un id que no
matchea ninguna OT, incluido uno malformado, devuelve `items: []`, no un error. Un
`technicianId` vacío se ignora, como los otros filtros.

### 5b. Una sola OT abierta por origen
Replanificar desde el calendario actualiza la programación existente (upsert de CAM-42)
y antes creaba una OT nueva cada vez: el técnico veía el mismo trabajo repetido. Ahora
`POST /api/work-orders` no crea otra si ya hay una OT **abierta** (asignada o en proceso)
para el mismo origen de fondo, y devuelve la existente con `200` en vez de `201` (mismo
criterio que el upsert del calendario):

- el mismo **defecto**, directo o vía una programación de origen defecto;
- la misma **asignación de plan**: cancelar una programación en el calendario y volver a
  planificar crea otra programación, pero sigue siendo el mismo trabajo;
- la misma **programación**, si es manual (sin defecto ni plan detrás).

Si hay más de una abierta (duplicados de antes de este cambio), se reusa la más antigua.

Los datos de la OT reusada (técnico, tipo de ejecución, descripción) **no** se pisan con
lo que venga en el body: el formulario de planificar arranca siempre vacío, y pisarlos
cambiaría sin querer una OT que el admin ya había configurado. El frontend distingue el
`200` y le avisa al admin que se mantuvo la OT existente. Lo único que se actualiza es
`scheduledMaintenanceId`, para que apunte a la programación vigente (la que se cierra al
finalizar).

Una vez finalizada o cancelada, sí se puede abrir otra. Las OTs manuales no tienen
origen y siempre crean una nueva.

Limitación conocida: es un chequeo en la aplicación, sin constraint único en la base
(`ddl-auto` no genera índices parciales). Dos pedidos exactamente simultáneos podrían
crear dos OTs; en la UI el botón se deshabilita mientras envía.

### 6. `GET /api/users?role=` es de solo lectura
Existe únicamente para poblar el selector de técnico. Devuelve `id`, `username`, `role`
(nunca el hash). `role` es obligatorio (422 si falta): como ningún endpoint valida el
JWT todavía, no se expone el listado completo de usuarios. Rol inexistente → 422.

**No** es gestión de usuarios: alta, registro según rol, invitaciones y edición quedan
para CAM-23 / el próximo sprint.

## Fuera de alcance
- Autorización por rol: igual que el resto de la API, el backend todavía no valida el
  JWT en estos endpoints.
- Restringir que solo el técnico asignado pueda avanzar su OT.
- La vista del técnico en el frontend.
