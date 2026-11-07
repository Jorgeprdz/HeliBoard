package helium314.keyboard.latin.gifs;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
/** Runs the same contracts with Android's actual JSONObject, not mockable Android stubs. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GifItemMetadataAndroidTest extends GifItemMetadataTest {}
