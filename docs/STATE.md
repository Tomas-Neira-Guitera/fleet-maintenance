# Estado — FleetGuard

Última actualización: **2026-09-01**
Se escribe con `/cierre`, siempre con confirmación de Guido. Procedimiento en
`guias/sesiones.md`.

---

## Dónde estamos

Terminada la convergencia con el stack de Tomás: los merges de `develop` a
`feature/guido` quedaron committeados y verificados en los dos repos, con la
pantalla de defectos reconstruida contra el contrato nuevo. Guido probó todo
de punta a punta por su cuenta (levantó el backend a mano, entendió los logs
de Spring Boot). Además quedó documentada una visión de largo plazo para la
vista de administrador (mock real, commiteado en el repo) y se revisó el
backlog de Jira para arrancar la próxima sesión por ahí.

## En qué quedé

- **Backend** (`d7e9dd0` merge, `4996fd0` docs, `c13f320` mock — todos en
  `feature/guido`, **sin pushear**): adoptado Spring Boot 3.3.4 completo,
  descartada la arquitectura vieja (`HttpServer`/JDBC/JSON a mano) y el
  contrato `GET /api/defectos`, reemplazado por `GET /api/defects` de Tomás.
  Documentación (`AGENTS.md`, `PROJECT.md`, `API.md`, `ROADMAP.md`,
  `SETUP.md`, guías) actualizada para reflejar el cambio de stack. Agregado
  `docs/design/admin-dashboard-mock.png` + sección "Visión de referencia —
  vista admin" en `ROADMAP.md`.
- **Frontend** (`d7fa2ee` merge, `638d31f` pantalla de defectos — en
  `feature/guido`, **sin pushear**): adoptada la UI de CAM-11 de Tomás,
  descartado `DefectosList.tsx` viejo, construida `DefectsList.tsx` nueva
  contra `GET /api/defects` con nav de tabs (Flota/Defectos) en `App.tsx`. La
  paleta "Cuidado preventivo" se conserva vía `styles/tokens.css`.
- Verificado con build + test en los dos repos y con un flujo real de punta a
  punta (vehículos seedeados, inspección completa desde la UI, defecto
  resultante visible en la lista nueva) — primero por mí, después replicado
  por Guido corriendo `./gradlew bootRun` en su propia terminal.
- Revisado el backlog de Jira (board CAM) a pedido de Guido: confirmado que
  está incompleto como él decía. `CAM-43` (Login) sin descripción ni link al
  épico de roles (`CAM-10`). `CAM-20` (US: Dashboard de estado de flota, bajo
  el épico `CAM-9`) sí tiene historia de usuario y un mock adjunto — probable
  el mismo que compartió acá. `CAM-37`/`38`/`39`/`40`/`44` son la misma idea
  de dashboard trozada en cards sueltas, casi sin descripción y sin linkear
  a `CAM-20` (salvo `CAM-44`).
- El merge trajo tests nuevos de Tomás (`ChecklistCatalogTest`,
  `DefectServiceTest`, `InspectionValidatorTest`) — cubren catálogo de
  checklist, `DefectService` y `InspectionValidator`. No hay tests todavía de
  ningún controller, de `VehicleService`/`PhotoService`, de los mappers ni de
  `GlobalExceptionHandler`.
- Gotcha nuevo, sin causa raíz confirmada: después de matar por PID el
  proceso de Vite en `:5173`, apareció otro proceso nuevo escuchando ahí sin
  que se corriera ningún comando que lo iniciara. Ver Callejones.
- Sigue pendiente: Tomás no revocó el PAT de GitHub embebido en el remote
  viejo. `.idea/misc.xml` volvió a aparecer modificado (JDK toolchain drift
  de siempre) — no se toca.

## Qué sigue

- **Punto de partida acordado para la próxima sesión: refinar el backlog de
  Jira antes de escribir código.** Consolidar `CAM-37`/`38`/`39`/`40`/`44`
  como subtareas de `CAM-20` (o cerrarlas si son duplicados), y escribir una
  descripción real para `CAM-43` (Login) apoyándose en la historia de roles
  de `CAM-23`. Recién después de eso, decidir si se ataca Login o el
  Dashboard admin primero.
- **Repasar la cobertura de tests del backend y completar lo que falte.**
  Hoy solo hay 3 archivos de test (checklist, `DefectService`,
  `InspectionValidator`), todos heredados del merge de Tomás — nada propio
  todavía, y nada de controllers, `VehicleService`, `PhotoService`, mappers
  ni `GlobalExceptionHandler`. Hacer una pasada dedicada la próxima sesión.
- **Pushear y abrir PR.** Sigue sin hacerse — falta `git push` de
  `feature/guido` y abrir el PR a `develop` en los dos repos, pendiente de
  que Guido dé el visto bueno.
- Consolidar `API.md` con `docs/api/openapi.yaml`.
- Evaluar Spring Actuator como reemplazo de `GET /api/health`.
- Flujo de ramas/PRs formal — hablarlo con Tomás y anotarlo en `PROJECT.md`.

## Decisiones abiertas

- **Forma del error de la API.** CAM-11 ya usa
  `{ error: "<CÓDIGO>", message: "<texto>" }` de hecho (ver
  `docs/api/CAM-11-dvir-contract.md` sección 6) pero falta confirmarla como
  convención para todo el backend.
- **Paginación.** Sigue abierta.
- **Autenticación.** Sigue abierta. CAM-11 resuelve la identidad con un
  header temporal `X-Driver-Id` (`auth.HeaderDriverResolver`) hasta que
  exista login real. CORS ya no es `*` — está restringido por config a
  `localhost:5173`/`127.0.0.1:5173`.
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
