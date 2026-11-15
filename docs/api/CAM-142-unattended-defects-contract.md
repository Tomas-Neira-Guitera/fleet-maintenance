# CAM-142 — Defectos abiertos sin atender

Decisiones que no entran en `openapi.yaml`. El tipo de aviso (`defect_unattended`) y el campo
`unattendedDays` de `DefectSummary` están documentados ahí.

## 1. Qué es "sin atender"

Un defecto está sin atender si se cumplen las tres cosas:

- está abierto (`status: open`);
- lleva **más de** `fleetguard.unattended-defects.days` días (7) desde que se reportó, contados en días de
  calendario de Argentina: uno reportado el 04/11 está sin atender el 12/11, y uno del 05/11 todavía no;
- no tiene una OT abierta (**asignada, en proceso o en espera**) ni un turno **programado**.

Una OT **en espera** (CAM-148) cuenta como atención: sigue abierta y alguien la tiene a cargo. Una OT o un
turno cancelados no cuentan, así que el defecto vuelve a estar sin atender.

La regla vive en un solo lugar, `UnattendedDefects`, y la usan el listado y la tarea diaria. Para todo el
listado son dos consultas (los defectos con OT abierta y los con turno programado), sin una por defecto.

## 2. `unattendedDays`

Los días desde que se reportó, solo si está sin atender; si no, `null`. Lo calcula `GET /api/defects`; el
resto de las respuestas que traen defectos (historial del vehículo, reporte en viaje) lo mandan `null`.

## 3. El aviso

- Tarea diaria a las 08:10 de Argentina (`fleetguard.unattended-defects.cron`).
- **Un aviso por vehículo**, con los defectos sin atender que todavía no se avisaron, del más viejo al más
  nuevo. Con uno: "«Luz de freno» lleva 9 días sin OT ni turno." Con varios: "3 defectos llevan más de 7
  días sin OT ni turno: Luz de freno (2 veces); Espejo roto." Los del mismo nombre (el mismo control
  reportado en varias inspecciones) se juntan con la cantidad.
- Un turno programado cuenta como atención aunque su fecha ya haya pasado sin hacerse.
- Lleva al detalle del vehículo (`/vehiculos/{id}`).
- Un vehículo dado de baja no avisa.

## 4. Una sola vez por defecto

Cada defecto avisado se anota en `unattended_defect_alerts` (`defect_id` único), en la misma transacción que
el aviso: si el aviso falla, no queda anotado y se reintenta al otro día. Si al defecto se le abre una OT, se
cancela y vuelve a quedar sin atender, la marca reaparece en Defectos pero **no se vuelve a avisar**.

Con el aviso apagado en Notificaciones no se manda ni se anota nada: si después se prende, avisa lo que esté
pendiente. La marca en Defectos se ve igual.

## 5. Pendiente de confirmar

- Bloqueantes y no bloqueantes tienen el mismo plazo. Un bloqueante ya avisa al reportarse (CAM-97) y frena
  al vehículo, así que no se le dio uno más corto.
- No se vuelve a avisar pasado un tiempo, para no volverse ruido.
