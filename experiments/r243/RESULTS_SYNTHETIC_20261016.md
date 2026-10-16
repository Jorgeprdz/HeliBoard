# Tactic Keyboard R2.4.3 — evidencia sintética, 16 octubre 2026

**Evaluación experimental: FAIL. Puerta de producción: BLOCKED. No APK.**
Evaluación congelada: `c79e3edfc17a0de823e9ec2b46e91c01508f115e`.
[Run con evidencia](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38833498780) ·
[Artefacto 12018403839](https://github.com/Jorgeprdz/HeliBoard/actions/runs/38833498780/artifacts/12018403839).
El job completa la evaluación y falla deliberadamente en la decisión de calidad.

## Corpus y reproducción
100 casos: 40 es-MX, 20 en-US, 10 mezclas artificiales, 30 correctos/protegidos.
70 casos ruidosos: diez por cada operación (vecindad, duplicación, omisión, transposición,
espacio ausente, concatenación de tres palabras, múltiples errores). Semilla 2026101601.
Evaluación sintética de procedencia independiente, **no evaluación humana ciega**.
Los fragmentos formales contienen tablas, encabezados y posibles defectos de extracción;
no representan conversaciones naturales ni garantizan corrección del texto original.
A/B/C no intervienen. Referencias no se suministran al generador, ranking ni modelos.
Corpus y metadatos congelados en `data/`; salidas completas, fuentes y vocabulario en el artefacto.
SHA256 corpus: `5f44e8fd374e1c78af57547979a7c03cfbc51a83c8b0c0dc275b54a1ed0ae2ac`.
SHA256 vocabulario: `f0ad0fb78cfb9fbab41350a692e7727d88185e39178eb214ea5f8ee2e3dc00a3`.

Fuentes: [INEGI MOLEC 2025](https://www.inegi.org.mx/contenidos/saladeprensa/boletines/2025/molec/molec2025_RR.pdf),
[libre uso INEGI](https://www.inegi.org.mx/inegi/terminos.html);
[USGS Hydrology](https://www.usgs.gov/water-science-school/science/what-hydrology),
[dominio público USGS](https://www.usgs.gov/information-policies-and-instructions/copyrights-and-credits).
Los casos son extractos transformados; no hay respaldo institucional.
Vocabulario externo: [FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(Hermit Dave/OpenSubtitles2018, datos CC-BY-SA-4.0; código MIT), revisión
`525f9b560de45753a5ea01069454e72e9aa541c6`; sin bigramas ni adiciones desde las referencias.

## Comparación ejecutada
Componentes Java reales de R241/R242 y prototipo R243, con el mismo adaptador léxico externo;
**no ejecución del IME Android completo ni sus diccionarios nativos**.
GGUF reales con decoder Linux llama.cpp b5046. Qwen 0.5B: 164/164 inferencias;
Qwen 1.5B: 9/9. En 91 casos sin candidatos no se ejecuta el selector 1.5B.
Asistido significa aceptación hipotética de una propuesta; no autorización de sustitución automática.
Métrica: eventos exactos de edición de caracteres. Residuales = FN + FP; no es WER/ERRANT.

| Motor / modo | TP | FP | FN | Residuales | Precisión | Recall | F0.5 | Cobertura | Abstenciones |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Todos / automático |30|27|84|111|0.526|0.263|0.439|36%|64|
| R241 / asistido |44|134|70|204|0.247|0.386|0.266|84%|16|
| R242 / asistido |45|134|69|203|0.251|0.395|0.271|84%|16|
| R243 / asistido |42|135|72|207|0.237|0.368|0.255|84%|16|

R243 frente a R241: **1.5% más residuales**; ES-MX 91→95 (+4.4%), EN-US 50→49 (−2%),
mixto 30→30 y protegido 33→33. No alcanza el objetivo de 20% menos errores.
En los 30 controles limpios: 4 alterados automáticamente y 19 con aceptación asistida, en los tres motores.
Proxy crítico de números/negaciones/nombres: cero cambios; **no demuestra conservación semántica**.
Pérdida de significado: NO VERIFICADA por humanos; hay sustituciones léxicas incorrectas observables.

| Entrada / referencia | R241 asistido | R242 asistido | R243 asistido |
|---|---|---|---|
| gupo / grupo, en «se registró en el …» | se registro … grupo | se registro … grupo | de registro … gupo |
| thedata / the data, en «work relies …» | work relief … thedata | work relies … the data | w or relies … thedata |
| foros (control correcto) | fotos … blog | fotos … blog | fotos … blog |

## Tres causas principales
1. **Generación/cobertura:** 91/100 sin candidatos, cero candidatos útiles generados o mostrados.
Filtros de contexto completo bloquean mayúsculas, cifras y negaciones; causas solapadas.
El presupuesto se consume además proponiendo cambios de palabras válidas al principio.
2. **Validez léxica y fallback:** lista de frecuencias limitada a 10k palabras por idioma;
faltan 130 palabras únicas de las referencias (202 ocurrencias). Ausencia de frecuencia
no implica palabra incorrecta. Estos números no describen la precisión del teclado instalado.
3. **Selección y guard:** 1.5B selecciona un candidato no nulo en sus nueve llamadas aunque ninguno
es útil. De 164 salidas 0.5B, 54 cambios son rechazados, 107 idénticos y tres aceptados.
No demuestra que relajar el guard sea seguro; el proxy no detecta «foros→fotos».

## Reparación aislada y siguientes pasos
Se añade regresión independiente «we» y «we recbir»: una palabra válida sin evidencia contextual
no recibe cambios motrices; el typo posterior mantiene candidatos. Se conservan alternativas
de acento o con respaldo contextual externo, siempre manuales. No cambia producción.
Se reproduce el fallo contra c79 y se valida el arreglo con pruebas separadas de componentes.
**No se repuntúa este corpus ni se afirma mejora lingüística del arreglo.**
Siguiente inversión: separar validez/frecuencia con diccionarios nativos, localizar segmentos
corregibles sin eliminar protecciones y evaluar en un conjunto nuevo, anotado por personas.
Integración/cursor/WhatsApp, intención y rendimiento físico: NOT RUN.
Host con carga del modelo incluida: mediana 1.63 s/587 MiB (0.5B), 5.29 s/1774 MiB (1.5B);
no son mediciones de Galaxy S25. Branding/iconos/identidad y comportamiento de producción intactos.
