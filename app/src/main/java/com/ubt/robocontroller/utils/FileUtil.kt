package com.ubt.robocontroller.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import android.widget.TextView
import timber.log.Timber
import xh.zero.core.utils.SystemUtil
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class FileUtil {
    companion object {

        /**
         * android assets path: file:///android_asset/file.txt
         * file: 直接填文件名就行了
         */
        fun readAssetsJson(file: String, context: Context): String {
//            var inS = inputStream
//            val inputStream = FileInputStream(file)
            val result = StringBuffer()
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(context.assets.open(file)))
                var line = reader.readLine()
                while (line != null) {
                    result.append(line).append("\n")
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    if (reader != null) {
                        reader.close()
                    }
//                    if (inputStream != null) {
//                        inputStream.close()
//                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
            return result.toString()
        }

        fun readFile(file: File): String {
            val result = StringBuffer()
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(FileReader(file))
                var line = reader.readLine()
                while (line != null) {
                    result.append(line).append("\n")
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    reader?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
            return result.toString()
        }

        fun getCacheFolderSize(context: Context) : Long {
            val cacheFile = File(context.cacheDir, ".")
            val outCacheFile = File(context.externalCacheDir, ".")

            val innerCache = getFileSize(cacheFile)
            val outerCache = getFileSize(outCacheFile)

            return innerCache + outerCache
        }

        fun getFileSize(file: File) : Long {
            var size: Long = 0
            if (file.isDirectory) {
                for (f in file.listFiles()) {
                    size += getFileSize(f)
                }
            } else {
                size += file.length()
            }

            return size
        }

        fun clearCacheFolder(context: Context) {
            val cacheFile = File(context.cacheDir, ".")
            val outCacheFile = File(context.externalCacheDir, ".")
            deleteFile(cacheFile)
            deleteFile(outCacheFile)
        }

        fun deleteFile(file: File): Boolean {
            return if (file.exists()) {
                if (file.isDirectory) {
                    for (f in file.listFiles()) {
                        deleteFile(f)
                    }
                }
                file.delete()
            } else {
                false
            }
        }

        var isSave = true

        fun saveImageToPath(img: Bitmap) {
            if (isSave) {
                isSave = false
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)
                val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Frame_${sdf.format(Date())}.png")
                try {
                    val out = FileOutputStream(f.absolutePath)
                    img.compress(Bitmap.CompressFormat.PNG, 100, out)
                    Timber.d("save image size: ${img.width} x ${img.height}")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        fun saveInputStreamToFile(input: InputStream, file: File) {
            try {
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(4 * 1024) // or other buffer size
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } finally {
                input.close()
            }
        }
    }
}