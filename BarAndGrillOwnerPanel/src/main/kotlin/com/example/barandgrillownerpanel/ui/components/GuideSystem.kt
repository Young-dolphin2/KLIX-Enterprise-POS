package com.example.barandgrillownerpanel.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*

data class GuideStep(
    val title: String,
    val description: String,
    val icon: ImageVector = Icons.Default.HelpCenter
)

@Composable
fun GuideOverlay(
    steps: List<GuideStep>,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var currentStepIdx by remember { mutableStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIdx) ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(450.dp)
                .clickable(enabled = false) {}, // Prevent dismiss when clicking card
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(currentStep.icon, null, tint = PrimaryOrange, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(currentStep.title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    currentStep.description,
                    fontSize = 16.sp,
                    color = TextSecondary,
                    lineHeight = 24.sp
                )
                
                Spacer(Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${currentStepIdx + 1} of ${steps.size}",
                        color = PrimaryOrange.copy(0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentStepIdx > 0) {
                            TextButton(onClick = { currentStepIdx-- }) {
                                Text("Back", color = TextSecondary)
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (currentStepIdx < steps.size - 1) {
                                    currentStepIdx++
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                        ) {
                            Text(
                                if (currentStepIdx < steps.size - 1) "Next" else "Got it!",
                                fontWeight = FontWeight.Bold,
                                color = DarkBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

// Predefined Guides
object Guides {
    val INVENTORY_GUIDE = listOf(
        GuideStep(
            "Inventory Basics",
            "Track your stock levels, costs, and retail prices across all branches.",
            Icons.Default.Inventory2
        ),
        GuideStep(
            "Adding Items",
            "Click '+ Add Item' to add new stock. You can set cost price and retail price separately to track margins.",
            Icons.Default.AddCircle
        ),
        GuideStep(
            "Shot & Quantity Tracking",
            "For alcohol (Spirits/Wine) or goods sold by weight, enable 'Sold by shot/glass'. \n\n" +
            "Example: A 750ml bottle can be sold as 30ml shots. KLIX will automatically deduct the correct fraction from your stock.",
            Icons.Default.LocalBar
        ),
        GuideStep(
            "Low Stock Alerts",
            "Items will automatically highlight in Orange when they hit your defined threshold. Set this per item to ensure you never run out.",
            Icons.Default.Warning
        ),
        GuideStep(
            "Smart Product Tips",
            "Standard measures: \n" +
            "• Spirits: 30ml, 35ml or 50ml shots. \n" +
            "• Wine: 125ml, 175ml or 250ml glasses. \n" +
            "• Bulk Goods: Use Kilograms (kg) and sell in Grams (g). \n\n" +
            "KLIX handles the math so your inventory stays accurate.",
            Icons.Default.Lightbulb
        )
    )

    val DASHBOARD_GUIDE = listOf(
        GuideStep(
            "Performance Overview",
            "View live sales across all branches. Use the date picker to compare performance over time.",
            Icons.Default.Dashboard
        ),
        GuideStep(
            "Predictive Trends",
            "The charts show expected revenue vs actual. Hover over bars to see detailed breakdowns.",
            Icons.Default.TrendingUp
        )
    )
    
    val SETTINGS_GUIDE = listOf(
        GuideStep(
            "Internationalization",
            "Change your country and currency code here. This updates symbols across the entire system.",
            Icons.Default.Public
        ),
        GuideStep(
            "Payment Options",
            "Add custom Mobile Money operators (Airtel, TNM, M-Pesa) or Bank Transfer options. These will appear in the checkout screen.",
            Icons.Default.Payments
        )
    )

    val MENU_CONTROL_GUIDE = listOf(
        GuideStep(
            "Menu Management",
            "Create and organize the items you sell. Every item can be categorized for faster discovery on the POS app.",
            Icons.Default.MenuBook
        ),
        GuideStep(
            "Inventory Linking",
            "If an item is linked to inventory, KLIX will automatically deduct stock when it is sold. Ensure the 'Category' and 'Subcategory' match the inventory item for perfect sync.",
            Icons.Default.Link
        ),
        GuideStep(
            "Item Status",
            "Toggle 'Active' to show or hide items on the mobile POS. Use this for seasonal items or out-of-stock specials.",
            Icons.Default.Visibility
        )
    )

    val SALES_STATS_GUIDE = listOf(
        GuideStep(
            "Sales Analysis",
            "Track your revenue trends over daily, weekly, or monthly periods.",
            Icons.Default.Assessment
        ),
        GuideStep(
            "Payment Breakdown",
            "See how much is coming in via Cash, Bank, or Mobile Money. This helps with bank reconciliation at the end of the shift.",
            Icons.Default.AccountBalanceWallet
        )
    )

    val CREDITS_GUIDE = listOf(
        GuideStep(
            "Credit Tracking",
            "Record sales where the customer 'pays later'. This tab keeps a ledger of who owes you what.",
            Icons.Default.HistoryEdu
        ),
        GuideStep(
            "Settling Debts",
            "When a customer pays back their credit, record it here to update your balance and total revenue.",
            Icons.Default.CheckCircleOutline
        )
    )

    val EXPENSES_GUIDE = listOf(
        GuideStep(
            "Expense Recording",
            "Log business costs like Rent, Salaries, Stock Purchase, or Utilities. This is vital for accurate profit calculation.",
            Icons.Default.ReceiptLong
        ),
        GuideStep(
            "Net Profit",
            "KLIX subtracts these expenses from your Total Sales to show your actual 'Take Home' profit in the Reports tab.",
            Icons.Default.Calculate
        )
    )

    val REPORTS_GUIDE = listOf(
        GuideStep(
            "Business Intelligence",
            "Generate detailed PDF/Excel reports for tax compliance, investor reviews, or personal bookkeeping.",
            Icons.Default.Description
        ),
        GuideStep(
            "Profit & Loss",
            "The P&L report gives you a birds-eye view of your business health by comparing Sales vs. Expenses vs. Stock Value.",
            Icons.Default.DonutLarge
        )
    )
}
