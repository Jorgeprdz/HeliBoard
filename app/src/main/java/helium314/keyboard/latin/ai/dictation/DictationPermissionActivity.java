// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.dictation;
import android.Manifest;import android.app.Activity;import android.os.Build;import android.os.Bundle;import android.content.pm.PackageManager;
/** Permission only. Returning never automatically opens the microphone. */
public final class DictationPermissionActivity extends Activity {
 @Override public void onCreate(Bundle state){super.onCreate(state);if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);else finish();}
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);finish();}
}
