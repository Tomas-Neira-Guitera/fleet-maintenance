# CAM-22 — Detalle e historial de vehículo

Decisiones que no entran en `openapi.yaml`.

- **Dos endpoints, no uno.** `GET /api/vehicles/{id}` devuelve la ficha (mismo
  shape que un item de `GET /api/vehicles`); `GET /api/vehicles/{id}/history`
  devuelve las tres listas juntas, porque la pantalla de detalle las pide a la vez.
- **Sin paginación** en el historial (decisión abierta del proyecto). Si un
  vehículo acumula mucho, se agrega después.
- **Orden:** inspecciones por `timestamp` desc; defectos por `createdAt` desc;
  mantenimientos por `completedAt` desc.
- **Defectos:** mismo shape que `GET /api/defects` (`DefectSummary`), filtrado
  por vehículo. Hoy todos son `status: "open"`.
- **Mantenimientos = `MaintenanceCompletion`.** No existen órdenes de trabajo;
  `workOrderId` es texto libre. `planName` sale del plan de la asignación.
- **Vehículos dados de baja** también tienen detalle e historial (baja lógica,
  no se borra nada).
- Los dos endpoints devuelven `404 VEHICLE_NOT_FOUND` si el id no existe.
