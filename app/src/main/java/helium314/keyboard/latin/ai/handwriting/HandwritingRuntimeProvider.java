// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.handwriting;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import com.google.mlkit.common.MlKit;

/** Non-exported, non-Direct-Boot provider: prepares CE runtime on unlocked process/job cold starts. */
public final class HandwritingRuntimeProvider extends ContentProvider {
    private static volatile boolean initialized;
    private static volatile boolean initializationFailed;
    @Override public boolean onCreate() {
        ensureInitialized(getContext());
        return true;
    }
    /** Retry after unlock if this process originally started during Direct Boot. */
    public static synchronized boolean ensureInitialized(Context context) {
        if (context == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
                if (users == null || !users.isUserUnlocked()) return false;
            }
        if (initializationFailed) return false;
        if (initialized) return true;
            Context credential = new HandwritingStorageContext(context);
            try {
                WorkManager.getInstance(credential);
            } catch (IllegalStateException notInitialized) {
                WorkManager.initialize(credential, configuration());
            }
            MlKit.initialize(credential);
            initialized = true;
            return !initializationFailed;
        } catch (RuntimeException | LinkageError unavailable) {
            // Optional recognition must never take down the keyboard or expose provider/key details.
            Log.w("HandwritingRuntime", "Optional handwriting runtime unavailable");
            return false;
        }
    }
    static Configuration configuration() {
        androidx.core.util.Consumer<Throwable> handler = error -> {
            // WorkManager opens its database asynchronously; the provider's catch
            // cannot contain that executor failure. Keep the keyboard available.
            initializationFailed = true;
            initialized = false;
            Log.w("HandwritingRuntime", "Optional handwriting database unavailable");
        };
        return new Configuration.Builder().setInitializationExceptionHandler(handler).build();
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
