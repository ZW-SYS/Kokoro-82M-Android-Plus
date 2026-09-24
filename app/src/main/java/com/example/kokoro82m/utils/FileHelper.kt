package com.example.kokoro82m.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

object FileHelper {

    private val textMimes = setOf(
        "text/plain", "text/markdown", "text/csv", "text/html", "text/xml",
        "application/json", "application/xml", "application/javascript"
    )

    fun isTextFile(mimeType: String, fileName: String): Boolean {
        if (mimeType in textMimes) return true
        val lower = fileName.lowercase()
        return lower.endsWith(".txt") || lower.endsWith(".md") ||
                lower.endsWith(".json") || lower.endsWith(".csv") ||
                lower.endsWith(".xml") || lower.endsWith(".log") ||
                lower.endsWith(".kt") || lower.endsWith(".java") ||
                lower.endsWith(".py") || lower.endsWith(".js") ||
                lower.endsWith(".html") || lower.endsWith(".css")
    }

    fun isPdf(mimeType: String, fileName: String): Boolean {
        return mimeType == "application/pdf" || fileName.lowercase().endsWith(".pdf")
    }

    fun isImage(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    fun readTextFile(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val text = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            text
        } catch (_: Exception) {
            null
        }
    }

    fun imageToBase64(context: Context, uri: Uri): Pair<String, String>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return null
            val result = compressBitmap(bitmap, 1024, 80)
            Pair(result, "image/jpeg")
        } catch (_: Exception) {
            null
        }
    }

    fun pdfToImageBase64(context: Context, uri: Uri, pageIndex: Int = 0): Pair<String, String>? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            if (pageIndex >= renderer.pageCount) return null
            val page = renderer.openPage(pageIndex)
            val scale = 2
            val bmp = Bitmap.createBitmap(
                page.width * scale,
                page.height * scale,
                Bitmap.Config.ARGB_8888
            )
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            val result = compressBitmap(bmp, 1600, 80)
            Pair(result, "image/jpeg")
        } catch (_: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun pdfPageCount(context: Context, uri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (_: Exception) {
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun compressBitmap(bitmap: Bitmap, maxSize: Int, quality: Int): String {
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val finalBitmap = if (ratio < 1) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap
        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}