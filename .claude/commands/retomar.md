---
description: Retomar el trabajo — lee STATE.md y lo contrasta con lo que pasó en git
allowed-tools: Bash(git *)
---

Commits recientes del backend:
!`git log --oneline -15`

Commits recientes del frontend:
!`git -C "../TIP - Frontend" log --oneline -15`

Trabajo sin commitear en backend:
!`git status --short`

Trabajo sin commitear en frontend:
!`git -C "../TIP - Frontend" status --short`

Leé `docs/guias/sesiones.md` y seguí el procedimiento de `/retomar`.
No escribas ningún archivo: este comando solo sitúa.
