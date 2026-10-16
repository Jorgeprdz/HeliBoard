// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.os.Handler
import android.os.Message
import android.text.InputType
import android.view.inputmethod.*
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowInputMethodService.Companion.composingEnd
import helium314.keyboard.ShadowInputMethodService.Companion.composingStart
import helium314.keyboard.ShadowInputMethodService.Companion.selectedText
import helium314.keyboard.ShadowInputMethodService.Companion.selectionEnd
import helium314.keyboard.ShadowInputMethodService.Companion.selectionStart
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.ShadowFacilitator2.Companion.lastAddedWord
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.inputlogic.SpaceState
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getTimestampFormatter
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*
import kotlin.math.min
import kotlin.streams.asSequence
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class InputLogicTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val settingsValues get() = Settings.getValues()
    private val inputLogic get() = latinIME.mInputLogic
    private val connection: RichInputConnection get() = inputLogic.mConnection
    private val composerReader = InputLogic::class.java.getDeclaredField("mWordComposer").apply { isAccessible = true }
    private val composer get() = composerReader.get(inputLogic) as WordComposer
    private val spaceStateReader = InputLogic::class.java.getDeclaredField("mSpaceState").apply { isAccessible = true }
    private val spaceState get() = spaceStateReader.get(inputLogic) as Int
    private val beforeComposingReader = RichInputConnection::class.java.getDeclaredField("mCommittedTextBeforeComposingText").apply { isAccessible = true }
    private val connectionTextBeforeComposingText get() = (beforeComposingReader.get(connection) as CharSequence).toString()
    private val composingReader = RichInputConnection::class.java.getDeclaredField("mComposingText").apply { isAccessible = true }
    private val connectionComposingText get() = (composingReader.get(connection) as CharSequence).toString()

    private val textBeforeCursor get() = ShadowInputMethodService.textBeforeCursor
    private val textAfterCursor get() = ShadowInputMethodService.textAfterCursor
    private val composingText get() = ShadowInputMethodService.composingText
    private val text get() = ShadowInputMethodService.text
    private val cursor get() = ShadowInputMethodService.cursor
    private var currentInputType get() = ShadowInputMethodService.currentInputType
        set(value) { ShadowInputMethodService.currentInputType = value }

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    // Exercise the real Hangul combiner without restoring removed production locales/layouts.
    private fun koreanCombiningSubtype() =
        helium314.keyboard.latin.utils.SubtypeUtilsAdditional.createAdditionalSubtype(
            Locale.KOREAN,
            "KeyboardLayoutSet=MAIN:qwerty,CombiningRules=hangul,SupportTouchPositionCorrection",
            false, true
        ).also {
            // ShadowKeyboardSwitcher exposes this value to real word-range lookup.
            currentScript = ScriptUtils.SCRIPT_HANGUL
        }

    private fun r22Enable(text: String) {
        setText(text)
        ShadowInputMethodService.editorPackage = "fixture.editor"
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit()
            .putBoolean("enabled", true).putBoolean("local_context", true).commit()
    }

    private fun r22Key(code: Int) {
        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(code))
    }

    private fun r23Assistant(): helium314.keyboard.latin.ai.AiKeyboardAssistant {
        latinIME.onLocalWordBoundary()
        val field = LatinIME::class.java.getDeclaredField("mAiKeyboardAssistant").apply { isAccessible = true }
        return field.get(latinIME) as helium314.keyboard.latin.ai.AiKeyboardAssistant
    }

    @Test fun R23TemporaryThemeFollowsKeyboardBackgroundInsteadOfTransparentKeyColor() {
        r22Enable("")
        val field = settingsValues.javaClass.getField("mColors").apply { isAccessible = true }
        val original = field.get(settingsValues)
        val palette = Mockito.mock(helium314.keyboard.latin.common.Colors::class.java)
        field.set(settingsValues, palette)
        try {
            Mockito.`when`(palette.get(helium314.keyboard.latin.common.ColorType.MAIN_BACKGROUND)).thenReturn(android.graphics.Color.WHITE)
            Mockito.`when`(palette.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)).thenReturn(0x18000000)
            assertEquals(helium314.keyboard.latin.ai.AiMenuTheme.resolve(latinIME, false).surface,
                helium314.keyboard.latin.ai.AiMenuTheme.resolveKeyboard(latinIME).surface)
            Mockito.`when`(palette.get(helium314.keyboard.latin.common.ColorType.MAIN_BACKGROUND)).thenReturn(android.graphics.Color.BLACK)
            Mockito.`when`(palette.get(helium314.keyboard.latin.common.ColorType.KEY_BACKGROUND)).thenReturn(0x18ffffff)
            assertEquals(helium314.keyboard.latin.ai.AiMenuTheme.resolve(latinIME, true).surface,
                helium314.keyboard.latin.ai.AiMenuTheme.resolveKeyboard(latinIME).surface)
        } finally { field.set(settingsValues, original) }
    }

    private fun r242Pending(assistant: helium314.keyboard.latin.ai.AiKeyboardAssistant, original: String): Any {
        val capture = assistant.javaClass.getDeclaredMethod("capture").apply { isAccessible = true }
        val snapshot = capture.invoke(assistant) as helium314.keyboard.latin.ai.EditorSnapshot
        val epoch = assistant.javaClass.getDeclaredField("continuousEpoch").apply { isAccessible = true }.getLong(assistant)
        val anchor = helium314.keyboard.latin.ai.local.ContinuousReviewAnchor(
            snapshot.editor, snapshot.packageName, snapshot.fieldId, epoch, snapshot.start - original.length, original)
        val corrections = helium314.keyboard.latin.ai.local.LocalContextReviewer().review(
            original, helium314.keyboard.latin.ai.local.R241WritingTest.Vocabulary(), emptySet()) { false }
        kotlin.test.assertTrue(corrections.isNotEmpty())
        val type = assistant.javaClass.declaredClasses.single { it.simpleName == "PendingReview" }
        return type.declaredConstructors.single().apply { isAccessible = true }.newInstance(anchor, corrections)
    }
    private fun r242Deliver(assistant: helium314.keyboard.latin.ai.AiKeyboardAssistant, pending: Any) {
        val field = assistant.javaClass.getDeclaredField("pendingReviews").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val queue = field.get(assistant) as java.util.ArrayDeque<Any>
        queue.add(pending)
        assistant.javaClass.getDeclaredMethod("applyPendingReview").apply { isAccessible = true }.invoke(assistant)
    }

    @Test fun R242ResultSurvivesRapidAppendAndWaitsForSafeBoundaryWithAtomicUndo() {
        r22Enable("nicuidando ")
        val assistant = r23Assistant()
        val pending = r242Pending(assistant, text)
        r22Key('x'.code)
        assertEquals("nicuidando x", text)
        val compositionBefore = composingText
        r242Deliver(assistant, pending)
        assertEquals("nicuidando x", text)
        assertEquals(compositionBefore, composingText)
        r22Key(' '.code)
        assertEquals("ni cuidando x ", text)
        assertEquals(text.length, cursor)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("nicuidando x ", text)
        latinIME.onLocalWordBoundary()
        assertEquals("nicuidando x ", text)
        checkConnectionConsistency()
    }

    @Test fun R242ObsoleteFragmentAndEditorGenerationCannotApplyBackgroundResult() {
        r22Enable("nincuidando ")
        val assistant = r23Assistant()
        val pending = r242Pending(assistant, text)
        r22Key('x'.code)
        ShadowInputMethodService.text = "nincuidandX x"
        r242Deliver(assistant, pending)
        r22Key(' '.code)
        assertEquals("nincuidandX x ", text)
        r22Enable("nincuidando ")
        val old = r242Pending(assistant, text)
        assistant.onNewEditor()
        r242Deliver(assistant, old)
        assertEquals("nincuidando ", text)
    }

    @Test fun R242BusyReviewCoalescesLatestCompletedWordsInsteadOfDroppingThem() {
        r22Enable("hola ")
        val assistant = r23Assistant()
        val busy = assistant.javaClass.getDeclaredField("continuousRunning").apply { isAccessible = true }
        busy.setBoolean(assistant, true) // Hold the producer; no simulated model output.
        "nicuidando ".forEach { r22Key(it.code) }
        val latestField = assistant.javaClass.getDeclaredField("latestReview").apply { isAccessible = true }
        val latest = latestField.get(assistant)
        kotlin.test.assertNotNull(latest)
        val context = latest.javaClass.getDeclaredField("context").apply { isAccessible = true }.get(latest) as String
        assertEquals("hola nicuidando ", context)
        busy.setBoolean(assistant, false)
        // The integration part consumes the real reviewer result for the coalesced context.
        r242Deliver(assistant, r242Pending(assistant, context))
        assertEquals("hola ni cuidando ", text)
        assertEquals(text.length, cursor)
        checkConnectionConsistency()
    }

    @Test fun R242SendingInvalidatesPendingReviewAndCannotEditSentText() {
        r22Enable("nincuidando ")
        val assistant = r23Assistant()
        val pending = r242Pending(assistant, text)
        assistant.onInputSubmitted()
        r242Deliver(assistant, pending)
        assertEquals("nincuidando ", text)
        checkConnectionConsistency()
    }

    @Test fun R242FastProtectedInputRemainsExactWithoutModelOrNetwork() {
        val original = "No quiero 3, quiero 2. Revisa los PRs de Archium. DW."
        r22Enable("")
        original.forEach { r22Key(it.code) }
        assertEquals(original, text)
        assertEquals(text.length, cursor)
        checkConnectionConsistency()
    }

    private fun r241Observe(word: String, valid: Boolean = true, candidates: List<String> = emptyList()) {
        val field = r23Assistant().javaClass.getDeclaredField("lexicon").apply { isAccessible = true }
        val observed = field.get(r23Assistant()) as helium314.keyboard.latin.ai.local.ObservedLexicon
        observed.record(word, valid, candidates, true)
        val positions = mutableMapOf<Char, DoubleArray>()
        listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm").forEachIndexed { y, row ->
            row.forEachIndexed { x, key -> positions[key] = doubleArrayOf(x * 10.0 + y * 2.5, y * 10.0, 10.0, 10.0) }
        }
        observed.keyboard(positions)
    }


    @Test fun R24DictionaryReadinessRejectsMissingAndPartialDictionaries() {
        r22Enable("voy haber si puedo")
        ShadowFacilitator2.initializedMain = false
        ShadowFacilitator2.uninitializedMain = false
        r22Key(' '.code)
        assertEquals("voy haber si puedo ", text)

        r22Enable("voy haber si puedo")
        ShadowFacilitator2.initializedMain = true
        ShadowFacilitator2.uninitializedMain = true
        r22Key(' '.code)
        assertEquals("voy haber si puedo ", text)

        r22Enable("voy haber si puedo")
        ShadowFacilitator2.initializedMain = true
        ShadowFacilitator2.uninitializedMain = false
        r22Key(' '.code)
        assertEquals("voy a ver si puedo ", text)
        checkConnectionConsistency()
    }

    @Test fun R241SegmentationUsesAtomicUndoAndPersistentPairVeto() {
        r22Enable("🙂 nincuidando")
        r241Observe("ni")
        r241Observe("cuidando")
        r241Observe("nincuidando", false)
        r22Key(' '.code)
        assertEquals("🙂 ni cuidando ", text)
        assertEquals(text.length, cursor)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("🙂 nincuidando ", text)
        latinIME.onLocalWordBoundary()
        assertEquals("🙂 nincuidando ", text)
        kotlin.test.assertTrue(helium314.keyboard.latin.ai.AiSettingsActivity.rejectedCorrections(latinIME)
            .contains("nincuidando→ni cuidando"))
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("🙂 nincuidando", text)
        checkConnectionConsistency()
    }

    @Test fun R241FollowingWordDisambiguatesInitialTypoWithoutChangingAcronyms() {
        r22Enable("Edta es")
        r241Observe("edta", false, listOf("esta"))
        r241Observe("esta")
        r22Key(' '.code)
        assertEquals("Esta es ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("Edta es ", text)
        checkConnectionConsistency()
        r22Enable("DW")
        r241Observe("dw", false, listOf("de"))
        r241Observe("de")
        r22Key(' '.code)
        assertEquals("DW ", text)
    }

    @Test fun R241ProtectedFieldsAndTermuxNeverApplyGrammar() {
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)) {
            r22Enable("la la gramática")
            setInputType(type)
            ShadowInputMethodService.editorPackage = "fixture.editor"
            r241Observe("la"); r241Observe("gramática")
            r22Key(' '.code)
            assertEquals("la la gramática ", text)
        }
        r22Enable("la la gramática")
        ShadowInputMethodService.editorPackage = "com.termux"
        r241Observe("la"); r241Observe("gramática")
        r22Key(' '.code)
        assertEquals("la la gramática ", text)
    }

    @Test fun R241ContextSuggestionOneTapAndStaleTapCannotInsertLabel() {
        r22Enable("Slll estoy ")
        val assistant = r23Assistant()
        val capture = assistant.javaClass.getDeclaredMethod("capture").apply { isAccessible = true }
        val correction = helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction(
            0, 4, "Slll", "Solo", "test-explicit-choice", .7, false)
        val ctor = helium314.keyboard.latin.ai.AiKeyboardAssistant.ContextSuggestion::class.java.declaredConstructors.single()
            .apply { isAccessible = true }
        val suggestion = ctor.newInstance(assistant, capture.invoke(assistant), text, correction) as SuggestedWordInfo
        latinIME.pickSuggestionManually(suggestion)
        assertEquals("Solo estoy ", text)
        checkConnectionConsistency()
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("Slll estoy ", text)
        assistant.onUserInput()
        latinIME.pickSuggestionManually(suggestion)
        assertEquals("Slll estoy ", text)
    }

    @Test fun R241MovedCursorRejectsPendingContextCorrection() {
        r22Enable("Slll estoy después")
        val assistant = r23Assistant()
        val capture = assistant.javaClass.getDeclaredMethod("capture").apply { isAccessible = true }
        val correction = helium314.keyboard.latin.ai.local.LocalContextReviewer.Correction(
            0, 4, "Slll", "Solo", "test-explicit-choice", .7, false)
        val ctor = helium314.keyboard.latin.ai.AiKeyboardAssistant.ContextSuggestion::class.java.declaredConstructors.single()
            .apply { isAccessible = true }
        val candidate = ctor.newInstance(assistant, capture.invoke(assistant), text, correction) as SuggestedWordInfo
        setCursorPosition(4)
        latinIME.pickSuggestionManually(candidate)
        assertEquals("Slll estoy después", text)
        assertEquals(4, cursor)
        checkConnectionConsistency()
    }

    @Test fun R25AmbiguousContextNeverAutoCommitsAndBackspaceStillEditsNormally() {
        for (source in listOf("Hola esto y escribiendo rápido",
                "Juan salto por la ventana", "Seguimos naciendo pruebas",
                "Aun tienensentido")) {
            r22Enable(source)
            r22Key(' '.code)
            assertEquals("$source ", text)
            functionalKeyPress(KeyCode.DELETE)
            assertEquals(source, text)
            checkConnectionConsistency()
        }
    }

    @Test fun R243ObservedNativeSuggestionsFeedHybridWithoutChangingEditor() {
        r22Enable("tecmaod")
        r22NativeCandidates("tecmaod", false, listOf("teclado"))
        val assistantField = LatinIME::class.java.getDeclaredField("mAiKeyboardAssistant").apply { isAccessible = true }
        val assistant = assistantField.get(latinIME) as helium314.keyboard.latin.ai.AiKeyboardAssistant
        val lexiconField = assistant.javaClass.getDeclaredField("lexicon").apply { isAccessible = true }
        val observed = lexiconField.get(assistant) as helium314.keyboard.latin.ai.local.ObservedLexicon
        kotlin.test.assertTrue(observed.nativeValidVocabulary().contains("teclado"))
        kotlin.test.assertTrue(observed.snapshot().candidates("tecmaod").contains("teclado"))
        assertEquals("tecmaod", text) // No new automatic edits from hybrid.
        checkConnectionConsistency()
    }

    @Test fun R24MotorCorrectionAndUndoReuseNativeTransaction() {
        r22Enable("poryectos")
        r22NativeCandidates("poryectos", false, listOf("proyectos"))
        r22Key(' '.code)
        assertEquals("proyectos ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("poryectos ", text)
        latinIME.onLocalWordBoundary()
        assertEquals("poryectos ", text)
        checkConnectionConsistency()
    }

    @Test fun R24ProtectedVocabularyIsNotAutocorrected() {
        r22Enable("poryectos")
        val learning = helium314.keyboard.latin.ai.local.PersonalTypingStore.get(latinIME)
        learning.erase()
        kotlin.test.assertTrue(learning.keepWord("poryectos", "en-US"))
        r22NativeCandidates("poryectos", false, listOf("proyectos"))
        r22Key(' '.code)
        assertEquals("poryectos ", text)
        checkConnectionConsistency()
    }

    @Test fun R24NativeRejectionVetoesFutureAutomaticCandidate() {
        r22Enable("poryectos")
        val assistant = r23Assistant()
        val model = helium314.keyboard.latin.ai.local.PersonalTypingStore.get(latinIME)
        model.erase()
        assistant.onNativeCorrectionReverted("poryectos", "proyectos")
        val typed = SuggestedWordInfo("poryectos", "", 0, SuggestedWordInfo.KIND_TYPED, null, -1, -1)
        val corrected = SuggestedWordInfo("proyectos", "", 100, SuggestedWordInfo.KIND_CORRECTION, null, -1, -1)
        val words = SuggestedWords(arrayListOf(typed, corrected), null, typed, false, true, false, SuggestedWords.INPUT_STYLE_TYPING, 0)
        val guarded = assistant.protectNativeSuggestions(words)
        kotlin.test.assertFalse(guarded.mWillAutoCorrect)
        assertEquals("poryectos", guarded.mTypedWordInfo!!.mWord)
        kotlin.test.assertTrue(helium314.keyboard.latin.ai.AiSettingsActivity.rejectedCorrections(latinIME).contains("poryectos→proyectos"))
        assertEquals("poryectos", text)
    }

    @Test fun R24ProtectedEditorCannotLearnStyleOrReorderPredictions() {
        r22Enable("Oye, además.")
        val assistant = r23Assistant()
        val model = helium314.keyboard.latin.ai.local.PersonalTypingStore.get(latinIME)
        model.erase()
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit().putBoolean("personal_style_consent", true).commit()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val infos = arrayListOf(SuggestedWordInfo("asimismo", "", 100, SuggestedWordInfo.KIND_PREDICTION, null, -1, -1),
            SuggestedWordInfo("hey", "", 90, SuggestedWordInfo.KIND_PREDICTION, null, -1, -1))
        val words = SuggestedWords(infos, null, null, false, false, false, SuggestedWords.INPUT_STYLE_PREDICTION, 0)
        kotlin.test.assertSame(words, assistant.rankStylePredictions(words))
        assistant.onWordBoundary()
        kotlin.test.assertTrue(model.styleSnapshot().isEmpty())
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit().putBoolean("personal_style_consent", false).commit()
    }

    @Test fun R24StyleLearnsCompleteEllipsisOnceWithoutTreatingLetterNAsSeparator() {
        r22Enable("Oye, además quería mencionar una reunión...")
        val assistant = r23Assistant()
        val model = helium314.keyboard.latin.ai.local.PersonalTypingStore.get(latinIME)
        model.erase()
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit().putBoolean("personal_style_consent", true).commit()
        try {
            assistant.onWordBoundary()
            assistant.onWordBoundary()
            val profile = model.styleSnapshot()
            assertEquals(1L, profile["opening.casual"])
            assertEquals(1L, profile["connector.además"])
            assertEquals(1L, profile["ending.ellipsis"])
            kotlin.test.assertFalse(profile.toString().contains("reunión"))
            assertEquals("Oye, además quería mencionar una reunión...", text)
            model.eraseStyle()
            kotlin.test.assertTrue(model.styleSnapshot().isEmpty())
        } finally {
            latinIME.getSharedPreferences("keyboard_ai_private", 0).edit().putBoolean("personal_style_consent", false).commit()
        }
    }

    @Test fun R24StyleOptOutPreservesOriginalSuggestionsAndOptInUsesSameStrip() {
        r22Enable("Oye ")
        val assistant = r23Assistant()
        val infos = arrayListOf(
            SuggestedWordInfo("asimismo", "", 100, SuggestedWordInfo.KIND_PREDICTION, null, -1, -1),
            SuggestedWordInfo("hey", "", 90, SuggestedWordInfo.KIND_PREDICTION, null, -1, -1))
        val words = SuggestedWords(infos, null, null, false, false, false, SuggestedWords.INPUT_STYLE_PREDICTION, 0)
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit()
            .putBoolean("personal_style_consent", false).commit()
        kotlin.test.assertSame(words, assistant.rankStylePredictions(words))
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit()
            .putBoolean("personal_style_consent", true).putString("style_tone", "casual").commit()
        val ranked = assistant.rankStylePredictions(words)
        assertEquals("hey", ranked.getWord(0))
        kotlin.test.assertFalse(ranked.mWillAutoCorrect)
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit().putBoolean("personal_style_consent", false).commit()
    }

    @Test fun R23PersonalBoundaryAndSingleBackspaceUseRealIme() {
        r22Enable("teh ")
        val assistant = r23Assistant()
        val learning = helium314.keyboard.latin.ai.local.PersonalTypingStore.get(latinIME)
        learning.erase()
        repeat(5) { learning.accept("teh", "the", "en-US") }
        val field = assistant.javaClass.getDeclaredField("lexicon").apply { isAccessible = true }
        val observed = field.get(assistant) as helium314.keyboard.latin.ai.local.ObservedLexicon
        observed.record("teh", false, listOf("the"), true)
        assistant.onWordBoundary()
        assertEquals("the ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("teh ", text)
        kotlin.test.assertNull(learning.automatic("teh", "en-US", false))
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("teh", text)
        checkConnectionConsistency()
    }

    @Test fun R23GeminiApplyUsesSameBackspaceTransactionWithoutExtraReviewActions() {
        r22Enable("Antes hola después")
        setCursorPosition(6, 10)
        val assistant = r23Assistant()
        val capture = assistant.javaClass.getDeclaredMethod("capture").apply { isAccessible = true }
        val snapshot = capture.invoke(assistant) as helium314.keyboard.latin.ai.EditorSnapshot
        val field = assistant.javaClass.getDeclaredField("review").apply { isAccessible = true }
        val review = field.get(assistant) as helium314.keyboard.latin.ai.AiResultReview
        review.offer(snapshot, "Hola", true)
        kotlin.test.assertTrue(review.reviewed(snapshot))
        assistant.onAction("Usar", null)
        assertEquals("Antes Hola después", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("Antes hola después", text)
        checkConnectionConsistency()
    }

    @Test fun R23DictationTransactionAndImmediateBackspaceRestoreWholeInsertion() {
        r22Enable("Antes ")
        val assistant = r23Assistant()
        val capture = assistant.javaClass.getDeclaredMethod("capture").apply { isAccessible = true }
        val finish = assistant.javaClass.getDeclaredMethod("finishAutomatic",
            helium314.keyboard.latin.ai.EditorSnapshot::class.java, String::class.java).apply { isAccessible = true }
        finish.invoke(assistant, capture.invoke(assistant), "dictado completo")
        assertEquals("Antes dictado completo", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("Antes ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("Antes", text)
        checkConnectionConsistency()
    }

    @Test fun R23DictationCannotApplyAfterEditorGenerationChanges() {
        r22Enable("Antes ")
        val assistant = r23Assistant()
        val capture = assistant.javaClass.getDeclaredMethod("capture").apply { isAccessible = true }
        val snapshot = capture.invoke(assistant)
        assistant.onNewEditor()
        val finish = assistant.javaClass.getDeclaredMethod("finishAutomatic",
            helium314.keyboard.latin.ai.EditorSnapshot::class.java, String::class.java).apply { isAccessible = true }
        finish.invoke(assistant, snapshot, "obsoleto")
        assertEquals("Antes ", text)
    }

    @Test fun r22ContextEditsThroughRealImeAndBackspaceOnce() {
        r22Enable("voy haber si puedo")
        r22Key(' '.code)
        assertEquals("voy a ver si puedo ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("voy haber si puedo ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("voy haber si puedo", text)
        checkConnectionConsistency()
    }

    @Test fun r22PunctuationAndUtf16Offsets() {
        r22Enable("🙂 voy haber si puedo")
        r22Key('.'.code)
        assertEquals("🙂 voy a ver si puedo.", text)
        checkConnectionConsistency()
    }

    @Test fun r22EnterCompletesLastWordBeforeKeyboardAction() {
        r22Enable("voy haber si puedo")
        ShadowInputMethodService.editorOptions = EditorInfo.IME_ACTION_SEND
        r22Key(Constants.CODE_ENTER)
        assertEquals("voy a ver si puedo", text)
        assertEquals(listOf(EditorInfo.IME_ACTION_SEND to "voy a ver si puedo"), ShadowInputMethodService.editorActions)
        checkConnectionConsistency()
    }

    @Test fun r22MultilineEnterCorrectsBeforeNewline() {
        r22Enable("voy haber si puedo")
        ShadowInputMethodService.editorOptions = EditorInfo.IME_ACTION_NONE
        r22Key(Constants.CODE_ENTER)
        assertEquals("voy a ver si puedo\n", text)
        checkConnectionConsistency()
    }

    @Test fun r22ExternalMutationAtSameCursorCannotUndoOldContext() {
        r22Enable("voy haber si puedo")
        r22Key(' '.code)
        assertEquals("voy a ver si puedo ", text)
        ShadowInputMethodService.text = "hoy a ver si puedo "
        assertEquals(false, latinIME.undoLocalCorrectionOnBackspace())
        assertEquals("hoy a ver si puedo ", text)
    }

    @Test fun r22CompositionChangeAtSameCursorInvalidatesUndo() {
        r22Enable("voy haber si puedo")
        r22Key(' '.code)
        val pos = cursor
        latinIME.onUpdateSelection(pos, pos, pos, pos, 0, pos)
        assertEquals(false, latinIME.undoLocalCorrectionOnBackspace())
        assertEquals("voy a ver si puedo ", text)
    }

    private fun r22NativeCandidates(typed: String, valid: Boolean, candidates: List<String>) {
        val typedInfo = SuggestedWordInfo(typed, "", 0, SuggestedWordInfo.KIND_TYPED, null, 0, 0)
        val source = Mockito.mock(helium314.keyboard.latin.dictionary.Dictionary::class.java)
        val infos = arrayListOf(typedInfo)
        candidates.forEach { infos.add(SuggestedWordInfo(it, "", 100, SuggestedWordInfo.KIND_CORRECTION, source, 0, 0)) }
        val words = SuggestedWords(infos, null, typedInfo, valid, false, false, SuggestedWords.INPUT_STYLE_TYPING, 0)
        LatinIME::class.java.getDeclaredMethod("setSuggestedWords", SuggestedWords::class.java)
            .apply { isAccessible = true }.invoke(latinIME, words)
    }

    @Test fun r22NativeCandidateObservationsDriveActualImeSpellingEdit() {
        r22Enable("necesito cotisacion")
        r22NativeCandidates("cotisacion", false, listOf("cotización", "cotizaciones"))
        r22Key(' '.code)
        assertEquals("necesito cotización ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("necesito cotisacion ", text)
        checkConnectionConsistency()
    }

    @Test fun r22ValidPersonalAndBilingualWordsNeverUseCandidateOverride() {
        for (word in listOf("cotisacion", "meeting")) {
            r22Enable("necesito $word")
            r22NativeCandidates(word, true, listOf("cotización"))
            r22Key(' '.code)
            assertEquals("necesito $word ", text)
        }
    }

    @Test fun r22AmbiguousNativeCandidatesStayManual() {
        r22Enable("casz")
        r22NativeCandidates("casz", false, listOf("casa", "caza"))
        r22Key(' '.code)
        assertEquals("casz ", text)
    }

    @Test fun r22TruncatedContextFailsClosed() {
        val original = "palabra ".repeat(70) + "voy haber si puedo"
        r22Enable(original)
        r22Key(' '.code)
        assertEquals("$original ", text)
    }

    @Test fun r22LaterTypingInvalidatesUndo() {
        r22Enable("voy haber si puedo")
        r22Key(' '.code)
        r22Key('x'.code)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("voy a ver si puedo ", text)
    }

    @Test fun r22PasswordAndQuotedTextStayExact() {
        r22Enable("\"voy haber si puedo\"")
        r22Key(' '.code)
        assertEquals("\"voy haber si puedo\" ", text)
        r22Enable("voy haber si puedo")
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        ShadowInputMethodService.editorPackage = "fixture.editor"
        r22Key(' '.code)
        assertEquals("voy haber si puedo ", text)
    }

    @Test fun aiConfirmedReplacementKeepsCacheConsistent() {
        setText("  hola 👋\n")
        val original = text
        val method = try { RichInputConnection::class.java.getMethod("replaceTextForAi", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, CharSequence::class.java, CharSequence::class.java) }
            catch (missing: NoSuchMethodException) { throw AssertionError("Confirmed IME edit adapter missing", missing) }
        assertEquals(true, method.invoke(connection, 0, original.length, original, "Hola 👋"))
        assertEquals("Hola 👋", text)
        assertEquals(text, getTextFromConnection())
        assertEquals(false, method.invoke(connection, 0, text.length, "wrong original", "should not insert"))
        assertEquals("Hola 👋", text)
    }

    @Test fun inputCode() {
        input('c')
        assertEquals("c", textBeforeCursor)
        assertEquals("c", getTextFromConnection())
        assertEquals("", textAfterCursor)
        assertEquals("c", composingText)
        latinIME.mHandler.onFinishInput()
        assertEquals("", composingText)
    }

    @Test fun delete() {
        setText("hello there ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there", text)
        assertEquals("there", composingText)
    }

    @Test fun deleteMultiCodepointText() {
        setText("hello there \uD83E\uDF00")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)
    }

    @Test fun deleteCombinedText() {
        setText("hello there э́")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)

        setText("hello there H̵̛͕̞̦̰̜͍̰̥̟͆̏͂̌͑́ͅ")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello there ", text)
    }

    @Test fun deleteInsideWord() {
        setText("hello you there")
        setCursorPosition(8) // after o in you
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello yu there", text)
        assertEquals("yu", composingText)
    }

    @Test fun insertLetterIntoWord() {
        setText("hello")
        setCursorPosition(3) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getTextFromConnection())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
        assertEquals("", composingText)
    }

    @Test fun insertLetterIntoWordWithWeirdEditor() {
        currentInputType = 180225 // should not change much, but just to be sure
        setText("hello")
        setCursorPosition(3, weirdTextField = true) // after first l
        input('i')
        assertEquals("helilo", getWordAtCursor())
        assertEquals("helilo", getTextFromConnection())
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
    }

    @Test fun insertLetterIntoOneOfSeveralWords() {
        setText("hello my friend")
        setCursorPosition(7) // between m and y
        input('a')
        assertEquals("may", getWordAtCursor())
        assertEquals("hello may friend", getTextFromConnection())
        assertEquals(8, getCursorPosition())
        assertEquals(8, cursor)
    }

    @Test fun combineHangul() {
        val ko = koreanCombiningSubtype()
        latinIME.switchToSubtype(ko)
        chainInput("ㅂㄱㅑ")
        assertEquals("ㅂ갸", text)
    }

    @Test fun emojiHangul() {
        val ko = koreanCombiningSubtype()
        latinIME.switchToSubtype(ko)
        input(0x1F970)
        assertEquals("\uD83E\uDD70", text)
    }

    // todo: make it work, but it might not be that simple because adding is done in combiner
    //  https://github.com/HeliBorg/HeliBoard/issues/214
    @Test fun insertLetterIntoWordHangulPreservesSuffix() {
        latinIME.switchToSubtype(koreanCombiningSubtype())
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input('ㄲ')
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getWordAtCursor())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", getTextFromConnection())
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", textBeforeCursor + textAfterCursor)
        assertEquals(4, getCursorPosition())
        assertEquals(4, cursor)
        // Continue composing the inserted syllable, then undo its last Jamo.
        input('ㅏ')
        assertEquals("ㅛㅎㄹ까혀ㅛ", text)
        assertEquals("혀ㅛ", textAfterCursor)
        assertEquals(4, cursor)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("ㅛㅎㄹㄲ혀ㅛ", text)
        assertEquals("혀ㅛ", textAfterCursor)
        assertEquals(4, cursor)
    }

    @Test fun spaceInsideHangulPreservesSuffix() {
        latinIME.switchToSubtype(koreanCombiningSubtype())
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input(' ')
        assertEquals("ㅛㅎㄹ 혀ㅛ", text)
        assertEquals("혀ㅛ", textAfterCursor)
        assertEquals(4, cursor)
    }

    @Test fun punctuationInsideHangulPreservesSuffix() {
        latinIME.switchToSubtype(koreanCombiningSubtype())
        chainInput("ㅛㅎㄹㅎㅕㅛ")
        setCursorPosition(3)
        input('.')
        assertEquals("ㅛㅎㄹ.혀ㅛ", text)
        assertEquals("혀ㅛ", textAfterCursor)
        assertEquals(4, cursor)
    }

    // see issue 1447
    @Test fun separatorAfterHangul() {
        latinIME.switchToSubtype(koreanCombiningSubtype())
        chainInput("ㅛ.")
        assertEquals("ㅛ.", text)
    }

    @Test fun deleteHangulInDebugMode() { // issue 1551, later only happened on phone
        latinIME.switchToSubtype(koreanCombiningSubtype())
        setText("ㅛㅛ ")
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
    }

    @Test fun separatorUnselectsWord() {
        setText("hello")
        assertEquals("hello", composingText)
        input('.')
        assertEquals("", composingText)
    }

    @Test fun autospace() {
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello")
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
    }

    @Test fun autospaceButWithTextAfter() {
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello.a", textBeforeCursor)
        assertEquals("hello.a there", text)
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setText("hello there")
        setCursorPosition(5) // after hello
        input('.')
        input('a')
        assertEquals("hello. a", textBeforeCursor)
        assertEquals("hello. a there", text)
    }

    @Test fun noAutospaceInUrlField() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("example.net")
        assertEquals("example. net", text)
        lastAddedWord = ""
        setText("")
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("example.net")
        assertEquals("", lastAddedWord)
        assertEquals("example.net", text)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceInUrlFieldWhenPickingSuggestion() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("exam")
        pickSuggestion("example")
        assertEquals("example", text)
        input('.')
        assertEquals("example.", text)
    }

    @Test fun noAutospaceForDetectedUrl() { // "light" version, should work without url detection
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("http://example.net")
        assertEquals("http://example.net", text)
        assertEquals("http", lastAddedWord)
        assertEquals("example.net", composingText)
    }

    @Test fun noAutospaceForDetectedEmail() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("mail@example.com")
        assertEquals("mail@example.com", text)
        assertEquals("mail@example", lastAddedWord) // todo: do we want this? not really nice, but don't want to be too aggressive with URL detection disabled
        assertEquals("com", composingText) // todo: maybe this should still see the whole address as a single word? or don't be too aggressive?
        setText("")
        lastAddedWord = ""
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("mail@example.com")
        assertEquals("", lastAddedWord)
        assertEquals("mail@example.com", composingText)
    }

    @Test fun urlDetectionThings() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("...h")
        assertEquals("...h", text)
        assertEquals("h", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("bla..", text)
        assertEquals("", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.c")
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, true) }
        input("bla")
        input('.')
        functionalKeyPress(KeyCode.SHIFT) // should remove the phantom space (in addition to normal effect)
        input('c')
        assertEquals("bla.c", text)
        assertEquals("bla.c", composingText)
    }

    @Test fun stripSeparatorsBeforeAddingToHistoryWithURLDetection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("example.com.")
        assertEquals("example.com.", composingText)
        input(' ')
        assertEquals("example.com", lastAddedWord)
    }

    @Test fun dontSelectConsecutiveSeparatorsWithURLDetection() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla..")
        assertEquals("", composingText)
        assertEquals("bla..", text)
    }

    @Test fun selectDoesSelect() {
        setText("this is some text")
        setCursorPosition(3, 8)
        assertEquals("s is ", text.substring(3, 8))
    }

    @Test fun noComposingForPasswordFields() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        input('a')
        input('b')
        assertEquals("", composingText)
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        input('.')
        input('c')
        assertEquals("", composingText)
    }

    @Test fun `don't select whole thing as composing word if URL detection disabled`() {
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and selecting`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com")
        setCursorPosition(13) // between l and e
        assertEquals("example.com", composingText)
        setText("http://bla.com http://example.com ")
        setCursorPosition(29) // between l and e
        assertEquals("example.com", composingText)
    }

    @Test fun `select whole thing except http(s) as composing word if URL detection enabled and typing`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://example.com")
        assertEquals("example.com", composingText)
    }

    @Test fun `don't add partial URL to history`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http:/") // just so lastAddedWord isn't set to http
        chainInput("/bla.com")
        assertEquals("", lastAddedWord)
    }

    @Test fun urlProperlySelected() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete com
        // todo: do we really want no composing text?
        //  probably not... try not to break composing
        assertEquals("", composingText)
        chainInput("net")
        assertEquals("example.net", composingText)
    }

    @Test fun urlProperlySelectedWhenNotDeletingFullTld() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setText("http://example.com/here")
        setCursorPosition(18) // after .com
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE) // delete om
        // todo: this is a weird difference to deleting the full TLD (see urlProperlySelected)
        //  what do we want here? (probably consistency)
        assertEquals("example.c/here", composingText)
        chainInput("z")
        assertEquals("", composingText) // todo: this is a weird difference to deleting the full TLD
//        assertEquals("example.cz", composingText) // fails, but probably would be better than above
    }

    @Test fun dontCommitPartialUrlBeforeFirstPeriod() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        // type http://bla. -> bla not selected, but clearly url, also means http://bla is committed which we probably don't want
        chainInput("http://bla.")
        assertEquals("bla.", composingText)
    }

    @Test fun `intermediate commits in text field without protocol`() {
        chainInput("bla.")
        assertEquals("bla", lastAddedWord)
        chainInput("com/")
        assertEquals("com", lastAddedWord)
        chainInput("img.jpg")
        assertEquals("img", lastAddedWord)
        assertEquals("jpg", composingText)
    }

    @Test fun `intermediate commit in text field without protocol and with URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("bla.com/img.jpg")
        assertEquals("bla", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `only protocol commit in text field with protocol and URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("http://bla.com/img.jpg")
        assertEquals("http", lastAddedWord)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("", lastAddedWord) // URI fields must not retain protocol in learned history.
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field with protocol and URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("http://bla.com/img.jpg")
        assertEquals("", lastAddedWord) // URI fields must not retain protocol in learned history.
        assertEquals("http://bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `no intermediate commit in URL field without protocol and with URL detection`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        chainInput("bla.com/img.jpg")
        assertEquals("", lastAddedWord)
        assertEquals("bla.com/img.jpg", text)
        assertEquals("bla.com/img.jpg", composingText)
    }

    @Test fun `don't accidentally detect some other text fields as URI`() {
        // see comment in InputLogic.textBeforeCursorMayBeUrlOrSimilar
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)
        chainInput("Hey,why")
        assertEquals("Hey, why", text)
    }

    @Test fun `URL detection does not trigger on non-words`() {
        // first make sure it works without URL detection
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
        // then with URL detection
        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        chainInput("15:50-17")
        assertEquals("15:50-17", text)
        assertEquals("", composingText)
    }

    @Test fun `autospace after selecting a suggestion`() {
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    @Test fun `autospace works in URL field when input isn't URL`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        input('b')
        assertEquals("this b", text)
        assertEquals("b", composingText)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/215
    // https://github.com/HeliBorg/HeliBoard/issues/229
    @Test fun `autospace works in URL field when input isn't URL, also for multiple suggestions`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pickSuggestion("this")
        pickSuggestion("is")
        assertEquals("this is", text)
        pickSuggestion("not")
        assertEquals("this is not", text)
        input('c')
        assertEquals("this is not c", text)
        assertEquals("c", composingText)
    }

    @Test fun `emoji is added to dictionary`() {
        // check both text and codepoint input
        chainInput("hello ")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        reset()
        chainInput("hello ")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)

        reset()
        chainInput("hello ")
        input("why 🤗 ") // not added because it's not only emoji (input can come from pasting)
        assertEquals("hello", lastAddedWord)
    }

    @Test fun `emoji uses phantom space`() {
        // check both text and codepoint input
        pickSuggestion("hi")
        input("🤗")
        assertEquals("\uD83E\uDD17", lastAddedWord)
        assertEquals("hi \uD83E\uDD17", text)
        reset()
        pickSuggestion("hi")
        input(0x1F36D)
        assertEquals(StringUtils.newSingleCodePointString(0x1F36D), lastAddedWord)
        assertEquals("hi ${StringUtils.newSingleCodePointString(0x1F36D)}", text)
    }

    // https://github.com/HeliBorg/HeliBoard/issues/230
    @Test fun `no autospace after opening quotes`() {
        chainInput("\"Hi\" \"h")
        assertEquals("\"Hi\" \"h", text)
        assertEquals("h", composingText)
        reset()
        chainInput("\"Hi\", \"h")
        assertEquals("\"Hi\", \"h", text)
        assertEquals("h", composingText)
    }

    @Test fun `autospace works in URL field when starting with quotes`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_URL_DETECTION, true) }
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input("\"")
        pickSuggestion("this")
        input("i")
        assertEquals("\"this i", text)
    }

    @Test fun `double space results in period and space, and delete removes the period`() {
        chainInput("hello")
        input(' ')
        input(' ')
        assertEquals("hello. ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello ", text)
    }

    @Test fun `no weird space inside multi-"`() {
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)

        reset()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"\"\"")
        assertEquals("\"\"\"", text)
    }

    @Test fun `autospace still happens after "`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if next word is in quotes`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\"\"you\"")
        assertEquals("\"hello\" \"you\"", text)
    }

    @Test fun `autospace propagates over "`() {
        input('"')
        pickSuggestion("hello")
        assertEquals(spaceState, SpaceState.PHANTOM) // picking a suggestion sets phantom space state
        chainInput("\"you")
        assertEquals("\"hello\" you", text)
    }

    @Test fun `autospace still happens after " if nex word is in " and after comma`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("\"hello\",\"you\"")
        assertEquals("\"hello\", \"you\"", text)
    }

    @Test fun `autospace in json editor`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        chainInput("{\"label\":\"")
        assertEquals("{\"label\": \"", text)
        input('c')
        assertEquals("{\"label\": \"c", text)
    }

    @Test fun `text input and delete`() {
        input("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hell", text)

        reset()
        input("hello ")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `emoji text input and delete`() {
        input("🕵🏼")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        reset()
        input("\uD83D\uDD75\uD83C\uDFFC")
        input(' ')
        assertEquals("🕵🏼 ", text)
        functionalKeyPress(KeyCode.DELETE)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)
    }

    // emoRegex update to unicode 16.0 was required, https://github.com/HeliBorg/HeliBoard/issues/1760
    @Test fun `emojis deleted one by one`() {
        chainInput("\uD83E\uDEC6\uD83E\uDEC6\uD83E\uDEC6")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("\uD83E\uDEC6\uD83E\uDEC6", text)
    }

    @Test fun `revert autocorrect on delete`() {
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        assertEquals("hello ", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hullo", text)

        reset()
        setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        latinIME.prefs().edit { putBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, false) }
        chainInput("hullo")
        getAutocorrectedWithSpaceAfter("hello", "hullo")
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("hello", text)
    }

    @Test fun `remove glide typing word on delete`() {
        glideTypingInput("hello")
        assertEquals("hello", text)
        functionalKeyPress(KeyCode.DELETE)
        assertEquals("", text)

        // todo: now we want some way to disable delete-all on backspace, either per setting or something else
        //  need to avoid getting into the mWordComposer.isBatchMode() part of handleBackspaceEvent
    }

    @Test fun timestamp() {
        chainInput("hello")
        functionalKeyPress(KeyCode.TIMESTAMP)
        assertEquals(Calendar.getInstance().time.time.toDouble(),
            getTimestampFormatter(latinIME).parse(text.substring(5))!!.time.toDouble(), 1000.0)
    }

    @Test fun inlineEmojiSearchStart() {
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, ' '.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch(' '.code, ':'.code, ' '.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '.'.code, settingsValues))
        assertEquals(true, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, "🌍".codePoints().asSequence().last(), settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, 't'.code, settingsValues))
        assertEquals(false, InputLogic.isStartOfInlineEmojiSearch('t'.code, ':'.code, '3'.code, settingsValues))
    }

    @Test fun inlineEmojiSearchString() {
        assertEquals("test", InputLogic.getInlineEmojiSearchString(":test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(" :test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("t:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString("6:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString("🌍:test"))
        assertEquals("test", InputLogic.getInlineEmojiSearchString(",:test"))
        assertEquals(null, InputLogic.getInlineEmojiSearchString(":test\nt"))
        assertEquals("/48", InputLogic.getInlineEmojiSearchString("2606:127.0.0.1::/48")) // do we want this?
    }

    @Test fun moveCursorHorizontally() {
        chainInput("hello")
        assertEquals(5, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(-2)
        assertEquals(3, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(-5)
        assertEquals(0, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(-1)
        assertEquals(0, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(3)
        assertEquals(3, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(3)
        assertEquals(5, cursor)
        latinIME.mKeyboardActionListener.onHorizontalSpaceSwipe(1)
        assertEquals(5, cursor)
    }

    // ------- helper functions ---------

    // should be called before every test, so the same state is guaranteed
    @BeforeTest
    fun reset() {
        // reset input connection & facilitator
        currentScript = ScriptUtils.SCRIPT_LATIN
        ShadowInputMethodService.reset()
        lastAddedWord = ""
        ShadowFacilitator2.initializedMain = true
        ShadowFacilitator2.uninitializedMain = false

        // reset settings
        latinIME.getSharedPreferences("keyboard_ai_private", 0).edit().clear().commit()
        latinIME.prefs().edit { clear() }
        // The new persistent singleton must be isolated just like preferences/dictionaries.
        helium314.keyboard.latin.ai.local.PersonalTypingStore.get(latinIME).erase()

        setText("") // (re)sets selection and composing word
    }

    private fun chainInput(text: String) = text.forEach { input(it.code) }

    private fun input(char: Char) = input(char.code)

    private fun input(codePoint: Int) {
        require(codePoint > 0) { "not a codePoint: $codePoint" }
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val insert = StringUtils.newSingleCodePointString(codePoint)
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""
        val oldIsAtEnd = !composer.isCursorFrontOrMiddleOfComposingWord
        val oldComposingStart = composingStart

        latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(codePoint))
        handleMessages()

        if (!latinIME.prefs().getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.contains("CombiningRules") // check fails if combiner merges symbols
            && !(codePoint == Constants.CODE_SPACE && oldBefore.lastOrNull() == ' ') // check fails when 2 spaces are converted into a period
            && !latinIME.mInputLogic.mSuggestedWords.mWillAutoCorrect // autocorrect obviously creates inconsistencies
            ) {
            if (phantomSpaceToInsert.isEmpty())
                assertEquals(oldBefore + insert, textBeforeCursor)
            else // in some cases autospace might be suppressed
                assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        }
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getTextFromConnection())
        // A new composing range may legitimately begin at a moved cursor.
        // Preserve the cursor-position invariant only while editing the same range.
        if (composer.isComposingWord && oldComposingStart == composingStart)
            assertEquals(oldIsAtEnd, !composer.isCursorFrontOrMiddleOfComposingWord)
        checkConnectionConsistency()
    }

    private fun functionalKeyPress(keyCode: Int) {
        require(keyCode < 0) { "not a functional key code: $keyCode" }
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, keyCode, 0, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        handleMessages()
        checkConnectionConsistency()
    }

    // almost the same as codePoint input, but calls different latinIME function
    private fun input(insert: String) {
        val oldBefore = textBeforeCursor
        val oldAfter = textAfterCursor
        val phantomSpaceToInsert = if (spaceState == SpaceState.PHANTOM) " " else ""

        latinIME.onTextInput(insert)
        handleMessages()

        if (phantomSpaceToInsert.isEmpty())
            assertEquals(oldBefore + insert, textBeforeCursor)
        else // in some cases autospace might be suppressed
            assert(oldBefore + phantomSpaceToInsert + insert == textBeforeCursor || oldBefore + insert == textBeforeCursor)
        assert(oldBefore + insert == textBeforeCursor || "$oldBefore $insert" == textBeforeCursor)
        assertEquals(oldAfter, textAfterCursor)
        assertEquals(textBeforeCursor + textAfterCursor, getTextFromConnection())
        checkConnectionConsistency()
    }

    private fun getWordAtCursor() = connection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations, currentScript)?.mWord

    private fun setCursorPosition(start: Int, end: Int = start, weirdTextField: Boolean = false) {
        val ei = EditorInfo()
        ei.inputType = currentInputType
        ei.initialSelStart = start
        ei.initialSelEnd = end
        // imeOptions should not matter

        // adjust text in inputConnection first, otherwise fixLyingCursorPosition will move cursor
        // to the end of the text
        val fullText = textBeforeCursor + selectedText + textAfterCursor
        assertEquals(fullText, getTextFromConnection())

        // need to update ic before, otherwise when reloading text cache from ic, ric will load wrong text before cursor
        val oldStart = selectionStart
        val oldEnd = selectionEnd
        selectionStart = start
        selectionEnd = end
        assertEquals(fullText, textBeforeCursor + selectedText + textAfterCursor)

        latinIME.onUpdateSelection(oldStart, oldEnd, start, end, composingStart, composingEnd)
        handleMessages()

        if (weirdTextField) {
            latinIME.mHandler.onStartInput(ei, true) // essentially does nothing
            latinIME.mHandler.onStartInputView(ei, true) // does the thing
            handleMessages()
        }

        assertEquals(fullText, getTextFromConnection())
        assertEquals(start, selectionStart)
        assertEquals(end, selectionEnd)
        checkConnectionConsistency()
    }

    // assumes we have nothing selected
    private fun getCursorPosition(): Int {
        assertEquals(cursor, connection.expectedSelectionStart)
        assertEquals(cursor, connection.expectedSelectionEnd)
        return cursor
    }

    // just sets the text and starts input so connection it set up correctly
    private fun setText(newText: String) {
        ShadowInputMethodService.text = newText
        selectionStart = newText.length
        selectionEnd = selectionStart
        composingStart = -1
        composingStart = -1

        // we need to start input to notify that something changed
        // restarting is false, so this is seen as a new text field
        val ei = EditorInfo()
        ei.inputType = currentInputType
        latinIME.mHandler.onStartInput(ei, false)
        latinIME.mHandler.onStartInputView(ei, false)
        handleMessages() // this is important so the composing span is set correctly
        checkConnectionConsistency()
    }

    // like selecting a suggestion from strip
    private fun pickSuggestion(suggestion: String) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        latinIME.pickSuggestionManually(info)
        checkConnectionConsistency()
    }

    // only works when autocorrect is on, separator after word is required
    private fun getAutocorrectedWithSpaceAfter(suggestion: String, typedWord: String?) {
        val info = SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0)
        val typedInfo = SuggestedWordInfo(typedWord, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(typedInfo, info)), null, typedInfo, false, true, false, 0, 0)
        latinIME.mInputLogic.setSuggestedWords(sw) // this prepares for autocorrect
        input(' ')
        checkConnectionConsistency()
    }

    private fun glideTypingInput(word: String) {
        val info = SuggestedWordInfo(word, "", 0, 0, null, 0, 0)
        val sw = SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0)
        latinIME.mInputLogic.onUpdateTailBatchInputCompleted(settingsValues, sw, KeyboardSwitcher.getInstance())
    }

    private fun checkConnectionConsistency() {
        // RichInputConnection only has composing text up to cursor, but InputConnection has full composing text
        val expectedConnectionComposingText = if (composingStart == -1 || composingEnd == -1) ""
        else text.substring(composingStart, min(composingEnd, selectionEnd))
        assert(composingText.startsWith(expectedConnectionComposingText))
        // RichInputConnection only returns text up to cursor
        val textBeforeComposingText = if (composingStart == -1) textBeforeCursor else text.substring(0, composingStart)

        println("consistency: $selectionStart, ${connection.expectedSelectionStart}, $selectionEnd, ${connection.expectedSelectionEnd}, $textBeforeComposingText, " +
                "$connectionTextBeforeComposingText, $composingText, $connectionComposingText, $textBeforeCursor, ${connection.getTextBeforeCursor(textBeforeCursor.length, 0)}" +
                ", $textAfterCursor, ${connection.getTextAfterCursor(textAfterCursor.length, 0)}")
        assertEquals(selectionStart, connection.expectedSelectionStart)
        assertEquals(selectionEnd, connection.expectedSelectionEnd)
        assertEquals(textBeforeComposingText, connectionTextBeforeComposingText)
        assertEquals(expectedConnectionComposingText, connectionComposingText)
        assertEquals(textBeforeCursor, connection.getTextBeforeCursor(textBeforeCursor.length, 0).toString())
        assertEquals(textAfterCursor, connection.getTextAfterCursor(textAfterCursor.length, 0).toString())
    }

    private fun getTextFromConnection() =
        connection.getTextBeforeCursor(100, 0).toString() + (connection.getSelectedText(0) ?: "") + connection.getTextAfterCursor(100, 0)

    private fun setInputType(inputType: Int) {
        // set text to actually apply input type
        currentInputType = inputType
        setText(text)
    }

    // always need to handle messages for proper simulation
    private fun handleMessages() {
        while (messages.isNotEmpty()) {
            latinIME.mHandler.handleMessage(messages.first())
            messages.removeAt(0)
        }
        while (delayedMessages.isNotEmpty()) {
            val msg = delayedMessages.first()
            if (msg.what != 2) // MSG_UPDATE_SUGGESTION_STRIP, we want to ignore it because it's irrelevant and has a 500 ms timeout
                latinIME.mHandler.handleMessage(delayedMessages.first())
            delayedMessages.removeAt(0)
            // delayed messages may post further messages, handle before next delayed message
            while (messages.isNotEmpty()) {
                latinIME.mHandler.handleMessage(messages.first())
                messages.removeAt(0)
            }
        }
        assertEquals(0, messages.size)
        assertEquals(0, delayedMessages.size)
    }

}

private var currentScript = ScriptUtils.SCRIPT_LATIN
private val messages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService
private val delayedMessages = mutableListOf<Message>() // for latinIME / ShadowInputMethodService

// Shadows are handled by Robolectric. @Implementation overrides built-in functionality.
// This is used for avoiding crashes (LocaleManagerCompat, InputMethodManager, KeyboardSwitcher)
// and for simulating system stuff (InputMethodService for controlling the InputConnection, which
// more or less is the contents of the text field), and for setting the current script in
// KeyboardSwitcher without having to care about InputMethodSubtypes

@Implements(Handler::class)
class ShadowHandler {
    @Implementation
    fun sendMessage(message: Message) {
        messages.add(message)
    }
    @Implementation
    fun sendMessageDelayed(message: Message, delay: Long) {
        delayedMessages.add(message)
    }
}

@Implements(KeyboardSwitcher::class)
class ShadowKeyboardSwitcher {
    @Implementation
    // basically only needed for null check
    fun getMainKeyboardView(): MainKeyboardView = Mockito.mock(MainKeyboardView::class.java)
    @Implementation
    // only affects view
    fun setKeyboard(keyboardId: Int, toggleState: KeyboardSwitcher.KeyboardSwitchState) = Unit
    @Implementation
    // only affects view
    fun setOneHandedModeEnabled(enabled: Boolean, force: Boolean) = Unit
    @Implementation
    fun getCurrentKeyboardScript() = currentScript
}

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator2 {
    // Robolectric provides no real native dictionaries; model their readiness explicitly,
    // instead of silently treating an empty fixture as production dictionary availability.
    @Implementation
    fun hasAtLeastOneInitializedMainDictionary(): Boolean = initializedMain

    @Implementation
    fun hasAtLeastOneUninitializedMainDictionary(): Boolean = uninitializedMain

    @Implementation
    fun isValidSpellingWord(word: String): Boolean =
        helium314.keyboard.latin.ai.local.R241WritingTest.Vocabulary().valid(word)

    @Implementation
    fun addToUserHistory(suggestion: String, wasAutoCapitalized: Boolean,
                         ngramContext: NgramContext, timeStampInSeconds: Long,
                         blockPotentiallyOffensive: Boolean) {
        lastAddedWord = suggestion
    }
    companion object {
        var lastAddedWord = ""
        var initializedMain = true
        var uninitializedMain = false
    }
}
