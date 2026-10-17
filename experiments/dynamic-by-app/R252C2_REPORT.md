# TACTIC KEYBOARD — R2.5.2c-2 REDUCIDO · DEBUG SWITCH + APK DEBUG

**ALCANCE AUTORIZADO POR EL USUARIO:** sólo interruptor ADB exclusivo de compilación debug y APK debug instalable etiquetado **NO VALIDADO**. Esta autorización reemplaza el gate de fase previamente exigido **sólo para generar y probar un APK experimental**; no declara R2.5.2c-1 aprobado, no habilita usuarios normales, no fusiona PR #8 y no inicia R2.5.3.

## Implementación

- `R252cDebugSwitch.kt`: lee **exclusivamente en builds debug** `Settings.Global.getInt(..., "tactic_r252c_by_app_debug", 0)`; únicamente el valor entero **1** activa el experimento. `BuildConfig.DEBUG=false` devuelve OFF sin leer Settings.Global en release. Una compilación release minimizada permite que R8 elimine la ruta no alcanzable; **no se compiló ningún APK release ni se verificó físicamente su DEX**.
- `R252ColorLifecycle.refreshDebugGate()`: evalúa en el inicio real de un editor, activa/desactiva sin UI ni preferencias, limpia estado/paleta al conmutar y emite el marcador logcat `R252C_DEBUG_SWITCH`. No interviene en extracción asíncrona ni introduce dependencias Samsung.
- `LatinIME.onStartInputInternal`: **una** llamada acotada antes de formar la identidad de sesión. Motor de escritura, geometría, estética y recursos de producto permanecen sin cambios.
- `app/build.gradle.kts`: el workflow usa `TACTIC_R252C_ISOLATED_APK=1` y aplica únicamente al buildType `debug` el suffix `.debug.r252c`. El debug normal conserva `.debug`; `debugNoMinify` y release no se modifican. La aplicación experimental puede convivir por `applicationId` distinto.
- `R252cDebugSwitchTest.kt`: comprueba release no activable con flag 1, debug sin flag OFF, flag 1 ON y flag eliminada OFF. El workflow intenta pruebas enfocadas tanto `testDebugUnitTest` como `testReleaseUnitTest` **sin crear APK release**.

## APK y comandos para Samsung Galaxy S25 / DeX

**Nombre previsto del artifact de GitHub Actions:** `Tactic-R252C2-NO-VALIDADO-debug-apk` (run candidato: [39001651540](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39001651540)). **El enlace directo al APK sólo debe publicarse cuando GitHub confirme artefacto, firma y tamaño**.

El APK dentro del artefacto se denomina `Tactic-Keyboard-R252C2-NO-VALIDADO-debug.apk`. Paquete esperado: `com.jorgeprdz.ioskeyboard.debug.r252c`.

```bash
# Con la depuración ADB conectada al Galaxy S25; sin necesidad de PC.
adb devices
adb install -r Tactic-Keyboard-R252C2-NO-VALIDADO-debug.apk
adb shell ime enable com.jorgeprdz.ioskeyboard.debug.r252c/helium314.keyboard.latin.LatinIME
adb shell ime set com.jorgeprdz.ioskeyboard.debug.r252c/helium314.keyboard.latin.LatinIME

# By App ON en ESTA variante debug, no en el teclado instalado normalmente.
adb shell settings put global tactic_r252c_by_app_debug 1

# Abra o vuelva a enfocar manualmente un EditText para ejecutar onStartInputInternal.
adb logcat -d -s R252C_DEBUG_SWITCH:I '*:S'
```

**Logcat de confirmación esperado, NO observado aún en Galaxy físico:**

```text
I/R252C_DEBUG_SWITCH: state=ON variant=debug source=Settings.Global
```

El mensaje aparece al **iniciar una nueva sesión de editor**; poner la bandera por sí solo no obliga a Android a volver a enfocar un campo ya activo. `ON` confirma la activación de la ruta debug, **no** que los colores o el compositor sean correctos.

Para restaurar OFF:
```bash
adb shell settings delete global tactic_r252c_by_app_debug
# Cambie el foco del editor para actualizar la sesión; confirme:
adb logcat -d -s R252C_DEBUG_SWITCH:I '*:S'
```
Esperado: `I/R252C_DEBUG_SWITCH: state=OFF variant=debug source=Settings.Global`. Para recuperar el teclado anterior, guardar su componente antes de seleccionar Tactic con `adb shell settings get secure default_input_method`, y después usar `adb shell ime set COMPONENTE_ORIGINAL`. En Samsung DeX sólo hacer validación manual real; un emulador no prueba DeX.

**Cautela:** `settings put global` se aplica al ajuste global del dispositivo. El código de producción/release no lo consulta, pero conviene eliminar la bandera una vez concluida la prueba. Esta es una compilación experimental, no autorizada para introducir contraseñas u otros datos sensibles mientras no se valide.

## Estado de pruebas y regresiones conocidas

- [39000407904](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000407904): el job Android API 35 completó satisfactoriamente las pruebas R2.5.1/R2.5.2/R2.5.2b (smoke 1/1, vector 5/5, lifecycle 3/3, host real/surface 5/5), **sin demostrar DeX ni dos UIDs**. El job JVM del mismo run **FAIL 540/541** en `XLinkTest.knownDictionaries`. **Causa inmediata DEMOSTRADA por XML:** `java.net.SocketTimeoutException: Read timed out` desde `HttpURLConnection.getResponseCode` → `XLinkTest.readLink(XLinkTest.kt:110)` → `XLinkTest.knownDictionaries(XLinkTest.kt:36)`. Es una lectura HTTPS de prueba con timeout externo, **no demuestra defecto del motor ni del debug switch**. El job también registró `NullPointerException` asíncronas en workers, sin causalidad probada respecto a este timeout.
- [39000140968](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000140968): **FAIL 540/541**, esta vez `InputLogicTest` por mensaje diferido `what=0` aún pendiente cuando su aserción esperaba la cola vacía. **Causa raíz: NO DETERMINADA**, no afirmar regresión reparada ni flaky PASS.
- [39000407892](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39000407892): prueba de dos apps UID reales **FAIL**, al faltar `OnPreDraw` en `App A`, segunda ronda A→B→A. Las aplicaciones A/B son fixtures; esta prueba **NO** aprueba el gate visual real.
- [39001651540](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39001651540): nueva compilación reducida; se debe registrar el resultado de pruebas enfocadas de variantes y JVM de esta misma compilación, sin sustituirlo por resultados de builds anteriores.

**Clasificación permanente del APK: NO VALIDADO**, incluso si el build y el switch pasan; siguen faltando gate real UID, compositor, composición cross-app y Samsung DeX. El workflow conserva fallas de pruebas con `continue-on-error` exclusivamente para permitir la entrega experimental; nunca las recalifica como PASS. `PR #8 = Draft`, `By App normal = OFF`, `merge = NO`, `release = NO`, `R2.5.3 = NOT_STARTED`.
