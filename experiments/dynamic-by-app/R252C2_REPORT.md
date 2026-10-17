# TACTIC KEYBOARD — R2.5.2c-2 REDUCIDO · DEBUG SWITCH + APK DEBUG

**ALCANCE AUTORIZADO POR EL USUARIO:** sólo interruptor ADB exclusivo de compilación debug y APK debug instalable etiquetado **NO VALIDADO**. Esta autorización reemplaza el gate de fase previamente exigido **sólo para generar y probar un APK experimental**; no declara R2.5.2c-1 aprobado, no habilita usuarios normales, no fusiona PR #8 y no inicia R2.5.3.

## Implementación

- `R252cDebugSwitch.kt`: lee **exclusivamente en builds debug** `Settings.Global.getInt(..., "tactic_r252c_by_app_debug", 0)`; únicamente el valor entero **1** activa el experimento. `BuildConfig.DEBUG=false` devuelve OFF sin leer Settings.Global en release. Una compilación release minimizada permite que R8 elimine la ruta no alcanzable; **no se compiló ningún APK release ni se verificó físicamente su DEX**.
- `R252ColorLifecycle.refreshDebugGate()`: evalúa en el inicio real de un editor, activa/desactiva sin UI ni preferencias, limpia estado/paleta al conmutar y emite el marcador logcat `R252C_DEBUG_SWITCH`. No interviene en extracción asíncrona ni introduce dependencias Samsung.
- `LatinIME.onStartInputInternal`: **una** llamada acotada antes de formar la identidad de sesión. Motor de escritura, geometría, estética y recursos de producto permanecen sin cambios.
- `app/build.gradle.kts`: el workflow crea exclusivamente la variante aislada `r252cDebug` derivada de debug (`assembleR252cDebug`), con suffix `.debug.r252c`. Los overlays privados `app/src/r252cDebug/res/values/{clip_provider,gesture_data,strings}.xml` asignan **autoridades de contenido exclusivas** y conservan el nombre Tactic Keyboard. Es necesario porque cambiar el applicationId sin cambiar las autoridades fijas de los proveedores debug habría impedido la convivencia. El buildType debug habitual conserva `.debug` y sus autoridades originales; `debugNoMinify` y release permanecen intactos.
- `R252cDebugSwitchTest.kt`: comprueba release no activable con flag 1, debug sin flag OFF, flag 1 ON y flag eliminada OFF. El workflow intenta pruebas enfocadas tanto `testDebugUnitTest` como `testReleaseUnitTest` **sin crear APK release**.

## APK y comandos para Samsung Galaxy S25 / DeX

**Nombre previsto del artifact de GitHub Actions:** `Tactic-R252C2-NO-VALIDADO-debug-apk` (run aislado corregido: [39002269865](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39002269865); build anterior de la variante debug regular: [39001651540](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39001651540)). **El enlace directo al APK sólo debe publicarse cuando GitHub confirme artefacto, firma y tamaño**.

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
- [39001651540](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39001651540): variante debug inicial; **pruebas enfocadas debug/release PASS, contratos PASS, suite JVM completa PASS y runtime ARM64 PASS**. El artefacto de esta primera variante no garantiza coexistencia de proveedores con otra copia debug, por eso se creó la variante aislada.
- [39002269865](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39002269865): build de variante `r252cDebug` con autoridades independientes; **sin resultado final al actualizar este informe**. Por ahorro de cuota se verifica switch de la propia variante y release, se conserva la evidencia JVM íntegra de la compilación anterior de código idéntico, sin repetir la suite completa.

**Clasificación permanente del APK: NO VALIDADO**, incluso si el build y el switch pasan; siguen faltando gate real UID, compositor, composición cross-app y Samsung DeX. El workflow deja explícitos los resultados de los tests enfocados y la procedencia de la suite JVM. Los tests enfocados usan `continue-on-error` sólo para permitir la entrega experimental aunque fallen y jamás se recalifican como PASS; la suite completa del commit previo no se repite sin necesidad. `PR #8 = Draft`, `By App normal = OFF`, `merge = NO`, `release = NO`, `R2.5.3 = NOT_STARTED`.

## Resultado verificable del APK experimental — actualización final de artefacto

- **GitHub Actions SUCCESS:** [run 39002332135](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39002332135).
- **Artefacto real publicado:** [Tactic-R252C2-NO-VALIDADO-debug-apk](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39002332135/artifacts/12089098807) (artifact ID `12089098807`).
- **APK:** `Tactic-Keyboard-R252C2-NO-VALIDADO-debug.apk`, 17,994,906 bytes.
- **ApplicationId validado por aapt:** `com.jorgeprdz.ioskeyboard.debug.r252c`.
- **Compilación:** `assembleR252cDebug`, commit `2e647798d13eeff354c037eb1098d6e3679fdfab`. No APK release ni debugNoMinify.
- **SHA256 del APK, comprobado contra el manifiesto del artifact y recalculado tras descarga:** `4c61032c3171118763a23b8a0f1aa13b1bdc82ea2d01b00a70dc9cdc0049984c`. Verificación ZIP y `apksigner` exitosa.
- **Proveedores:** la tabla compilada `resources.arsc` contiene las autoridades exclusivas `helium314.keyboard.r252cdebug.clipprovider` y `helium314.keyboard.r252cdebug.provider`; no se reutilizaron los nombres de autoridades del debug habitual.
- **Contratos host:** PASS en run APK. **Suite JVM completa de commit anterior de código de interruptor idéntico:** **545/545 PASS**, 0 errores ni omitidos; se verificaron 65 archivos XML del artifact de run `39001651540`, incluido `R252cDebugSwitchTest` de cuatro casos. Esa prueba global **no** se repitió en el último run por ahorro de compilación; no atribuir 545/545 al commit `2e647798`.
- **Pruebas enfocadas del run del APK:** `testVariants=failure`, porque AGP no expone `testR252cDebugUnitTest`; **no** porque la lógica del interruptor haya reprobado. Esto impide declarar ese gate del run del APK como PASS. Se lanzó una verificación independiente y sin compilar APK usando `testDebugUnitTest --tests ...` y revisión de `BuildConfig.DEBUG=false` de release: [run 39002908242](https://github.com/Jorgeprdz/HeliBoard/actions/runs/39002908242). Su resultado debe consultarse antes de declarar esa evidencia definitiva.

**Veredicto de entrega:** `APK_CREATED_AND_SHA_VERIFIED=YES`; `APK_VALIDATION=NO_VALIDADO`; `PHYSICAL_GALAXY_S25_DEX=NOT_EXECUTED`; `R253=NOT_STARTED`. El paso de comprobación de release se describe como **revisión estática de BuildConfig**, no como release APK compilado ni prueba de eliminación del bytecode en release.
