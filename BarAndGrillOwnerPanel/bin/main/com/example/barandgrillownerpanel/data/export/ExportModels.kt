package com.example.barandgrillownerpanel.data.export

import java.time.LocalDate
import java.time.LocalDateTime

enum class ExportPeriod(val label: String, val days: Long) {
    WEEK("Last 7 Days", 7),
    MONTH("Last Month", 30),
    TWO_MONTHS("Last 2 Months", 60),
    SIX_MONTHS("Last 6 Months", 180),
    ONE_YEAR("Last Year", 365);

    fun startDate(): LocalDate = LocalDate.now().minusDays(days)
    fun endDate(): LocalDate = LocalDate.now()
    fun startMillis(): Long = startDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
}

enum class PdfReportType(val label: String, val description: String, val icon: String) {
    PROFIT_AND_LOSS(
        "Profit & Loss Statement",
        "Revenue, cost of sales, gross profit, expenses, net profit",
        "P&L"
    ),
    CASH_FLOW(
        "Cash Flow Summary",
        "Cash inflows from sales, outflows from expenses, net position",
        "CF"
    ),
    SALES_PERFORMANCE(
        "Sales Performance Report",
        "Top items, branch comparison, daily averages, peak periods",
        "SP"
    ),
    INVENTORY_VALUATION(
        "Inventory Valuation Report",
        "Stock on hand, cost valuation, low-stock alerts",
        "IV"
    ),
    CREDITS_RECEIVABLES(
        "Credits & Receivables Report",
        "Outstanding credits, aged receivables, settlement rate",
        "CR"
    )
}

data class ExportOptions(
    val period: ExportPeriod = ExportPeriod.MONTH,
    val includeSales: Boolean = true,
    val includeInventory: Boolean = true,
    val includeCredits: Boolean = true,
    val includeExpenses: Boolean = true,
    val branchId: String? = null // null = all branches
)


