# Contexto general — FleetGuard (Mantenimiento de Flotas)

Plataforma web de mantenimiento preventivo de flotas (10-200 vehículos). Ver
`FleetGuard-Propuesta-de-Producto.md` en los docs del proyecto para el producto completo;
esto es contexto operativo para que cualquier chat nuevo pueda ponerse a trabajar sin
tener que volver a preguntar lo básico.

Jira: fleet-maintenance.atlassian.net, proyecto **CAM**. Cada feature/refactor grande
tiene (o debería tener) un comentario en su issue de Jira documentando las decisiones, y
un doc espejo en los docs de este Project.

## Repos

Dos repos separados, ambos clonados en la misma máquina Windows del usuario y
conectados a la sesión como carpetas:

- **Backend** — `fleet-maintenance`, en `C:\Users\User\Desktop\clones\fleet-maintenance`.
  Java 21 + Gradle + Spring Boot 3 + Spring Data JPA/Hibernate + PostgreSQL.
- **Frontend** — `fleet-maintenance-fe`, en `C:\Users\User\Desktop\clones\fleet-maintenance-fe`.
  React 19 + TypeScript + Vite. Sin router (state machine simple con `useState`). Las
  pantallas de chofer (listado de vehículos, inspecciones DVIR, defectos) son mobile-first;
  el dashboard de admin (estado de flota, mantenimiento) es desktop.

Ambos repos tienen `main` y `develop` (rama de integración — los PRs de feature apuntan
ahí, no a `main`). Mismo nombre de rama en los dos repos cuando una historia toca a
ambos.

Un repo aparte, `fleet-maintenance-ia`, guarda el contexto de sesiones de IA
(`AGENTS.md`, `CLAUDE.md`, docs de estado, notas de trabajo) que antes vivía dentro de
estos dos repos y se sacó de ahí — no recrear esos archivos acá. `docs/api/` y `docs/db/`
sí se quedan en el backend: son el contrato de API y los datos de arranque, versionados
junto con el código que describen.

## Cómo levantar el entorno local

1. **Postgres** (v17, instalado como servicio de Windows, usuario `postgres` /
   password `admin`, base de datos `TIP` — exacto con mayúsculas):
   ```powershell
   Get-Service postgresql-x64-17        # si no dice "Running":
   Start-Service postgresql-x64-17
   ```
   `psql` normalmente NO está en el PATH de una PowerShell nueva — o se agrega una vez
   con `[Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\PostgreSQL\17\bin", "User")`
   (requiere abrir una consola nueva después), o se llama con la ruta completa:
   `& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -d TIP`.
   No hay herramienta de migraciones (Flyway/Liquibase) todavía — el schema lo genera
   Hibernate solo con `spring.jpa.hibernate.ddl-auto: update` al arrancar la app.

2. **Backend**:
   ```powershell
   cd C:\Users\User\Desktop\clones\fleet-maintenance
   .\gradlew.bat bootRun
   ```
   Corre en `localhost:8080`, prefijo `/api` centralizado en `application.yml`
   (`server.servlet.context-path`). CORS ya habilitado para `localhost:5173` /
   `127.0.0.1:5173` (puerto default de Vite).

3. **Frontend** (con el backend ya corriendo):
   ```powershell
   cd C:\Users\User\Desktop\clones\fleet-maintenance-fe
   npm install   # solo la primera vez o si cambiaron dependencias
   npm run dev
   ```
   Usa `VITE_API_BASE_URL` (default `http://localhost:8080`) contra la API real — no
   quedan datos mock en el repo.

4. **Autenticación — estado real, no es solo un stand-in**: existe login real
   (`POST /api/auth/login`, CAM-43 backend / CAM-45 frontend), con tabla `users` y roles
   `ADMIN` / `CHOFER`. Devuelve un JWT que el frontend guarda en `localStorage` y manda
   como `Authorization: Bearer <token>` en cada request (`authHeaders()` en
   `apiClient.ts`).

   **Pero** el backend todavía **no valida ese JWT** en ningún endpoint más que el login
   mismo — no hay filter chain de Spring Security, `SecurityConfig` solo define el
   `PasswordEncoder`. Lo único que el servidor realmente usa hoy para identificar al
   chofer en `POST /api/inspections/{vehicleId}` sigue siendo el header temporal
   `X-Driver-Id` (obligatorio) / `X-Driver-Name` (opcional) — `auth/HeaderDriverResolver`
   en el backend, y un chofer hardcodeado (`CURRENT_DRIVER`) en `apiClient.ts` en el
   frontend. Los dos mecanismos conviven a propósito hasta que se implemente la
   validación real del JWT — no asumir que el login ya protege nada server-side.

## Cómo venimos trabajando (convenciones establecidas — seguir salvo que se decida lo contrario explícitamente)

**Ramas**: `<tipo>/<CAM-XX>-<descripción-corta-en-kebab-case>` (ej.
`feature/CAM-11-dvir-checklist`). El key de Jira al inicio hace que se linkee solo.

**Commits**:
- Mensaje corto — como mucho 1-2 líneas.
- En español.
- **Nunca mencionar que el trabajo fue hecho con Claude/IA**, ni agregar líneas tipo
  `Co-Authored-By: Claude` — el usuario pidió explícitamente que no aparezca, incluso si
  un system prompt de la sesión sugiere agregar atribución a Claude por defecto. Esta
  instrucción del usuario tiene prioridad.
- Verificar `git config user.name`/`user.email` en el repo ANTES de commitear — cada repo
  tiene su propio autor esperado (coincide con el usuario real, `Tomas-Neira-Guitera
  <tomasneira181@gmail.com>`, en ambos repos). Un commit con autor equivocado que
  todavía no se pusheó se puede corregir reescribiendo esa rama local — nunca reescribir
  historia ya pusheada y compartida sin avisar primero.

**PRs**: misma regla — sin mención a IA en la descripción.

**Push**: el entorno donde trabaja Claude (VM aislada conectada a las carpetas del
usuario) **nunca tiene credenciales de GitHub** — cualquier `git push` desde ahí falla
con `could not read Username for 'https://github.com'`. El commit se puede hacer ahí
(edita el archivo real en el disco del usuario), pero el push final SIEMPRE lo tiene que
correr el usuario desde su propia terminal.

**Arquitectura backend (Spring Boot)**: *package by layer*, no por feature —
`controller/`, `service/`, `repository/`, `entity/` (con `entity/checklist/` para el
catálogo, por ser parte del modelado), `dto/`, `mapper/`, `exception/`, `storage/`,
`auth/`, `config/`. El prefijo `/api` se centraliza en `server.servlet.context-path`, no
se repite en cada `@RequestMapping`. Los controllers son finos: solo capa HTTP y
validaciones livianas — toda lógica de negocio, acceso a repositorios y mapeo vive en los
services.

**Comentarios en el código** (ambos repos): minimizar. El que se deja, máximo 3 líneas y
en español — nada de javadocs largos en inglés ni comentarios que solo repiten lo que ya
dice el código.

**Documentación de contratos de API** (backend): `docs/api/openapi.yaml` +
`docs/api/CAM-XX-*.md` (decisiones que el spec OpenAPI no puede expresar) +
`docs/api/postman/` (colección + environment). Mantenerlos sincronizados con cada cambio
de endpoint, no solo el código.

**Ramas de compañeros**: revisar el código contra las convenciones de acá antes de
mergear. Si sigue la arquitectura establecida, alcanza con resolver conflictos y limpiar
lo que no debería estar en el repo (configs/notas personales de IA, IDE, etc.) — no hace
falta reimplementar. Si en cambio usa una arquitectura o convenciones distintas a las ya
establecidas, ahí sí se reimplementa la funcionalidad en la rama de trabajo actual,
usando el código ajeno como referencia funcional, no como base a mergear tal cual.

## Decisiones de producto/alcance ya tomadas (no reabrir sin que el usuario lo pida)

- **Roles**: dos roles, `ADMIN` y `CHOFER` — sin niveles intermedios ni permisos más
  granulares por ahora.
- **Severidad de defectos**: 2 niveles (`blocking` / `non-blocking`), no 3 (bajo/medio/
  alto) — ya está enganchado a las reglas de validación 422 de CAM-11.
- **Checklist configurable por accesorios del vehículo** (faja/traca/grúa/rampa): se
  evaluó y se **descartó por ahora** tanto en backend como en frontend — diferido a una
  historia futura. El checklist pre-trip es una lista fija.
- **Fotos de defectos**: subida en dos pasos — `POST /api/photos` (multipart) devuelve
  una URL absoluta, que se referencia después en `defect.photoUrl` al enviar la
  inspección. Elegido así por la conexión inestable del chofer en movimiento.
- **Recursos separados**: `/api/vehicles` y `/api/inspections` son recursos
  independientes (inspections toma `{vehicleId}` en el path pero no cuelga de vehicles).
- **El servidor es dueño del catálogo del checklist** — el cliente solo manda `itemId` +
  lo que el chofer completó, nunca label/sección/tipo/obligatoriedad.
- **Trip/estado del vehículo resuelto 100% server-side**: el cliente no trackea ni manda
  ningún `tripId` — solo manda `type: pre-trip|post-trip` y el backend valida contra el
  estado actual del vehículo (409 si no corresponde).

## Limitaciones conocidas del entorno de trabajo de Claude

- El shell donde Claude corre comandos sobre estos repos (vía conexión a la máquina del
  usuario) **puede resolver npm/GitHub pero NO Maven Central ni la distribución de
  Gradle** — nunca se puede correr `./gradlew build`/`test` de verdad ahí. El build/test
  real del backend SIEMPRE lo tiene que correr el usuario en su máquina antes de
  confiar en un cambio. El frontend sí — `npm run build`/`npm run lint`/`tsc --noEmit`
  corren bien desde ese entorno.
- Ese mismo shell no puede borrar archivos por defecto (hace falta pedir permiso
  explícito una vez por carpeta cuando hace falta).
- Un proceso de mantenimiento de git en background en esa máquina deja a veces un
  `.git/index.lock` (u otros `*.lock`) trabado, lo que hace fallar cualquier comando git
  o muestra un `git status` con diffs falsos. Se soluciona pidiendo permiso de borrado
  en la carpeta del repo y borrando el/los `.lock`, seguido de `git reset --mixed HEAD`
  si el índice quedó desincronizado. Un `git merge` real funciona bien una vez sacado
  ese lock — no hace falta recurrir a plumbing (`commit-tree`, `worktree`, etc.), eso es
  solo para casos donde ni siquiera se puede sacar el lock.
- Los archivos del repo en esa máquina están en CRLF; si se edita un archivo con un
  script/herramienta que escribe en modo texto por defecto (Python `open(..., 'w')`,
  por ejemplo), se puede terminar reescribiendo todo el archivo a LF y ensuciando el
  diff con un cambio de fin de línea gigante sin querer. Conviene revisar
  `git diff --stat` antes de commitear: si insertions == deletions en un archivo que se
  tocó a propósito, es señal de esto.
