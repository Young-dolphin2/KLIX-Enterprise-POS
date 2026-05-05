package com.example.barandgrillownerpanel.data.export

import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.ui.dashboard.InventoryItem
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ExcelExportService {

    fun generateExport(
        filePath: String,
        options: ExportOptions,
        branches: List<BranchDto>,
        inventory: List<InventoryItem>,
        sales: List<SaleRecord>,
        credits: List<CreditDto>,
        expenses: List<ExpenseDto>,
        businessName: String
    ) {
        val workbook = XSSFWorkbook()
        val creationHelper = workbook.creationHelper

        // Styles
        val headerStyle = createHeaderStyle(workbook)
        val currencyStyle = createCurrencyStyle(workbook, creationHelper)
        val dateStyle = createDateStyle(workbook, creationHelper)

        // Filter data based on period and branch
        val startTime = options.period.startMillis()
        val filteredSales = sales.filter { it.timestamp >= startTime && (options.branchId == null || it.branchId == options.branchId) }
        val filteredCredits = credits.filter { 
            val ts = it.created_at?.let { t -> try { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch(e: Exception) { 0L } } ?: 0L
            ts >= startTime && (options.branchId == null || it.branchId == options.branchId)
        }
        val filteredExpenses = expenses.filter {
            val ts = it.expenseDate?.let { t -> try { java.time.LocalDate.parse(t).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() } catch(e: Exception) { 0L } } ?: 0L
            ts >= startTime && (options.branchId == null || it.branchId == options.branchId)
        }

        if (options.includeSales) createSalesSheet(workbook, filteredSales, headerStyle, currencyStyle, dateStyle)
        if (options.includeInventory) createInventorySheet(workbook, inventory.filter { options.branchId == null || it.branchId == options.branchId }, headerStyle, currencyStyle)
        if (options.includeCredits) createCreditsSheet(workbook, filteredCredits, headerStyle, currencyStyle, dateStyle)
        if (options.includeExpenses) createExpensesSheet(workbook, filteredExpenses, headerStyle, currencyStyle, dateStyle)
        
        createSummarySheet(workbook, businessName, options.period, filteredSales, filteredExpenses, filteredCredits, headerStyle, currencyStyle)

        FileOutputStream(filePath).use { workbook.write(it) }
        workbook.close()
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.color = IndexedColors.WHITE.index
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.DARK_BLUE.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        return style
    }

    private fun createCurrencyStyle(workbook: Workbook, helper: CreationHelper): CellStyle {
        val style = workbook.createCellStyle()
        style.dataFormat = helper.createDataFormat().getFormat("#,##0.00")
        return style
    }

    private fun createDateStyle(workbook: Workbook, helper: CreationHelper): CellStyle {
        val style = workbook.createCellStyle()
        style.dataFormat = helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm")
        return style
    }

    private fun createSalesSheet(workbook: Workbook, sales: List<SaleRecord>, headerStyle: CellStyle, currencyStyle: CellStyle, dateStyle: CellStyle) {
        val sheet = workbook.createSheet("Sales Summary")
        val headers = listOf("Date", "Order ID", "Branch", "Payment", "Sold By", "Total Amount")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> 
            val cell = headerRow.createCell(i)
            cell.setCellValue(h)
            cell.cellStyle = headerStyle
        }

        sales.forEachIndexed { i, sale ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).apply { setCellValue(java.util.Date(sale.timestamp)); cellStyle = dateStyle }
            row.createCell(1).setCellValue(sale.id)
            row.createCell(2).setCellValue(sale.branchId ?: "Main")
            row.createCell(3).setCellValue(sale.paymentMethod)
            row.createCell(4).setCellValue(sale.soldBy)
            row.createCell(5).apply { setCellValue(sale.totalAmount); cellStyle = currencyStyle }
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
    }

    private fun createInventorySheet(workbook: Workbook, items: List<InventoryItem>, headerStyle: CellStyle, currencyStyle: CellStyle) {
        val sheet = workbook.createSheet("Inventory")
        val headers = listOf("Item Name", "Category", "Subcategory", "Stock", "Cost", "Price", "Value")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> 
            val cell = headerRow.createCell(i)
            cell.setCellValue(h)
            cell.cellStyle = headerStyle
        }

        items.forEachIndexed { i, item ->
            val row = sheet.createRow(i + 1)
            row.createCell(0).setCellValue(item.name)
            row.createCell(1).setCellValue(item.category)
            row.createCell(2).setCellValue(item.subcategory)
            row.createCell(3).setCellValue(item.currentStock)
            row.createCell(4).apply { setCellValue(item.unitCost); cellStyle = currencyStyle }
            row.createCell(5).apply { setCellValue(item.retailPrice); cellStyle = currencyStyle }
            row.createCell(6).apply { setCellValue(item.currentStock * item.unitCost); cellStyle = currencyStyle }
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
    }

    private fun createCreditsSheet(workbook: Workbook, credits: List<CreditDto>, headerStyle: CellStyle, currencyStyle: CellStyle, dateStyle: CellStyle) {
        val sheet = workbook.createSheet("Credits & Debts")
        val headers = listOf("Date", "Contact", "Type", "Description", "Amount", "Settled")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> 
            val cell = headerRow.createCell(i)
            cell.setCellValue(h)
            cell.cellStyle = headerStyle
        }

        credits.forEachIndexed { i, c ->
            val row = sheet.createRow(i + 1)
            val ts = c.created_at?.let { try { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() } catch(e: Exception) { 0L } } ?: 0L
            row.createCell(0).apply { setCellValue(java.util.Date(ts)); cellStyle = dateStyle }
            row.createCell(1).setCellValue(c.contactName)
            row.createCell(2).setCellValue(c.creditType)
            row.createCell(3).setCellValue(c.description)
            row.createCell(4).apply { setCellValue(c.amount); cellStyle = currencyStyle }
            row.createCell(5).setCellValue(if (c.isSettled) "Yes" else "No")
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
    }

    private fun createExpensesSheet(workbook: Workbook, expenses: List<ExpenseDto>, headerStyle: CellStyle, currencyStyle: CellStyle, dateStyle: CellStyle) {
        val sheet = workbook.createSheet("Expenses")
        val headers = listOf("Date", "Category", "Description", "Amount")
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h -> 
            val cell = headerRow.createCell(i)
            cell.setCellValue(h)
            cell.cellStyle = headerStyle
        }

        expenses.forEachIndexed { i, e ->
            val row = sheet.createRow(i + 1)
            val date = e.expenseDate?.let { try { java.time.LocalDate.parse(it) } catch(ex: Exception) { null } }
            row.createCell(0).setCellValue(date?.toString() ?: "")
            row.createCell(1).setCellValue(e.category)
            row.createCell(2).setCellValue(e.description)
            row.createCell(3).apply { setCellValue(e.amount); cellStyle = currencyStyle }
        }
        headers.indices.forEach { sheet.autoSizeColumn(it) }
    }

    private fun createSummarySheet(
        workbook: Workbook, 
        businessName: String, 
        period: ExportPeriod,
        sales: List<SaleRecord>,
        expenses: List<ExpenseDto>,
        credits: List<CreditDto>,
        headerStyle: CellStyle,
        currencyStyle: CellStyle
    ) {
        val sheet = workbook.createSheet("Summary Dashboard")
        var rowIdx = 0
        
        fun createInfoRow(label: String, value: String) {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
        }

        fun createMetricRow(label: String, value: Double) {
            val row = sheet.createRow(rowIdx++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).apply { setCellValue(value); cellStyle = currencyStyle }
        }

        createInfoRow("Business Name", businessName)
        createInfoRow("Report Period", period.label)
        createInfoRow("Generated At", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        rowIdx++

        val totalRevenue = sales.sumOf { it.totalAmount }
        val totalExpenses = expenses.sumOf { it.amount }
        val givenCredits = credits.filter { it.creditType == "GIVEN" && !it.isSettled }.sumOf { it.amount }
        val receivedCredits = credits.filter { it.creditType == "RECEIVED" && !it.isSettled }.sumOf { it.amount }

        createMetricRow("Total Revenue", totalRevenue)
        createMetricRow("Total Expenses", totalExpenses)
        createMetricRow("Net Cash Position", totalRevenue - totalExpenses)
        rowIdx++
        createMetricRow("Outstanding Receivables (Credits Given)", givenCredits)
        createMetricRow("Outstanding Payables (Credits Received)", receivedCredits)

        sheet.autoSizeColumn(0)
        sheet.autoSizeColumn(1)
    }
}
