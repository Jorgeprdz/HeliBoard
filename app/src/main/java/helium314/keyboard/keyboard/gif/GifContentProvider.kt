// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class GifContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/gif"

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("GIF provider is read-only")
        val remoteUrl = uri.getQueryParameter(PARAM_URL)
            ?: throw FileNotFoundException("Missing remote GIF URL")
        val remote = Uri.parse(remoteUrl)
        val host = remote.host?.lowercase()
        if (remote.scheme != "https" || host == null ||
            (host != "giphy.com" && !host.endsWith(".giphy.com"))) {
            throw FileNotFoundException("Unsupported GIF host")
        }

        val pipe = ParcelFileDescriptor.createPipe()
        IO.execute {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(remote.toString()).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 20_000
                        instanceFollowRedirects = true
                        setRequestProperty("Accept", "image/gif,image/*;q=0.8,*/*;q=0.5")
                    }
                    val code = connection.responseCode
                    if (code !in 200..299) return@use
                    connection.inputStream.use { input -> input.copyTo(output) }
                } catch (_: Exception) {
                    // Reader sees EOF if the upstream request fails.
                } finally {
                    connection?.disconnect()
                }
            }
        }
        return pipe[0]
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val PARAM_URL = "url"
        private val IO = Executors.newCachedThreadPool()

        fun uriFor(packageName: String, id: String, remoteUrl: String): Uri =
            Uri.Builder()
                .scheme("content")
                .authority("$packageName.gifprovider")
                .appendPath("gif")
                .appendPath(id)
                .appendQueryParameter(PARAM_URL, remoteUrl)
                .build()
    }
}
