package helium314.keyboard

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.getKnownDictionariesForLocale
import org.json.JSONArray
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class XLinkTest { // Without the X, SubtypeTests fail with ClassCastException. WTF?
    @Test fun knownDictionaries() {
        if (BuildConfig.BUILD_TYPE == "runTests") return // don't spam requests to Codeberg on every PR update
        val context = ApplicationProvider.getApplicationContext<App>()
        val urls = mutableSetOf<String>()
        context.assets.open("dictionaries_in_dict_repo.csv").reader().readLines().forEach { line ->
            getKnownDictionariesForLocale(line.split(",")[1].constructLocale(), context).forEach {
                urls.add(it.second)
            }
        }
        // Validate every generated path against the live repository, with only
        // two API requests instead of hundreds of rate-limited raw-file requests.
        val directories = listOf("dictionaries", "emoji_cldr_signal_dictionaries")
        val available = mutableSetOf<String>()
        directories.forEach { directory ->
            val api = "https://codeberg.org/api/v1/repos/Helium314/aosp-dictionaries/contents/$directory?ref=main"
            val entries = JSONArray(readLink(api))
            assertTrue(entries.length() > 0, "Empty catalog: $api")
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                if (entry.getString("type") == "file")
                    available.add("$directory/" + entry.getString("name"))
            }
        }
        val prefix = Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX
        assertTrue(urls.isNotEmpty(), "No dictionary URLs were generated")
        urls.forEach { link ->
            assertTrue(link.startsWith(prefix), "Unexpected dictionary origin: $link")
            assertTrue(link.removePrefix(prefix) in available, "Missing dictionary: $link")
        }
        // Also exercise the actual raw download endpoint for both directories.
        directories.forEach { directory ->
            checkLink(urls.first { it.startsWith(prefix + directory + "/") })
        }
    }

    @Test fun dictionaryLinksUseCurrentCatalogNames() {
        val context = ApplicationProvider.getApplicationContext<App>()
        val expected = listOf(
            "en_US" to "dictionaries/main_en_us__experimental.dict",
            "es_MX" to "dictionaries/main_es__experimental.dict",
            "bn" to "dictionaries/main_bn__legacy.dict",
            "de_AT" to "dictionaries/main_de_at.dict",
            "tr" to "dictionaries/main_tr__legacy.dict"
        )
        expected.forEach { (locale, path) ->
            val links = getKnownDictionariesForLocale(locale.constructLocale(), context).map { it.second }
            assertTrue(Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX + path in links,
                "Missing current dictionary URL for $locale: $path")
        }
    }

    @Test fun layoutsLinksInternal() {
        val file = File("../layouts.md")
        val internalLinkRegex = "app/src/\\b(?:[-a-zA-Z0-9@:%_\\+.~#?&\\/\\/=]*)".toRegex()
        internalLinkRegex.findAll(file.readText()).forEach {
            val path = it.value.substringBefore('#')
            assertTrue(File("..", path).isFile, "Missing documented source path: $path")
        }
    }

    private fun checkLink(link: String) {
        var status = responseCode(link, "HEAD")
        // Some sites disallow HEAD while serving ordinary GET requests.
        if (status == 403 || status == 405)
            status = responseCode(link, "GET")
        assertEquals(200, status, "Unavailable link: $link")
    }

    private fun connection(link: String, method: String): HttpURLConnection =
        (URL(link).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("User-Agent",
                "HeliBoard-link-check/1.0 (+https://github.com/Jorgeprdz/HeliBoard)")
        }

    private fun responseCode(link: String, method: String): Int {
        val connection = connection(link, method)
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun readLink(link: String): String {
        val connection = connection(link, "GET")
        return try {
            assertEquals(200, connection.responseCode, "Unavailable link: $link")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

}
