package helium314.keyboard.latin.ai.handwriting;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class HandwritingStorageContextTest {
 @Test public void normalizationStaysCredentialScopedAndLeavesOriginalContextUnchanged(){
  Context app=ApplicationProvider.getApplicationContext();Context dp=app.createDeviceProtectedStorageContext();
  String original=dp.getApplicationInfo().dataDir;
  HandwritingStorageContext ce=new HandwritingStorageContext(dp);
  assertSame(ce,ce.getApplicationContext());assertFalse(ce.isDeviceProtectedStorage());
  assertNotSame(dp.getApplicationInfo(),ce.getApplicationInfo());
  assertEquals(original,dp.getApplicationInfo().dataDir);assertTrue(dp.isDeviceProtectedStorage());
  assertEquals(ce.getDataDir(),ce.getFilesDir().getParentFile());assertEquals(ce.getDataDir(),ce.getNoBackupFilesDir().getParentFile());
  assertEquals(ce.getDataDir(),ce.getCacheDir().getParentFile());assertEquals(ce.getDataDir(),ce.getCodeCacheDir().getParentFile());
 }
 @Test public void deviceProtectedRootsResolveToCorrespondingCredentialUser(){
  assertEquals(new File("/data/user/0/pkg"),HandwritingStorageContext.credentialRoot("/data/user_de/0/pkg"));
  assertEquals(new File("/data/user/10/pkg"),HandwritingStorageContext.credentialRoot("/data/user_de/10/pkg"));
  assertEquals(new File("/data/user/0/pkg"),HandwritingStorageContext.credentialRoot("/data/user/0/pkg"));
 }
 @Test public void roomDatabaseIsCreatedOnlyUnderScopedCredentialRoot(){
  Context app=ApplicationProvider.getApplicationContext();HandwritingStorageContext ce=new HandwritingStorageContext(app);
  File path=ce.getDatabasePath("handwriting-test.db");assertEquals(new File(ce.getDataDir(),"databases"),path.getParentFile());
  assertEquals(new File(ce.getNoBackupFilesDir(),"androidx.work.workdb"),ce.getDatabasePath(new File(ce.getNoBackupFilesDir(),"androidx.work.workdb").getPath()));
  SQLiteDatabase db=ce.openOrCreateDatabase("handwriting-test.db",Context.MODE_PRIVATE,null);assertEquals(path.getPath(),db.getPath());db.close();assertTrue(ce.deleteDatabase("handwriting-test.db"));
 }
}
