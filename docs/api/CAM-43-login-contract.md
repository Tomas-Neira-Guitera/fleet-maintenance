# CAM-43 — Contrato de API para login

Este documento acompaña a `openapi.yaml` (mismo directorio) con las decisiones y reglas
de negocio que el spec OpenAPI no puede expresar bien por sí solo. Referencia: historia
CAM-43 en Jira.

## Endpoint

| Método | Endpoint | Qué hace |
|---|---|---|
| POST | `/api/auth/login` | Autentica usuario y contraseña, devuelve un JWT + rol |

Alcance: solo autentica y emite el token — no incluye alta ni invitación de usuarios (eso
es CAM-23, a futuro). Los usuarios de prueba se cargan a mano con `docs/db/seed-users.sql`,
todavía no hay pantalla de alta.

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
HS256. `fleetguard.jwt.expiration-minutes` controla el vencimiento (60 min por defecto).
El token incluye `sub` (id de usuario), `username` y `role` como claims.

### 4. Convive con el header temporal de CAM-11
`X-Driver-Id` (stand-in de identidad del chofer para las inspecciones DVIR, ver
`docs/api/CAM-11-dvir-contract.md`) no se reemplaza todavía por este login — conviven
hasta que el frontend (CAM-45) esté migrado a usar el JWT en el resto de los endpoints.

## Fuera de alcance de este contrato

- Alta / invitación de usuarios (CAM-23).
- Autorización por rol en el resto de los endpoints (el rol viaja en el token, pero
  todavía no hay ningún endpoint que lo valide).
- Reemplazar `X-Driver-Id` por el JWT en los endpoints de CAM-11 (queda para cuando el
  frontend haga esa migración).
