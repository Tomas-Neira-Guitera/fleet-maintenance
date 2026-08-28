# AGENTS.md — fleet-maintenance (backend)

Backend de **FleetGuard**. Antes de escribir código leé `docs/PROJECT.md`
(qué es el sistema y por qué está construido así) y `docs/API.md` (el contrato
con el frontend). El estado de avance vive en `docs/STATE.md`.

## Stack

| Qué | Con qué |
|---|---|
| Lenguaje | Java 21 |
| Build | Gradle 8.14, Kotlin DSL (`build.gradle.kts`) |
| Servidor HTTP | `com.sun.net.httpserver.HttpServer` (viene con el JDK) |
| Base de datos | PostgreSQL, driver JDBC `org.postgresql:postgresql:42.7.4` |
| Tests | JUnit 5 (ya configurado, `src/test/java` todavía vacío) |

## Estructura hoy

```
src/main/java/org/example/
├── Main.java                # levanta el HttpServer en :8080 y registra los handlers
└── DatabaseConnection.java  # abre conexiones JDBC leyendo db.properties
src/main/resources/
└── db.properties            # credenciales locales — está en .gitignore
```

`rootProject.name` es `TIP`, por eso el jar sale como `TIP-1.0-SNAPSHOT.jar`.

## Reglas

**Sin frameworks.** Nada de Spring, Micronaut, Javalin, Jackson, Hibernate ni
similares. Es una decisión deliberada del equipo, no una deuda pendiente. Si una
tarea parece pedir un framework, la respuesta correcta es resolverla con el JDK
y avisarme, no agregar la dependencia.

**Agregar una dependencia es una decisión, no un detalle.** Proponela y esperá
confirmación. La única que hay hoy es el driver de Postgres.

**JDBC directo, siempre con `PreparedStatement`.** Nunca concatenes valores
dentro del SQL. Cerrá siempre `Connection`, `Statement` y `ResultSet` con
try-with-resources, como hace `handleHealth` en `Main.java`.

**JSON a mano por ahora.** Se arma con `String.format` y se escapan las comillas
de los valores que vienen de afuera. Cuando esto empiece a doler de verdad,
discutimos meter una librería; hasta entonces, a mano.

**Un endpoint nuevo se registra en `Main.java`** con `server.createContext(...)`.
El procedimiento completo, de punta a punta con el frontend, está en
`docs/guias/nuevo-endpoint.md`.

**Todo endpoint nuevo se anota en `docs/API.md` en el mismo cambio.** No después.
Es el único lugar donde el frontend ve qué existe.

**Nunca commitees `src/main/resources/db.properties`.** Tiene credenciales y está
ignorado a propósito.

## Comandos

```bash
./gradlew run      # levanta el servidor en http://localhost:8080
./gradlew build    # compila, corre tests y empaqueta
./gradlew test     # solo tests
```

## El otro repo

El frontend es `fleet-maintenance-fe`, en la carpeta hermana `TIP - Frontend`.
Consume este backend vía `VITE_API_BASE_URL` (por defecto `http://localhost:8080`).
Si cambiás la forma de una respuesta, el frontend se rompe en silencio: actualizá
`docs/API.md` y decime qué hay que tocar del otro lado.
