package com.example.barandgrillownerpanel.ui.dashboard
import com.example.barandgrillownerpanel.data.export.*

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.barandgrillownerpanel.models.*
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import com.example.barandgrillownerpanel.utils.Logger
import io.github.jan.supabase.auth.auth
import java.util.UUID
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.GlobalScope

enum class SettingsSection {
    BUSINESS_PROFILE, REGIONAL, TARGETS, DATA, HARDWARE, EMPLOYEES, SECURITY, BRANDING, SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    branches: List<BranchDto> = emptyList(),
    onBranchesChange: (List<BranchDto>) -> Unit = {},
    inventoryItems: List<InventoryItem> = emptyList(),
    saleHistory: List<SaleRecord> = emptyList(),
    credits: List<CreditDto> = emptyList(),
    expenses: List<ExpenseDto> = emptyList()
) {
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf(SettingsSection.BUSINESS_PROFILE) }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // SIDEBAR NAVIGATION
        Card(
            modifier = Modifier.width(280.dp).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Settings Hub",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                SettingsNavItem("Business Profile", Icons.Default.Business, selectedSection == SettingsSection.BUSINESS_PROFILE) {
                    selectedSection = SettingsSection.BUSINESS_PROFILE
                }
                SettingsNavItem("Regional & Payments", Icons.Default.Public, selectedSection == SettingsSection.REGIONAL) {
                    selectedSection = SettingsSection.REGIONAL
                }
                SettingsNavItem("Targets & Alerts", Icons.Default.NotificationsActive, selectedSection == SettingsSection.TARGETS) {
                    selectedSection = SettingsSection.TARGETS
                }
                SettingsNavItem("Data & Exports", Icons.Default.FileDownload, selectedSection == SettingsSection.DATA) {
                    selectedSection = SettingsSection.DATA
                }
                SettingsNavItem("Hardware (Printers)", Icons.Default.Print, selectedSection == SettingsSection.HARDWARE) {
                    selectedSection = SettingsSection.HARDWARE
                }
                SettingsNavItem("Employee Manager", Icons.Default.People, selectedSection == SettingsSection.EMPLOYEES) {
                    selectedSection = SettingsSection.EMPLOYEES
                }
                SettingsNavItem("Staff & Security", Icons.Default.Security, selectedSection == SettingsSection.SECURITY) {
                    selectedSection = SettingsSection.SECURITY
                }
                SettingsNavItem("Branding & UI", Icons.Default.Palette, selectedSection == SettingsSection.BRANDING) {
                    selectedSection = SettingsSection.BRANDING
                }
                SettingsNavItem("System & Backup", Icons.Default.SettingsSuggest, selectedSection == SettingsSection.SYSTEM) {
                    selectedSection = SettingsSection.SYSTEM
                }
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // CONTENT AREA
        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = CharcoalGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.padding(32.dp).fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedSection,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { section ->
                    when (section) {
                        SettingsSection.BUSINESS_PROFILE -> BusinessProfileSection(settings, onSettingsChange, branches, onBranchesChange)
                        SettingsSection.REGIONAL -> RegionalSettingsSection(settings, onSettingsChange)
                        SettingsSection.TARGETS -> TargetsSection(settings, onSettingsChange)
                        SettingsSection.DATA -> DataExportSection(
                            settings = settings,
                            branches = branches,
                            inventoryItems = inventoryItems,
                            saleHistory = saleHistory,
                            credits = credits,
                            expenses = expenses
                        )
                        SettingsSection.HARDWARE -> HardwareSection()
                        SettingsSection.EMPLOYEES -> EmployeesSection(branches = branches)
                        SettingsSection.SECURITY -> SecuritySection(settings, onSettingsChange)
                        SettingsSection.BRANDING -> BrandingSection(settings, onSettingsChange)
                        SettingsSection.SYSTEM -> SystemSection()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsNavItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isSelected) PrimaryOrange.copy(0.1f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (isSelected) PrimaryOrange else TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                label,
                color = if (isSelected) PrimaryOrange else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

// --- SECTIONS ---

@Composable
fun BusinessProfileSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    branches: List<BranchDto> = emptyList(),
    onBranchesChange: (List<BranchDto>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showAddBranchDialog by remember { mutableStateOf(false) }
    var editingBranch by remember { mutableStateOf<BranchDto?>(null) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SectionTitle("Business Profile", "Manage your bar's identity, contact details, and branches")
        Spacer(modifier = Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(CharcoalGray).border(2.dp, PrimaryOrange, CircleShape), contentAlignment = Alignment.Center) {
                if (settings.companyLogoUrl != null) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(40.dp))
                } else {
                    Icon(Icons.Default.AddAPhoto, null, tint = PrimaryOrange)
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Button(onClick = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Company Logo", java.awt.FileDialog.LOAD)
                            dialog.isVisible = true
                            val fileStr = dialog.file
                            val dirStr = dialog.directory
                            if (fileStr != null && dirStr != null) {
                                val file = java.io.File(dirStr, fileStr)
                                val bytes = file.readBytes()
                                val uploadPath = "logo_${System.currentTimeMillis()}_${fileStr}"
                                val bucket = SupabaseManager.client?.storage?.from("branding") ?: return@launch
                                bucket.upload(uploadPath, bytes) { upsert = true }
                                val url = bucket.publicUrl(uploadPath)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    onSettingsChange(settings.copy(companyLogoUrl = url))
                                }
                            }
                        } catch (e: Exception) {
                            Logger.error("SETTINGS", "Logo upload failed", e)
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)) {
                    Text("Upload Logo", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
                Text("Recommended size: 512x512px", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        SettingsTextField("Business Name", settings.businessName) { onSettingsChange(settings.copy(businessName = it)) }
        SettingsTextField("Phone Number", settings.phoneNumber) { onSettingsChange(settings.copy(phoneNumber = it)) }
        SettingsTextField("Email Address", settings.email) { onSettingsChange(settings.copy(email = it)) }
        SettingsTextField("Full Address", settings.address, isMultiline = true) { onSettingsChange(settings.copy(address = it)) }

        Spacer(modifier = Modifier.height(32.dp))

        // --- BRANCH MANAGEMENT ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Branch Management", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                Text("Add or edit your business branches", color = TextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = { showAddBranchDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Branch")
            }
        }

        Spacer(Modifier.height(12.dp))

        branches.forEach { branch ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (branch.type == "LIQUOR_SHOP") Icons.Default.LocalDrink else Icons.Default.Storefront,
                        null, tint = PrimaryOrange, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(branch.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(branch.type.replace("_", " ") + (if (branch.address != null) " • ${branch.address}" else ""), color = TextSecondary, fontSize = 11.sp)
                    }
                    IconButton(onClick = { editingBranch = branch }) {
                        Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?.postgrest?.get("branches")?.delete { filter { eq("id", branch.id ?: "") } }
                                onBranchesChange(branches.filter { it.id != branch.id })
                            } catch (e: Exception) {
                                Logger.error("SETTINGS", "Failed to delete branch", e)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        var isSavingProfile by remember { mutableStateOf(false) }

        Button(onClick = {
            isSavingProfile = true
            scope.launch {
                try {
                    SupabaseManager.client?.auth?.updateUser {
                        data {
                            put("business_name", settings.businessName)
                        }
                    }
                } catch (e: Exception) {
                    Logger.error("SETTINGS", "Failed to save profile changes", e)
                } finally {
                    isSavingProfile = false
                }
            }
        }, modifier = Modifier.width(200.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) {
            if (isSavingProfile) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
            } else {
                Text("Save Profile Changes", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
    }

    // Add/Edit Branch Dialog
    val dialogBranch = editingBranch
    if (showAddBranchDialog || dialogBranch != null) {
        var bName by remember(dialogBranch) { mutableStateOf(dialogBranch?.name ?: "") }
        var bType by remember(dialogBranch) { mutableStateOf(dialogBranch?.type ?: "BAR") }
        var bAddress by remember(dialogBranch) { mutableStateOf(dialogBranch?.address ?: "") }
        var isSaving by remember { mutableStateOf(false) }
        var typeExpanded by remember { mutableStateOf(false) }
        val types = listOf("BAR", "LIQUOR_SHOP")

        AlertDialog(
            onDismissRequest = { if (!isSaving) { showAddBranchDialog = false; editingBranch = null } },
            containerColor = SurfaceColor,
            title = {
                Text(
                    if (dialogBranch == null) "Add Branch" else "Edit Branch",
                    color = TextPrimary, fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bName, onValueChange = { bName = it },
                        label = { Text("Branch Name", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = bType, onValueChange = {}, readOnly = true,
                            label = { Text("Type", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { typeExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                        )
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                            types.forEach { t -> DropdownMenuItem(text = { Text(t.replace("_"," "), color = TextPrimary) }, onClick = { bType = t; typeExpanded = false }) }
                        }
                    }
                    OutlinedTextField(
                        value = bAddress, onValueChange = { bAddress = it },
                        label = { Text("Address (optional)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                if (dialogBranch == null) {
                                    val newBranch = BranchDto(name = bName, type = bType, address = bAddress.ifBlank { null })
                                    val inserted = com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?.postgrest?.get("branches")?.insert(newBranch) { select() }
                                        ?.decodeAs<List<BranchDto>>() ?: emptyList()
                                    onBranchesChange(branches + inserted)
                                } else {
                                    com.example.barandgrillownerpanel.data.remote.SupabaseManager.client?.postgrest?.get("branches")?.update({
                                            set("name", bName)
                                            set("type", bType)
                                            set("address", bAddress.ifBlank { null })
                                        }) { filter { eq("id", dialogBranch.id ?: "") } }
                                    onBranchesChange(branches.map { if (it.id == dialogBranch.id) it.copy(name = bName, type = bType, address = bAddress.ifBlank { null }) else it })
                                }
                                showAddBranchDialog = false
                                editingBranch = null
                            } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Save settings failed", e) } finally { isSaving = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    enabled = !isSaving && bName.isNotBlank()
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (dialogBranch == null) "Add Branch" else "Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBranchDialog = false; editingBranch = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionalSettingsSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    var showAddPaymentDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SectionTitle("Regional & Payments", "Configure local currency and accepted payment methods")
        Spacer(modifier = Modifier.height(32.dp))

        var countryDropdownExpanded by remember { mutableStateOf(false) }
        val countries = remember {
            listOf(
                Triple("Malawi", "MWK", "MK"),
                Triple("South Africa", "ZAR", "R"),
                Triple("Zambia", "ZMW", "K"),
                Triple("Zimbabwe", "USD", "$"),
                Triple("Kenya", "KES", "KSh"),
                Triple("Tanzania", "TZS", "TSh"),
                Triple("Botswana", "BWP", "P"),
                Triple("Nigeria", "NGN", "₦"),
                Triple("United States", "USD", "$"),
                Triple("United Kingdom", "GBP", "£")
            )
        }

        Text("Country", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        ExposedDropdownMenuBox(
            expanded = countryDropdownExpanded,
            onExpandedChange = { countryDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = settings.country,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryDropdownExpanded) },
                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryOrange,
                    unfocusedBorderColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            ExposedDropdownMenu(
                expanded = countryDropdownExpanded,
                onDismissRequest = { countryDropdownExpanded = false },
                containerColor = CharcoalGray
            ) {
                countries.forEach { (name, code, sym) ->
                    DropdownMenuItem(
                        text = { Text(name, color = TextPrimary) },
                        onClick = {
                            onSettingsChange(settings.copy(country = name, currencyCode = code, currencySymbol = sym))
                            countryDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SettingsTextField("Currency Code (e.g. MWK)", settings.currencyCode) { onSettingsChange(settings.copy(currencyCode = it)) }
            }
            Box(modifier = Modifier.weight(0.5f)) {
                SettingsTextField("Symbol", settings.currencySymbol) { onSettingsChange(settings.copy(currencySymbol = it)) }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Accepted Payment Methods", fontWeight = FontWeight.ExtraBold, color = TextPrimary, fontSize = 16.sp)
                Text("Manage how customers pay across your branches", color = TextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = { showAddPaymentDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Option")
            }
        }

        Spacer(Modifier.height(16.dp))

        settings.paymentMethods.forEach { method ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when(method.type) {
                            "MOBILE_MONEY" -> Icons.Default.PhoneAndroid
                            "BANK_TRANSFER" -> Icons.Default.AccountBalance
                            "POS" -> Icons.Default.CreditCard
                            else -> Icons.Default.Payments
                        },
                        null, tint = PrimaryOrange, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(method.toString(), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val newList = settings.paymentMethods.filter { it != method }
                        onSettingsChange(settings.copy(paymentMethods = newList))
                    }) {
                        Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showAddPaymentDialog) {
        var type by remember { mutableStateOf("MOBILE_MONEY") }
        var operator by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = false },
            containerColor = SurfaceColor,
            title = { Text("Add Payment Option", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Payment Type", color = TextSecondary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("CASH", "MOBILE_MONEY", "BANK_TRANSFER", "POS", "CHEQUE")?.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t) },
                                modifier = Modifier.padding(end = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryOrange, selectedLabelColor = DarkBackground)
                            )
                        }
                    }
                    if (type == "MOBILE_MONEY" || type == "BANK_TRANSFER") {
                        OutlinedTextField(
                            value = operator, onValueChange = { operator = it },
                            label = { Text(if (type == "MOBILE_MONEY") "Mobile Money Operator" else "Bank Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newMethod = PaymentMethod(type, operator.ifBlank { null })
                        onSettingsChange(settings.copy(paymentMethods = settings.paymentMethods + newMethod))
                        showAddPaymentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Add Method", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPaymentDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
@Composable
fun TargetsSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Column {
        SectionTitle("Targets & Alerts", "Set the goals that power your dashboard charts")
        Spacer(modifier = Modifier.height(32.dp))
        
        SettingsTextField("Daily Sales Goal (MK)", settings.dailySalesGoal.toInt().toString()) {
            val newValue = it.toDoubleOrNull() ?: settings.dailySalesGoal
            onSettingsChange(settings.copy(dailySalesGoal = newValue))
        }
        SettingsTextField("Low Stock Alert Threshold (%)", settings.lowStockThresholdPercent.toInt().toString()) {
            val newValue = it.toDoubleOrNull() ?: settings.lowStockThresholdPercent
            onSettingsChange(settings.copy(lowStockThresholdPercent = newValue))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Current Threshold Behavior:", color = TextSecondary, fontSize = 13.sp)
        Text("Items will glow Orange when stock falls below ${settings.lowStockThresholdPercent.toInt()}% of their total capacity.", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HardwareSection() {
    Column {
        SectionTitle("Hardware (Printers)", "Configure your thermal receipt printers")
        Spacer(modifier = Modifier.height(32.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkBackground)) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Print, null, tint = PrimaryOrange, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Receipt Printer 1", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("IP Address: 192.168.1.100", color = TextSecondary, fontSize = 12.sp)
                }
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = CharcoalGray)) {
                    Text("Test Connection", color = TextPrimary)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesSection(branches: List<BranchDto> = emptyList()) {
    val scope = rememberCoroutineScope()
    val employees = remember { mutableStateListOf<Employee>() }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Branch filter — null means "ALL"
    var selectedFilterBranch by remember { mutableStateOf<BranchDto?>(null) }

    // Dialog state
    val generateEmployeeId = { UUID.randomUUID().toString().substring(0, 8).uppercase() }
    var newEmployeeId by remember { mutableStateOf(generateEmployeeId()) }
    var newName by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var selectedParentBranch by remember { mutableStateOf<BranchDto?>(null) }
    var selectedSubBranch by remember { mutableStateOf<BranchDto?>(null) }
    var parentDropdownOpen by remember { mutableStateOf(false) }
    var subBranchDropdownOpen by remember { mutableStateOf(false) }
    val branchById = remember(branches) { branches.associateBy { it.id } }
    val barAndGrillParent = remember(branches) {
        branches.firstOrNull { branch ->
            branch.parentId == null && branch.name.equals("Bar and Grill", ignoreCase = true)
        }
    }
    val mainBranches = remember(branches) { branches.filter { it.parentId == null } }
    val currentSubBranches = remember(selectedParentBranch, branches) {
        if (selectedParentBranch != null) {
            branches.filter { it.parentId == selectedParentBranch?.id }
        } else emptyList()
    }

    LaunchedEffect(Unit) {
        try {
            val list = SupabaseManager.client?.postgrest?.get("employees")?.select()
                ?.decodeAs<List<Employee>>() ?: emptyList()
            employees.clear()
            employees.addAll(list)
        } catch (e: Exception) {
            com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Failed loading employee list", e)
        } finally {
            isLoading = false
        }
    }

    // Filtered employee list — null branchId = legacy Bar & Grill employee
    val displayedEmployees = remember(employees.size, selectedFilterBranch, branches) {
        if (selectedFilterBranch == null) employees.toList()
        else {
            val selectedId = selectedFilterBranch?.id
            val selectedChildIds = branches
                .filter { it.parentId == selectedId }
                .mapNotNull { it.id }
                .toSet()
            val selectedAndChildren = buildSet {
                if (selectedId != null) add(selectedId)
                addAll(selectedChildIds)
            }
            val isMainBranch = selectedFilterBranch?.type == "BAR" ||
                selectedFilterBranch?.name?.contains("Bar", ignoreCase = true) == true
            employees.filter { emp ->
                emp.branchId in selectedAndChildren ||
                    (isMainBranch && emp.branchId == null)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("Employee Manager", "Manage staff members per branch")
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = {
                    newEmployeeId = generateEmployeeId()
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = DarkBackground)
                Spacer(Modifier.width(8.dp))
                Text("Add Employee", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Branch filter pills
        if (branches.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = selectedFilterBranch == null,
                    onClick = { selectedFilterBranch = null },
                    label = { Text("All Branches", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryOrange,
                        selectedLabelColor = DarkBackground
                    )
                )
                branches.forEach { branch ->
                    FilterChip(
                        selected = selectedFilterBranch?.id == branch.id,
                        onClick = { selectedFilterBranch = branch },
                        label = { Text(branch.name, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryOrange,
                            selectedLabelColor = DarkBackground
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryOrange)
            }
        } else if (displayedEmployees.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedFilterBranch == null) "No employees added yet."
                    else "No employees for ${selectedFilterBranch?.name}.",
                    color = TextSecondary
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                displayedEmployees.forEach { employee ->
                    // Show branch chip on card if viewing "All"
                    val branchName = if (selectedFilterBranch == null)
                        if (employee.branchId == null) barAndGrillParent?.name
                        else branchById[employee.branchId]?.name
                    else null
                    EmployeeCard(employee, branchName = branchName, onDelete = {
                        scope.launch {
                            try {
                                SupabaseManager.client?.postgrest?.get("employees")?.delete { filter { eq("id", employee.id!!) } }
                                employees.remove(employee)
                            } catch (e: Exception) {
                                com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Failed deleting employee", e)
                            }
                        }
                    })
                }
            }
        }
    }

    // Add Employee Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = CharcoalGray,
            title = { Text("Add New Employee", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsTextField("Full Name", newName) { newName = it }

                    Column {
                        Text("Employee ID", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newEmployeeId,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedBorderColor = PrimaryOrange,
                                unfocusedContainerColor = DarkBackground,
                                focusedContainerColor = DarkBackground,
                                unfocusedTextColor = TextPrimary,
                                focusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Use this ID for mobile login. It is generated by the owner.", color = TextSecondary, fontSize = 12.sp)
                    }
                    
                    var roleDropdownExpanded by remember { mutableStateOf(false) }
                    val roles = listOf("Supervisor", "Employee")
                    
                    Text("Role", color = TextSecondary, fontSize = 13.sp)
                    ExposedDropdownMenuBox(
                        expanded = roleDropdownExpanded,
                        onExpandedChange = { roleDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = newRole,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select Role...", color = TextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryOrange,
                                unfocusedBorderColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = roleDropdownExpanded,
                            onDismissRequest = { roleDropdownExpanded = false },
                            containerColor = CharcoalGray
                        ) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role, color = TextPrimary) },
                                    onClick = {
                                        newRole = role
                                        roleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    SettingsTextField("PIN (Optional)", newPin) { newPin = it }

                    val flavor = System.getProperty("appFlavor", "barandgrill")
                    val isMultiBar = flavor == "barandgrill"

                    // Main Branch assignment dropdown
                    if (isMultiBar && mainBranches.isNotEmpty()) {
                        Text("Assign to Branch", color = TextSecondary, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = parentDropdownOpen,
                            onExpandedChange = { parentDropdownOpen = it }
                        ) {
                            OutlinedTextField(
                                value = selectedParentBranch?.name ?: "Select main branch...",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentDropdownOpen) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryOrange,
                                    unfocusedBorderColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = parentDropdownOpen,
                                onDismissRequest = { parentDropdownOpen = false },
                                containerColor = CharcoalGray
                            ) {
                                mainBranches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name, color = TextPrimary) },
                                        onClick = {
                                            selectedParentBranch = branch
                                            selectedSubBranch = null // reset sub-bar when clicking new main branch
                                            parentDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Sub-branch assignment dropdown
                    if (isMultiBar && currentSubBranches.isNotEmpty()) {
                        Text("Select Sub-Bar", color = TextSecondary, fontSize = 13.sp)
                        ExposedDropdownMenuBox(
                            expanded = subBranchDropdownOpen,
                            onExpandedChange = { subBranchDropdownOpen = it }
                        ) {
                            OutlinedTextField(
                                value = selectedSubBranch?.name ?: "Select sub-bar...",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subBranchDropdownOpen) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryOrange,
                                    unfocusedBorderColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = subBranchDropdownOpen,
                                onDismissRequest = { subBranchDropdownOpen = false },
                                containerColor = CharcoalGray
                            ) {
                                currentSubBranches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name, color = TextPrimary) },
                                        onClick = {
                                            selectedSubBranch = branch
                                            subBranchDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val flavor = System.getProperty("appFlavor", "barandgrill")
                        val isMultiBar = flavor == "barandgrill"
                        
                        val finalBranchToAssign = if (!isMultiBar) {
                            mainBranches.firstOrNull()
                        } else {
                            if (currentSubBranches.isNotEmpty()) selectedSubBranch else selectedParentBranch
                        }
                        
                        val isBranchValid = if (!isMultiBar) {
                            true
                        } else if (mainBranches.isNotEmpty()) {
                            if (currentSubBranches.isNotEmpty()) selectedSubBranch != null else selectedParentBranch != null
                        } else true

                        if (newName.isNotBlank() && newRole.isNotBlank() && isBranchValid) {
                            scope.launch {
                                try {
                                    val emp = Employee(
                                        id = newEmployeeId,
                                        name = newName,
                                        role = newRole,
                                        pin = newPin.ifBlank { null },
                                        branchId = finalBranchToAssign?.id
                                    )
                                    val inserted = SupabaseManager.client?.postgrest?.get("employees")?.insert(emp) { select() }
                                        ?.decodeSingle<Employee>() ?: return@launch
                                    employees.add(inserted)
                                    showAddDialog = false
                                    newEmployeeId = generateEmployeeId()
                                    newName = ""; newRole = ""; newPin = ""; selectedParentBranch = null; selectedSubBranch = null
                                } catch (e: Exception) {
                                    com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Failed adding new employee", e)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Add Employee", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun EmployeeCard(employee: Employee, branchName: String? = null, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(PrimaryOrange.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(employee.name.take(1).uppercase(), color = PrimaryOrange, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(employee.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(employee.role, color = TextSecondary, fontSize = 13.sp)
                if (!employee.id.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ID: ${employee.id}", color = TextSecondary, fontSize = 12.sp)
                }
                if (branchName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PrimaryOrange.copy(alpha = 0.15f)
                    ) {
                        Text(
                            branchName,
                            color = PrimaryOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = ErrorRed.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun SecuritySection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
   Column {
        SectionTitle("Staff & Security", "Manage access and security pins")
        Spacer(modifier = Modifier.height(32.dp))
        
        SettingsTextField("Admin Override PIN", settings.adminPin) { onSettingsChange(settings.copy(adminPin = it)) }
        SettingsTextField("Manager PIN", settings.managerPin) { onSettingsChange(settings.copy(managerPin = it)) }
        SettingsTextField("Lock Screen Timeout (Minutes, 0 to disable)", settings.lockTimeoutMinutes.toString()) { 
            val newTimeout = it.toIntOrNull() ?: settings.lockTimeoutMinutes
            onSettingsChange(settings.copy(lockTimeoutMinutes = newTimeout))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        var newOwnerPassword by remember { mutableStateOf("") }
        var isUpdatingPassword by remember { mutableStateOf(false) }
        SettingsTextField("Owner Login Password (Leave blank to keep)", newOwnerPassword) { newOwnerPassword = it }
        Button(
            onClick = {
                if (newOwnerPassword.isNotBlank()) {
                    isUpdatingPassword = true
                    GlobalScope.launch {
                        try {
                            SupabaseManager.client?.auth?.updateUser { password = newOwnerPassword }
                        } catch (e: Exception) {
                            com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Failed updating owner password", e)
                        } finally {
                            isUpdatingPassword = false
                            newOwnerPassword = ""
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            enabled = !isUpdatingPassword && newOwnerPassword.isNotBlank()
        ) {
            Text("Update Login Password", color = DarkBackground, fontWeight = FontWeight.Bold)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Checkbox(checked = true, onCheckedChange = {}, colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange))
            Text("Require Admin PIN to delete items", color = TextSecondary)
        }
    }
}

@Composable
fun BrandingSection(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Column {
        SectionTitle("Branding & UI", "Personalize the look and feel of your app")
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Primary Theme Color", color = TextSecondary, fontSize = 12.sp)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorCircle(Color(0xFF3B82F6), settings.primaryColorHex == "#3B82F6") { onSettingsChange(settings.copy(primaryColorHex = "#3B82F6")) }
            ColorCircle(Color(0xFF6366F1), settings.primaryColorHex == "#6366F1") { onSettingsChange(settings.copy(primaryColorHex = "#6366F1")) }
            ColorCircle(Color(0xFFEC4899), settings.primaryColorHex == "#EC4899") { onSettingsChange(settings.copy(primaryColorHex = "#EC4899")) }
            ColorCircle(Color(0xFF10B981), settings.primaryColorHex == "#10B981") { onSettingsChange(settings.copy(primaryColorHex = "#10B981")) }
            ColorCircle(Color(0xFFF59E0B), settings.primaryColorHex == "#F59E0B") { onSettingsChange(settings.copy(primaryColorHex = "#F59E0B")) }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SettingsTextField("Currency Symbol", settings.currencySymbol) { onSettingsChange(settings.copy(currencySymbol = it)) }
    }
}

@Composable
fun DataExportSection(
    settings: AppSettings,
    branches: List<BranchDto>,
    inventoryItems: List<InventoryItem>,
    saleHistory: List<SaleRecord>,
    credits: List<CreditDto>,
    expenses: List<ExpenseDto>
) {
    val scope = rememberCoroutineScope()
    var exportOptions by remember { mutableStateOf(ExportOptions()) }
    var selectedReportType by remember { mutableStateOf(PdfReportType.PROFIT_AND_LOSS) }
    var branchExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    var reportPeriodExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SectionTitle("Data & Exports", "Export your business data and download accounting reports")
        Spacer(modifier = Modifier.height(32.dp))

        // --- EXCEL EXPORT CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableChart, null, tint = PrimaryOrange, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Export Data to Excel", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("Download your business data in a professionally formatted spreadsheet", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Period Selection
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = exportOptions.period.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Period", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { periodExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) } },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                        )
                        DropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                            ExportPeriod.values().forEach { p ->
                                DropdownMenuItem(text = { Text(p.label, color = TextPrimary) }, onClick = { exportOptions = exportOptions.copy(period = p); periodExpanded = false })
                            }
                        }
                    }

                    // Branch Selection
                    Box(modifier = Modifier.weight(1f)) {
                        val selectedBranchName = branches.find { it.id == exportOptions.branchId }?.name ?: "All Branches"
                        OutlinedTextField(
                            value = selectedBranchName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Branch", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { branchExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) } },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange)
                        )
                        DropdownMenu(expanded = branchExpanded, onDismissRequest = { branchExpanded = false }, modifier = Modifier.background(SurfaceColor)) {
                            DropdownMenuItem(text = { Text("All Branches", color = TextPrimary) }, onClick = { exportOptions = exportOptions.copy(branchId = null); branchExpanded = false })
                            branches.forEach { b ->
                                DropdownMenuItem(text = { Text(b.name, color = TextPrimary) }, onClick = { exportOptions = exportOptions.copy(branchId = b.id); branchExpanded = false })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save Excel Export", java.awt.FileDialog.SAVE)
                                dialog.file = "KLIX_Export_${exportOptions.period.name}_${java.time.LocalDate.now()}.xlsx"
                                dialog.isVisible = true
                                val file = dialog.file
                                val dir = dialog.directory
                                if (file != null && dir != null) {
                                    ExcelExportService.generateExport(
                                        filePath = java.io.File(dir, file).absolutePath,
                                        options = exportOptions,
                                        branches = branches,
                                        inventory = inventoryItems,
                                        sales = saleHistory,
                                        credits = credits,
                                        expenses = expenses,
                                        businessName = settings.businessName
                                    )
                                }
                            } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Failed listing files", e) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export to Excel", color = DarkBackground, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- PDF REPORTS CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = PrimaryOrange, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Financial Reports (PDF)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("Professional accounting statements ready for audit", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                
                Spacer(modifier = Modifier.height(24.dp))

                // Report Type Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfReportType.values().forEach { type ->
                        Surface(
                            onClick = { selectedReportType = type },
                            color = if (selectedReportType == type) PrimaryOrange.copy(0.1f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selectedReportType == type) PrimaryOrange else Color.White.copy(0.05f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedReportType == type, onClick = { selectedReportType = type }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(type.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(type.description, color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save PDF Report", java.awt.FileDialog.SAVE)
                                dialog.file = "KLIX_${selectedReportType.name}_${exportOptions.period.name}_${java.time.LocalDate.now()}.pdf"
                                dialog.isVisible = true
                                val file = dialog.file
                                val dir = dialog.directory
                                if (file != null && dir != null) {
                                    val startTime = exportOptions.period.startMillis()
                                    val filteredSales = saleHistory.filter { it.timestamp >= startTime }
                                    val filteredExpenses = expenses.filter { 
                                        val ts = it.expenseDate?.let { t -> try { java.time.LocalDate.parse(t).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() } catch(e: Exception) { 0L } } ?: 0L
                                        ts >= startTime 
                                    }
                                    
                                    val totalRev = filteredSales.sumOf { it.totalAmount }
                                    val totalExp = filteredExpenses.sumOf { it.amount }

                                    PdfReportService.generateReport(
                                        filePath = java.io.File(dir, file).absolutePath,
                                        type = selectedReportType,
                                        period = exportOptions.period,
                                        businessName = settings.businessName,
                                        data = mapOf("metrics" to mapOf("Total Revenue" to totalRev, "Total Expenses" to totalExp, "Net Profit" to (totalRev - totalExp)))
                                    )
                                }
                            } catch (e: Exception) { com.example.barandgrillownerpanel.utils.Logger.error("SETTINGS", "Failed uploading branding", e) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download PDF Report", color = DarkBackground, fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun SystemSection() {
    Column {
        SectionTitle("System & Backup", "Manage database sync and data safety")
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DarkBackground)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, null, tint = SuccessGreen)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Cloud Sync: Active", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Last Backup: Today at 14:22", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = CharcoalGray)) {
                    Text("Backup Now", color = TextPrimary)
                }
            }
        }
    }
}

// --- UTILS ---

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color.White.copy(0.05f))
    }
}

@Composable
fun SettingsTextField(label: String, value: String, isMultiline: Boolean = false, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.White.copy(0.1f),
                focusedBorderColor = PrimaryOrange,
                unfocusedContainerColor = DarkBackground,
                focusedContainerColor = DarkBackground,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary
            ),
            minLines = if (isMultiline) 3 else 1
        )
    }
}

@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (isSelected) 3.dp else 0.dp, Color.White, CircleShape)
            .clickable { onClick() }
    )
}




