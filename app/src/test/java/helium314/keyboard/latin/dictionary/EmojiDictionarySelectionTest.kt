// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictionary

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Locale
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class EmojiDictionarySelectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun bundledEmojiDataAvailableForLanguagesAndRegionalFallbackWithoutDownload() {
        for (locale in listOf(Locale("es"), Locale.forLanguageTag("es-MX"), Locale.US, Locale.FRANCE, Locale.JAPAN)) {
            val (cached, bundled) = DictionaryFactory.getAvailableDictsForLocale(locale, context, true)
            assertTrue(cached.any { it.name.startsWith("emoji") } || bundled.any { it.startsWith("emoji_") },
                "No emoji dictionary for $locale")
            if (locale.language in listOf("es", "en"))
                assertTrue(bundled.any { it.startsWith("main_") } || cached.any { it.name.startsWith("main") })
        }
    }

    @Test fun bundledEmojiAvailabilityDoesNotRequireFirstDictionaryExtraction() {
        helium314.keyboard.latin.settings.Settings.getInstance().loadSettings(context)
        assertTrue(DictionaryInfoUtils.getLocalesWithEmojiDicts(context).isNotEmpty(),
            "Bundled dictionaries must be available before typing or downloading anything")
    }

    @Test fun disablingEmojiSuggestionsExcludesBundledAndCachedEmojiData() {
        val locale = Locale("es")
        val directory = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)!!
        val cachedEmojis = listOf(File(directory, "emoji_user.dict"), File(directory, "emoji.dict"))
        cachedEmojis.forEach { it.writeText("selection-only fixture; never decoded") }
        try {
            val (cached, bundled) = DictionaryFactory.getAvailableDictsForLocale(locale, context, false)
            assertFalse(cached.any { it.name.startsWith("emoji") })
            assertFalse(bundled.any { it.startsWith("emoji_") })
            if (locale.language in listOf("es", "en"))
                assertTrue(bundled.any { it.startsWith("main_") } || cached.any { it.name.startsWith("main") })
        } finally {
            cachedEmojis.forEach { it.delete() }
        }
    }
}
