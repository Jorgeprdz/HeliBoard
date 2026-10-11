<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="HeliBoard icon" width="104" />

# HeliBoard · iOS Glass + Dynamic Monet

### Tu teclado. Tus colores. Tu privacidad.

Una edición experimental de **HeliBoard para Android**, centrada en la personalización visual, los esquemas de color dinámico y una experiencia de escritura sin conexión.

**An experimental HeliBoard fork focused on iOS Glass styling, Android dynamic color and offline typing.**

[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Jorgeprdz/HeliBoard)
[![GPL v3](https://img.shields.io/badge/License-GPL--3.0-64748B?style=for-the-badge)](LICENSE)
[![Offline](https://img.shields.io/badge/Keyboard-offline-2563EB?style=for-the-badge)](#privacidad)
[![Preview release](https://img.shields.io/badge/Download-preview_APK-8B5CF6?style=for-the-badge&logo=github)](https://github.com/Jorgeprdz/HeliBoard/releases)

**[Descargar APK](https://github.com/Jorgeprdz/HeliBoard/releases)** · **[Novedades](#qué-incluye-esta-edición)** · **[Código experimental](https://github.com/Jorgeprdz/HeliBoard/tree/feature/dynamic-color-schemes)** · **[HeliBoard original](https://github.com/HeliBorg/HeliBoard)**

</div>

---

## ✨ Qué incluye esta edición

| Experiencia | Descripción |
| :--- | :--- |
| 🎨 **Dynamic Monet** | Esquemas que usan colores dinámicos de Android, con variantes como Neutral, Tonal Spot, Vibrant, Expressive, Fidelity, Rainbow, Monochrome y otras. |
| 🫧 **iOS Glass** | Rama experimental dedicada a una apariencia de teclado inspirada en superficies translúcidas. La implementación visual está en evolución. |
| 🌗 **Claro y oscuro** | Generación de paletas para ambos modos. |
| ⌨️ **Base HeliBoard** | Predicción, diccionarios, teclado multilingüe, portapapeles, diseños personalizables y más capacidades del proyecto original. |
| 🔐 **Privacidad** | Sin permiso de acceso a Internet declarado en el manifiesto de la aplicación de esta rama. |

> **Estado: preview / versión de prueba.** La edición experimental se desarrolla en [`feature/dynamic-color-schemes`](https://github.com/Jorgeprdz/HeliBoard/tree/feature/dynamic-color-schemes). La rama `main` puede tener código anterior; consulta la fuente específica indicada en cada release. No es una distribución oficial del equipo HeliBoard.

## 📲 Descargar e instalar

1. Abre **[Releases](https://github.com/Jorgeprdz/HeliBoard/releases)** y elige la publicación más reciente.
2. Descarga el archivo **.apk** adjunto a la publicación (no `Source code.zip`).
3. Instala la APK en Android y activa el teclado desde los ajustes del sistema.
4. Comprueba la suma **SHA-256** con el archivo `SHA256SUMS.txt` de esa misma publicación, si está disponible.

**Importante:** Las versiones etiquetadas como **debug / preview** están firmadas para pruebas y usan un identificador de paquete distinto del HeliBoard oficial. No son actualizaciones de F-Droid ni versiones estables. No introduzcas información sensible mientras estés evaluando una compilación experimental que no hayas verificado.

## 🎨 Color dinámico

Los modos experimentales usan una implementación de esquemas Material con fuentes de color de Android. Las opciones disponibles, su apariencia y el resultado final dependen de la versión de Android y de la configuración del sistema.

**Código y pruebas:** [DynamicSchemeProvider](https://github.com/Jorgeprdz/HeliBoard/blob/feature/dynamic-color-schemes/app/src/main/java/helium314/keyboard/latin/common/dynamic/DynamicSchemeProvider.kt) · [Workflows](.github/workflows)

## 🔐 Privacidad

La experiencia de teclado está diseñada para funcionar **sin conexión**. La rama de la preview no declara `android.permission.INTERNET`. Como con cualquier teclado, revisa los permisos y el código antes de utilizarlo para contraseñas o datos privados.

## 🧰 Compilar desde código

Consulta el código asociado a la release o usa la rama experimental:

```bash
git clone https://github.com/Jorgeprdz/HeliBoard.git
cd HeliBoard
git checkout feature/dynamic-color-schemes
./gradlew :app:assembleDebug
```

Requiere el entorno Android SDK / JDK correspondiente al proyecto. Las compilaciones de prueba usan la variante `debug`; su salida está en `app/build/outputs/apk/debug/`.

## 🤝 Proyecto original y licencias

Este repositorio es un **fork** del proyecto libre [HeliBoard](https://github.com/HeliBorg/HeliBoard), basado a su vez en [OpenBoard](https://github.com/openboard-team/openboard) y AOSP Keyboard. El crédito por el proyecto base, el icono y las contribuciones corresponde a sus autores originales y colaboradores.

- Licencia del código: **[GPL-3.0](LICENSE)**.
- Componentes AOSP: **[Apache 2.0](LICENSE-Apache-2.0)**.
- Icono original: **[CC BY-SA 4.0](LICENSE-CC-BY-SA-4.0)**.
- Guía original: [Wiki HeliBoard](https://github.com/HeliBorg/HeliBoard/wiki) · [Contribución](CONTRIBUTING.md) · [Créditos del upstream](https://github.com/HeliBorg/HeliBoard#credits).

---

<div align="center">

**HeliBoard · iOS Glass + Dynamic Monet**

Hecho para explorar hasta dónde puede llegar un teclado Android personalizable. 💜

[Reportar problema](https://github.com/Jorgeprdz/HeliBoard/issues) · [Releases](https://github.com/Jorgeprdz/HeliBoard/releases)

</div>
