// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.dictionary;

import android.content.Context;

import com.android.inputmethod.latin.BinaryDictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import helium314.keyboard.latin.utils.Log;

/**
 * Small built-in supplementary dictionary for common proper names.
 *
 * <p>The source word lists live as plain-text assets so they can be reviewed and updated without
 * replacing the language's main binary dictionary. The list is compiled into an expandable binary
 * dictionary on first use.</p>
 */
public final class ProperNamesDictionary extends ExpandableBinaryDictionary {
    private static final String TAG = ProperNamesDictionary.class.getSimpleName();
    private static final String NAME = "proper_names_v1";
    private static final int FREQUENCY_FOR_PROPER_NAMES = 100;

    private static final String ASSET_EN_US = "proper_names/proper_names_en-US.txt";
    private static final String ASSET_ES_419 = "proper_names/proper_names_es-419.txt";

    private final String mAssetPath;

    private ProperNamesDictionary(final Context context, final Locale locale,
            final String assetPath) {
        super(context, getDictName(NAME, locale, null), locale, Dictionary.TYPE_PROPER_NAMES, null);
        mAssetPath = assetPath;
        reloadDictionaryIfRequired();
    }

    /**
     * Returns a supplementary proper-name dictionary for supported locales.
     * English is intentionally limited to US/generic English. Spanish uses the Latin-American
     * list for generic Spanish and all non-Spain regional variants.
     */
    public static ProperNamesDictionary forLocale(final Context context, final Locale locale) {
        final String language = locale.getLanguage();
        final String country = locale.getCountry();
        if ("en".equals(language) && (country.isEmpty() || "US".equals(country))) {
            return new ProperNamesDictionary(context, locale, ASSET_EN_US);
        }
        if ("es".equals(language) && (country.isEmpty() || !"ES".equals(country))) {
            return new ProperNamesDictionary(context, locale, ASSET_ES_419);
        }
        return null;
    }

    @Override
    protected void loadInitialContentsLocked() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                mContext.getAssets().open(mAssetPath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                final int codePointCount = name.codePointCount(0, name.length());
                if (codePointCount <= 1 || codePointCount > MAX_WORD_LENGTH) {
                    continue;
                }
                addUnigramLocked(name, FREQUENCY_FOR_PROPER_NAMES,
                        null /* shortcutTarget */, 0 /* shortcutFreq */, false /* isNotAWord */,
                        false /* isPossiblyOffensive */,
                        BinaryDictionary.NOT_A_VALID_TIMESTAMP);
            }
        } catch (final IOException e) {
            Log.e(TAG, "Could not load proper-name asset " + mAssetPath + ": " + e);
        }
    }
}
