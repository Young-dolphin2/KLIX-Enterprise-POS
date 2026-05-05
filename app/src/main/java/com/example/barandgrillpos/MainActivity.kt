@file:Suppress("SpellCheckingInspection")

package com.example.barandgrillpos

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillpos.ui.theme.*
import com.example.barandgrillpos.ui.theme.ThemePreferences
import com.example.barandgrillpos.ui.onboarding.OnboardingScreen
import com.example.barandgrillpos.ui.auth.LoginScreen
import com.example.barandgrillpos.ui.auth.SignUpScreen
import com.example.barandgrillpos.models.*
import com.example.barandgrillpos.data.local.*
import com.example.barandgrillpos.data.sync.*
import com.example.barandgrillpos.data.remote.SupabaseManager
import com.example.barandgrillpos.data.remote.dto.SaleDto
import com.example.barandgrillpos.data.local.EmployeeEntity
import com.example.barandgrillpos.ui.*
import com.example.barandgrillpos.utils.SecurityUtils
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var _printerStatus = mutableStateOf(PrinterStatus.DISCONNECTED)
    private var connectivityManager: ConnectivityManager? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            SyncManager.startImmediateSync(this@MainActivity)
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> updatePrinterStatus()
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> updatePrinterStatus()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            checkBluetoothState()
        } else {
            Toast.makeText(this, "Permissions required for printing", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissionsAndEnableBluetooth()

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) { }

        val prefs: SharedPreferences = getSharedPreferences("employee_prefs", Context.MODE_PRIVATE)
        val employeeName = mutableStateOf(prefs.getString("employee_name", "") ?: "")
        val employeeRole = mutableStateOf(prefs.getString("employee_role", "") ?: "")

        SyncManager.schedulePeriodicSync(this)

        setContent {
            val themePreferences = remember { ThemePreferences(this@MainActivity) }
            val seedColor by themePreferences.seedColor.collectAsState(initial = 0xFFFF8C00)
            val isFirstLaunch by themePreferences.isFirstLaunch.collectAsState(initial = true)

            // Dynamic Settings State - Declared at top level of setContent
            var dynamicPaymentMethods by remember { mutableStateOf<List<PaymentMethod>>(emptyList()) }
            var dynamicCurrencySymbol by remember { mutableStateOf("MK") }
            var dynamicPrimaryColor by remember { mutableStateOf<Long?>(null) }
            var businessName by remember { mutableStateOf("KLIX") }

            val sessionStatus by SupabaseManager.client.auth.sessionStatus.collectAsState()

            LaunchedEffect(sessionStatus) {
                if (sessionStatus is SessionStatus.Authenticated) {
                    try {
                        // 1. Fetch from app_settings table (Source of Truth)
                        val settings = SupabaseManager.client.postgrest["app_settings"]
                            .select()
                            .decodeSingleOrNull<AppSettingsDto>()
                        
                        settings?.let {
                            dynamicCurrencySymbol = it.currency_symbol
                            dynamicPaymentMethods = it.payment_options
                            businessName = it.business_name
                            try {
                                dynamicPrimaryColor = android.graphics.Color.parseColor(it.primary_color_hex).toLong()
                            } catch (_: Exception) {}
                        }

                        // 2. Fallback/Sync to metadata if table is empty (unlikely after onboarding)
                        if (settings == null) {
                            val user = SupabaseManager.client.auth.retrieveUserForCurrentSession()
                            user.userMetadata?.let { meta ->
                                meta["currency_symbol"]?.let { dynamicCurrencySymbol = it.toString().replace("\"", "") }
                                meta["payment_methods"]?.let {
                                    val jsonStr = it.toString().trim('"').replace("\\\"", "\"")
                                    try {
                                        dynamicPaymentMethods = Json.decodeFromString<List<PaymentMethod>>(jsonStr)
                                    } catch (_: Exception) { }
                                }
                                meta["primary_color_hex"]?.let {
                                    val hex = it.toString().replace("\"", "")
                                    try {
                                        dynamicPrimaryColor = android.graphics.Color.parseColor(hex).toLong()
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            BarAndGrillPOSTheme(seedColor = dynamicPrimaryColor ?: seedColor) {
                var currentScreen by remember { mutableStateOf<Screen?>(null) }
                
                LaunchedEffect(isFirstLaunch, sessionStatus) {
                    if (currentScreen == null || currentScreen == Screen.ONBOARDING) {
                        currentScreen = if (isFirstLaunch) {
                            Screen.ONBOARDING
                        } else if (sessionStatus !is SessionStatus.Authenticated) {
                            Screen.LOGIN
                        } else {
                            Screen.POS
                        }
                    } else if (sessionStatus is SessionStatus.Authenticated && (currentScreen == Screen.LOGIN || currentScreen == Screen.SIGN_UP)) {
                        currentScreen = Screen.POS
                    } else if (sessionStatus !is SessionStatus.Authenticated && currentScreen == Screen.POS) {
                        currentScreen = Screen.LOGIN
                    }
                }

                var currentEmployeeName by remember { employeeName }
                var currentEmployeeRole by remember { mutableStateOf(prefs.getString("employee_role", "Staff") ?: "Staff") }
                var isUnlocked by remember { mutableStateOf(false) }

                val registeredEmployees = remember { mutableStateListOf<EmployeeEntity>() }
                var isEmployeeRegistered by remember { mutableStateOf(false) }

                val database = remember { AppDatabase.getDatabase(applicationContext) }
                val saleDao = database.saleDao()

                LaunchedEffect(Unit) {
                    try {
                        database.cacheDao().observeActiveEmployees().collectLatest { cached ->
                            registeredEmployees.clear()
                            registeredEmployees.addAll(cached)
                        }
                    } catch (_: Exception) { }
                }

                LaunchedEffect(currentEmployeeName, registeredEmployees.size) {
                    isEmployeeRegistered = registeredEmployees.any { it.name == currentEmployeeName }
                }

                val currentEmployee = remember(currentEmployeeName, registeredEmployees) {
                    registeredEmployees.find { it.name == currentEmployeeName }
                }

                LaunchedEffect(currentEmployeeName) {
                    isUnlocked = false
                }

                val allBranches = remember { mutableStateListOf<BranchRef>() }
                var appBranchId by remember { mutableStateOf<String?>(null) }
                var appBranchType by remember { mutableStateOf("RETAIL") }
                var childBranchIds by remember { mutableStateOf<List<String>>(emptyList()) }

                LaunchedEffect(Unit) {
                    try {
                        val fetched = SupabaseManager.client.postgrest["branches"]
                            .select()
                            .decodeAs<List<BranchRef>>()
                        allBranches.clear()
                        allBranches.addAll(fetched)
                        val currentBranch = fetched.firstOrNull() // Default to first or add selection
                        val resolvedId = currentBranch?.id
                        appBranchType = currentBranch?.type ?: "RETAIL"
                        childBranchIds = fetched.filter { it.parentId == resolvedId }.map { it.id }
                        appBranchId = resolvedId
                    } catch (e: Exception) { e.printStackTrace() }
                }

                val flowSales = remember { saleDao.getAllSales() }
                val dbSales by flowSales.collectAsState(initial = emptyList())

                val saleHistory = remember(dbSales) {
                    dbSales.map { entity ->
                        SaleRecord(
                            id = entity.id,
                            items = Json.decodeFromString(entity.itemsJson),
                            totalAmount = entity.totalAmount,
                            paymentMethod = entity.paymentMethod,
                            soldBy = entity.soldBy,
                            timestamp = entity.timestamp
                        )
                    }
                }

                // Daily remote sales sync
                LaunchedEffect(appBranchId) {
                    while (true) {
                        try {
                            val remoteSales = SupabaseManager.client
                                .postgrest["sales"]
                                .select { filter { eq("is_active", true) } }
                                .decodeAs<List<SaleDto>>()
                            remoteSales.forEach { dto ->
                                val items: List<OrderItem> = emptyList()
                                val entity = SaleEntity(
                                    id = dto.orderId,
                                    itemsJson = Json.encodeToString(items),
                                    totalAmount = dto.totalAmount,
                                    paymentMethod = dto.paymentMethod ?: "Cash",
                                    soldBy = dto.soldBy ?: "",
                                    timestamp = try {
                                        java.time.OffsetDateTime.parse(dto.timestamp).toInstant().toEpochMilli()
                                    } catch (_: Exception) { System.currentTimeMillis() },
                                    branchId = dto.branchId,
                                    isSynced = true
                                )
                                saleDao.insertSale(entity)
                            }
                        } catch (_: Exception) { }
                        delay(24 * 60 * 60 * 1000L)
                    }
                }

                val saleBranchId = appBranchId

                if (currentScreen == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    return@BarAndGrillPOSTheme
                }

                // PIN protection
                if (currentScreen == Screen.POS || currentScreen == Screen.STATS) {
                    if (!isUnlocked && currentEmployee != null && currentEmployee.pinHash != null) {
                        PinEntryScreen(
                            employeeName = currentEmployeeName,
                            onPinEntered = { enteredPin ->
                                if (SecurityUtils.verifyPin(enteredPin, currentEmployee.pinHash!!)) {
                                    isUnlocked = true
                                } else {
                                    Toast.makeText(this@MainActivity, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLogout = {
                                lifecycleScope.launch {
                                    SupabaseManager.client.auth.signOut()
                                    currentEmployeeName = ""
                                    currentEmployeeRole = "Staff"
                                    prefs.edit().clear().apply()
                                    currentScreen = Screen.LOGIN
                                }
                            }
                        )
                        return@BarAndGrillPOSTheme
                    }
                }

                Crossfade(targetState = currentScreen!!, label = "ScreenTransition") { screen ->
                    when (screen) {
                        Screen.ONBOARDING -> OnboardingScreen(
                            onComplete = {
                                lifecycleScope.launch {
                                    themePreferences.setFirstLaunchCompleted()
                                    if (sessionStatus !is SessionStatus.Authenticated) {
                                        currentScreen = Screen.LOGIN
                                    } else {
                                        currentScreen = Screen.POS
                                    }
                                }
                            },
                            onColorSelected = { colorVal ->
                                lifecycleScope.launch { themePreferences.setSeedColor(colorVal) }
                            }
                        )
                        Screen.LOGIN -> LoginScreen(
                            onLoginSuccess = { currentScreen = Screen.POS },
                            onNavigateToSignUp = { currentScreen = Screen.SIGN_UP }
                        )
                        Screen.SIGN_UP -> SignUpScreen(
                            onSignUpSuccess = { currentScreen = Screen.POS },
                            onNavigateToLogin = { currentScreen = Screen.LOGIN }
                        )
                        Screen.POS -> POSScreen(
                            printerStatus = _printerStatus.value,
                            onNavigateToStats = { currentScreen = Screen.STATS },
                            onNavigateToProfile = { currentScreen = Screen.EMPLOYEE_PROFILE },
                            employeeName = currentEmployeeName,
                            isEmployeeRegistered = isEmployeeRegistered,
                            appBranchId = saleBranchId,
                            appBranchType = appBranchType,
                            parentBranchId = if (appBranchType == "RETAIL") appBranchId else null, // Logic based on type
                            childBranchIds = childBranchIds,
                            paymentMethods = dynamicPaymentMethods,
                            currencySymbol = dynamicCurrencySymbol,
                            onSaleComplete = { sale ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        val entity = SaleEntity(
                                            id = sale.id,
                                            itemsJson = Json.encodeToString(sale.items),
                                            totalAmount = sale.totalAmount,
                                            paymentMethod = sale.paymentMethod,
                                            soldBy = sale.soldBy,
                                            timestamp = sale.timestamp,
                                            branchId = saleBranchId
                                        )
                                        saleDao.insertSale(entity)
                                        val syncedNow = trySyncSaleNow(entity)
                                        if (syncedNow) {
                                            saleDao.markAsSynced(entity.id)
                                        } else {
                                            saleDao.addToSyncQueue(
                                                SyncQueueEntry(
                                                    action = "INSERT_SALE",
                                                    payloadJson = Json.encodeToString(entity)
                                                )
                                            )
                                            SyncManager.startImmediateSync(this@MainActivity)
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        )
                        Screen.STATS -> SalesStatsScreen(
                            saleHistory = saleHistory,
                            onBackToPOS = { currentScreen = Screen.POS },
                            context = this@MainActivity,
                            onReprint = { sale ->
                                printThermalReceipt(this@MainActivity, sale.items, sale.totalAmount)
                            }
                        )
                        Screen.EMPLOYEE_PROFILE -> EmployeeProfileScreen(
                            currentName = currentEmployeeName,
                            currentRole = currentEmployeeRole,
                            onBack = { currentScreen = Screen.POS },
                            onLogout = {
                                lifecycleScope.launch {
                                    SupabaseManager.client.auth.signOut()
                                    currentEmployeeName = ""
                                    currentEmployeeRole = "Staff"
                                    prefs.edit().clear().apply()
                                    currentScreen = Screen.LOGIN
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { }
    }

    private fun checkPermissionsAndEnableBluetooth() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            updatePrinterStatus()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkBluetoothState() = updatePrinterStatus()

    private fun updatePrinterStatus() {
        _printerStatus.value = when {
            bluetoothAdapter == null -> PrinterStatus.DISCONNECTED
            !bluetoothAdapter.isEnabled -> PrinterStatus.BLUETOOTH_OFF
            BluetoothPrintersConnections.selectFirstPaired() != null -> PrinterStatus.READY
            else -> PrinterStatus.DISCONNECTED
        }
    }

    private suspend fun trySyncSaleNow(entity: SaleEntity): Boolean {
        return try {
            val json = buildJsonObject {
                put("order_id", entity.id)
                put("total_amount", entity.totalAmount)
                put("payment_method", entity.paymentMethod)
                put("sold_by", entity.soldBy)
                put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(entity.timestamp)))
                put("branch_id", entity.branchId)
            }
            SupabaseManager.client.postgrest["sales"].insert(json)
            true
        } catch (e: Exception) {
            false
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// POS SCREEN
// ═════════════════════════════════════════════════════════════════════════

@Composable
fun POSScreen(
    printerStatus: PrinterStatus,
    onNavigateToStats: () -> Unit,
    onNavigateToProfile: () -> Unit,
    employeeName: String,
    isEmployeeRegistered: Boolean,
    appBranchId: String? = null,
    appBranchType: String = "RETAIL",
    parentBranchId: String? = null,
    childBranchIds: List<String> = emptyList(),
    paymentMethods: List<PaymentMethod> = emptyList(),
    currencySymbol: String = "MK",
    onSaleComplete: (SaleRecord) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val cacheDao = remember { database.cacheDao() }

    val cachedMenu by cacheDao.observeActiveMenu().collectAsState(initial = emptyList())
    val cachedInventory by cacheDao.observeInventory().collectAsState(initial = emptyList())
    val cachedCategories by cacheDao.observeCategories().collectAsState(initial = emptyList())

    val menuItems: List<MenuItem> = remember(cachedMenu, cachedInventory, appBranchId, parentBranchId, childBranchIds) {
        val allowedIds = setOfNotNull(null, appBranchId, parentBranchId) + childBranchIds
        val scoped = cachedMenu.filter { it.branchId in allowedIds }
        scoped
            .groupBy { "${it.name.trim().lowercase()}|${it.category}|${it.subcategory}" }
            .values
            .map { group ->
                val preferred = group.firstOrNull { it.branchId == appBranchId }
                    ?: group.firstOrNull { it.branchId == parentBranchId }
                    ?: group.firstOrNull { it.branchId == null }
                    ?: group.first()
                MenuItem(
                    id = preferred.id,
                    name = preferred.name,
                    price = preferred.price,
                    category = preferred.category,
                    subcategory = preferred.subcategory,
                    branchId = preferred.branchId,
                    ingredients = preferred.ingredientsJson?.let {
                        try { Json.decodeFromString<List<Ingredient>>(it) } catch (_: Exception) { null }
                    }
                )
            }
            .sortedBy { it.name }
    }

    val categories = remember(cachedCategories, menuItems) {
        val fromMenu = menuItems.map { it.category to it.subcategory }.distinct()
        fromMenu.map { (cat, sub) ->
            CategoryEntity(name = cat.uppercase(), parentName = sub.uppercase())
        }.distinctBy { it.name }
    }

    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedSubcategory by remember { mutableStateOf("ALL") }

    val filteredItems = remember(menuItems, selectedCategory, selectedSubcategory) {
        when {
            selectedCategory == "ALL" -> menuItems
            selectedSubcategory == "ALL" -> menuItems.filter { it.category.uppercase() == selectedCategory.uppercase() }
            else -> menuItems.filter {
                it.category.uppercase() == selectedCategory.uppercase() &&
                it.subcategory.uppercase() == selectedSubcategory.uppercase()
            }
        }
    }

    var orderItems by remember { mutableStateOf(listOf<OrderItem>()) }
    var paymentDialog by remember { mutableStateOf(false) }
    var selectedAssetForRent by remember { mutableStateOf<MenuItem?>(null) }
    var isCustomersTabActive by remember { mutableStateOf(false) }

    val total = remember(orderItems) { orderItems.sumOf { it.item.price * it.quantity } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.app_icon_main),
                            contentDescription = "KLIX",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(BuildConfig.STORE_NAME, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextPrimary)
                    }
                },
                actions = {
                    if (appBranchType == "GYM") {
                        IconButton(onClick = { isCustomersTabActive = !isCustomersTabActive }) {
                            Icon(
                                imageVector = if (isCustomersTabActive) Icons.Default.ShoppingCart else Icons.Default.People,
                                contentDescription = "Toggle Members",
                                tint = if (isCustomersTabActive) PrimaryOrange else TextSecondary
                            )
                        }
                    }
                    Text(employeeName.ifEmpty { "Staff" }, color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(end = 8.dp))
                    Icon(
                        imageVector = when (printerStatus) {
                            PrinterStatus.READY -> Icons.Default.BluetoothConnected
                            PrinterStatus.DISCONNECTED -> Icons.Default.BluetoothDisabled
                            PrinterStatus.BLUETOOTH_OFF -> Icons.Default.Bluetooth
                        },
                        contentDescription = "Printer",
                        tint = when (printerStatus) {
                            PrinterStatus.READY -> SuccessGreen
                            PrinterStatus.DISCONNECTED -> ErrorRed
                            PrinterStatus.BLUETOOTH_OFF -> TextSecondary
                        },
                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                    )
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Default.BarChart, contentDescription = "Sales Stats", tint = TextSecondary)
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (isCustomersTabActive && appBranchType == "GYM") {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CustomersTab(branchId = appBranchId, currencySymbol = currencySymbol)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // LEFT: Menu Items
                Column(modifier = Modifier.weight(2f).fillMaxHeight().padding(12.dp)) {
                    // Category Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == "ALL",
                            onClick = { selectedCategory = "ALL"; selectedSubcategory = "ALL" },
                            label = { Text("ALL", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryOrange, containerColor = CharcoalGray
                            )
                        )
                        categories.take(8).forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat.name.uppercase(),
                                onClick = {
                                    if (selectedCategory == cat.name.uppercase()) {
                                        selectedCategory = "ALL"; selectedSubcategory = "ALL"
                                    } else {
                                        selectedCategory = cat.name.uppercase(); selectedSubcategory = "ALL"
                                    }
                                },
                                label = { Text(cat.name.uppercase().take(12), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryOrange, containerColor = CharcoalGray
                                )
                            )
                        }
                    }
                    // Subcategory Row
                    if (selectedCategory != "ALL") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedSubcategory == "ALL",
                                onClick = { selectedSubcategory = "ALL" },
                                label = { Text("ALL", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryOrange.copy(alpha = 0.7f), containerColor = CharcoalGray
                                )
                            )
                            categories.filter { it.name.uppercase() == selectedCategory.uppercase() }
                                .map { it.parentName ?: "" }.filter { it.isNotBlank() }.distinct()
                                .forEach { sub ->
                                    FilterChip(
                                        selected = selectedSubcategory == sub.uppercase(),
                                        onClick = {
                                            selectedSubcategory = if (selectedSubcategory == sub.uppercase()) "ALL" else sub.uppercase()
                                        },
                                        label = { Text(sub.uppercase().take(12), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryOrange.copy(alpha = 0.7f), containerColor = CharcoalGray
                                        )
                                    )
                                }
                        }
                    }
                    // Menu Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems) { item ->
                            val assetStatus = cachedInventory.find { it.name == item.name }?.status ?: "AVAILABLE"
                            MenuItemCard(
                                item = item, 
                                currencySymbol = currencySymbol, 
                                branchType = appBranchType,
                                status = assetStatus,
                                onClick = {
                                    if (appBranchType == "CAR_RENTAL") {
                                    selectedAssetForRent = item
                                } else {
                                    val existing = orderItems.indexOfFirst { it.item.id == item.id }
                                    if (existing >= 0) {
                                        val list = orderItems.toMutableList()
                                        list[existing] = list[existing].copy(quantity = list[existing].quantity + 1)
                                        orderItems = list
                                    } else {
                                        orderItems = orderItems + OrderItem(item = item, quantity = 1)
                                    }
                                }
                            })
                        }
                    }
                }
                // RIGHT: Cart
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text("Current Order", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Divider(color = CharcoalGray)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(orderItems, key = { it.item.id }) { orderItem ->
                                CartItemRow(
                                    orderItem = orderItem,
                                    currencySymbol = currencySymbol,
                                    onUpdate = { updated -> orderItems = orderItems.map { if (it.item.id == updated.item.id) updated else it } },
                                    onRemove = { orderItems = orderItems.filter { it.item.id != orderItem.item.id } }
                                )
                            }
                            if (orderItems.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                        Text("Tap items to add", color = TextSecondary, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Divider(color = CharcoalGray)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("$currencySymbol${String.format("%,.0f", total)}", color = PrimaryOrange, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { paymentDialog = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = orderItems.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange, disabledContainerColor = CharcoalGray),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("PAY", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }

    if (paymentDialog) {
        PaymentMethodDialog(
            total = total,
            currencySymbol = currencySymbol,
            customMethods = paymentMethods,
            onDismiss = { paymentDialog = false },
            onPaymentComplete = { method ->
                val sale = SaleRecord(
                    id = UUID.randomUUID().toString(),
                    items = orderItems,
                    totalAmount = total,
                    paymentMethod = method,
                    soldBy = employeeName,
                    timestamp = System.currentTimeMillis()
                )
                onSaleComplete(sale)
                printThermalReceipt(context, orderItems, total)
                orderItems = emptyList()
                paymentDialog = false
            }
        )
    }

    if (selectedAssetForRent != null) {
        val asset = cachedInventory.find { it.name == selectedAssetForRent!!.name }
        MobileRentAssetDialog(
            assetName = selectedAssetForRent!!.name,
            currentStatus = asset?.status ?: "AVAILABLE",
            branchId = appBranchId,
            onDismiss = { selectedAssetForRent = null },
            onComplete = {
                selectedAssetForRent = null
                SyncManager.startImmediateSync(context)
            }
        )
    }
}
}

// ═════════════════════════════════════════════════════════════════════════
// MENU ITEM CARD
// ═════════════════════════════════════════════════════════════════════════

@Composable
fun MenuItemCard(
    item: MenuItem, 
    currencySymbol: String = "MK", 
    branchType: String = "RETAIL",
    status: String = "AVAILABLE",
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (branchType == "CAR_RENTAL") {
                Surface(
                    color = when(status) {
                        "RENTED" -> ErrorRed.copy(alpha = 0.2f)
                        "MAINTENANCE" -> PrimaryOrange.copy(alpha = 0.2f)
                        else -> SuccessGreen.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        status, 
                        color = when(status) {
                            "RENTED" -> ErrorRed
                            "MAINTENANCE" -> PrimaryOrange
                            else -> SuccessGreen
                        },
                        fontSize = 9.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(item.name.uppercase(), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("$currencySymbol${String.format("%,.0f", item.price)}", color = PrimaryOrange, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// CART ITEM ROW
// ═════════════════════════════════════════════════════════════════════════

@Composable
fun CartItemRow(orderItem: OrderItem, currencySymbol: String = "MK", onUpdate: (OrderItem) -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onUpdate(orderItem.copy(quantity = orderItem.quantity + 1)) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Increase", tint = SuccessGreen, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(orderItem.item.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("x${orderItem.quantity}", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Text("$currencySymbol${String.format("%.0f", orderItem.item.price * orderItem.quantity)}",
            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ErrorRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// PAYMENT METHOD DIALOG
// ═════════════════════════════════════════════════════════════════════════

@Composable
fun PaymentMethodDialog(
    total: Double, 
    currencySymbol: String = "MK",
    customMethods: List<PaymentMethod> = emptyList(),
    onDismiss: () -> Unit, 
    onPaymentComplete: (String) -> Unit
) {
    val methods = if (customMethods.isNotEmpty()) customMethods.map { it.toString() } else listOf("Cash", "Airtel Money", "TNM Mpamba", "Bank")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("SELECT PAYMENT METHOD", color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$currencySymbol${String.format("%,.0f", total)}", color = PrimaryOrange, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { method ->
                    Button(
                        onClick = { onPaymentComplete(method) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalGray),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(method, color = TextPrimary, fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

// ═════════════════════════════════════════════════════════════════════════
// PRINTING
// ═════════════════════════════════════════════════════════════════════════

suspend fun printThermalReceipt(context: Context, orderItems: List<OrderItem>, total: Double): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val selectedPrinter = BluetoothPrintersConnections.selectFirstPaired()
            if (selectedPrinter == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No printer found. Pair a Bluetooth printer.", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
            val printer = EscPosPrinter(selectedPrinter, 203, 48f, 32)
            val receiptText = buildReceiptText(orderItems, total)
            printer.printFormattedText(receiptText)
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Print failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}

fun buildReceiptText(orderItems: List<OrderItem>, total: Double): String {
    val sb = StringBuilder()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val currentDate = sdf.format(Date())
    sb.append("[C]<font size='big'>${BuildConfig.STORE_NAME}</font>\n")
    sb.append("[C]--------------------------------\n")
    sb.append("[C]Date: $currentDate\n")
    sb.append("[C]--------------------------------\n")
    orderItems.forEach { item ->
        sb.append("[L]${item.item.name.take(20)}[R]x${item.quantity}  MK${String.format("%.0f", item.item.price * item.quantity)}\n")
    }
    sb.append("[C]--------------------------------\n")
    sb.append("[L]<font size='big'>TOTAL</font>[R]<font size='big'>MK${String.format("%,.0f", total)}</font>\n")
    sb.append("[C]--------------------------------\n")
    sb.append("[C]Thank you for your patronage!\n")
    sb.append("[C]Powered by KLIX Enterprise POS\n")
    return sb.toString()
}

@Composable
fun MobileRentAssetDialog(
    assetName: String,
    currentStatus: String,
    branchId: String?,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isCheckIn = currentStatus == "RENTED"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Text(
                if (isCheckIn) "CHECK-IN: $assetName" else "CHECK-OUT: $assetName",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isCheckIn) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Customer Phone") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Condition") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                
                if (isCheckIn) {
                    Text("Are you sure you want to return this asset to inventory?", color = TextSecondary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isCheckIn && (customerName.isBlank() || customerPhone.isBlank())) return@Button
                    isProcessing = true
                    scope.launch {
                        try {
                            val newStatus = if (isCheckIn) "AVAILABLE" else "RENTED"
                            val newStock = if (isCheckIn) 1.0 else 0.0

                            // 1. Update Inventory Item
                            SupabaseManager.client.postgrest["inventory"].update({
                                set("status", newStatus)
                                set("stock_quantity", newStock)
                            }) {
                                filter { 
                                    eq("name", assetName)
                                    eq("branch_id", branchId ?: "")
                                }
                            }

                            // 2. Log History
                            val historyData = buildJsonObject {
                                put("asset_id", assetName) // Using name as ID for simplicity if needed, or fetch ID
                                put("action", if (isCheckIn) "CHECK_IN" else "CHECK_OUT")
                                put("customer_name", if (isCheckIn) "N/A" else customerName)
                                put("customer_phone", if (isCheckIn) "N/A" else customerPhone)
                                put("notes", notes)
                                put("branch_id", branchId)
                            }
                            SupabaseManager.client.postgrest["asset_history"].insert(historyData)

                            onComplete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = if (isCheckIn) SuccessGreen else PrimaryOrange)
            ) {
                if (isProcessing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text(if (isCheckIn) "Confirm Return" else "Complete Rental")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

// ═════════════════════════════════════════════════════════════════════════
// CUSTOMERS & MEMBERSHIP TAB (GYM FOCUS)
// ═════════════════════════════════════════════════════════════════════════

@Composable
fun CustomersTab(
    branchId: String?,
    currencySymbol: String = "MK"
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val cacheDao = remember { database.cacheDao() }
    val customers by cacheDao.observeCustomers(branchId ?: "").collectAsState(initial = emptyList())
    
    var searchQuery by remember { mutableStateOf("") }
    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.phone.contains(searchQuery) || 
            (it.idNumber ?: "").contains(searchQuery)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            placeholder = { Text("Search by name, phone, or ID...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary, 
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = PrimaryOrange,
                unfocusedBorderColor = CharcoalGray
            ),
            shape = RoundedCornerShape(12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredCustomers) { customer ->
                CustomerMembershipCard(customer)
            }
            if (filteredCustomers.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No customers found", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerMembershipCard(customer: CustomerEntity) {
    val isExpired = customer.membershipExpiry?.let { it < System.currentTimeMillis() } ?: true
    val status = customer.membershipStatus?.uppercase() ?: "INACTIVE"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image Placeholder
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(PrimaryOrange.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryOrange)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                Text(customer.phone, color = TextSecondary, fontSize = 13.sp)
                if (!customer.idNumber.isNullOrBlank()) {
                    Text("ID: ${customer.idNumber}", color = TextSecondary, fontSize = 11.sp)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = when {
                        status == "ACTIVE" && !isExpired -> SuccessGreen.copy(alpha = 0.2f)
                        status == "ACTIVE" && isExpired -> ErrorRed.copy(alpha = 0.2f)
                        else -> CharcoalGray.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (status == "ACTIVE" && isExpired) "EXPIRED" else status,
                        color = when {
                            status == "ACTIVE" && !isExpired -> SuccessGreen
                            status == "ACTIVE" && isExpired -> ErrorRed
                            else -> TextSecondary
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                customer.membershipExpiry?.let {
                    val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    Text(
                        "Exp: ${df.format(Date(it))}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
