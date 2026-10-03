# CAM-32 — Descripciones por audio

Acompaña a `openapi.yaml` (mismo directorio) con las decisiones que el spec no
expresa por sí solo.

## Motivación

CAM-32 pide que el chofer y el técnico puedan dictar una descripción desde el celular en
vez de escribirla. El dictado se resuelve en el frontend con el reconocimiento de voz del
navegador (Web Speech API): el backend no recibe audio ni transcribe nada, solo guarda el
texto.

## Decisiones

### 1. El defecto tiene título y descripción
Desde CAM-79 `description` está limitado a 30 caracteres porque funciona como título (se
copia a la programación y a la OT). Para que dictar tenga sentido hace falta un texto largo
aparte:
- `description`: título corto, obligatorio, hasta 30 caracteres. Sin cambios.
- `details`: descripción larga, **opcional**, hasta 1000 caracteres. Columna nueva
  `defects.details` (nullable), así que los defectos anteriores quedan con `details` null.

Se mantiene el nombre `description` para el título para no romper datos ni clientes
existentes; en pantalla los rótulos son "Título" y "Descripción".

### 2. Dónde viaja `details`
- Entra en `DefectDetail`, dentro de cada respuesta de `POST /api/inspections/{vehicleId}`.
  Un string vacío se guarda como null. Si supera los 1000 caracteres, `422` con el `itemId`.
- Sale en `DefectSummary`: `GET /api/defects`, el historial del vehículo y el `defect` de
  una orden de trabajo.

### 3. La descripción de cierre de la OT no cambia
`closingDescription` ya era texto largo. El dictado del técnico solo cambia cómo se carga
el texto en el frontend.

## Fuera de alcance

- Recibir, guardar o transcribir audio en el backend.
- Generar el título a partir de la descripción.
