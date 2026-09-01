# AGENTS.md — fleet-maintenance (backend)

Backend de **FleetGuard**. Antes de escribir código leé `docs/PROJECT.md`
(qué es el sistema y por qué está construido así) y `docs/API.md` (el contrato
con el frontend). El estado de avance vive en `docs/STATE.md`.

## Stack

Migrado a Spring Boot el 2026-08-31 (ver el porqué en `docs/PROJECT.md`).

| Qué | Con qué |
|---|---|
| Lenguaje | Java 21 |
| Build | Gradle 8.14, Kotlin DSL (`build.gradle.kts`) |
| Framework | Spring Boot 3.3.x (Spring MVC + Spring Data JPA + Hibernate) |
| Base de datos | PostgreSQL, `ddl-auto: update` (sin Flyway/Liquibase todavía) |
| Tests | JUnit 5 + `spring-boot-starter-test` |

## Estructura hoy

Package-by-layer, heredada de la migración de Tomás (CAM-11) sobre el molde de
capas que ya existía:

```
src/main/java/org/example/
├── FleetGuardApplication.java   # entry point Spring Boot
├── controller/                  # @RestController: bind/validación HTTP, delegan a service/
├── service/                     # lógica de negocio (casos de uso)
├── repository/                  # interfaces Spring Data JpaRepository
├── entity/                      # entidades @Entity + enums que las acompañan
│   └── checklist/                   # catálogo server-side del checklist (espejo de checklistDefinitions.ts en el FE)
├── dto/                          # DTOs de request/response, + ApiError/ValidationError
├── mapper/                       # entidad <-> DTO
├── exception/                    # excepciones de dominio + @RestControllerAdvice uniforme
├── storage/                      # PhotoStorage (interfaz) + implementación local
└── auth/                         # DriverResolver (interfaz) + stand-in por header, TEMPORAL hasta login real
```

**Un endpoint nuevo toca varias capas:** entidad (si hace falta tabla nueva),
repository, service con la lógica, DTOs de entrada/salida, mapper entidad↔DTO,
y el controller que expone la ruta. El prefijo `/api` se centraliza una sola
vez en `server.servlet.context-path` (`application.yml`) — los controllers
mapean rutas planas (`/vehicles`, no `/api/vehicles`).

`rootProject.name` es `TIP`, por eso el jar sale como `TIP-1.0-SNAPSHOT.jar`.

## Reglas

**Spring Boot es el framework del equipo**, no una deuda ni una excepción. La
regla vieja de "sin frameworks" quedó revertida — ver `docs/PROJECT.md` para
el porqué y la fecha. Lo que sigue vigente es no sumar frameworks *adicionales*
sin decidirlo primero (ver la regla siguiente).

**Agregar una dependencia nueva sigue siendo una decisión, no un detalle.**
Proponela y esperá confirmación antes de sumarla a `build.gradle.kts`.

**JPA + `PreparedStatement` implícito de Spring Data.** No armar SQL a mano
salvo que haga falta un `@Query` explícito — en ese caso, siempre con parámetros
nombrados/posicionales, nunca concatenando valores.

**JSON vía Jackson (default de Spring MVC).** Ya no se arma a mano. Los DTOs
son `record`s; para que Jackson los deserialice desde JSON sin `@JsonCreator`
extra, `build.gradle.kts` agrega `-parameters` al compilador.

**Un endpoint nuevo se registra solo** con las anotaciones de Spring
(`@RestController`, `@RequestMapping`) — no hay un lugar central donde
"registrarlo" a mano. El procedimiento de punta a punta con el frontend
(desactualizado tras esta migración, revisar antes de seguirlo al pie de la
letra) está en `docs/guias/nuevo-endpoint.md`.

**Todo endpoint nuevo se anota en `docs/API.md` en el mismo cambio.** No después.
Es el único lugar donde el frontend ve qué existe. (CAM-11 documentó lo suyo en
`docs/api/openapi.yaml` + `docs/api/CAM-11-dvir-contract.md` en vez de acá —
pendiente decidir si `API.md` pasa a ser un índice que apunta a esos archivos,
o si se vuelca todo a `API.md` de nuevo. Ver `docs/STATE.md`.)

**Nunca commitees `src/main/resources/application-local.yml`.** Tiene
credenciales y está ignorado a propósito — reemplaza al viejo
`db.properties`/`db.properties.example`.

## Comandos

```bash
./gradlew bootRun       # levanta el servidor en http://localhost:8080
./gradlew build         # compila, corre tests y empaqueta
./gradlew test          # solo tests
./gradlew compileJava   # solo compila
```

## El otro repo

El frontend es `fleet-maintenance-fe`, en la carpeta hermana `TIP - Frontend`.
Consume este backend vía `VITE_API_BASE_URL` (por defecto `http://localhost:8080`).
Si cambiás la forma de una respuesta, el frontend se rompe en silencio: actualizá
`docs/API.md` y decime qué hay que tocar del otro lado.
