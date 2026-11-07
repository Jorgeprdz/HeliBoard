// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai.handwriting;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.IOException;

/** Credential storage for optional ML Kit/WorkManager only. The keyboard's global DP context is untouched. */
public final class HandwritingStorageContext extends ContextWrapper {
    private final File root;

    public HandwritingStorageContext(Context context) {
        super(context);
        root = credentialRoot(getBaseContext().getApplicationInfo().dataDir);
    }

    static File credentialRoot(String dataDir) {
        if (dataDir == null || dataDir.isEmpty()) throw new IllegalArgumentException("Missing application data directory");
        return new File(dataDir.replace("/user_de/", "/user/"));
    }

    /** Both libraries normalize their input context this way; retain this scoped CE identity. */
    @Override public Context getApplicationContext() { return this; }
    @Override public boolean isDeviceProtectedStorage() { return false; }
    @Override public ApplicationInfo getApplicationInfo() {
        ApplicationInfo info = new ApplicationInfo(super.getApplicationInfo());
        info.dataDir = root.getPath();
        return info;
    }
    @Override public File getDataDir() { return root; }
    private File directory(String name) {
        File path = new File(root, name);
        if (!path.isDirectory() && !path.mkdirs() && !path.isDirectory())
            throw new IllegalStateException("Credential storage unavailable");
        return path;
    }
    @Override public File getFilesDir() { return directory("files"); }
    @Override public File getNoBackupFilesDir() { return directory("no_backup"); }
    @Override public File getCacheDir() { return directory("cache"); }
    @Override public File getCodeCacheDir() { return directory("code_cache"); }
    @Override public File getDir(String name, int mode) {
        if (name == null || name.indexOf('/') >= 0) throw new IllegalArgumentException("Invalid private directory");
        return directory("app_" + name);
    }
    @Override public File getDatabasePath(String name) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Invalid database name");
        // Room's no-backup helper passes an absolute path rather than a database basename.
        if (new File(name).isAbsolute()) {
            try {
                File path = new File(name).getCanonicalFile();
                if (!path.getPath().startsWith(root.getCanonicalPath() + File.separator))
                    throw new IllegalArgumentException("Database must remain in credential storage");
                return path;
            } catch (IOException failure) { throw new IllegalArgumentException("Invalid database path", failure); }
        }
        if (name.indexOf('/') >= 0) throw new IllegalArgumentException("Invalid database name");
        return new File(directory("databases"), name);
    }
    @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return openOrCreateDatabase(name, mode, factory, null);
    }
    @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                                         DatabaseErrorHandler errorHandler) {
        int flags = SQLiteDatabase.CREATE_IF_NECESSARY;
        if ((mode & Context.MODE_ENABLE_WRITE_AHEAD_LOGGING) != 0) flags |= SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING;
        if ((mode & Context.MODE_NO_LOCALIZED_COLLATORS) != 0) flags |= SQLiteDatabase.NO_LOCALIZED_COLLATORS;
        return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory, flags, errorHandler);
    }
    @Override public boolean deleteDatabase(String name) { return SQLiteDatabase.deleteDatabase(getDatabasePath(name)); }
    @Override public String[] databaseList() {
        String[] files = directory("databases").list();
        return files == null ? new String[0] : files;
    }
}
