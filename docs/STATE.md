# Estado — FleetGuard

Última actualización: **2026-09-03**
Se escribe con `/cierre`, siempre con confirmación de Guido. Procedimiento en
`guias/sesiones.md`.

---

## Dónde estamos

Se resolvió por completo el ciclo de CAM-43/CAM-45 (Login): refinamiento del
backlog de Jira, implementación de backend y frontend, revisión por el
subagente `revisor` en las dos partes, y los cuatro PRs correspondientes
abiertos en GitHub. De paso se resolvió un problema de organización de git
que Guido señaló a mitad de sesión — `feature/guido` se había convertido en
una rama que mezclaba la migración a Spring Boot, CAM-13 y ahora CAM-43 sin
pushear durante varias sesiones — adoptando una convención nueva: una rama
por card de Jira, nombrada solo con el ID.

## En qué quedé

- **Backend, rama `CAM-43`** (commit `89242ab`, pusheada,
  [PR #4](https://github.com/Tomas-Neira-Guitera/fleet-maintenance/pull/4)
  contra `develop`): `POST /api/auth/login` — entidad `Usuario`+`Rol`,
  hasheo BCrypt, JWT (HS512), seed de usuarios de prueba
  (`docs/db/seed-users.sql`), tests de `AuthService`, documentado en
  `API.md`. El subagente `revisor` encontró y se corrigió un canal de timing
  que permitía enumerar usuarios (contraseña incorrecta vs. usuario
  inexistente tardaban distinto pese al mismo mensaje de error) y una
  inconsistencia de documentación (decía HS256, firma en HS512).
- **Backend, rama `feature/guido`** (pusheada tal cual, sin CAM-43 adentro,
  [PR #3](https://github.com/Tomas-Neira-Guitera/fleet-maintenance/pull/3)
  contra `develop`): infraestructura de contexto para IA (`docs/`,
  `.claude/`, `AGENTS.md`) — sin código de aplicación, confirmado con el
  diff real contra `develop`.
- **Frontend, rama `CAM-45`** (commit `c8c65ca`, pusheada,
  [PR #4](https://github.com/Tomas-Neira-Guitera/fleet-maintenance-fe/pull/4)
  contra `develop`): pantalla de login, sesión en `localStorage`, redirect
  por rol (chofer → flujo existente, admin → placeholder), logout, header
  `Authorization` sumado a los servicios existentes (`X-Driver-Id` sigue
  conviviendo sin cambios). Revisado por `revisor` sin hallazgos
  bloqueantes.
- **Frontend, rama `feature/guido`** (pusheada,
  [PR #3](https://github.com/Tomas-Neira-Guitera/fleet-maintenance-fe/pull/3)
  contra `develop`): pantalla de defectos de CAM-13 + docs de contexto.
- **Colección de Postman**: se encontró que la colección viva en el
  workspace de Postman del equipo ("TIP - Fleet Maintenance") estaba
  desactualizada (endpoints de antes de la migración a Spring Boot,
  `/api/health` y `/api/defectos`). Se reemplazó su contenido por la
  colección completa y al día del repo (ahora incluye la carpeta Auth de
  CAM-43) y se subió también el entorno `FleetGuard — Local`, que no
  existía ahí.
- **Convención de ramas nueva**: una rama por card, nombrada solo con el ID
  de Jira (`CAM-43`, no `feature/CAM-43-...`), cortada desde `develop`.
  Guardada en memoria para sesiones futuras.
- Se instaló y autenticó **GitHub CLI** (`gh`) en la máquina de Guido
  (cuenta `guidogg01`) — de acá en más los PRs se abren directo, sin pasar
  links.
- `.idea/misc.xml` volvió a aparecer modificado — drift de JDK de siempre,
  no se toca.

## Qué sigue

- **Mergear los 4 PRs en orden**: primero los de `feature/guido` (#3 en
  cada repo, son la base), después los de las cards (`CAM-43` #4 backend,
  `CAM-45` #4 frontend) — así sus diffs quedan limpios en vez de arrastrar
  los commits de `feature/guido`.
- **Hablar con Tomás** sobre la convención de "una rama por card, nombrada
  con el ID de Jira" para que sea la del equipo, no solo de Guido — es la
  resolución parcial de la decisión abierta de "flujo de ramas/PRs formal"
  en `PROJECT.md`.
- **Revisar el sprint activo** ("Sprint 1 - PoC 2", terminaba el
  2026-09-05) — decidir si el resto de las cards (CAM-20/37/40, panel
  admin) sigue en pie o se reprograma.
- **Proteger endpoints con el JWT real**: hoy `X-Driver-Id` sigue siendo lo
  único que el backend valida de verdad. Decidir cuándo se reemplaza (fuera
  de alcance explícito de CAM-43/CAM-45).
- **CAM-23** (gestión/invitación de usuarios) queda para cuando exista el
  panel admin.
- Consolidar `API.md` con `docs/api/openapi.yaml`.
- Evaluar Spring Actuator como reemplazo de `GET /api/health`.
- Repasar la cobertura de tests del backend más allá de `AuthService`
  (controllers, `VehicleService`/`PhotoService`, mappers,
  `GlobalExceptionHandler`) — sigue pendiente de sesiones anteriores.

## Decisiones abiertas

- **Autenticación.** JWT decidido y funcionando para el login (HS512).
  Sigue abierto *cuándo* se usa para proteger el resto de los endpoints
  (reemplazo real de `X-Driver-Id`, hoy resuelto con el header temporal
  `auth.HeaderDriverResolver`).
- **Flujo de ramas/PRs formal.** Parcialmente resuelto esta sesión (una
  rama por card, nombrada con el ID de Jira, cortada desde `develop`) —
  falta consensuarlo con Tomás.
- **Dónde van los commits de `STATE.md`** ahora que `feature/guido` está
  pusheada (PR abierto, todavía sin mergear). La regla vieja decía "hasta
  que se cierre la rama o Guido hable con Tomi"; pushear no es lo mismo que
  mergear, así que sigue sin resolverse del todo. No asumir que vuelve a
  `main`/`develop` sin confirmarlo primero.
- **Forma del error de la API.** CAM-11 ya usa
  `{ error: "<CÓDIGO>", message: "<texto>" }` de hecho (ver
  `docs/api/CAM-11-dvir-contract.md` sección 6) pero falta confirmarla como
  convención para todo el backend.
- **Paginación.** Sigue abierta.
- **Router del frontend.** Sigue sin elegirse, pero ahora con un motivo
  concreto: la vista admin (sidebar multi-sección) no entra en el `useState`
  simple actual.
- **Modelo de datos / versionado de schema.** Sin cambios (JPA `ddl-auto`,
  sin Flyway).
- **Consolidación de `API.md` y `openapi.yaml`.** Sin cambios.

## Callejones sin salida

Lo que se probó y no funcionó, con el motivo. Se agrega, no se reemplaza.

- **2026-08-28** — Primero se armó todo para OpenCode (`opencode.json`,
  `.opencode/commands/`, `.opencode/agents/`) en la carpeta padre `TIP`. No servía:
  la herramienta que se usa es Claude Code, que lee `.claude/` y solo `CLAUDE.md`.
  El contenido (`AGENTS.md` y `docs/`) se reusó tal cual —era markdown plano a
  propósito— y solo hubo que rehacer la capa de punteros. Se puede borrar
  `TIP\opencode.json` y `TIP\.opencode\`.
- **2026-08-28** — El error de Rolldown en Windows ("Cannot find native
  binding") no se resolvió solo borrando `node_modules` + `package-lock.json`
  y reinstalando (la solución que sugiere el propio mensaje de error). La causa
  real era el bug npm/cli#4828 combinado con Node por debajo del `engines`
  mínimo (`v20.17.0` vs `^20.19.0` que piden Vite 8 / rolldown). Se resolvió
  instalando el binding a mano (`npm install @rolldown/binding-win32-x64-msvc
  --save-optional`) y actualizando Node a `20.20.2` vía
  `winget upgrade --id OpenJS.NodeJS.20`.
- **2026-08-28** — Un commit (`5f68501`) se hizo estando en HEAD desprendida
  tras un `git checkout origin/<rama>`, lo que lo dejó sin rama que lo sostenga
  (git avisa "leaving N commits behind" en este caso). Se recupera con
  `git branch <rama> <sha>` + checkout + push, sin pérdida de trabajo mientras
  no se corra `git gc` antes de rescatarlo. Confirmado con `git fetch` +
  comparación de SHAs que el push efectivamente llegó a GitHub.
- **2026-08-28** — El mismo problema de detached HEAD que ya estaba anotado
  para el backend (`git checkout origin/<rama>` en vez de la rama local) pasó
  también en el frontend. Se resolvió con
  `git switch -c feature/guido --track origin/feature/guido`, sin perder los
  cambios sin commitear que había en el working tree.
- **2026-08-29** — Un proceso backend levantado por Claude Code en una
  sesión anterior (vía `./gradlew run` en background) quedó ocupando el
  puerto 8080 después de terminar las pruebas, y rompió el "Run" desde
  IntelliJ con un error sin causa aparente (`BUILD FAILED in 644ms`, exit
  value 1, sin stacktrace visible). Se diagnostica con
  `netstat -ano | grep 8080` (compara el PID contra el que arranca
  IntelliJ) y se soluciona matando el proceso viejo. Vale la pena recordar
  cerrar los procesos de background al terminar una sesión de prueba.
- **2026-08-31** — Repetición del problema anterior, con una vuelta de
  rosca: parar la tarea en background de `./gradlew bootRun` (con la
  herramienta de tareas de Claude Code) no libera el puerto 8080, porque
  Gradle forkea la JVM de Spring Boot en un proceso hijo separado que
  sigue vivo. Hace falta matar ese PID específico
  (`netstat -ano | grep 8080` → `taskkill /PID <pid> /F`), no alcanza con
  parar la tarea que lo lanzó.
- **2026-09-01** — Después de matar por PID el proceso de Vite en `:5173`
  (parado explícitamente), volvió a aparecer un proceso nuevo escuchando en
  ese puerto sin que se ejecutara ningún comando que lo iniciara. Sospecha
  sin confirmar: la tarea de background quedó marcada "failed" tras el
  `taskkill` anterior y algo la reintentó. Se resolvió igual que siempre
  (`netstat -ano` → `taskkill /PID <pid> /F`). Si vuelve a pasar, vale la
  pena investigar si hay un auto-retry de tareas fallidas.
- **2026-09-03** — `psql` no estaba en el PATH de esta máquina pese a tener
  Postgres instalado; hubo que usar la ruta completa
  (`C:\Program Files\PostgreSQL\16\bin\psql.exe`). Además, correrlo con
  `PGPASSWORD=""` (vacío, en vez de la contraseña real) lo dejó colgado
  esperando un prompt de contraseña en una shell no interactiva — se
  resolvió matando la tarea y reintentando con la contraseña real de
  `application-local.yml`.
- **2026-09-03** — `gh` CLI no estaba instalado; se instaló con
  `winget install --id GitHub.cli`. Ni Bash ni PowerShell heredan el PATH
  actualizado dentro de la misma sesión — hubo que invocar `gh` por ruta
  completa (`C:\Program Files\GitHub CLI\gh.exe`) el resto de la sesión,
  hasta que se reinicien las terminales.
- **2026-09-03** — Al verificar a mano un fix de seguridad en `AuthService`
  (cerrar un canal de timing), medir con `curl` contra un backend "recién
  reiniciado" dio una diferencia de 81ms vs 4ms — como si el fix no
  funcionara. Resultó que el proceso JVM contra el que se medía había
  quedado corriendo con el código viejo pese a parecer recién iniciado. Se
  resolvió instrumentando con logs de timing directos en el código y
  reiniciando el proceso de cero — ahí sí confirmó que el fix andaba
  (~77-80ms en los dos casos). Ante una medición que no cierra, conviene
  matar y reiniciar el proceso antes de asumir que el código tiene un bug.

## Historial

Una línea por sesión.

- **2026-08-23** — Backend inicial: Gradle + `HttpServer` en `:8080`,
  `DatabaseConnection` contra Postgres `TIP`, `GET /api/health`, README.
- **2026-08-25** — Frontend inicial: scaffold de Vite + React 19 + TS, Oxlint,
  `App.tsx` con chequeo de conexión, README.
- **2026-08-28** — Infraestructura de contexto para IA: `AGENTS.md` por repo,
  `docs/` (PROJECT, STATE, ROADMAP, API, SETUP, guías) y configuración de Claude
  Code versionada en `.claude/`. Se detectó la desalineación `/api/ping` vs
  `/api/health`.
- **2026-08-28** — Corregido `/api/ping` → `/api/health` en `App.tsx` (Hito 0
  #1, sin commitear). Resuelto bug de entorno del frontend en Windows (Node
  desactualizado + npm/cli#4828 con Rolldown). Recuperado y pusheado a
  `origin/feature/guido` el commit de la capa de contexto que había quedado en
  HEAD desprendida — confirmado en GitHub con `git fetch`.
- **2026-08-28** — Cerrado Hito 0 completo (toolchain Java,
  `db.properties.example`, remote sin token — con el hallazgo de que el PAT
  era de Tomás, no de Guido). Completada la card CAM-13 en Jira. Implementado
  `GET /api/defectos` con refactor a arquitectura en capas a pedido explícito
  de Guido. Probado a mano (datos, vacío, caracteres especiales, DB caída) y
  revisado por el subagente `revisor` en dos pasadas. Agregado el request a
  la colección de Postman. Commiteado y pusheado en los dos repos a
  `origin/feature/guido` (backend `617aa8b`, frontend `0eff382`).
- **2026-08-29** — Cerrada CAM-13 de punta a punta: frontend consume
  `GET /api/defectos` con los tres estados, primer paso del sistema de
  diseño "Cuidado preventivo" (paleta + tipografía) aplicado vía variables
  CSS. Encontrado y arreglado un bug de CORS preexistente en el backend que
  bloqueaba todo fetch del frontend. Revisado por el subagente `revisor` sin
  hallazgos bloqueantes. Commiteado y pusheado en los dos repos a
  `origin/feature/guido` (backend `41c2656`, frontend `71cb44c`).
- **2026-08-31** — Confirmado (leyendo el PR de GitHub) que Tomás había
  migrado su parte a Spring Boot con arquitectura en capas (CAM-11) y ya
  mergeó ese trabajo a `develop` en los dos repos. Guido confirmó adoptar
  ese stack como el del equipo, se mergeó `develop` → `feature/guido` en
  backend y frontend (se descartó la arquitectura vieja y el contrato de
  `/api/defectos`, se adoptó `GET /api/defects` de Tomás), y se actualizó
  toda la documentación para reflejar el cambio de stack. Se construyó la
  pantalla de listado de defectos que quedaba pendiente (`DefectsList.tsx`),
  verificada de punta a punta con datos reales. Guido compartió un mock de
  la vista de administrador (dashboard de escritorio, distinta del flujo
  mobile del chofer) como norte de largo plazo — commiteado en
  `docs/design/admin-dashboard-mock.png` y anotado en `ROADMAP.md`. Guido
  probó el backend por su cuenta, corriendo `./gradlew bootRun` con éxito.
  Revisado el backlog de Jira: confirmado que varias historias del
  dashboard admin están fragmentadas y sin refinar (`CAM-37/38/39/40/44`) y
  que Login (`CAM-43`) no tiene descripción — acordado como punto de
  partida de la próxima sesión, junto con una revisión de cobertura de
  tests del backend. Commits en local en los dos repos (backend
  `d7e9dd0`/`4996fd0`/`c13f320`, frontend `d7fa2ee`/`638d31f`) — **sigue
  faltando pushear y abrir el PR a `develop`**.
- **2026-09-03** — Confirmado el commit de estado 2026-09-01 a
  `feature/guido`. Refinado el backlog de Jira para Login: CAM-43 pasó a
  ser "Login — backend" (modelo mínimo de Usuario+Rol, sin invitaciones) y
  CAM-45 "Login — pantalla y sesión en frontend" se creó como card nueva,
  las dos bajo el épico CAM-10; CAM-23 anotada como dependiente sin tocar
  su alcance; JWT elegido como mecanismo de sesión. Implementado y
  verificado de punta a punta el login de backend (JWT+BCrypt, con un canal
  de timing de enumeración de usuarios encontrado y corregido por el
  subagente `revisor`) y de frontend (pantalla de login, sesión en
  `localStorage`, redirect por rol, logout), cada uno probado a mano contra
  el backend/frontend reales y pasado por `revisor`. Resuelto el problema
  de organización de git que señaló Guido: se pusheó `feature/guido` tal
  cual (limpio, solo docs/infraestructura, confirmado con el diff real) y
  se adoptó la convención de una rama por card de Jira, nombrada solo con
  el ID, para todo trabajo nuevo. Se instaló y autenticó GitHub CLI. Se
  abrieron 4 PRs a `develop` (2 por repo) — pendientes de merge, sugerido
  mergear primero los de `feature/guido`.
