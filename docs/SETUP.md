# Setup — de cero a los dos repos corriendo

## Requisitos

- JDK 21+
- Node 20+
- PostgreSQL corriendo local
- Git
- Claude Code

## 1. Clonar los dos repos como carpetas hermanas

```bash
mkdir TIP && cd TIP
git clone https://github.com/Tomas-Neira-Guitera/fleet-maintenance.git "TIP - Backend"
git clone https://github.com/Tomas-Neira-Guitera/fleet-maintenance-fe.git "TIP - Frontend"
```

Los nombres de carpeta **importan**: `CLAUDE.md`, `.claude/settings.json` y los
comandos usan `../TIP - Frontend` tal cual. Si los ponés distinto, hay que
ajustar esas rutas.

## 2. Base de datos

Creá una base llamada `TIP` en tu Postgres local:

```sql
CREATE DATABASE "TIP";
```

## 3. Credenciales del backend

`src/main/resources/application-local.yml` está en `.gitignore`, así que no
viene en el clone. Copiá el ejemplo y completá tu password:

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

```yaml
spring:
  datasource:
    username: postgres
    password: tu_password
```

## 4. Levantar el backend

```bash
cd "TIP - Backend"
./gradlew bootRun
```

Verificar: `curl http://localhost:8080/api/defects` tiene que devolver `[]` (o
la lista de defectos si ya cargaste datos) sin pedir autenticación. Todavía no
hay un endpoint de health en Spring Boot — ver `docs/API.md`.

## 5. Levantar el frontend

```bash
cd "TIP - Frontend"
npm install
cp .env.example .env
npm run dev
```

Queda en `http://localhost:5173`.

## 6. Contexto para la IA

**No hay que instalar ni copiar nada.** Todo el contexto —`AGENTS.md`, `docs/`,
`CLAUDE.md`, los comandos y el subagente `revisor`— viene versionado con el
clone.

Abrí Claude Code **parado en `TIP - Backend`**:

```bash
cd "TIP - Backend"
claude
```

Desde ahí `/retomar` y `/cierre` funcionan, y el frontend queda accesible como
directorio adicional gracias a `.claude/settings.json`.

Chequeo rápido de que quedó bien: corré `/retomar`. Si el comando no existe,
estás parado en la carpeta equivocada. Si existe pero no sabe nada del proyecto,
`CLAUDE.md` no está cargando sus imports. Si no puede leer el frontend, corré
`/add-dir "../TIP - Frontend"` a mano y avisá para arreglar `settings.json`.
