package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.FarmActivity
import com.example.data.FarmProfile
import com.example.data.LearnedMemory
import com.example.data.MemoryCategory
import com.example.data.PartyRole
import com.example.data.PaymentStatus
import com.example.data.RecordType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorSheet(
    initialActivity: FarmActivity,
    currencySymbol: String,
    isExistingRecord: Boolean,
    confirmButtonLabel: String,
    onSave: (FarmActivity) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var recordType by remember(initialActivity) { mutableStateOf(initialActivity.parsedRecordType) }
    var customTypeName by remember(initialActivity) { mutableStateOf(initialActivity.customTypeName.orEmpty()) }
    var title by remember(initialActivity) { mutableStateOf(initialActivity.title) }
    var itemOrCrop by remember(initialActivity) { mutableStateOf(initialActivity.itemOrCrop) }
    var plotOrUnit by remember(initialActivity) { mutableStateOf(initialActivity.plotOrUnit) }
    var quantityText by remember(initialActivity) {
        mutableStateOf(if (initialActivity.quantity > 0.0) initialActivity.quantity.toString().removeSuffix(".0") else "")
    }
    var unit by remember(initialActivity) { mutableStateOf(initialActivity.unit) }
    var totalText by remember(initialActivity) {
        mutableStateOf(if (initialActivity.totalAmount > 0.0) initialActivity.totalAmount.toString().removeSuffix(".0") else "")
    }
    var paidText by remember(initialActivity) {
        mutableStateOf(if (initialActivity.amountPaid > 0.0) initialActivity.amountPaid.toString().removeSuffix(".0") else "")
    }
    var paymentStatus by remember(initialActivity) { mutableStateOf(initialActivity.parsedPaymentStatus) }
    var partyName by remember(initialActivity) { mutableStateOf(initialActivity.partyName) }
    var partyRole by remember(initialActivity) { mutableStateOf(initialActivity.parsedPartyRole) }
    var activityDate by remember(initialActivity) { mutableStateOf(initialActivity.activityDate) }
    var notes by remember(initialActivity) { mutableStateOf(initialActivity.notes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isExistingRecord) "Edit Farm Record" else "Review & Confirm Record",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecordType.entries.forEach { type ->
                    FilterChip(
                        selected = recordType == type,
                        onClick = { recordType = type },
                        label = { Text(type.label) }
                    )
                }
            }

            if (recordType == RecordType.CUSTOM) {
                OutlinedTextField(
                    value = customTypeName,
                    onValueChange = { customTypeName = it },
                    label = { Text("Custom Section Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Summary Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("activity_title_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = itemOrCrop,
                    onValueChange = { itemOrCrop = it },
                    label = { Text("Crop / Item") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = plotOrUnit,
                    onValueChange = { plotOrUnit = it },
                    label = { Text("Plot / Pen") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (bags, kg...)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Total ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it },
                    label = { Text("Paid ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentStatus.entries.forEach { st ->
                    FilterChip(
                        selected = paymentStatus == st,
                        onClick = {
                            paymentStatus = st
                            if (st == PaymentStatus.PAID && totalText.isNotBlank()) {
                                paidText = totalText
                            }
                        },
                        label = { Text(st.label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = partyName,
                    onValueChange = { partyName = it },
                    label = { Text("Buyer / Supplier / Staff") },
                    singleLine = true,
                    modifier = Modifier.weight(1.3f)
                )
                OutlinedTextField(
                    value = activityDate,
                    onValueChange = { activityDate = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PartyRole.entries.forEach { role ->
                    FilterChip(
                        selected = partyRole == role,
                        onClick = { partyRole = role },
                        label = { Text(role.label) }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp).testTag("delete_activity_button")
                    ) {
                        Text("Delete")
                    }
                } else {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                }
                Button(
                    onClick = {
                        val qty = quantityText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val total = totalText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val paid = paidText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                        val resolvedStatus = when {
                            total > 0.0 && paid >= total -> PaymentStatus.PAID
                            total > 0.0 && paid > 0.0 -> PaymentStatus.PARTIAL
                            else -> paymentStatus
                        }
                        onSave(
                            initialActivity.copy(
                                recordType = recordType.name,
                                customTypeName = customTypeName.takeIf { recordType == RecordType.CUSTOM && it.isNotBlank() },
                                title = title.ifBlank { itemOrCrop.ifBlank { recordType.label } },
                                itemOrCrop = itemOrCrop,
                                plotOrUnit = plotOrUnit,
                                quantity = qty,
                                unit = unit,
                                totalAmount = total,
                                amountPaid = paid,
                                paymentStatus = resolvedStatus.name,
                                partyName = partyName,
                                partyRole = partyRole.name,
                                activityDate = activityDate,
                                notes = notes
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_activity_button")
                ) {
                    Text(confirmButtonLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryEditorSheet(
    initialMemory: LearnedMemory?,
    currencySymbol: String,
    onSave: (LearnedMemory) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember(initialMemory) { mutableStateOf(initialMemory?.parsedCategory ?: MemoryCategory.CROP_LIVESTOCK) }
    var label by remember(initialMemory) { mutableStateOf(initialMemory?.label.orEmpty()) }
    var detail by remember(initialMemory) { mutableStateOf(initialMemory?.detail.orEmpty()) }
    var unit by remember(initialMemory) { mutableStateOf(initialMemory?.unit.orEmpty()) }
    var priceText by remember(initialMemory) {
        mutableStateOf(initialMemory?.typicalPrice?.let { it.toString().removeSuffix(".0") }.orEmpty())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialMemory == null) "Teach Buddy Something New" else "Edit Farm Memory",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MemoryCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.displayTitle) }
                    )
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name / Label (e.g. Mr. Ade, Maize, Plot B)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("memory_label_input")
            )

            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text("Details") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Typical Price ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete")
                    }
                }
                Button(
                    onClick = {
                        if (label.isNotBlank()) {
                            onSave(
                                (initialMemory ?: LearnedMemory()).copy(
                                    category = category.name,
                                    label = label.trim(),
                                    detail = detail.trim(),
                                    unit = unit.trim().takeIf { it.isNotEmpty() },
                                    typicalPrice = priceText.toDoubleOrNull()?.coerceAtLeast(0.0)
                                )
                            )
                        }
                    },
                    enabled = label.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_memory_button")
                ) {
                    Text("Save Memory")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorSheet(
    profile: FarmProfile,
    onSave: (ownerName: String, farmName: String, currency: String, goal: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ownerName by remember(profile) { mutableStateOf(profile.ownerName) }
    var farmName by remember(profile) { mutableStateOf(profile.farmName) }
    var currency by remember(profile) { mutableStateOf(profile.currencySymbol) }
    var goalNote by remember(profile) { mutableStateOf(profile.dailyGoalNote.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Farm Profile",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Your Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = farmName,
                onValueChange = { farmName = it },
                label = { Text("Farm Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it },
                label = { Text("Currency Symbol (e.g. ₦, $, GHS, KES)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = goalNote,
                onValueChange = { goalNote = it },
                label = { Text("Season Goal") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSave(ownerName, farmName, currency, goalNote) },
                enabled = ownerName.isNotBlank() && farmName.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Save Profile")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
