package com.opentouch.sensorapp.ml

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CatalogModel(
    val id: String,
    val title: String,
    val url: String
)

/**
 * Direct zip links from the Models section on https://opentouch.org/mobile/.
 * Replace [MODEL_HOST] or a model's [CatalogModel.url] if the files move.
 */
object RemoteModels {
    private const val MODEL_HOST = "https://opentouch.org/models"

    val catalog = listOf(
        CatalogModel(
            id = "key_finger",
            title = "key_finger",
            url = "$MODEL_HOST/key_finger.zip"
        ),
        CatalogModel(
            id = "max_model",
            title = "max_model",
            url = "$MODEL_HOST/max_model.zip"
        )
    )
}

object ModelDownloader {
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val MAX_REDIRECTS = 5
    private const val MAX_BYTES = 1024L * 1024L * 1024L

    fun downloadAndImport(context: Context, model: CatalogModel): File {
        val zip = File(context.cacheDir, "remote_${model.id}.zip")
        try {
            download(model.url, zip)
            return ModelRepository.importZipFile(context, zip, "${model.id}.zip")
        } finally {
            zip.delete()
        }
    }

    private fun download(urlString: String, destination: File) {
        var current = urlString
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "OpenTouch-Android")
                setRequestProperty("Accept", "application/zip, application/octet-stream, */*")
            }
            try {
                val code = connection.responseCode
                when {
                    code in 300..399 -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw IOException("Download redirected without a location")
                        current = URL(URL(current), location).toString()
                    }
                    code in 200..299 -> {
                        connection.inputStream.use { input ->
                            destination.outputStream().use { output ->
                                var total = 0L
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= MAX_BYTES) { "The downloaded file is too large" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        return
                    }
                    else -> throw IOException("Could not download the model (HTTP $code)")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many redirects while downloading the model")
    }
}
