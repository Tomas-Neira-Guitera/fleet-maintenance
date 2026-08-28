# Estado — FleetGuard

Última actualización: **2026-08-28**
Se escribe con `/cierre`, siempre con confirmación de Guido. Procedimiento en
`guias/sesiones.md`.

---

## Dónde estamos

Los dos repos son esqueletos que todavía no tienen nada del dominio. El backend
levanta un `HttpServer` en `:8080` con un único endpoint (`GET /api/health`) que
verifica la conexión a Postgres. El frontend es el scaffold de Vite con un
placeholder que intenta chequear la API. Lo que sí está terminado es la
infraestructura de contexto para trabajar con IA: `AGENTS.md`, `docs/` y la
configuración de Claude Code, todo versionado dentro de los repos.

## En qué quedé

- Backend (`TIP - Backend`): `Main.java` con `handleHealth`, `DatabaseConnection`
  leyendo `db.properties`, README completo. Último commit de código: "Subo Readme"
  (2026-08-23).
- Frontend (`TIP - Frontend`): clonado el 2026-08-25, sin commits propios
  todavía. `App.tsx` con el placeholder y el chequeo de conexión.
- Sesión del 2026-08-28: se escribió toda la capa de contexto —`AGENTS.md` en los
  dos repos, `docs/` completo en el backend, `CLAUDE.md` en los dos repos, y
  `.claude/` (comandos `/retomar` y `/cierre`, subagente `revisor`,
  `settings.json` con el frontend como directorio adicional) en el backend.

## Qué sigue

**Hito 0 del roadmap**, en este orden:

1. Corregir `../TIP - Frontend/src/App.tsx` para que llame a `GET /api/health`
   (hoy llama a `/api/ping`, que no existe, y por eso la home dice "offline"
   siempre). Sirve además para verificar que la sesión puede escribir en el repo
   del frontend.
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
