package com.example.kokoro82m.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ExportHelper {
    fun exportHistory(context: Context, historyList: List<HistoryItem>) {
        val arr = JSONArray()
        for (item in historyList) {
            val obj = JSONObject()
            obj.put("text", item.text)
            obj.put("style", item.style)
            obj.put("speed", item.speed.toDouble())
            obj.put("time", item.time)
            arr.put(obj)
        }
        val fileName = "kokoro_history_${System.currentTimeMillis()}.json"
        val file = File(context.cacheDir, fileName)
        try {
            file.writeText(arr.toString(2))
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export History"))
        } catch (_: Exception) {}
    }
}