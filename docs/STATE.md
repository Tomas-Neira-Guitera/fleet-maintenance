# Estado — FleetGuard

Última actualización: **2026-08-28**
Se escribe con `/cierre`, siempre con confirmación de Guido. Procedimiento en
`guias/sesiones.md`.

---

## Dónde estamos

Hito 0 del roadmap está cerrado. El primer endpoint de dominio,
`GET /api/defectos` (CAM-13), está implementado con una arquitectura en capas
nueva (`controller`/`model`/`dao`/`util`) que reemplaza el molde anterior de
un handler por endpoint, probado a mano y pusheado. El frontend recuperó su
capa de contexto de IA y el fix de `/api/health`, todo commiteado. Falta: el
frontend todavía no consume `/api/defectos`.

## En qué quedé

- **Backend** (`617aa8b` en `origin/feature/guido`): `GET /api/defectos` —
  tabla `defectos` en `sql/schema.sql` (aplicada a mano en Postgres local, sin
  herramienta de migraciones todavía), refactor completo a capas (`Main.java`
  quedó solo como bootstrap), contrato en `API.md`. Decisión de la
  arquitectura en capas documentada con fecha y motivo en `PROJECT.md`, molde
  nuevo en `AGENTS.md` y `docs/guias/nuevo-endpoint.md`.
- **Frontend** (`0eff382` en `origin/feature/guido`): `App.tsx` corregido a
  `/api/health`, `AGENTS.md`/`CLAUDE.md` commiteados, fix de Rolldown en
  Windows.
- **Jira**: card [CAM-13](https://fleet-maintenance.atlassian.net/browse/CAM-13)
  completada con criterios de aceptación y contrato técnico.
- **Postman**: colección "TIP - Fleet Maintenance" (workspace TIP) con el
  request `Defectos - Listado` agregado.
- Pendiente sin resolver: Tomás todavía no revocó el PAT de GitHub que tenía
  embebido en el remote del backend.
- `.idea/misc.xml` sigue modificado sin commitear en el backend, dos sesiones
  seguidas — no se tocó a propósito, no está claro qué lo cambia (¿el IDE al
  releer el toolchain de Java?). Vale la pena revisarlo en algún momento.

## Qué sigue

Consumir `GET /api/defectos` desde el frontend: componente que liste los
defectos ordenados por gravedad y fecha, con los tres estados
(cargando/ok/error), siguiendo `docs/guias/nuevo-endpoint.md` paso 3. El
contrato ya está en `API.md`, solo falta el lado frontend.

## Decisiones abiertas

- **Forma del error de la API.** Sigue sin cerrar: hoy conviven dos formas
  (`/api/health` devuelve `{status, error}`, `/api/defectos` devuelve solo
  `{error}`). Bloquea: unificarlo antes de que un tercer endpoint copie un
  estilo distinto.
- **Paginación.** Sigue abierta, quedó explícitamente fuera de alcance de
  CAM-13.
- **Autenticación.** Sigue abierta.
- **Modelo de datos / versionado de schema.** Parcialmente resuelto de forma
  pragmática para `defectos`: `sql/schema.sql` como convención informal,
  corrida a mano con `psql`. Falta decidir si alcanza así o hace falta algo
  más formal a medida que crezcan las tablas.

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
