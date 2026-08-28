# FleetGuard — backend (`fleet-maintenance`)

Este repo es también la casa de la documentación compartida del proyecto: en
`docs/` viven el contexto, el estado y el contrato de API que usan los dos repos.
**Abrí Claude Code parado acá**, no en la carpeta padre ni en el frontend.

Las reglas de código y el contexto se cargan solos desde estos archivos:

@AGENTS.md
@docs/PROJECT.md
@docs/STATE.md
@docs/API.md

## El otro repo

El frontend (`fleet-maintenance-fe`) está en la carpeta hermana `TIP - Frontend`.
`.claude/settings.json` lo agrega como directorio adicional, así que podés leerlo
y editarlo desde esta sesión.

**Antes de tocar cualquier archivo del frontend, leé `../TIP - Frontend/AGENTS.md`.**
No se importa automáticamente acá para no cargarlo en las sesiones que son solo
de backend.

Si el acceso al frontend no funciona, corré `/add-dir "../TIP - Frontend"` a mano
y avisame que hay que arreglar `settings.json`.

## Documentación que se lee a pedido

- `docs/ROADMAP.md` — qué falta construir, por hitos
- `docs/SETUP.md` — armar el proyecto en una máquina nueva
- `docs/guias/sesiones.md` — el ritual de `/retomar` y `/cierre`, y la referencia
  rápida de cómo se usa todo esto
- `docs/guias/nuevo-endpoint.md` — agregar un endpoint de punta a punta
