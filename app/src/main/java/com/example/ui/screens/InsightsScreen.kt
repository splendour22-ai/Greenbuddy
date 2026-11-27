package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ChartCardData
import com.example.data.ChartPoint
import com.example.data.FarmActivity
import com.example.data.MemoryCategory
import com.example.data.formatMoney
import com.example.ui.BuddyScreenState
import com.example.ui.components.FarmBarChartCard

@Composable
fun InsightsScreen(
    state: BuddyScreenState,
    onAskBuddy: (String) -> Unit,
    onMarkPaid: (FarmActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = state.profile.currencySymbol
    val analytics = state.analytics
    val unpaidActivities = state.activities.filter { it.balanceDue > 0.0 }
    val reminders = state.memories.filter { it.parsedCategory == MemoryCategory.REMINDER }

    val topCropChart = if (analytics.cropRevenueMap.isNotEmpty()) {
        ChartCardData(
            title = "Top Earning Crops & Items",
            chartType = "BAR",
            points = analytics.cropRevenueMap.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { ChartPoint(label = it.key, value = it.value) }
        )
    } else null

    val cashflowChart = ChartCardData(
        title = "Overall Farm Cashflow",
        chartType = "BAR",
        points = listOf(
            ChartPoint("Revenue", analytics.totalRevenue),
            ChartPoint("Expenses", analytics.totalExpenses),
            ChartPoint("Net Profit", analytics.netProfit.coerceAtLeast(0.0)),
            ChartPoint("Owed", analytics.totalUnpaidReceivables)
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Farm Insights",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Net Farm Profit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = formatMoney(analytics.netProfit, cur),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Across ${analytics.activityCount} recorded farm activities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            FarmBarChartCard(
                chartData = cashflowChart,
                currencySymbol = cur
            )
        }

        topCropChart?.let { chart ->
            item {
                FarmBarChartCard(
                    chartData = chart,
                    currencySymbol = cur
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Who Owes Me Money",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedButton(
                    onClick = { onAskBuddy("Who owes me money and how much?") },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Ask Buddy")
                }
            }
        }

        if (unpaidActivities.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Everyone is all settled up! No unpaid balances right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(unpaidActivities, key = { "unpaid_${it.id}" }) { act ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = act.partyName.ifBlank { "Customer" },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${act.title} • ${act.activityDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Owes ${formatMoney(act.balanceDue, cur)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Button(
                            onClick = { onMarkPaid(act) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Mark Paid")
                        }
                    }
                }
            }
        }

        if (reminders.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Patterns & Reminders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(reminders, key = { it.id }) { rem ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(rem.label, style = MaterialTheme.typography.titleSmall)
                            Text(rem.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
