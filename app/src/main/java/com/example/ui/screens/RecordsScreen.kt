package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FarmActivity
import com.example.data.PaymentStatus
import com.example.data.RecordType
import com.example.data.formatMoney
import com.example.data.formatQuantity
import com.example.ui.BuddyScreenState
import com.example.ui.components.ActivityEditorSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(
    state: BuddyScreenState,
    onSaveActivity: (FarmActivity) -> Unit,
    onMarkPaid: (FarmActivity) -> Unit,
    onDeleteActivity: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var editingActivity by remember { mutableStateOf<FarmActivity?>(null) }

    val filteredActivities = remember(state.activities, searchQuery, selectedFilter) {
        state.activities.filter { act ->
            val matchesFilter = when (selectedFilter) {
                "ALL" -> true
                "UNPAID" -> act.balanceDue > 0.0
                else -> act.recordType == selectedFilter || act.customTypeName == selectedFilter
            }
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                act.title.lowercase().contains(q) ||
                act.itemOrCrop.lowercase().contains(q) ||
                act.partyName.lowercase().contains(q) ||
                act.plotOrUnit.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    editingActivity = FarmActivity(activityDate = today, paymentStatus = PaymentStatus.PAID.name)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_record_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add farm record")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Farm Ledger",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // KPI Summary Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMiniCard(
                    title = "Revenue",
                    value = formatMoney(state.analytics.totalRevenue, state.profile.currencySymbol),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "Expenses",
                    value = formatMoney(state.analytics.totalExpenses, state.profile.currencySymbol),
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "Owed",
                    value = formatMoney(state.analytics.totalUnpaidReceivables, state.profile.currencySymbol),
                    highlight = state.analytics.totalUnpaidReceivables > 0.0,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search crop, buyer, or plot...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("records_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val baseFilters = listOf(
                    "ALL" to "All",
                    RecordType.SALE.name to "Sales",
                    RecordType.HARVEST.name to "Harvests",
                    RecordType.EXPENSE.name to "Expenses",
                    "UNPAID" to "Owed to Me"
                )
                baseFilters.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
                state.customTypes.forEach { ct ->
                    FilterChip(
                        selected = selectedFilter == ct.name,
                        onClick = { selectedFilter = ct.name },
                        label = { Text(ct.name) }
                    )
                }
            }

            if (filteredActivities.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.no_records_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredActivities, key = { it.id }) { act ->
                        ActivityLedgerCard(
                            activity = act,
                            currencySymbol = state.profile.currencySymbol,
                            onClick = { editingActivity = act },
                            onMarkPaid = { onMarkPaid(act) }
                        )
                    }
                }
            }
        }
    }

    editingActivity?.let { act ->
        ActivityEditorSheet(
            initialActivity = act,
            currencySymbol = state.profile.currencySymbol,
            isExistingRecord = act.id.isNotBlank(),
            confirmButtonLabel = if (act.id.isBlank()) "Add Record" else "Save Changes",
            onSave = { updated ->
                onSaveActivity(updated)
                editingActivity = null
            },
            onDelete = if (act.id.isNotBlank()) {
                {
                    onDeleteActivity(act.id)
                    editingActivity = null
                }
            } else null,
            onDismiss = { editingActivity = null }
        )
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActivityLedgerCard(
    activity: FarmActivity,
    currencySymbol: String,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = activity.customTypeName ?: activity.parsedRecordType.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = activity.activityDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (activity.totalAmount > 0.0) {
                    Text(
                        text = formatMoney(activity.totalAmount, currencySymbol),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val metaParts = buildList {
                if (activity.quantity > 0.0) add(formatQuantity(activity.quantity, activity.unit))
                if (activity.plotOrUnit.isNotBlank()) add(activity.plotOrUnit)
                if (activity.partyName.isNotBlank()) add(activity.partyName)
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activity.balanceDue > 0.0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Balance due: ${formatMoney(activity.balanceDue, currencySymbol)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    OutlinedButton(
                        onClick = onMarkPaid,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Mark Paid", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
