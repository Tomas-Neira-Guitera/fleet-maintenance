# CAM-23 — Gestión de usuarios y roles

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

Hasta ahora los usuarios existían (CAM-43, CAM-67), pero solo se creaban a mano con
`docs/db/seed-users.sql`. CAM-23 suma el alta, la edición y la desactivación desde el
panel de admin.

Publicar `POST /api/users` sin proteger dejaría que cualquiera se creara un ADMIN sin
loguearse. Por eso esta card también arma la validación del JWT, pero la aplica
**solo a `/api/users`**. El resto de la API sigue sin validarlo hasta CAM-73, que
reusa el mismo mecanismo (decisión del 2026-09-28).

## Decisiones

### 1. Interceptor de Spring MVC, no filtro de servlet ni Spring Security completo
`JwtAuthInterceptor` (paquete `auth`) se registra en `WebConfig` para `/users` y
`/users/**`.

- **No es un filtro de servlet**: un filtro corre antes que el manejo de CORS de
  Spring MVC, así que sus 401/403 saldrían sin `Access-Control-Allow-Origin` y el
  navegador los mostraría como un error de red, no como un 401 legible. El
  interceptor corre después.
- **No es la cadena de filtros de Spring Security**: el proyecto solo usa su módulo
  de BCrypt (CAM-43). Sumar el framework entero era una dependencia nueva para
  proteger un solo recurso.
- Sus excepciones (`UnauthorizedException`, `ForbiddenException`) pasan por
  `GlobalExceptionHandler`, con el mismo formato `{error, message}` que el resto.
- Deja pasar los `OPTIONS` (preflight de CORS), que nunca traen el token.

**Para CAM-73:** alcanza con sumar rutas en `WebConfig.addInterceptors`. Si el resto
de la API admite otros roles además de ADMIN, el interceptor necesita recibir qué
roles acepta cada ruta (hoy exige ADMIN fijo).

### 2. El usuario se busca en la base en cada pedido
El token solo aporta el id (`sub`); el rol y el estado salen de la base. Así, a un
usuario desactivado o al que le sacaron ADMIN se le corta el acceso desde el pedido
siguiente, sin esperar a que venza su token (60 minutos). Cuesta una consulta por
clave primaria por pedido.

- Sin header, token mal formado, adulterado o vencido, o usuario inexistente o
  desactivado → **401 `UNAUTHORIZED`**. No se distingue el motivo.
- Token válido de un usuario que no es ADMIN → **403 `FORBIDDEN`**.

El usuario autenticado queda en el atributo del request
`AuthenticatedUser.REQUEST_ATTRIBUTE`, y el controller lo recibe con
`@RequestAttribute`.

### 3. `User.active` y el login
Columna `users.active`, `NOT NULL` con default `true` a nivel columna. Sin el default,
`ddl-auto: update` no puede agregarla a una tabla con filas (ver Callejones de
`STATE.md`, mismo caso que `Vehicle.active`).

Un usuario desactivado que se loguea con la contraseña correcta recibe **403
`USER_INACTIVE`** con un mensaje claro. Con una contraseña incorrecta recibe el 401
`INVALID_CREDENTIALS` de siempre: el estado se revela solo a quien ya conoce la
contraseña, para no delatar qué usuarios existen (mismo criterio que el hash dummy
de CAM-43).

Desactivar no borra nada: inspecciones, OTs y todo lo que referencia al usuario se
conserva.

### 4. Validaciones del alta
- `username`: obligatorio, hasta 30 caracteres. Admite letras con acentos y ñ, números,
  **espacios**, punto, guion y guion bajo (`[\p{L}\p{N} ._-]`), así que "Juan Pérez" es
  un usuario válido (decisión de Guido, 2026-10-03: el usuario es también el nombre que
  se muestra, sin un campo de nombre completo aparte).
  - Se guarda en forma canónica (`UserService.normalizeUsername`): sin espacios en los
    extremos, los repetidos del medio colapsados a uno y en Unicode NFC. Así "Juan  Pérez"
    y "Juan Pérez" son el mismo usuario, y una "é" escrita como "e" + tilde combinada
    también. Cualquier tipo de espacio (también el espacio duro, NBSP, que llega al pegar
    desde un documento) se convierte en un espacio común antes de validar. El **login normaliza igual** antes de buscar, y después compara exacto
    (sensible a mayúsculas).
  - Es **único sin distinguir mayúsculas** (`existsByUsernameIgnoreCase`); si ya existe,
    **409 `USERNAME_TAKEN`**. "Perez" y "Pérez" son usuarios distintos.
  - Limitación conocida: la base solo tiene el `unique` exacto de `username`. Si dos
    altas con el mismo nombre exacto llegan a la vez, la que pierde la carrera choca con
    ese `unique` y también recibe 409 (se atrapa el error de la base). En cambio, dos
    altas simultáneas con "Juan" y "juan" podrían crear las dos. Con un solo admin
    cargando usuarios no se consideró que valga un índice sobre `lower(username)`, que
    además `ddl-auto` no genera.
  - Limitación conocida: la comparación sin mayúsculas la hace Postgres (`upper()`), y
    pasar "é" a "É" depende del `LC_CTYPE` de la base. Con una base en locale `C`,
    "JOSÉ MUÑOZ" y "José Muñoz" no chocarían. En la base local de Guido sí chocan
    (probado el 2026-10-03: 409); vale la pena confirmarlo en otras bases.
- `password`: al menos **6** caracteres y hasta **72 bytes en UTF-8**, que es el tope de
  BCrypt. Se mide en bytes y no en caracteres porque una letra con acento ocupa 2: sin
  eso, una contraseña larga con acentos se truncaría en silencio. No se recorta. Se
  guarda solo el hash.
- `role`: `ADMIN`, `CHOFER` o `TECNICO`.
- Todos los errores de validación salen juntos en un 422 con `details[].field`.

### 5. Semántica del PATCH
Campos parciales (`role`, `password`, `active`): lo que no viene no se toca. El
`username` no se edita.

Un admin **no puede desactivarse a sí mismo ni quitarse el rol ADMIN** (**409
`CANNOT_MODIFY_SELF`**). Con eso siempre queda al menos un admin activo: el que hace
el pedido. Sí puede cambiarse su propia contraseña.

**Resetear la contraseña no invalida los tokens ya emitidos**: el interceptor no compara
el `iat` del token contra la fecha del cambio. Si hay que cortarle el acceso a alguien ya,
hay que desactivarlo: eso sí rige desde el pedido siguiente (decisión 2).

### 6. Un técnico con OTs abiertas no cambia de rol
Las OTs apuntan a su técnico por `technicianId`. Si un TECNICO pasara a CHOFER o ADMIN,
sus OTs abiertas quedarían a cargo de alguien que ya no puede trabajarlas, y el selector
de técnico, que solo lista técnicos, las mostraría como "Sin asignar". Por eso, si tiene
OTs `asignada` o `en_proceso`, el cambio de rol responde **409
`TECHNICIAN_HAS_OPEN_WORK_ORDERS`** con la cantidad. Primero hay que reasignarlas.

Sí se lo puede **desactivar** con OTs abiertas: sigue siendo técnico, y el selector lo
muestra como "(desactivado)" en las OTs que ya tiene a cargo. Al revés, el backend no
deja **asignarle** OTs nuevas a un técnico desactivado (422 `technicianId` en
`/work-orders`). Reenviar el técnico que ya está a cargo no se valida, para que editar
otro campo de la OT no falle.

### 7. `GET /api/users` sin `role` obligatorio
Antes `role` era obligatorio, para no exponer el listado completo en un endpoint
abierto. Ahora que está protegido, sin `role` lista todos (activos y desactivados),
y la respuesta suma `active`.

El selector de técnico de las OTs (CAM-60) sigue pidiendo `role=TECNICO` y ofrece solo
los activos, más el que ya está a cargo (ver decisión 6). Como ahora pide el token, si la
sesión del admin venció muestra el aviso de sesión vencida en vez de una lista vacía.

## Fuera de alcance
Proteger el resto de la API (CAM-73), invitaciones y recuperación de contraseña por
email, multi-tenant.
