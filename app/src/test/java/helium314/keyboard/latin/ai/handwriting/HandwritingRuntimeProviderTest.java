package helium314.keyboard.latin.ai.handwriting;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.UserManager;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class HandwritingRuntimeProviderTest {
 @Test public void lockedUserNeverTouchesCredentialStorageOrRuntime(){
  Context app=ApplicationProvider.getApplicationContext();UserManager users=(UserManager)app.getSystemService(Context.USER_SERVICE);
  Shadows.shadowOf(users).setUserUnlocked(false);
  Context failOnStorage=new ContextWrapper(app){@Override public ApplicationInfo getApplicationInfo(){throw new AssertionError("Credential storage touched while locked");}};
  try{assertFalse(HandwritingRuntimeProvider.ensureInitialized(failOnStorage));}finally{Shadows.shadowOf(users).setUserUnlocked(true);}
 }
 @Test public void missingContextDoesNotCrashKeyboard(){assertFalse(HandwritingRuntimeProvider.ensureInitialized(null));}
 @Test public void asynchronousDatabaseFailureDisablesOptionalRuntimeWithoutThrowing(){
  try {
   assertNotNull(HandwritingRuntimeProvider.configuration().getInitializationExceptionHandler());
   HandwritingRuntimeProvider.configuration().getInitializationExceptionHandler().accept(new IllegalStateException("test database unavailable"));
   assertFalse(HandwritingRuntimeProvider.ensureInitialized(ApplicationProvider.getApplicationContext()));
  } finally {
   try {java.lang.reflect.Field flag=HandwritingRuntimeProvider.class.getDeclaredField("initializationFailed");flag.setAccessible(true);flag.setBoolean(null,false);}
   catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
  }
 }
}
