# Estado — FleetGuard

Última actualización: **2026-08-28**
Se escribe con `/cierre`, siempre con confirmación de Guido. Procedimiento en
`guias/sesiones.md`.

---

## Dónde estamos

La capa de contexto para IA (`AGENTS.md`, `docs/`, `.claude/`) ya está
commiteada y confirmada en GitHub, en `origin/feature/guido` (rama propia,
todavía no mergeada a `main`). Del lado del dominio los dos repos siguen en
skeleton, pero el Hito 0 ya arrancó: el bug `/api/ping` vs `/api/health` está
corregido en el working tree del frontend, y el entorno de desarrollo del
frontend (que estaba roto en Windows) ya funciona.

## En qué quedé

- **Backend:** se recuperó el commit `5f68501` ("docs: contexto de proyecto y
  configuracion de Claude Code"), que había quedado en HEAD desprendida sin
  rama. Se creó la rama local `feature/guido` sobre ese commit y se pusheó a
  `origin/feature/guido`. Verificado con `git fetch` + comparación de SHAs que
  el push llegó bien a GitHub.
- **Frontend** (`TIP - Frontend`): cuatro cosas sin commitear todavía:
  - `src/App.tsx` — corregido para llamar a `GET /api/health` con el tipo
    `HealthResponse` (antes `/api/ping` + `PingResponse`), siguiendo `API.md`.
  - `package.json` / `package-lock.json` — cambiaron por el arreglo del
    entorno: se agregó `@rolldown/binding-win32-x64-msvc` como
    `optionalDependency` para destrabar un bug de npm (npm/cli#4828) que no
    instalaba el binario nativo de Rolldown en Windows.
  - `AGENTS.md` y `CLAUDE.md` del frontend — siguen `untracked`, sin commitear.

## Qué sigue

Commitear lo pendiente del frontend (el fix de `App.tsx` y la capa de contexto
`AGENTS.md`/`CLAUDE.md` pueden ir en commits separados). Después, seguir con el
**Hito 0 del roadmap**, en este orden:

1. ~~Corregir `../TIP - Frontend/src/App.tsx` para que llame a `GET /api/health`~~
   — hecho, falta commitear.
2. Agregar `src/main/resources/db.properties.example` al repo del backend: el
   README lo manda a copiar y no existe.
3. Limpiar el token de GitHub embebido en el remote del backend y revocarlo.
4. Fijar el toolchain de Java en `build.gradle.kts`.

Después de eso, cerrar las decisiones transversales del Hito 1 (forma del error,
paginación, auth, modelo de datos) antes de escribir el primer endpoint de
dominio.

## Decisiones abiertas

- **Forma del error de la API.** Qué claves tiene siempre una respuesta de error.
  Bloquea: cualquier endpoint que pueda fallar por regla de negocio.
- **Paginación.** Si los listados la tienen y con qué parámetros.
  Bloquea: el primer endpoint de listado (flota).
- **Autenticación.** Qué mecanismo, qué header, qué pasa cuando falta.
  Bloquea: el hito de login y, en cascada, todo lo que va protegido.
- **Modelo de datos.** Entidades, relaciones, y si el schema se versiona en el
  repo y cómo se aplica. Bloquea: todo el dominio.

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
