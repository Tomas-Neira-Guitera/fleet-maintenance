---
description: Cierre de sesión — redacta el resumen de estado y espera confirmación
allowed-tools: Bash(git *)
---

Cambios sin commitear en backend:
!`git status --short`
!`git diff --stat`

Cambios sin commitear en frontend:
!`git -C "../TIP - Frontend" status --short`
!`git -C "../TIP - Frontend" diff --stat`

Commits de hoy:
!`git log --oneline --since=midnight`
!`git -C "../TIP - Frontend" log --oneline --since=midnight`

Leé `docs/guias/sesiones.md` y seguí el procedimiento de `/cierre`.

REGLA QUE NO SE ROMPE: mostrame el borrador del bloque de estado y **detenete**.
No escribas `docs/STATE.md` hasta que yo lo confirme explícitamente.
