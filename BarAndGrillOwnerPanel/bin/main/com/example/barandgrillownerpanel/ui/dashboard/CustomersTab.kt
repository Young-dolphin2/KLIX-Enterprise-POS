package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.ui.theme.*
import com.example.barandgrillownerpanel.models.CustomerDto
import com.example.barandgrillownerpanel.data.remote.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersTab(
    branchId: String? = null
) {
    val scope = rememberCoroutineScope()
    var customers by remember { mutableStateOf<List<CustomerDto>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val filteredCustomers = remember(customers, searchQuery) {
        customers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
    }

    LaunchedEffect(Unit) {
        try {
            val fetched = SupabaseManager.client.postgrest["customers"]
                .select()
                .decodeAs<List<CustomerDto>>()
            customers = fetched
        } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Customer Directory", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Text("Manage members, renters, and loyal clients", color = TextSecondary, fontSize = 14.sp)
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Register New Customer", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name or phone number...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryOrange, unfocusedBorderColor = Color.White.copy(0.1f)),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryOrange)
            }
        } else if (filteredCustomers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No customers found", color = TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredCustomers) { customer ->
                    CustomerCard(customer)
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomerDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newCustomer ->
                scope.launch {
                    try {
                        val inserted = SupabaseManager.client.postgrest["customers"]
                            .insert(newCustomer.copy(branchId = branchId)) { select() }
                            .decodeSingle<CustomerDto>()
                        customers = customers + inserted
                        showAddDialog = false
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        )
    }
}

@Composable
fun CustomerCard(customer: CustomerDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CharcoalGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Profile Placeholder
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(PrimaryOrange.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(customer.name.take(1).uppercase(), color = PrimaryOrange, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(customer.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(customer.phone, color = TextSecondary, fontSize = 13.sp)
            }

            // Status Badge
            val statusColor = when(customer.membershipStatus) {
                "ACTIVE" -> SuccessGreen
                "EXPIRED" -> ErrorRed
                else -> TextSecondary
            }
            
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(statusColor.copy(0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(customer.membershipStatus, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            
            Spacer(Modifier.width(16.dp))
            
            IconButton(onClick = { /* TODO: View Profile/History */ }) {
                Icon(Icons.Default.ArrowForwardIos, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AddCustomerDialog(onDismiss: () -> Unit, onSave: (CustomerDto) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf("National ID") }
    var idNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("Register Customer", color = TextPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = idType, onValueChange = { idType = it }, label = { Text("ID Type") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("ID Number") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Physical Address") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(CustomerDto(name = name, phone = phone, idType = idType, idNumber = idNumber, address = address)) },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
            ) { Text("Save Customer", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
