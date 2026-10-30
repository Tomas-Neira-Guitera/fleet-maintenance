# CAM-135 — Defectos recurrentes

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Cada defecto nace de una respuesta del checklist, así que se sabe qué control
falló. Pero cada defecto se ve suelto: si los frenos de un vehículo fallan tres
veces en un mes y se reparan cada vez, nada lo marca. Esta card avisa al admin
cuando el mismo control falla seguido en el mismo vehículo y lo muestra en el
detalle del vehículo y en el Resumen, para detectar reparaciones que no
resuelven el problema de fondo.

## Endpoint y aviso

| Qué | Dónde |
|---|---|
| Lectura | `GET /api/defects/recurring` (todos) y `?vehicleId=` (un vehículo), solo ADMIN |
| Aviso | `recurring_defect`, configurable como el resto (por defecto, a los admins por campanita y push) |

## Decisiones

### 1. La regla
Un control es recurrente cuando el mismo `itemId` del checklist falla en el mismo
vehículo **3 veces en los últimos 30 días**. Los dos números están en
`application.yml` (`fleetguard.recurring-defects.threshold` y `window-days`). La
ventana es móvil: se cuenta hacia atrás desde el momento en que se mira (o se
reporta), no por mes calendario.

### 2. Qué defectos cuentan
**Todos**: bloqueantes y no bloqueantes, abiertos y resueltos. Lo que importa es
que el mismo control vuelva a fallar, sea cual sea la gravedad. Que estén
resueltos es justamente el caso que se quiere ver: se arregló y volvió a fallar.
La card dejaba abierto si un defecto reabierto cuenta como una falla más; hoy un
defecto no se puede reabrir, así que no aplica.

### 3. Se identifica por `itemId`, no por el nombre
El nombre de un control puede cambiar (los ítems propios del vehículo se editan,
CAM-31), pero el id no. El nombre que se muestra es el que tenía en la falla más
reciente.

### 4. Cuándo se avisa, y una sola vez por ventana
Al guardar una inspección con defectos, después del commit, se revisa cada control
que falló. Si llegó al umbral y no se avisó ese control de ese vehículo en los
últimos 30 días, se avisa y se anota en `recurring_defect_alerts`. Por eso un
cuarto defecto dentro de la misma ventana no repite el aviso, y uno que llega
pasados los 30 días del aviso anterior sí vuelve a avisar, si vuelve a haber 3
fallas en la ventana. La marca y el aviso van en la misma transacción (igual que
el recordatorio de viaje de CAM-101): si el aviso falla, la marca se deshace y la
inspección queda guardada igual.

Dos costos aceptados:

- **El aviso apagado también marca.** Si el admin desactivó `recurring_defect` o
  no tiene destinatarios, la marca se guarda igual: al reactivarlo, ese control no
  avisa hasta que pasen 30 días del momento en que hubiera avisado. Lo ve igual en
  el detalle del vehículo y el Resumen (§5).
- **Dos inspecciones casi simultáneas pueden avisar dos veces.** Si dos inspecciones
  del mismo vehículo con el mismo control fallado se confirman a la vez, las dos
  pueden ver que no hay marca y avisar. Con una ventana móvil no hay una clave única
  simple que lo impida, y es improbable: un vehículo lo inspecciona un chofer a la vez.

### 5. La lectura no depende del aviso
`GET /api/defects/recurring` recalcula la regla sobre los defectos de la ventana,
sin mirar `recurring_defect_alerts`. Así, si el aviso estaba desactivado o falló,
el detalle del vehículo y el Resumen lo muestran igual.
