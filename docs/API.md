# Contrato de API — FleetGuard

Fuente de verdad de qué endpoints existen y qué devuelven. **El backend no
expone nada que no esté acá o en `docs/api/openapi.yaml`, y el frontend no
consume nada que no esté documentado en alguno de los dos.**

Base URL en desarrollo: `http://localhost:8080`
El frontend la toma de `VITE_API_BASE_URL`.

Convenciones: todas las rutas cuelgan de `/api` (centralizado en
`server.servlet.context-path`, ver `application.yml` — los controllers mapean
rutas planas). Respuestas siempre JSON con `Content-Type: application/json`.
CORS restringido por config a `http://localhost:5173` /
`http://127.0.0.1:5173` (`fleetguard.cors.allowed-origins`) — ya no es el
`Access-Control-Allow-Origin: *` sin restricción de antes.

---

## ⚠️ Dos fuentes de contrato conviven hoy

Desde la migración a Spring Boot (2026-08-31, ver `docs/PROJECT.md`), CAM-11
documentó su contrato en `docs/api/openapi.yaml` +
`docs/api/CAM-11-dvir-contract.md` en vez de en este archivo. Ese es hoy **el
contrato completo y más detallado** para vehículos, fotos, inspecciones y
defectos — léanlo ahí. Este `API.md` queda como índice de qué existe y dónde,
más lo que todavía no tiene otro lugar. Pendiente decidir (anotado en
`STATE.md`) si esto se consolida en un solo lugar antes de que se desalinee.

---

## Endpoints implementados

Documentados en detalle en
[`docs/api/openapi.yaml`](api/openapi.yaml) y
[`docs/api/CAM-11-dvir-contract.md`](api/CAM-11-dvir-contract.md) (CAM-11):

| Método | Endpoint | Qué hace |
|---|---|---|
| `GET` | `/api/vehicles` | Lista de vehículos con estado (disponible/en viaje) |
| `POST` | `/api/photos` | Sube la foto de un defecto (multipart), devuelve `photoUrl` |
| `GET` | `/api/photos/{id}` | Sirve la foto subida |
| `POST` | `/api/inspections/{vehicleId}` | Envía una inspección pre-trip o post-trip |
| `GET` | `/api/defects` | Listado de defectos, para mantenimiento ([CAM-13](https://fleet-maintenance.atlassian.net/browse/CAM-13)) |
| `POST` | `/api/auth/login` | Autentica usuario y contraseña, devuelve un JWT + rol ([CAM-43](https://fleet-maintenance.atlassian.net/browse/CAM-43)) |

Todos (salvo `/api/defects` y `/api/auth/login`) requieren, además del `Authorization: Bearer
<token>` que pide el contrato (todavía no hay auth real), un header
**temporal** `X-Driver-Id` que hace de stand-in del chofer — ver
`auth/DriverResolver` y `docs/api/CAM-11-dvir-contract.md`.

### `GET /api/defects` — forma de la respuesta

Reemplaza al viejo `GET /api/defectos` (contrato en español, tabla
independiente) — descartado en el merge de CAM-11 a `feature/guido`
(2026-08-31). Un defecto ahora nace **siempre** de una respuesta de inspección
(relación 1 a 1 con `inspection_answers`), no es una entidad suelta.

**200 OK**
```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "severity": "blocking",
    "description": "Pastillas de freno gastadas",
    "photoUrl": "/api/photos/...",
    "createdAt": "2026-08-28T14:32:07.481Z",
    "vehiclePlate": "AB123CD",
    "status": "open"
  }
]
```

| Campo | Tipo | Qué es |
|---|---|---|
| `id` | string (UUID) | identificador del defecto |
| `severity` | string | enum: `"blocking"` \| `"non-blocking"` — **dos niveles**, no los tres (`bajo`/`medio`/`alto`) del contrato viejo; se evaluó esa taxonomía y se descartó para no romper las reglas de validación de CAM-11 |
| `description` | string | descripción libre del defecto |
| `photoUrl` | string | URL de la foto asociada |
| `createdAt` | string | fecha de reporte, ISO-8601 UTC |
| `vehiclePlate` | string | patente del vehículo asociado |
| `status` | string | siempre `"open"` por ahora — reservado para la futura gestión de defectos |

Orden: severidad descendente (`blocking` primero), luego fecha descendente.
Sin paginación ni autenticación por ahora.

Implementado en `DefectController` → `DefectService` → `DefectRepository`.

### `POST /api/auth/login` — forma de la petición y la respuesta

Backend de [CAM-43](https://fleet-maintenance.atlassian.net/browse/CAM-43).
Solo autentica y emite el token — no incluye invitación ni alta de usuarios
(eso es [CAM-23](https://fleet-maintenance.atlassian.net/browse/CAM-23), a
futuro). Los usuarios de prueba se cargan a mano con
`docs/db/seed-users.sql`, no hay pantalla de alta todavía.

**Body**
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**200 OK**
```json
{
  "token": "<JWT firmado, HS256>",
  "role": "ADMIN"
}
```

| Campo | Tipo | Qué es |
|---|---|---|
| `token` | string | JWT firmado (HS512, según el largo de `fleetguard.jwt.secret`) con `fleetguard.jwt.secret`, vence a los `fleetguard.jwt.expiration-minutes` (60 min por defecto). Incluye `sub` (id de usuario), `username` y `role` como claims. |
| `role` | string | enum: `"ADMIN"` \| `"CHOFER"` |

**401 Unauthorized** — usuario inexistente, contraseña incorrecta, o
credenciales vacías (mismo error para los tres casos, a propósito, para no
revelar si el usuario existe):
```json
{ "error": "INVALID_CREDENTIALS", "message": "Usuario o contraseña incorrectos." }
```

Todavía no reemplaza al header temporal `X-Driver-Id` de CAM-11 — conviven
hasta que el frontend (CAM-45) esté migrado.

Implementado en `AuthController` → `AuthService` → `UserRepository`, con
`JwtService` para emitir el token y `PasswordEncoder` (BCrypt) para verificar
la contraseña.

### `GET /api/health` — sin equivalente todavía

El chequeo de conexión a Postgres que existía en el backend viejo
(`HealthController`, `HttpServer`) no tiene reemplazo en Spring Boot. Evaluar
Spring Actuator (`/actuator/health`) — anotado en `STATE.md`.

## Por definir

Cada uno de estos se documenta acá **en el mismo cambio** en que se implementa,
nunca después:

- Flota — alta, baja, edición, ficha completa (más allá del listado de CAM-11)
- Órdenes de trabajo — abrir, asignar, cerrar
- Mantenimiento preventivo — planes y vencimientos

Antes de agregar el primero hay que cerrar dos decisiones transversales, porque
después son caras de cambiar: **paginación** (si los listados la tienen y
cómo) y **autenticación** (qué mecanismo reemplaza al header temporal
`X-Driver-Id`). La forma del error ya se resolvió de hecho para CAM-11
(`{ error: "<CÓDIGO>", message: "<texto>" }`, + `details` en 422 — ver
`docs/api/CAM-11-dvir-contract.md` sección 6) pero falta confirmarla como
convención para todo el backend, no solo estos endpoints. Están anotadas en
`STATE.md` como decisiones abiertas.

---

## Cuándo migrar a OpenAPI

CAM-11 ya usa OpenAPI (`docs/api/openapi.yaml`) sin esperar a que se cumpliera
ninguno de estos tres disparadores — quedan igual como criterio para el resto:
más de ~10 endpoints estables, el primer bug causado por un campo desalineado,
o la necesidad de generar tipos TypeScript.
