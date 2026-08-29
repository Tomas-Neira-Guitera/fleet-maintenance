# Estado — FleetGuard

Última actualización: **2026-08-29**
Se escribe con `/cierre`, siempre con confirmación de Guido. Procedimiento en
`guias/sesiones.md`.

---

## Dónde estamos

CAM-13 cerrada de punta a punta: el listado de defectos ahora se ve en el
frontend (`http://localhost:5173`) con los tres estados (cargando/ok/error),
consumiendo `GET /api/defectos` tal como está documentado en `API.md`. De
paso se destapó y arregló un bug de CORS que bloqueaba **toda** comunicación
frontend→backend (no solo defectos, también `/api/health`) — probablemente
estaba ahí desde el principio del proyecto, recién se notó al conectar el
primer fetch real desde el navegador.

## En qué quedé

- **Backend** (`41c2656` en `origin/feature/guido`): `HttpResponses.sendJson`
  agrega `Access-Control-Allow-Origin: *` a toda respuesta, documentado en
  `API.md`. Nuevo `.claude/launch.json` para levantar el preview del
  frontend desde Claude Code.
- **Frontend** (`71cb44c` en `origin/feature/guido`): componente nuevo
  `DefectosList.tsx` + `DefectosList.css`, integrado en `App.tsx`. Primer
  paso real del sistema de diseño "Cuidado preventivo" que pasó Guido
  (paleta cálida `#F6F2EA`/salvia/ámbar/terracota, tipografías Space
  Grotesk + Inter + IBM Plex Mono vía Google Fonts) aplicado como variables
  CSS centralizadas en `index.css`, pensado para ser barato de ajustar
  cuando se unifique con el diseño del compañero de equipo.
- Revisado por el subagente `revisor` en una pasada: sin hallazgos
  bloqueantes. Único señalamiento (documentar el header CORS en `API.md`)
  ya aplicado.
- Probado a mano en los dos lados: los tres estados del componente, headers
  de CORS, fuentes cargando correctamente — confirmado con automatización
  de navegador de mi lado, y repetido por Guido en su propio entorno.
- **Gotcha resuelto de la sesión**: un proceso Java quedó corriendo en el
  puerto 8080 después de las pruebas y rompía el "Run" desde IntelliJ
  (`BUILD FAILED`, exit value 1, sin pista clara de la causa real). Ver
  Callejones sin salida.
- Pendiente sin resolver: Tomás todavía no revocó el PAT de GitHub que tenía
  embebido en el remote del backend.
- `.idea/misc.xml` sigue modificado sin commitear en el backend, tres
  sesiones seguidas — no se tocó a propósito, no está claro qué lo cambia
  (¿el IDE al releer el toolchain de Java?). Vale la pena revisarlo en algún
  momento.

## Qué sigue

No quedó decidido explícitamente cuál es el próximo paso — quedan dos
caminos abiertos, a elegir la próxima sesión:

- **Seguir el backlog de Jira** (épica
  [CAM-7](https://fleet-maintenance.atlassian.net/browse/CAM-7)): candidatas
  son [CAM-12](https://fleet-maintenance.atlassian.net/browse/CAM-12)
  (reporte de defectos con foto) o
  [CAM-14](https://fleet-maintenance.atlassian.net/browse/CAM-14) (crear
  orden de trabajo desde un defecto).
- **Frontend**: si se arma un layout de varias pantallas (el dashboard de
  referencia que mostró Guido, con sidebar y navegación), hace falta elegir
  un router antes — no hay ninguno instalado todavía. Es una decisión a
  proponer, no a instalar de pasada (`AGENTS.md`).

## Decisiones abiertas

- **Forma del error de la API.** Sigue sin cerrar: conviven
  `{status, error}` (`/api/health`) y `{error}` (`/api/defectos`). Bloquea:
  unificarlo antes de que un tercer endpoint copie un estilo distinto.
- **Paginación.** Sigue abierta, fuera de alcance de CAM-13.
- **Autenticación.** Sigue abierta. Mientras no exista, las respuestas
  llevan `Access-Control-Allow-Origin: *` sin restricción — aceptable hoy
  (sin cookies, sin datos sensibles), pero hay que acotarlo el día que se
  agregue auth con cookies (anotado en `API.md`).
- **Router del frontend.** Nueva. Ninguno elegido todavía; hace falta antes
  de armar navegación multi-pantalla. Candidato natural: react-router, pero
  es una decisión a proponer formalmente.
- **Modelo de datos / versionado de schema.** Sin cambios respecto a la
  sesión anterior — `sql/schema.sql` como convención informal, corrida a
  mano.

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
