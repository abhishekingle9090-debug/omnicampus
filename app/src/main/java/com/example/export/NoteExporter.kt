package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.entity.NoteEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteExporter {

    /**
     * Exports structured note to standard Markdown (.md)
     */
    fun exportToMarkdown(
        context: Context,
        note: NoteEntity,
        subjectName: String,
        lectureTitle: String
    ): File {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date(note.createdAt))

        val md = buildString {
            appendLine("# ${note.title}")
            appendLine("**Subject:** $subjectName  ")
            appendLine("**Lecture:** $lectureTitle  ")
            appendLine("**Date:** $dateString  ")
            appendLine("**Style:** ${note.style} | **Detail:** ${note.detailLevel}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Overview")
            appendLine(note.overview)
            appendLine()

            val keyConcepts = parseJsonList(note.keyConceptsJson)
            if (keyConcepts.isNotEmpty()) {
                appendLine("## Key Concepts")
                keyConcepts.forEach { appendLine("- $it") }
                appendLine()
            }

            val definitions = parseDefinitions(note.definitionsJson)
            if (definitions.isNotEmpty()) {
                appendLine("## Definitions")
                definitions.forEach { (term, def) ->
                    appendLine("- **$term**: $def")
                }
                appendLine()
            }

            val examples = parseJsonList(note.examplesJson)
            if (examples.isNotEmpty()) {
                appendLine("## Examples")
                examples.forEach { appendLine("- $it") }
                appendLine()
            }

            val formulas = parseFormulas(note.formulasJson)
            if (formulas.isNotEmpty()) {
                appendLine("## Formulas & Rules")
                formulas.forEach { (name, formula, desc) ->
                    appendLine("### $name")
                    appendLine("```")
                    appendLine(formula)
                    appendLine("```")
                    if (desc.isNotBlank()) appendLine("*$desc*")
                    appendLine()
                }
            }

            val importantPoints = parseJsonList(note.importantPointsJson)
            if (importantPoints.isNotEmpty()) {
                appendLine("## Critical Takeaways")
                importantPoints.forEach { appendLine("- $it") }
                appendLine()
            }

            appendLine("## Summary")
            appendLine(note.summary)
            appendLine()

            val revisionQuestions = parseJsonList(note.revisionQuestionsJson)
            if (revisionQuestions.isNotEmpty()) {
                appendLine("## Revision Questions")
                revisionQuestions.forEachIndexed { i, q ->
                    appendLine("${i + 1}. $q")
                }
                appendLine()
            }

            if (note.customNotes.isNotBlank()) {
                appendLine("## Student Notes & Annotations")
                appendLine(note.customNotes)
            }
        }

        val exportDir = File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }
        val safeTitle = note.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_").take(30)
        val file = File(exportDir, "Note_${safeTitle}_${System.currentTimeMillis()}.md")
        file.writeText(md)
        return file
    }

    /**
     * Exports formatted note to a clean multi-page PDF document
     */
    fun exportToPdf(
        context: Context,
        note: NoteEntity,
        subjectName: String,
        lectureTitle: String
    ): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595 // A4 standard pt width
        val pageHeight = 842 // A4 standard pt height
        val margin = 48f
        val contentWidth = pageWidth - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.rgb(11, 19, 43)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subheadPaint = Paint().apply {
            color = Color.rgb(108, 92, 231)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDoc.startPage(pageInfo)
        var canvas = page.canvas
        var yCursor = margin + 10f

        fun checkNewPage(neededHeight: Float) {
            if (yCursor + neededHeight > pageHeight - margin - 20f) {
                // Draw footer
                canvas.drawText("Page $pageNumber", pageWidth / 2f - 15f, pageHeight - margin / 2f, footerPaint)
                canvas.drawText("OmniCampus AI Study Platform", margin, pageHeight - margin / 2f, footerPaint)

                pdfDoc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDoc.startPage(pageInfo)
                canvas = page.canvas
                yCursor = margin + 10f
            }
        }

        fun drawWrappedText(text: String, paint: Paint, leading: Float = 14f) {
            val words = text.split(" ")
            var line = StringBuilder()
            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(testLine) > contentWidth) {
                    checkNewPage(leading)
                    canvas.drawText(line.toString(), margin, yCursor, paint)
                    yCursor += leading
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(testLine)
                }
            }
            if (line.isNotEmpty()) {
                checkNewPage(leading)
                canvas.drawText(line.toString(), margin, yCursor, paint)
                yCursor += leading
            }
        }

        // Title and Header
        canvas.drawText("OMNICAMPUS AI — LECTURE NOTES", margin, yCursor, metaPaint)
        yCursor += 16f
        drawWrappedText(note.title, titlePaint, 22f)
        yCursor += 4f

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateString = dateFormat.format(Date(note.createdAt))
        canvas.drawText("Subject: $subjectName | Lecture: $lectureTitle | Date: $dateString", margin, yCursor, metaPaint)
        yCursor += 16f

        // Separator line
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1.5f
        }
        canvas.drawLine(margin, yCursor, pageWidth - margin, yCursor, linePaint)
        yCursor += 18f

        // Overview
        checkNewPage(30f)
        canvas.drawText("Overview", margin, yCursor, subheadPaint)
        yCursor += 16f
        drawWrappedText(note.overview, bodyPaint)
        yCursor += 14f

        // Key Concepts
        val keyConcepts = parseJsonList(note.keyConceptsJson)
        if (keyConcepts.isNotEmpty()) {
            checkNewPage(30f)
            canvas.drawText("Key Concepts", margin, yCursor, subheadPaint)
            yCursor += 16f
            keyConcepts.forEach { concept ->
                drawWrappedText("• $concept", bodyPaint)
                yCursor += 4f
            }
            yCursor += 10f
        }

        // Definitions
        val definitions = parseDefinitions(note.definitionsJson)
        if (definitions.isNotEmpty()) {
            checkNewPage(30f)
            canvas.drawText("Definitions", margin, yCursor, subheadPaint)
            yCursor += 16f
            definitions.forEach { (term, def) ->
                drawWrappedText("• $term: $def", bodyPaint)
                yCursor += 4f
            }
            yCursor += 10f
        }

        // Summary
        checkNewPage(30f)
        canvas.drawText("Summary", margin, yCursor, subheadPaint)
        yCursor += 16f
        drawWrappedText(note.summary, bodyPaint)
        yCursor += 14f

        // Final footer
        canvas.drawText("Page $pageNumber", pageWidth / 2f - 15f, pageHeight - margin / 2f, footerPaint)
        canvas.drawText("OmniCampus AI Study Platform", margin, pageHeight - margin / 2f, footerPaint)
        pdfDoc.finishPage(page)

        val exportDir = File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }
        val safeTitle = note.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_").take(30)
        val file = File(exportDir, "Note_${safeTitle}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()
        return file
    }

    /**
     * Shares an exported file via Android system share sheet using FileProvider
     */
    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Study Notes")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun parseJsonList(jsonString: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) list.add(array.getString(i))
        } catch (_: Exception) {}
        return list
    }

    private fun parseDefinitions(jsonString: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(obj.optString("term", "") to obj.optString("definition", ""))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseFormulas(jsonString: String): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Triple(
                        obj.optString("name", ""),
                        obj.optString("formula", ""),
                        obj.optString("description", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
