# Roadmap — FleetGuard

Qué falta construir, en orden. Cambia por hito, no por sesión: el día a día va
en `STATE.md`.

Este archivo **no** se carga automáticamente en el contexto de la IA. Se lee a
pedido, cuando toca planificar.

---

## Hito 0 — Arreglar lo que ya está roto

Cosas chicas que hoy hacen que el esqueleto no funcione del todo. Salen en una
sesión corta y sacan ruido de encima.

- [x] **`App.tsx` llama a `/api/ping`, que no existe.** El backend expone
      `/api/health`. Hoy el indicador de la home dice "offline" siempre.
      Corregir la ruta y el `type` de la respuesta según `API.md`.
- [x] **Falta `db.properties.example` en el repo del backend.** El README y
      `DatabaseConnection.java` le dicen a quien clona que lo copie, y no existe.
      Quien clone hoy no puede levantar el backend sin preguntarle a alguien.
- [ ] **El remote del backend tiene un token de GitHub embebido en la URL**
      (`.git/config` local, no está en el repo). Reemplazado el remote local por
      la URL limpia (2026-08-28). Falta que **Tomás** revoque el token desde su
      cuenta de GitHub — el token es suyo, no de Guido, así que no aparece en la
      lista de tokens de Guido.
- [x] **Fijar la versión de Java en `build.gradle.kts`** con un toolchain
      explícito, para que no dependa del JDK que tenga cada uno instalado.

## Hito 1 — Decisiones transversales

Baratas ahora, caras después de diez endpoints. Se cierran discutiéndolas y se
anotan en `API.md` y `PROJECT.md`.

- [ ] Forma del error de la API: qué claves tiene siempre una respuesta de error.
- [ ] Paginación de listados: si la hay, y con qué parámetros.
- [ ] Autenticación: qué mecanismo, qué header, qué pasa cuando falta o vence.
- [ ] Modelo de datos: entidades, relaciones y script de creación de tablas.
      Falta decidir si el schema se versiona en el repo y cómo se aplica.

## Hito 2 — Login y roles

- [ ] Tablas de usuarios y roles.
- [ ] `POST /api/auth/login`.
- [ ] Protección de los endpoints que la necesiten.
- [ ] Pantalla de login y manejo de sesión en el frontend.

## Hito 3 — Flota

El CRUD más simple del dominio: sirve para fijar el molde que van a seguir todos
los demás (handler, acceso a datos, pantalla, manejo de errores).

- [ ] Endpoints de vehículos: listar, ver, crear, editar, baja.
- [ ] Pantallas de flota.

## Hito 4 — Inspecciones y defectos

- [x] Modelo y endpoints de inspecciones — CAM-11, ver `docs/api/openapi.yaml`.
- [x] Defectos como resultado de una inspección — `GET /api/defects`.
- [x] Pantalla de carga de inspección en el frontend — `InspectionFlow.tsx`,
      contra la API real (sin mocks).
- [x] Pantalla de consulta/listado de defectos en el frontend —
      `DefectsList.tsx`, reemplaza a la vieja `DefectosList.tsx` (CAM-13,
      descartada en el merge del 2026-08-31 por apuntar al contrato viejo).
      Agregada una navegación simple de tabs (Flota/Defectos) en `App.tsx` —
      todavía sin router, ver decisión abierta en `STATE.md`.

## Hito 5 — Órdenes de trabajo

- [ ] Ciclo de vida: abrir, asignar, cerrar.
- [ ] Relación con los defectos que la originan.

## Hito 6 — Mantenimiento preventivo

- [ ] Planes por tiempo o por uso.
- [ ] Cálculo de vencimientos y alertas.

---

## Deuda conocida, sin fecha

- **Sin tests.** JUnit 5 ya está configurado en `build.gradle.kts` y
  `src/test/java` está vacío. El primer test conviene escribirlo junto con el
  primer endpoint de dominio, no antes: hoy no hay lógica que testear.
- **Sin CI/CD.** Cuando haya tests, un workflow que corra `./gradlew build` y
  `npm run build` en cada push.
- ~~**`Main.java` va a crecer demasiado.**~~ Resuelto el 2026-08-28: se separó
  en capas (`controller`/`model`/`dao`), y esas capas se sumaron a Spring Boot
  el 2026-08-31 (ya no hay `Main.java`, ver `PROJECT.md`).
- ~~**JSON a mano.**~~ Resuelto el 2026-08-31: Jackson vía Spring MVC.
- **Contrato en markdown vs. OpenAPI, conviven los dos.** `API.md` para lo
  viejo/pendiente, `docs/api/openapi.yaml` para CAM-11. Ver `API.md` para el
  detalle y los disparadores de migración originales.
