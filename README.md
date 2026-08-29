# fleet-maintenance

Backend del proyecto **FleetGuard** — mantenimiento preventivo e inspecciones de flota.

## Stack

- Java 21
- Gradle (Kotlin DSL)
- `com.sun.net.httpserver.HttpServer` (servidor HTTP incluido en el JDK, sin frameworks)
- PostgreSQL (driver JDBC)

## Requisitos previos

- JDK 21+
- PostgreSQL corriendo localmente, con una base de datos llamada `TIP`

## Levantar el proyecto

```bash
./gradlew run
```

El servidor queda disponible en `http://localhost:8080`.

## Configuración

Copiar `db.properties.example` a `db.properties` dentro de `src/main/resources` y completar la password de tu usuario de Postgres:

```bash
cp src/main/resources/db.properties.example src/main/resources/db.properties
```

```
db.host=localhost
db.port=5432
db.name=TIP
db.user=postgres
db.password=tu_password
```

> `db.properties` está en `.gitignore` — nunca se sube al repo.

## Base de datos

Todavía no hay herramienta de migraciones (ver `docs/STATE.md`). El schema se
aplica a mano con `psql`:

```bash
psql -U postgres -d TIP -f sql/schema.sql
```

## Scripts

- `./gradlew run` — levanta el servidor
- `./gradlew build` — compila, corre tests y empaqueta
- `./gradlew test` — corre los tests

## Endpoints

- `GET /api/health` — chequea que el servidor esté arriba y que la conexión a Postgres (base `TIP`) funcione correctamente.
- `GET /api/defectos` — listado de defectos ordenado por gravedad y fecha.

## Estructura

```
src/main/java/org/example/
├── Main.java                # entry point: levanta el HttpServer y registra los endpoints
└── DatabaseConnection.java  # utilidad para obtener conexiones JDBC a Postgres
```

A partir de acá se van a ir agregando los endpoints del dominio (inspecciones, defectos,
órdenes de trabajo, flota, mantenimiento preventivo, login/roles).
