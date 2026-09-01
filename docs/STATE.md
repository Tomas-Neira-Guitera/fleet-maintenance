# Estado — FleetGuard

Última actualización: **2026-08-31**
Se escribe con `/cierre`, normalmente con confirmación de Guido. Esta vez
Guido pidió explícitamente que se actualizara la documentación en el momento,
como parte del propio trabajo de integración — no se esperó al cierre de
sesión para redactarla.

---

## Dónde estamos

Convergencia de stack con Tomás: él había migrado su parte a Spring Boot
(CAM-11, PR
[`fleet-maintenance#2`](https://github.com/Tomas-Neira-Guitera/fleet-maintenance/pull/2))
y ya la mergeó a `develop` en los dos repos antes de que se terminara de
hablar la decisión entre los dos. Guido confirmó adoptar ese stack, así que
esta sesión se usó para mergear `develop` → `feature/guido` en backend y
frontend, adoptar el modelo de datos de defectos de Tomás (se descarta el
propio de CAM-13), y dejar la documentación al día con el cambio de stack.
Guido pidió después específicamente cubrir el hueco de UI que dejaba esa
adopción: se construyó `DefectsList.tsx`, la pantalla de listado de defectos
contra `GET /api/defects`, con una navegación simple de tabs (Flota/Defectos)
en `App.tsx`. Verificado de punta a punta con datos reales: se cargó
`docs/db/seed-vehicles.sql`, se completó una inspección real desde la UI
(`AB123CD`, defecto no bloqueante en "Neumáticos") y el defecto generado por
el backend apareció correctamente en la pantalla nueva — patente, fecha y tag
de gravedad.

## En qué quedé

- **Backend** (`d7e9dd0` en `feature/guido`, commit de merge — **sin
  pushear**): adoptado Spring Boot 3.3.4 completo (Spring MVC + Spring Data
  JPA). Se descartó toda la arquitectura vieja (`Main.java`,
  `HttpServer`/JDBC, `HealthController`, `DefectoController`/`DefectoDao`,
  JSON a mano, `sql/schema.sql`, `db.properties`) porque sobrevivía el merge
  sin conflicto (nunca se tocó desde el ancestro común de las dos ramas) y
  quedaba duplicada junto a los equivalentes en Spring Boot. `GET
  /api/defectos` (contrato en español, tabla independiente) queda
  reemplazado por `GET /api/defects` de Tomás (inglés, atado 1 a 1 a una
  respuesta de inspección DVIR — ver `docs/API.md`). `GET /api/health` no
  tiene equivalente todavía en Spring Boot.
  Documentación actualizada en el mismo cambio: `AGENTS.md`, `PROJECT.md`
  (la reversión de "sin frameworks" queda anotada con fecha y motivo),
  `API.md` (ahora apunta a `docs/api/openapi.yaml` como fuente principal
  para CAM-11), `ROADMAP.md`, `SETUP.md` y `guias/nuevo-endpoint.md`.
- **Frontend** (`d7fa2ee` en `feature/guido`, commit de merge — **sin
  pushear**): adoptada la UI de CAM-11 de Tomás (`VehicleList` →
  `InspectionFlow`, servicios reales sin mocks). Se descartó
  `DefectosList.tsx`/`.css` (CAM-13) porque apuntaba al contrato viejo. La
  paleta "Cuidado preventivo" de Guido **no se perdió**: Tomás ya la había
  portado a `src/styles/tokens.css` con los mismos valores, solo renombró
  las variables. Resueltos a mano los conflictos de `App.tsx`/`App.css`/
  `index.css` (a favor de la versión de Tomás) y un `<link>` de Google Fonts
  duplicado en `index.html`. `AGENTS.md` actualizado.
- **Frontend, segundo commit** (pendiente de crear al cierre de esta sesión):
  pantalla nueva `DefectsList.tsx` + `services/defectsService.ts` + tipo
  `DefectSummary` en `types/domain.ts`, contra `GET /api/defects`. `App.tsx`
  suma una navegación de tabs (Flota/Defectos) — sigue sin router, es un
  `useState` simple como el resto de la navegación de CAM-11. Estilos nuevos
  en `App.css` (`.top-nav`, `.defect-summary-item__info/__meta`,
  `.photo-link`) reusando los tokens existentes.
- Verificado con build + test en los dos repos (`./gradlew build`,
  `npm run build`, `npm run lint`, todo verde) y con los dos servidores
  levantados de verdad y probados desde el navegador, incluyendo un flujo de
  inspección real de punta a punta (no solo listas vacías).
- **Gotcha nuevo de la sesión**: parar el backend con `TaskStop` sobre la
  tarea de `./gradlew bootRun` no basta — Gradle forkea la JVM de Spring
  Boot en un proceso hijo que sigue escuchando en el puerto 8080 después de
  que la tarea "termina". Se soluciona igual que la vez anterior:
  `netstat -ano | grep 8080` y `taskkill /PID <pid> /F` sobre el PID real de
  Spring Boot (mismo PID que loguea `Started FleetGuardApplication`).
- Pendiente sin resolver: Tomás todavía no revocó el PAT de GitHub que tenía
  embebido en el remote del backend.
- `.idea/misc.xml`: el cambio local de siempre (JDK_19 → JDK_21) volvió a
  aparecer y se dejó en un `git stash` sin aplicar (`wip antes de merge de
  develop`) para no mezclarlo con el merge — sigue sin soltarse, Guido puede
  hacer `git stash drop` si no lo necesita.

## Qué sigue

- **Pushear y abrir PR.** El merge quedó local en `feature/guido` en los dos
  repos — falta `git push` y abrir el PR de `feature/guido` → `develop` en
  cada uno, que era el objetivo original de esta sesión. Pendiente de que
  Guido revise el resultado primero.
- **Consolidar `API.md` con `docs/api/openapi.yaml`.** Hoy conviven las dos
  fuentes de contrato; decidir si `API.md` pasa a ser solo índice o si se
  vuelve a todo a un único lugar.
- **Evaluar Spring Actuator** como reemplazo de `GET /api/health`.
- **Flujo de ramas/PRs formal.** Ya no es rama única sin PRs (hay `main`,
  `develop`, `feature/*` con PRs reales) pero no está escrito en ningún lado
  cómo funciona el flujo completo (¿todo pasa por `develop`? ¿quién
  aprueba?) — conviene hablarlo con Tomás y anotarlo en `PROJECT.md`.

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
- **Router del frontend.** Sigue sin elegirse; no hizo falta para CAM-11
  porque la navegación es un `useState` simple (`Route`) en `App.tsx`.
- **Modelo de datos / versionado de schema.** Cambió de convención: ya no es
  `sql/schema.sql` a mano, ahora es JPA con `ddl-auto: update`. Sin
  Flyway/Liquibase todavía — anotado como próximo paso en el README del
  backend.
- **Consolidación de `API.md` y `openapi.yaml`.** Nueva, ver "Qué sigue".

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
  toda la documentación (`AGENTS.md`, `PROJECT.md`, `API.md`, `ROADMAP.md`,
  `SETUP.md`, guías) para reflejar el cambio de stack. A pedido de Guido, se
  construyó además la pantalla de listado de defectos que quedaba pendiente
  (`DefectsList.tsx` contra `GET /api/defects`, con nav de tabs en `App.tsx`)
  para no perder del todo el trabajo propio de CAM-13 en la integración.
  Verificado de punta a punta con datos reales (vehículos seedeados,
  inspección completa desde la UI, defecto resultante visible en la lista
  nueva). Commiteado en local en los dos repos (backend `d7e9dd0`, frontend
  `d7fa2ee` + un segundo commit con la pantalla de defectos) — **falta
  pushear y abrir el PR a `develop`**, pendiente de revisión de Guido.
