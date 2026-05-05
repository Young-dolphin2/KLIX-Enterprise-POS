package com.example.barandgrillownerpanel.data.export

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PdfReportService {

    fun generateReport(
        filePath: String,
        type: PdfReportType,
        period: ExportPeriod,
        businessName: String,
        data: Map<String, Any>
    ) {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontNormal = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        PDPageContentStream(document, page).use { contentStream ->
            var yPosition = 750f

            // Header
            contentStream.beginText()
            contentStream.setFont(fontBold, 20f)
            contentStream.newLineAtOffset(50f, yPosition)
            contentStream.showText("KLIX ENTERPRISE POS")
            contentStream.endText()
            yPosition -= 30f

            contentStream.beginText()
            contentStream.setFont(fontBold, 16f)
            contentStream.newLineAtOffset(50f, yPosition)
            contentStream.showText(type.label)
            contentStream.endText()
            yPosition -= 20f

            contentStream.beginText()
            contentStream.setFont(fontNormal, 12f)
            contentStream.newLineAtOffset(50f, yPosition)
            contentStream.showText("Business: $businessName")
            contentStream.endText()
            yPosition -= 15f

            contentStream.beginText()
            contentStream.setFont(fontNormal, 12f)
            contentStream.newLineAtOffset(50f, yPosition)
            contentStream.showText("Period: ${period.label}")
            contentStream.endText()
            yPosition -= 15f

            contentStream.beginText()
            contentStream.setFont(fontNormal, 10f)
            contentStream.newLineAtOffset(50f, yPosition)
            contentStream.showText("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
            contentStream.endText()
            yPosition -= 40f

            // Simple Content rendering
            contentStream.beginText()
            contentStream.setFont(fontBold, 14f)
            contentStream.newLineAtOffset(50f, yPosition)
            contentStream.showText("Financial Summary")
            contentStream.endText()
            yPosition -= 25f

            val metrics = data["metrics"] as? Map<String, Double> ?: emptyMap()
            metrics.forEach { (label, value) ->
                contentStream.beginText()
                contentStream.setFont(fontNormal, 12f)
                contentStream.newLineAtOffset(50f, yPosition)
                contentStream.showText("$label: ${String.format("%.2f", value)} MK")
                contentStream.endText()
                yPosition -= 20f
                
                if (yPosition < 50) return@forEach
            }
        }

        document.save(FileOutputStream(filePath))
        document.close()
    }
}
