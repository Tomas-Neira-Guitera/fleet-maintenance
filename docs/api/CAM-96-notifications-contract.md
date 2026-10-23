# CAM-96 — Avisos in-app (campanita)

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Cada rol tiene algo que le importa enterarse sin estar mirando la pantalla
correcta (épica CAM-95): el admin, un defecto bloqueante; el técnico, que le
asignaron o le sacaron una OT; el chofer, que se olvidó de cerrar el viaje.
CAM-96 es la base que usan todos esos avisos: dónde se guardan, cómo los lee
cada usuario y la campanita del header. Cada aviso concreto es su propia card
(CAM-97 a CAM-103).

## Estado: qué está hecho y qué falta

La card se repartió en dos partes, para que los avisos concretos no esperen a
la base:

| Parte | Estado |
|---|---|
| Contrato (`/api/notifications`, schema `Notification`) | Hecho, en `openapi.yaml` |
| `NotificationType` y la interfaz `NotificationService` | Hecho |
| Avisos que la usan: CAM-99, CAM-100, CAM-101 | Hechos, contra la interfaz |
| Campanita en el front (los tres roles) | Hecha, contra el contrato |
| **Modelo de datos y endpoints** | **Falta** (ver abajo) |

Mientras falte, `LoggingNotificationService` implementa la interfaz escribiendo
en el log ("Aviso work_order_assigned para el usuario ...") y la campanita
muestra "No se pudieron cargar los avisos", porque `/api/notifications`
responde 404.

### Lo que falta

1. **Entidad `Notification`** (tabla `notifications`): `id`, `recipientId`
   (usuario), `type` (`NotificationType`, `@Enumerated(STRING)`), `title`,
   `message`, `link` (nullable), `createdAt`, `readAt` (nullable).
   Conviene un índice por `(recipientId, readAt)`: el conteo de sin leer se
   pide cada 60 segundos por cada usuario conectado.
2. **`NotificationRepository`**.
3. **Una implementación de `NotificationService` que guarde en la base**, y
   borrar `LoggingNotificationService` (si quedan las dos, Spring no sabe cuál
   inyectar y la app no levanta). `notifyAllAdmins` crea un aviso por cada
   usuario ADMIN activo.
4. **`NotificationController`** con los cuatro endpoints del contrato, con el
   usuario del `AuthenticatedUser` (nunca un id que venga en el pedido).
5. **Registrar `/notifications`, `/notifications/**` en `WebConfig`** con los
   tres roles: `new JwtAuthInterceptor(jwtService, userRepository, Role.ADMIN,
   Role.CHOFER, Role.TECNICO)`.
6. Tests del servicio y del controller (el CI pide 85% de líneas).

Nada de lo que ya está hecho tiene que cambiar.

## Decisiones

### 1. Los avisos nacen de eventos de dominio
Como pide la épica: el servicio que hace el cambio (por ejemplo
`WorkOrderService`) publica un evento con `ApplicationEventPublisher`, y un
listener con `@TransactionalEventListener` (AFTER_COMMIT) decide a quién avisar
y llama a `NotificationService`. Así la lógica de negocio no sabe nada de
avisos, y si la operación falla y se deshace no queda un aviso de algo que no
pasó.

La excepción es CAM-101: el recordatorio no nace de un cambio sino del paso del
tiempo, así que lo pide una tarea programada (`@Scheduled`) directo.

### 2. Los listeners abren su propia transacción
En AFTER_COMMIT la transacción del pedido ya se confirmó. Si la implementación
de `NotificationService` se sumara a ella (`@Transactional` por defecto, REQUIRED),
el `save` del aviso no se escribiría nunca, sin ningún error. Por eso los
listeners llevan `@Transactional(propagation = REQUIRES_NEW)`. La
implementación real puede llevar `@Transactional` común.

### 3. Un aviso que falla no rompe la operación
En AFTER_COMMIT una excepción del listener le llega al pedido como un 500,
aunque el cambio ya esté guardado. Los listeners atrapan cualquier error, lo
dejan en el log y siguen: perder un aviso es mejor que hacerle creer al admin
que no se asignó una OT que sí se asignó.

### 4. El texto lo arma el backend
`title` y `message` llegan listos para mostrar. El front no interpreta `type`
para armar nada: así un tipo nuevo (CAM-97, CAM-98...) no necesita cambios en
el front.

### 5. `link` es una ruta del front
Para el admin, una ruta de su vista (`/vehiculos/{id}`). Chofer y técnico
viven en `/` (sus flujos no están en la URL, ver `frontend-AGENTS.md`), así
que sus avisos llevan `/`.

### 6. El aviso de otro usuario es 404
Marcar como leído un aviso ajeno responde lo mismo que uno que no existe,
para no confirmar que el id existe.

### 7. Sin paginación ni borrado
`GET /api/notifications` trae los últimos `limit` (30 por defecto). Los avisos
viejos no se borran todavía.

## Fuera de alcance

Email (CAM-19), push (CAM-119), preferencias por usuario (CAM-24), WebSockets
(la campanita refresca cada 60 segundos).
