@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pansare.sadan.ui.tenants

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pansare.sadan.data.TenantEntity
import com.pansare.sadan.domain.MonthKey
import com.pansare.sadan.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun EditTenantScreen(vm: AppViewModel, tenantId: Long, navController: NavController) {
    var tenant by remember { mutableStateOf<TenantEntity?>(null) }
    
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var rentText by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()

    LaunchedEffect(tenantId) {
        val t = vm.findTenant(tenantId)
        if (t != null) {
            tenant = t
            name = t.tenantName
            mobile = t.mobileNumber
            remarks = t.remarks
            rentText = t.monthlyRent.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Tenant") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        val t = tenant ?: return@Scaffold
        
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tenant Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("Mobile Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Divider(Modifier.padding(vertical = 8.dp))
            Text("Rent Details", style = MaterialTheme.typography.titleMedium)
            Text("Changing the rent will record a new effective rate starting this month.", style = MaterialTheme.typography.bodySmall)
            
            OutlinedTextField(
                value = rentText,
                onValueChange = { rentText = it },
                label = { Text("Monthly Rent (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val newRent = rentText.toLongOrNull() ?: t.monthlyRent
                    scope.launch {
                        vm.updateTenant(
                            t.copy(
                                tenantName = name,
                                mobileNumber = mobile,
                                remarks = remarks,
                                monthlyRent = newRent
                            )
                        )
                        if (newRent != t.monthlyRent) {
                            vm.repo.getDatabase().rentChangeDao().insert(
                                com.pansare.sadan.data.RentChangeEntity(
                                    tenantId = t.id,
                                    effectiveFromMonth = MonthKey.current(),
                                    monthlyRent = newRent,
                                    note = "Rent modified from UI"
                                )
                            )
                        }
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && rentText.toLongOrNull() != null
            ) {
                Text("Save Changes")
            }
        }
    }
}
