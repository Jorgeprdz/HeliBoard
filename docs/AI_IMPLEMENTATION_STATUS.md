# Estado de desarrollo: HeliBoard AI

Rama: `feature/dynamic-color-schemes`
PR de código preliminar: https://github.com/Jorgeprdz/HeliBoard/pull/4
Diseño y criterios completos: https://github.com/Jorgeprdz/HeliBoard/pull/3

## Implementado en código (NO verificado por compilación ni dispositivo)

- Mantiene la misma jerarquía/dimensiones de teclas de HeliBoard iOS Glass.
- Icono ✨ reutiliza el botón de portapapeles; **long press abre el portapapeles original**.
- Mismo `SuggestionStripView` para chips IA. Al cerrar, barra de herramientas original.
- Menú para corregir / reescribir / traducir texto seleccionado a través de Gemini API.
- Pantalla para API key BYOK cifrada con Android Keystore (API 23+); no secret en repo.
- Bloqueo en tipos de campo protegidos, tratamiento explícito de texto seleccionado, peticiones asíncronas, cancelación por generación de sesión y reemplazo solo confirmado.
- Extracción contextual básica LOCAL desde texto seleccionado: «Calendario», «Mapa», «Marcar», «Correo», «Copiar CLABE». No requiere Accessibility, no lee chats ajenos. Calendar abre ACTION_INSERT y obliga a completar/revisar los datos sin inventar fecha u hora.

## Pendiente (NO afirmar como implementado)

- Compilar Gradle en GitHub Actions, corregir todos los errores y verificar el APK real.
- Validar interfaz comparada con capturas del Galaxy S25 / DeX.
- Implementar PORT real de motor de transcripción de Ramblr. El micrófono original queda conservado entretanto. No confundir con integración completa.
- Extender extracción de fecha y hora en español (actualmente evento con título sin horario preasignado).
- Tests reales de TextView/InputConnection, cancelación, privacidad, offline, dispositivos.
- Verificar huella del certificado del APK instalado. **Crítico:** certificados de APKs históricos NO coinciden entre sí.

## Identidad del build

Source: `feature/dynamic-color-schemes` (descendiente de versión v4.1-iosglass.4).
`applicationId`: `com.jorgeprdz.ioskeyboard`; debug APK: `com.jorgeprdz.ioskeyboard.debug`.
`versionCode`: 4105 (anterior 4104). Variante debug, NO es firma release.

**Firmas conocidas de logs:**
- GitHub Actions #36277943585 (26 Sep): SHA256 cert `E8:72:CC:90:3C:A5:AC:08:B8:74:06:B2:65:76:26:F3:60:3B:3B:72:C7:5E:50:55:34:32:F3:8C:11:70:FA:92`
- GitHub Actions #36293970462 (27 Sep): SHA256 cert `54:47:1E:4F:B6:F1:BB:0F:B2:7A:37:89:72:D6:D9:2A:F9:66:B0:AF:0A:8A:8F:BF:02:17:3F:DF:2C:FF:D1:CC`
- GitHub Releases `v4.1-iosglass.4-preview.1` (11 Oct): SHA256 cert `5F:19:E8:A9:0B:18:A7:22:E3:EE:BD:FC:F0:B0:78:C1:07:15:C3:B5:F2:39:85:6F:11:D2:77:01:60:62:FD:12`

Los tres certificados son **diferentes**. El paso de CI que cachea `~/.android` NO ofrece garantía observable de firma estable. Para actualizar la app instalada se necesita una APK firmada con la **misma clave privada usada al instalar esa app**. Ni package/version ni el certificado público por sí solos bastan.

## Compilación

El conector GitHub permitió subir cambios pero no disparó Actions con los pushes, y no expone workflow_dispatch. Desde Termux, autenticado en la cuenta:
```bash
gh workflow run build-debug-apk.yml --repo Jorgeprdz/HeliBoard --ref feature/dynamic-color-schemes
```
Tras disparar, revisar resultado con:
```bash
gh run list -R Jorgeprdz/HeliBoard --branch feature/dynamic-color-schemes --limit 5
```
No afirmar archivo descargable antes de verificar un run SUCCESS y su artifact.

## Política de entrega

No instalar encima de la app actual hasta comparar firma del APK instalado y del build. Nunca pedir desinstalar por defecto. Un APK con firma distinta sería solo un artefacto de prueba para una instalación limpia, no una actualización.
