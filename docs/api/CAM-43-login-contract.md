# CAM-43 — Contrato de API para login

Este documento acompaña a `openapi.yaml` (mismo directorio) con las decisiones y reglas
de negocio que el spec OpenAPI no puede expresar bien por sí solo. Referencia: historia
CAM-43 en Jira.

## Endpoint

| Método | Endpoint | Qué hace |
|---|---|---|
| POST | `/api/auth/login` | Autentica usuario y contraseña, devuelve un JWT + rol |

Alcance: solo autentica y emite el token — no incluye alta ni invitación de usuarios. El
alta y la edición son de CAM-23 (`POST`/`PATCH /api/users`, ver
`CAM-23-users-contract.md`); los usuarios de prueba iniciales se siguen cargando con
`docs/db/seed-users.sql`. Desde CAM-23 el login también rechaza a los usuarios
desactivados (403 `USER_INACTIVE`, solo si la contraseña es correcta).

## Decisiones clave

### 1. Mismo error para usuario inexistente, contraseña incorrecta y credenciales vacías
Los tres casos devuelven `401 INVALID_CREDENTIALS` con el mismo mensaje, a propósito, para
no revelar si un nombre de usuario existe en el sistema.

### 2. Comparación contra un hash "dummy" para evitar timing attacks
Cuando el usuario no existe, el servicio igual corre `PasswordEncoder.matches()` contra un
hash BCrypt fijo que no le pertenece a nadie (`AuthService.DUMMY_HASH`), en vez de
devolver el error apenas no lo encuentra. Sin esto, un request a un usuario inexistente
respondería mucho más rápido que uno con contraseña incorrecta — esa diferencia de tiempo
delata qué usuarios existen aunque el mensaje de error sea idéntico.

### 3. Token: JWT firmado HS512, vencimiento configurable
`fleetguard.jwt.secret` (64 caracteres en el `application.yml` committeado, valor de
desarrollo — sobreescribir en `application-local.yml` fuera de la máquina local) determina
el algoritmo HMAC vía `Keys.hmacShaKeyFor`; con ese largo de clave firma en HS512, no
HS256. `fleetguard.jwt.expiration-minutes` controla el vencimiento: 720 minutos (12 horas)
desde CAM-85, para que un chofer no tenga que volver a entrar entre el pre-viaje y el
post-viaje de una jornada. Antes eran 60.
El token incluye `sub` (id de usuario), `username` y `role` como claims.

### 4. Reemplazó al header temporal de CAM-11
`X-Driver-Id` era el stand-in de identidad del chofer para las inspecciones DVIR. Convivió
con este login hasta CAM-85: desde entonces el chofer de una inspección es el usuario del
JWT y el header ya no existe (ver `docs/api/CAM-87-trips-and-blocking-contract.md`).

## Fuera de alcance de este contrato

- Alta y edición de usuarios (CAM-23). Invitaciones por email: sin card.
- Autorización por rol: el JWT se valida en `/api/users` y `/api/expenses` (exigen ADMIN,
  CAM-23 y CAM-52) y en `POST /api/inspections/{vehicleId}` (exige CHOFER, CAM-85); el
  resto de los endpoints todavía no lo valida (CAM-73).
