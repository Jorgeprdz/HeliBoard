# Material 3 Expressive App UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign HeliBoard's app-owned interface in Material 3 Expressive while leaving the typing keyboard's appearance and behavior unchanged.

**Architecture:** Keep the existing Compose navigation and preference model. Centralize app-only theme tokens, update shared settings components, then sweep screens and dialogs to consume them. App UI follows Android system dynamic colors and remains independent of keyboard theme selections.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 already in the app, Android dynamic color resources, Robolectric/JUnit tests already configured.

**Spec:** `docs/superpowers/specs/2026-09-26-m3-expressive-app-ui-design.md`

## Global Constraints

- Preserve all existing settings destinations, names, preference keys, search/back behavior, import/export flows, onboarding, and iOSGlass keyboard controls.
- Do not modify keyboard rendering, colors, geometry, previews, toolbar/emoji/clipboard surfaces, iOSGlass keyboard drawables, or input-service behavior.
- Do not edit `KeyboardTheme.kt`, `Colors.kt`, `InputView.java`, `KeyboardSwitcher.java`, keyboard layouts/drawables, or `latin/common/dynamic` runtime color code.
- Keep app UI colors independent of selected keyboard themes; use Android dynamic colors on API 31+ and a light/dark fallback below API 31.
- Use existing Material 3 dependencies; add no runtime dependency or screenshot-test framework.
- Preserve RTL, large text, and the existing system animation-scale behavior.

## Review Focus

- API 30 and earlier: light and dark fallback schemes remain distinct and readable (Task 1 tests).
- API 31+: the app uses the Android dynamic system scheme in both light and dark mode (Task 1 tests).
- Long localized labels, RTL, and font scale up to 2.0: controls remain visible and tappable (Task 7 manual layout review).
- Menus and dialogs: selection, confirmation, cancel, and back behavior remain intact after restyling (Task 4 smoke review).
- Keyboard isolation: the final diff contains no changes to the typing keyboard renderer or its visual resources (Task 7 diff audit).

---

### Task 1: App-only theme and design tokens

**Files:**
- Create: `app/src/main/java/helium314/keyboard/latin/utils/AppThemeColors.kt`
- Modify: `app/src/main/java/helium314/keyboard/latin/utils/Theme.kt`
- Test: `app/src/test/java/helium314/keyboard/latin/utils/AppThemeColorsTest.kt`

**Interfaces:**
- Produces `internal fun resolveAppColorScheme(context: Context, dark: Boolean): ColorScheme`.
- Produces `internal object AppShapes` with `compact`/`medium`/`large` corner radii of 12/20/28 dp and `internal object AppSpacing` with `xs`/`small`/`medium`/`large`/`extraLarge`/`huge` values of 4/8/12/16/24/32 dp. These are app-only; do not share them with keyboard runtime styling.
- Keeps the public Compose entry point `Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`.

- [ ] **Step 1: Write failing resolver tests** named `preApi31FallbackHasDistinctLightAndDarkSchemes` and `api31AndAboveUseSystemDynamicColors`, asserting the fallback accent and, on API 31+, light primary equals `system_accent1_600` while dark primary equals `system_accent1_200`.
- [ ] **Step 2: Run the two tests** with `./gradlew :app:testDebugUnitTest --tests 'helium314.keyboard.latin.utils.AppThemeColorsTest'`; verify they fail because the resolver is absent.
- [ ] **Step 3: Implement** the resolver with `dynamicLightColorScheme`/`dynamicDarkColorScheme` on API 31+ and the existing app accent fallback below API 31. Define app-only shape radii 12/20/28 dp and spacing tokens 4/8/12/16/24/32 dp.
- [ ] **Step 4: Wire `Theme`** to the resolver and tokens; preserve the existing system dark-mode default and typography roles while setting a deliberate Expressive hierarchy.
- [ ] **Step 5: Run the focused tests** and verify both platform branches pass.
- [ ] **Step 6: Commit** as `feat: define expressive app theme tokens`.

### Task 2: Settings shell, home, and navigation surfaces

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt`
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsNavHost.kt`
- Modify: `app/src/main/java/helium314/keyboard/settings/SearchScreen.kt`
- Modify: `app/src/main/java/helium314/keyboard/settings/screens/MainSettingsScreen.kt`
- Modify: `app/src/main/java/helium314/keyboard/settings/preferences/Preference.kt`
- Create: `app/src/main/java/helium314/keyboard/settings/components/SettingsCategoryIcon.kt`, exposing `@Composable fun SettingsCategoryIcon(icon: Int, contentDescription: String? = null, modifier: Modifier = Modifier)`.
- Modify app-owned category icons in `app/src/main/res/drawable/ic_settings_*.xml` and `ic_dictionary.xml` only if shared tint/container treatment is insufficient.

**Interfaces:**
- Consumes Task 1 app theme/shape/spacing tokens.
- Preserves current `SettingsNavHost`, `SettingsDestination`, `SearchSettingsScreen`, and `Preference` call contracts.

- [ ] **Step 1: Implement** expressive app-bar/search styling and use a shared category-icon container in the home screen while preserving every current category and click callback.
- [ ] **Step 2: Update** shared preference row spacing, title/summary hierarchy, icon tint/container, touch target, selected/disabled states, and dividers.
- [ ] **Step 3: Update** top-level surfaces and navigation transitions to use shared tokens while retaining back-stack and system animation-scale behavior.
- [ ] **Step 4: Build** with `./gradlew :app:assembleDebug`; verify the screen and navigation Kotlin sources compile.
- [ ] **Step 5: Commit** as `feat: restyle settings shell and home`.

### Task 3: Shared preference inputs and section hierarchy

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/settings/preferences/SwitchPreference.kt`
- Modify: `ListPreference.kt`, `TextInputPreference.kt`, `SliderPreference.kt`, `ReorderSwitchPreference.kt`, `KeyboardScalePreference.kt`
- Modify: `BackgroundImagePreference.kt`, `BackupRestorePreference.kt`, `CustomFontPreference.kt`, `LoadGestureLibPreference.kt`
- Create: `app/src/main/java/helium314/keyboard/settings/components/SettingsSectionHeader.kt`, exposing `@Composable fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier)`.

**Interfaces:**
- Consumes the app-only tokens and shared preference row from Tasks 1–2.
- Preserves all existing preference callback and state APIs.

- [ ] **Step 1: Update** input controls to use Material 3 colors/shapes and app spacing without changing preference keys, defaults, or state writes.
- [ ] **Step 2: Update** reorderable and specialty controls to keep drag handles, selection affordances, and accessible touch areas clear.
- [ ] **Step 3: Replace** one-off section headings with a shared section header where the existing screen hierarchy already has a group.
- [ ] **Step 4: Build** with `./gradlew :app:assembleDebug` and run the current unit suite.
- [ ] **Step 5: Commit** as `feat: apply expressive styling to preference controls`.

### Task 4: Menus, selectors, and dialogs

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/settings/dialogs/ThreeButtonAlertDialog.kt`
- Modify all app-owned dialog composables under `app/src/main/java/helium314/keyboard/settings/dialogs/`, including list/multi-list, layout, color, dictionary, confirmation, reorder, and information dialogs.
- Modify: `app/src/main/java/helium314/keyboard/settings/Misc.kt`

**Interfaces:**
- Consumes Task 1 tokens and the shared preference component from Tasks 2–3.
- Keeps each dialog/menu parameter list and result/selection callback unchanged.

- [ ] **Step 1: Restyle** the common alert/dialog surface, button emphasis, and content spacing using existing Material 3 components while preserving current action order.
- [ ] **Step 2: Restyle** list selectors, color-theme picker, layout selectors, and dropdown menus with clear selected/disabled states; preserve existing dynamic color grouping.
- [ ] **Step 3: Restyle** editor, reorder, dictionary, and information dialogs without changing their data operations.
- [ ] **Step 4: Run** focused app unit tests and `./gradlew :app:assembleDebug`; manually smoke-check select/cancel/confirm/back paths when a compatible signed install is available.
- [ ] **Step 5: Commit** as `feat: restyle app menus and dialogs`.

### Task 5: Apply the design system across settings screens

**Files:**
- Modify app-owned screens under `app/src/main/java/helium314/keyboard/settings/screens/`, including `AppearanceScreen.kt`, `ColorsScreen.kt`, `PreferencesScreen.kt`, `LanguageScreen.kt`, `SubtypeScreen.kt`, `SecondaryLayoutScreen.kt`, `DictionaryScreen.kt`, `PersonalDictionariesScreen.kt`, `PersonalDictionaryScreen.kt`, `TextCorrectionScreen.kt`, `ToolbarScreen.kt`, `GestureTypingScreen.kt`, `AdvancedScreen.kt`, `AboutScreen.kt`, `DebugScreen.kt`, and `screens/gesturedata/`.

**Interfaces:**
- Consumes shared theme, section, preference, dialog, and selector components from Tasks 1–4.
- Preserves each screen's route, setting definitions, conditional visibility, and preference behavior.

- [ ] **Step 1: Restyle** appearance/colors, preferences/correction, and toolbar screens, keeping keyboard theme previews and all setting actions intact.
- [ ] **Step 2: Restyle** language/layout, subtype, dictionaries, and personal dictionary screens, retaining localization and route arguments.
- [ ] **Step 3: Restyle** advanced/about/debug and gesture-data screens, retaining feature gates, export actions, and data collection behavior.
- [ ] **Step 4: Search** this screen tree for remaining hard-coded app surface colors/shapes and migrate each intentional app-owned outlier to a token or document why it remains.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`.
- [ ] **Step 6: Commit** as `feat: apply expressive styling across settings screens`.

### Task 6: Welcome and spell-checker app surfaces

**Files:**
- Modify: `app/src/main/java/helium314/keyboard/settings/WelcomeWizard.kt`
- Modify: `app/src/main/java/helium314/keyboard/settings/SettingsActivity.kt` spell-checker settings branch (the spell-checker Activity delegates to this Compose surface).

**Interfaces:**
- Consumes shared theme/components from Tasks 1–4.
- Preserves onboarding activation flow and spell-checker preference callbacks.

- [ ] **Step 1: Restyle** welcome/setup steps, progress, and actions with the shared hierarchy, keeping the current activation and close/finish paths.
- [ ] **Step 2: Restyle** the app-owned spell-checker settings surface while preserving Android integration.
- [ ] **Step 3: Run** the complete app unit suite and build the debug APK.
- [ ] **Step 4: Commit** as `feat: restyle onboarding and spellchecker settings`.

### Task 7: Full verification and keyboard-isolation review

**Files:**
- Inspect all modified files; do not change keyboard-rendering files.
- Update the GitHub Actions debug build workflow only if it does not already run the complete unit suite and upload the correct iOSGlass APK artifact.

- [ ] **Step 1: Run** `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` in GitHub Actions and require success for the final SHA.
- [ ] **Step 2: Review** the app UI on a device in light/dark, RTL, font scale 1.0/2.0, long-label, navigation, menu, and dialog scenarios. Record any unavailable device scenario rather than marking it verified.
- [ ] **Step 3: Audit** `git diff --name-only` and confirm no typing-keyboard renderer, color provider, geometry, layout, or drawable file changed during this redesign.
- [ ] **Step 4: Download** the final Actions APK, verify its package/application version and SHA-256, and report signer compatibility honestly; do not uninstall the current app to work around a signer mismatch.
- [ ] **Step 5: Commit** any final app-only fixes as `fix: complete expressive app UI verification`.
