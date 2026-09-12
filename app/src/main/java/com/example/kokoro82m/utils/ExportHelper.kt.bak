package com.example.kokoro82m.utils

import android.content.Context
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    fun exportHistory(context: Context, history: List<HistoryItem>): File? {
        if (history.isEmpty()) {
            Toast.makeText(context, "没有历史记录可导出", Toast.LENGTH_SHORT).show()
            return null
        }

        val timestamp = dateFormat.format(Date())
        val fileName = "kokoro_history_$timestamp.json"
        val file = File(context.getExternalFilesDir(null), fileName)

        val json = buildJson(history)
        file.writeText(json)

        Toast.makeText(context, "导出成功: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        return file
    }

    private fun buildJson(history: List<HistoryItem>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        history.forEachIndexed { index, item ->
            sb.append("  {\n")
            sb.append("    \"id\": \"${item.id}\",\n")
            sb.append("    \"text\": \"${escape(item.text)}\",\n")
            sb.append("    \"style\": \"${escape(item.style)}\",\n")
            sb.append("    \"speed\": ${item.speed},\n")
            sb.append("    \"time\": \"${escape(item.time)}\",\n")
            sb.append("    \"filePath\": \"${escape(item.filePath)}\"\n")
            sb.append("  }")
            if (index < history.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}