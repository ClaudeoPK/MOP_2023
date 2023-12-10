package com.example.animationgenerator

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.random.Random

class FileUtils {
    val imageFileNameDateFormat = SimpleDateFormat("yyyyMMdd_hhmmss")

    fun createTempImageFile(context: Context): File? {
        return try {
            val imageFileName = "IMG_" + imageFileNameDateFormat.format(Date()) + "${Random.nextInt(0, 1000)}"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: IOException) {
            null
        }
    }

    fun createTempVideoFile(context: Context): File? {
        return kotlin.runCatching {
            val imageFileName = "VIDEO" + imageFileNameDateFormat.format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile(imageFileName, ".mp4", storageDir)
        }.getOrNull()
    }
    fun createTempFileFromUri(context: Context, uri: Uri, isVideo: Boolean): File? {
        return try {
            val prefix = if(isVideo) "VIDEO_" else "IMG_"
            val suffix = if(isVideo) ".mp4" else ".jpg"
            val inputStream = context.contentResolver.openInputStream(uri)
            val imageFileName = prefix + imageFileNameDateFormat.format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val tempFile = File.createTempFile(imageFileName, suffix, storageDir)

            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(4 * 1024) // 4KB buffer size
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            tempFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun getRealPathFromUri(uri: Uri, context: Context): String {
        val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(uri, filePathColumn, null, null, null)
//        var fileName = ""
//        var fileFormat = ""
//        var newFile: File? = null
        var filePath = ""

        if(cursor?.moveToFirst() == true) {
            cursor.getColumnIndex(filePathColumn.get(0)).let {
                cursor.getString(it)?.let { filePath = it }
            }
        }

        return filePath
    }

    fun checkIsFilePath(filePath: String?): Boolean {
        return (filePath?.split("/")?.get(1) == "storage")
    }

    fun filePathToUri(isVideo: Boolean, filePath: String?, contentResolver: ContentResolver): Uri? {

        try {
            var photoId: Long? = null
            val photoUri = if(isVideo) {
                MediaStore.Video.Media.getContentUri("external")
            } else {
                MediaStore.Images.Media.getContentUri("external")
            }
            val projection = arrayOf(MediaStore.Images.ImageColumns._ID)

            return if (!filePath.isNullOrEmpty()) {
                val cursor = contentResolver.query(photoUri, projection, MediaStore.Images.ImageColumns.DATA + " LIKE ?", arrayOf(filePath), null)
                cursor?.moveToFirst()
                cursor?.getColumnIndex(projection[0])?.let {
                    photoId = cursor.getLong(it)
                }
                cursor?.close()
                Uri.parse("$photoUri/$photoId")
            } else {
                null
            }

        } catch (e: Exception) {
            return Uri.fromFile(File(filePath))
        }
    }


    fun getRealPathFromUriTest(context: Context, uri: Uri): String? {
        var filePath: String? = null
        val scheme = uri.scheme
        if (scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.let {
                if (it.moveToFirst()) {
                    val displayNameColumnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameColumnIndex != -1) {
                        filePath = it.getString(displayNameColumnIndex)
                    }
                    cursor.close()
                }
            }
        } else if (scheme == "file") {
            filePath = uri.path
        }
        return filePath
    }

    fun getExternalStorageDirectory(): String? {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    fun getAppSpecificDirectory(context: Context): String? {
        return context.getExternalFilesDir(null)?.absolutePath
    }

    fun isFileInInternalStorage(context: Context, filePath: String): Boolean {
        val internalStoragePath = context.filesDir.absolutePath // 내부 저장소 경로
        return filePath.startsWith(internalStoragePath)
    }

    fun getBitmapUri(view: View, fileNameToSave: String, context: Context): Pair<File, Uri>? {
        val bitmap = getBitmap(view)
        val file = bitmapToFile(bitmap, fileNameToSave, context)
        val uri = file?.let {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
        }
        return if (file != null && uri != null) file to uri else null
    }

    private fun bitmapToFile(bitmap: Bitmap?, fileNameToSave: String, context: Context): File? {
        var file: File? = null
        return try {
            file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + File.separator + fileNameToSave + ".png")
            file.createNewFile()

            val bitmapByteArray = ByteArrayOutputStream().let { bos ->
                bitmap?.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bos.toByteArray()
            }

            FileOutputStream(file).let { fos ->
                fos.write(bitmapByteArray)
                fos.flush()
                fos.close()
            }
            file
        } catch (e: Exception) {
            file
        }
    }
    private fun getBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.draw(canvas)
        return bitmap
    }
}