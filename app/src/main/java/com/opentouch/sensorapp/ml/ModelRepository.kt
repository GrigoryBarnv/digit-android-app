package com.opentouch.sensorapp.ml

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
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
    private const val PACKAGE_EXTENSION = "opentouchmodel"
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
        if (extension == PACKAGE_EXTENSION) {
            return importPackage(context, uri, safeName, directory)
        }
        require(extension == "onnx" || extension == "json") {
            "Only .onnx, .json, or .opentouchmodel files can be imported"
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

    /** Extracts a single downloaded package containing model.onnx and model.json. */
    private fun importPackage(
        context: Context,
        uri: Uri,
        packageName: String,
        directory: File
    ): File {
        val baseName = packageName
            .substringBeforeLast('.', missingDelimiterValue = packageName)
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
        var modelFound = false
        var configFound = false

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val entryName = entry.name.replace('\\', '/')
                        val output = when (entryName.lowercase(Locale.US)) {
                            PACKAGE_MODEL_ENTRY -> modelTemporary
                            PACKAGE_CONFIG_ENTRY -> configTemporary
                            else -> error("The model package contains an unexpected file")
                        }
                        require(!output.exists()) {
                            "The model package contains duplicate files"
                        }
                        copyLimited(zip, output, MAX_PACKAGE_ENTRY_BYTES)
                        if (output == modelTemporary) modelFound = true else configFound = true
                    }
                }
            } ?: error("Could not open the model package")

            require(modelFound) {
                "The model package must contain model.onnx"
            }
            require(configFound) {
                "The model package must contain model.json"
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
