# fleet-maintenance

Backend del proyecto **FleetGuard** — mantenimiento preventivo e inspecciones de flota.

## Stack

- Java 21
- Gradle (Kotlin DSL)
- Spring Boot 3.3.x (Spring MVC + Spring Data JPA) — reemplaza el `com.sun.net.httpserver.HttpServer` y el JDBC plano usados antes
- PostgreSQL (`spring-boot-starter-data-jpa` + driver JDBC; `ddl-auto: update`, sin herramienta de migraciones todavía — ver "Próximos pasos")

## Requisitos previos

- JDK 21+
- PostgreSQL corriendo localmente, con una base de datos llamada `TIP`

## Levantar el proyecto

```bash
./gradlew bootRun
```

El servidor queda disponible en `http://localhost:8080`.

## Configuración

La configuración vive en `src/main/resources/application.yml` (valores por defecto:
`localhost:5432/TIP`, usuario `postgres`, sin password). Para no editar ese archivo con
tus credenciales locales, copiá el ejemplo a `application-local.yml` (gitignored, mismo
directorio) y completá ahí lo que necesites — reemplaza al viejo patrón
`db.properties` / `db.properties.example`:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

```yaml
spring:
  datasource:
    username: postgres
    password: tu_password
```

> `application-local.yml` está en `.gitignore` — nunca se sube al repo.

## Scripts

- `./gradlew bootRun` — levanta el servidor
- `./gradlew build` — compila, corre tests y empaqueta
- `./gradlew test` — corre los tests (los de `InspectionValidatorTest` / `ChecklistCatalogTest` no necesitan Postgres; no hay tests de contexto Spring con DB todavía)
- `./gradlew compileJava` — solo compila

## Endpoints

Implementados para CAM-11 (inspecciones DVIR del chofer) — contrato completo en
`docs/api/openapi.yaml` y `docs/api/CAM-11-dvir-contract.md`:

- `GET /api/vehicles` — lista de vehículos con estado (disponible/en viaje).
- `POST /api/photos` — sube la foto de un defecto (multipart), devuelve `photoUrl`.
- `GET /api/photos/{id}` — sirve la foto subida (implementación local, ver más abajo).
- `POST /api/inspections/{vehicleId}` — envía una inspección pre-trip o post-trip.
- `GET /api/defects` — lista de defectos reportados, para mantenimiento (CAM-13).

Requieren, además del `Authorization: Bearer <token>` que pide el contrato (todavía no
hay auth real), un header **temporal** `X-Driver-Id` (y opcionalmente `X-Driver-Name`)
que hace de stand-in del chofer resuelto por token — ver `auth/DriverResolver`.

## Estructura

Organización package-by-layer (capas técnicas), con un par de paquetes propios
para lo que no encaja en una capa:

```
src/main/java/org/example/
├── FleetGuardApplication.java   # entry point Spring Boot
├── controller/                  # @RestController: bind/validación HTTP, delegan a service/
├── service/                     # lógica de negocio (casos de uso) -- incluye InspectionValidator
├── repository/                  # interfaces Spring Data JpaRepository
├── entity/                      # entidades @Entity + enums que las acompañan
│   └── checklist/               # catálogo server-side del checklist (espejo de checklistDefinitions.ts)
├── dto/                         # DTOs de request/response que matchean openapi.yaml, + ApiError/ValidationError
├── mapper/                      # entidad <-> DTO
├── exception/                   # excepciones de dominio + @RestControllerAdvice uniforme (404/409/422/...)
├── storage/                     # PhotoStorage (interfaz) + implementación local -- infraestructura, no repo JPA
└── auth/                        # DriverResolver (interfaz) + stand-in por header, TEMPORAL hasta que exista auth real -- transversal, no una capa
```

Los paths reales de la API (`/api/vehicles`, `/api/photos`, `/api/inspections/{vehicleId}`)
no cambian: el prefijo `/api` ya no se repite en cada `@RequestMapping` de cada
controller, sino que se centraliza una sola vez en `server.servlet.context-path`
(ver `application.yml`).

## Decisiones e implementación (a tener en cuenta)

- **Fotos**: se guardan en el filesystem local (`./uploads/photos`, configurable por
  `fleetguard.photos.storage-dir`) y se sirven por `GET /api/photos/{id}`. No hay storage
  en la nube configurado en este repo; cambiarlo a algo tipo S3 debería ser solo swapear
  la implementación de `storage.PhotoStorage`, sin tocar el resto de la app.
- **Identidad del chofer**: `auth.HeaderDriverResolver` es un stand-in temporal que lee
  `X-Driver-Id`/`X-Driver-Name` en vez de resolver el chofer de un token verificado. Se
  reemplaza por una implementación real de `auth.DriverResolver` cuando exista la
  historia de login/roles — el resto de la app depende de la interfaz, no del header.
- **Esquema de datos**: `spring.jpa.hibernate.ddl-auto: update` — suficiente para este
  MVP en movimiento rápido. Agregar Flyway/Liquibase es un buen próximo paso, pero
  queda fuera de este alcance.

## Próximos pasos (fuera de este alcance)

- Login/roles reales (reemplaza `auth.HeaderDriverResolver`).
- Gestión de defectos/órdenes de trabajo más allá del listado (`GET /api/defects` ya
  está implementado, ver `entity/Defect.java`).
- Herramienta de migraciones (Flyway/Liquibase) en vez de `ddl-auto: update`.
- Resto del CRUD de `/api/vehicles` (alta, baja, edición, ficha completa) — historia de
  gestión de flota.
- Checklist configurable por accesorios del vehículo (faja/traca/grúa/rampa) — se sacó
  de esta historia, ver `docs/api/CAM-11-dvir-contract.md`.
