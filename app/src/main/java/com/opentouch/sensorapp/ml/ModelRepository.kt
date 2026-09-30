package com.opentouch.sensorapp.ml

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class StoredModel(
    val modelFile: File,
    val configFile: File
) {
    val displayName: String
        get() = modelFile.nameWithoutExtension
}



/** Owns model files kept outside the APK so they can be imported or downloaded independently. */
object ModelRepository {
    private const val DIRECTORY_NAME = "models"
    private const val PACKAGE_EXTENSION = "zip"
    private const val PACKAGE_MODEL_ENTRY = "model.onnx"
    private const val PACKAGE_CONFIG_ENTRY = "model.json"
    private const val MAX_PACKAGE_ENTRY_BYTES = 1024L * 1024L * 1024L

    fun modelsDirectory(context: Context): File = File(
        context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir,
        DIRECTORY_NAME
    ).also { directory ->
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create model directory: ${directory.absolutePath}")
        }
    }



    fun listModels(context: Context): List<StoredModel> {
        val directory = modelsDirectory(context)
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.lowercase(Locale.US) == "onnx" }
            .mapNotNull { modelFile ->
                val configFile = File(directory, "${modelFile.nameWithoutExtension}.json")
                configFile.takeIf { it.isFile }?.let { StoredModel(modelFile, it) }
            }
            .sortedBy { it.displayName.lowercase(Locale.US) }
    }

    /** Copies selected model/config files into the app-owned model directory. */
    fun importFile(context: Context, uri: Uri): File {
        val directory = modelsDirectory(context)
        val displayName = queryDisplayName(context, uri)
        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(120)
        val extension = safeName.substringAfterLast('.', "").lowercase(Locale.US)
        if (extension == PACKAGE_EXTENSION || (extension.isEmpty() && looksLikeZip(context, uri))) {
            return importPackage(context, uri, safeName, directory)
        }
        require(extension == "onnx" || extension == "json") {
            "Only .zip, .onnx, or .json files can be imported"
        }
        require(safeName.isNotBlank() && safeName != ".") { "The selected file has no usable name" }

        val target = File(directory, safeName)
        val temporary = File(directory, ".${safeName}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Could not open the selected file")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } finally {
            temporary.delete()
        }
        return target
    }

    /** Extracts a zip that was downloaded into a local file. */
    fun importZipFile(context: Context, zipFile: File, packageName: String = zipFile.name): File {
        require(zipFile.isFile && zipFile.length() > 0L) { "The downloaded zip is empty" }
        return zipFile.inputStream().use { input ->
            importPackageFromStream(input, packageName, modelsDirectory(context))
        }
    }

    /** Deletes the selected model and its matching configuration file. */
    fun deleteModel(context: Context, model: StoredModel) {
        val directory = modelsDirectory(context).canonicalFile
        val modelFile = model.modelFile.canonicalFile
        val configFile = model.configFile.canonicalFile
        require(modelFile.parentFile == directory && configFile.parentFile == directory) {
            "The selected model is outside the app model directory"
        }
        if (modelFile.exists() && !modelFile.delete()) {
            throw IOException("Could not delete model ${modelFile.name}")
        }
        if (configFile.exists() && !configFile.delete()) {
            throw IOException("Could not delete configuration ${configFile.name}")
        }
    }

    /** Extracts a downloaded zip containing one ONNX model and one JSON config. */
    private fun importPackage(
        context: Context,
        uri: Uri,
        packageName: String,
        directory: File
    ): File {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open the model package")
        return input.use { importPackageFromStream(it, packageName, directory) }
    }

    private fun importPackageFromStream(
        input: InputStream,
        packageName: String,
        directory: File
    ): File {
        val baseName = packageName
            .substringBeforeLast('.', missingDelimiterValue = packageName)
            .ifBlank { "imported_model" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(120)
        require(baseName.isNotBlank() && baseName != ".") {
            "The package has no usable model name"
        }

        val temporaryDirectory = File(directory, ".${baseName}_import_${System.nanoTime()}")
        if (!temporaryDirectory.mkdirs()) {
            throw IOException("Could not create a temporary model directory")
        }
        val modelTemporary = File(temporaryDirectory, PACKAGE_MODEL_ENTRY)
        val configTemporary = File(temporaryDirectory, PACKAGE_CONFIG_ENTRY)
        var preferredModel = false
        var preferredConfig = false
        var modelFound = false
        var configFound = false

        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val fileName = entry.name.replace('\\', '/')
                        .substringAfterLast('/')
                        .lowercase(Locale.US)
                    val isPreferredModel = fileName == PACKAGE_MODEL_ENTRY
                    val isPreferredConfig = fileName == PACKAGE_CONFIG_ENTRY
                    val isOnnx = fileName.endsWith(".onnx")
                    val isJson = fileName.endsWith(".json")
                    when {
                        isOnnx && (isPreferredModel || !preferredModel) -> {
                            require(isPreferredModel || !modelFound) {
                                "The zip contains more than one ONNX file"
                            }
                            copyLimited(zip, modelTemporary, MAX_PACKAGE_ENTRY_BYTES)
                            modelFound = true
                            preferredModel = isPreferredModel
                        }
                        isJson && (isPreferredConfig || !preferredConfig) -> {
                            require(isPreferredConfig || !configFound) {
                                "The zip contains more than one JSON file"
                            }
                            copyLimited(zip, configTemporary, MAX_PACKAGE_ENTRY_BYTES)
                            configFound = true
                            preferredConfig = isPreferredConfig
                        }
                        else -> zip.closeEntry()
                    }
                }
            }

            require(modelFound) {
                "The zip must contain an .onnx model file"
            }
            require(configFound) {
                "The zip must contain a .json configuration file with labels and input size"
            }

            val modelFile = File(directory, "$baseName.onnx")
            val configFile = File(directory, "$baseName.json")
            moveIntoPlace(modelTemporary, modelFile)
            moveIntoPlace(configTemporary, configFile)
            return modelFile
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }

    private fun looksLikeZip(context: Context, uri: Uri): Boolean {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(2)
            val read = input.read(header)
            read == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        } ?: false
    }

    private fun copyLimited(input: java.io.InputStream, output: File, limit: Long) {
        var total = 0L
        output.outputStream().use { destination ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "The model package entry is too large" }
                destination.write(buffer, 0, count)
            }
        }
    }

    private fun moveIntoPlace(source: File, target: File) {
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported_model.onnx"
    }

}
