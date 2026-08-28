# FleetGuard — qué es y por qué está así

Documento estable. Cambia cuando cambia una decisión de fondo, no cada sesión.
Para "en qué quedé" andá a `STATE.md`; para "qué falta", a `ROADMAP.md`.

## Qué es

Sistema de **mantenimiento preventivo e inspecciones de flota**. Trabajo práctico
integrador (TIP) de facultad, hecho por un equipo, donde todos usamos asistentes
de IA para trabajar.

## Dominio

Estas son las áreas a construir. La modelación fina de cada una todavía no está
cerrada: cuando se cierre, se anota acá.

- **Flota** — los vehículos y sus datos.
- **Inspecciones** — el chequeo que alguien le hace a un vehículo, con su
  resultado.
- **Defectos** — lo que una inspección encuentra mal.
- **Órdenes de trabajo** — el trabajo de reparación que se abre para resolver
  defectos.
- **Mantenimiento preventivo** — lo que hay que hacer antes de que falle, por
  tiempo o por uso.
- **Login y roles** — quién entra y qué puede hacer.

Hoy no hay nada de esto implementado: los dos repos son esqueletos que se hablan
por un endpoint de health.

## Arquitectura

Dos repos separados, no monorepo.

| Repo | Carpeta local | Qué es |
|---|---|---|
| `fleet-maintenance` | `TIP - Backend` | API REST en Java 21 |
| `fleet-maintenance-fe` | `TIP - Frontend` | SPA en React 19 + TypeScript |

Se comunican por REST sobre `http://localhost:8080`. El frontend toma esa URL de
`VITE_API_BASE_URL`. El contrato está en `API.md`, escrito a mano.

## Decisiones y por qué

**Backend sin frameworks.** `com.sun.net.httpserver.HttpServer` del JDK y JDBC
pelado, sin Spring ni ORM. Es una decisión del equipo: en un TIP se aprende más
viendo cómo funciona un servidor HTTP y una query que aprendiendo a configurar
un framework. Costo aceptado: más código repetido (parseo de rutas, armado de
JSON, manejo de conexiones). Si en algún momento ese costo supera al beneficio,
se revisa acá y se anota el cambio con fecha.

**JSON armado a mano.** Corolario de lo anterior. Sin Jackson ni Gson. Se
revisará cuando haya suficientes endpoints con payloads anidados como para que
el `String.format` sea un peligro real.

**Repos separados.** Se despliegan y evolucionan por separado, y el equipo puede
trabajar en uno sin tocar el otro.

**La documentación compartida vive en `fleet-maintenance/docs/`**, no en un
tercer repo. Cinco markdown no justifican un repo entero, con su clone, su
permiso y su historial. Costo aceptado: los commits de estado se mezclan en el
historial del backend. Se mitiga con la regla de que el commit de estado va solo
y toca únicamente `docs/`.

**Contrato de API en markdown, no OpenAPI.** Un `API.md` que se lee de un vistazo
vale más hoy que un spec que hay que mantener y del que nadie genera nada.
Se migra a OpenAPI cuando pase cualquiera de estas tres cosas: haya más de ~10
endpoints estables, aparezca el primer bug por un campo desalineado, o se quieran
generar los tipos de TypeScript automáticamente.

**El contenido va en `AGENTS.md`, y `CLAUDE.md` es un puntero de una línea.**
Claude Code lee únicamente `CLAUDE.md`; otras herramientas (OpenCode, Cursor,
Codex) leen `AGENTS.md`. Poner el contenido en el formato universal y hacer que
`CLAUDE.md` lo importe con `@AGENTS.md` deja las reglas en un solo lugar, legibles
por cualquier herramienta y por cualquier humano. Duplicarlas es garantía de que
se desincronicen.

**Todo el contexto vive dentro de los repos.** Reglas, documentación, comandos y
subagentes están en `AGENTS.md`, `docs/` y `.claude/`, todo versionado. Nada
queda en una carpeta suelta de una sola máquina: quien clona el repo recibe el
sistema completo, sin que nadie tenga que pasarle archivos por chat.

**El backend es la casa del proyecto.** Claude Code se abre parado en
`TIP - Backend`, que es donde están `docs/` y `.claude/`, y llega al frontend
como directorio adicional (`.claude/settings.json`). Es la única forma de tener
los comandos versionados y ver los dos repos en la misma sesión.

**Agentes por modo de trabajo, no por área del código.** Un agente "backend" y
uno "frontend" duplicarían lo que ya dice cada `AGENTS.md`, que se carga solo.
Lo que sí cambia el comportamiento es *cómo* se trabaja: proponer sin tocar
(plan mode), construir (modo normal), revisar sin poder editar (`revisor`).

**Guías en markdown plano, no skills.** Una guía en `docs/guias/` la lee
cualquier herramienta y cualquier persona del equipo, hoy y dentro de dos años.
Una skill la lee una sola herramienta. Cuando una guía se vuelva un procedimiento
que se repite idéntico muchas veces, ahí se evalúa convertirla.

## Convenciones

- Java: paquete `org.example`, un handler por endpoint en `Main.java` por ahora.
- Frontend: Oxlint como linter, TypeScript estricto vía `tsc -b` en el build.
- Sin CI/CD todavía. Sin convención de nombres de commit todavía.
- Rama única `main` en los dos repos. Sin PRs por ahora.
