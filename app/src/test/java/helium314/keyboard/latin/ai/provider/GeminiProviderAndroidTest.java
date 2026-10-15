// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.provider;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** Android JSON contract; these tests exercise HTTP fakes, not native TLS cryptography. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GeminiProviderAndroidTest extends GeminiProviderTest {}
