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

Implementado hoy: **Inspecciones** (DVIR pre/post-trip) y **Defectos** (nacen de
una respuesta de checklist), vía CAM-11. Ver `docs/STATE.md` para el detalle y
`docs/api/openapi.yaml` para el contrato. El resto del dominio (flota más allá
del listado, órdenes de trabajo, mantenimiento preventivo, login/roles) sigue
sin construir.

## Arquitectura

Dos repos separados, no monorepo.

| Repo | Carpeta local | Qué es |
|---|---|---|
| `fleet-maintenance` | `TIP - Backend` | API REST en Java 21 |
| `fleet-maintenance-fe` | `TIP - Frontend` | SPA en React 19 + TypeScript |

Se comunican por REST sobre `http://localhost:8080`. El frontend toma esa URL de
`VITE_API_BASE_URL`. El contrato está en `API.md`, escrito a mano.

## Decisiones y por qué

**Backend con Spring Boot, desde 2026-08-31.** Reemplaza la decisión anterior
("sin frameworks", ver más abajo el porqué original). Guido y Tomás venían
trabajando cada uno en su propio stack (`HttpServer`/JDBC a mano en un repo,
Spring Boot en el otro) y habían hablado de converger — Tomás migró primero su
parte (CAM-11, PR
[`fleet-maintenance#2`](https://github.com/Tomas-Neira-Guitera/fleet-maintenance/pull/2))
y la mergeó a `develop` antes de que se terminara de decidir formalmente entre
los dos. Guido confirmó adoptar ese stack como el del equipo y se hizo el merge
de `develop` a `feature/guido`, descartando la implementación vieja
(`HttpServer`/JDBC/JSON a mano) por completo. Costo aceptado: se pierde el
valor pedagógico original de escribir el servidor HTTP a mano; a cambio, todo
el equipo trabaja sobre el mismo stack sin tener que mantener dos arquitecturas
en paralelo.

**Backend sin frameworks — decisión original, revertida el 2026-08-31 (ver
arriba).** `com.sun.net.httpserver.HttpServer` del JDK y JDBC pelado, sin
Spring ni ORM. Fue una decisión del equipo: en un TIP se aprende más viendo
cómo funciona un servidor HTTP y una query que aprendiendo a configurar un
framework. Se mantiene acá como registro histórico de por qué el proyecto
empezó así.

**JSON vía Jackson, desde 2026-08-31.** Corolario de la migración a Spring
Boot — reemplaza el JSON armado a mano con `String.format`. Los DTOs son
`record`s de Java; Jackson los deserializa desde el JSON de entrada usando los
nombres de los parámetros del constructor (compilador con `-parameters`, sin
`@JsonCreator` manual).

**Arquitectura en capas en el backend, desde 2026-08-28 — vigente, con capas
nuevas desde 2026-08-31.** Arrancó como `controller`/`model`/`dao` (reemplazando
"un handler por endpoint en `Main.java`") a pedido de Guido, antes de que
doliera de verdad. Con la migración a Spring Boot se suman `service`,
`repository`, `dto`, `mapper` y `exception` — las capas que trae Tomás de CAM-11.
El criterio de fondo no cambió: mantener la misma estructura desde el principio
en vez de refactorizar más adelante. El molde queda anotado en `AGENTS.md` →
"Estructura hoy".

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

- Java: paquete `org.example`, package-by-layer (ver `AGENTS.md`).
- Frontend: Oxlint como linter, TypeScript estricto vía `tsc -b` en el build.
- Sin CI/CD todavía. Sin convención de nombres de commit todavía.
- **Ramas y PRs, desde 2026-08-31.** Ya no es rama única sin PRs: hay `main`,
  `develop` y ramas de feature (`feature/guido`, `feature/CAM-11-dvir-checklist`)
  en los dos repos, con PRs de feature branches hacia `develop`/`main` en
  GitHub. El flujo exacto (¿todo pasa por `develop`? ¿quién aprueba?) todavía no
  está escrito en ningún lado — anotado como decisión abierta en `STATE.md`.
