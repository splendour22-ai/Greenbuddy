package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.LearnedMemory
import com.example.data.formatMoney
import com.example.ui.BuddyScreenState
import com.example.ui.components.MemoryEditorSheet
import com.example.ui.components.ProfileEditorSheet

@Composable
fun MemorySettingsScreen(
    state: BuddyScreenState,
    userEmail: String?,
    onSaveMemory: (LearnedMemory) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onDeleteCustomType: (String) -> Unit,
    onUpdateProfile: (ownerName: String, farmName: String, currency: String, goal: String) -> Unit,
    onRestartOnboarding: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingMemory by remember { mutableStateOf<LearnedMemory?>(null) }
    var showAddMemory by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMemory = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_memory_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add farm memory")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Farm Memory & Settings",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Farm Profile Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.profile.farmName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${state.profile.ownerName} • Currency: ${state.profile.currencySymbol}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showProfileEditor = true }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit farm profile")
                            }
                        }

                        state.profile.dailyGoalNote?.takeIf { it.isNotBlank() }?.let { goal ->
                            Text(
                                text = "Goal: $goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedButton(
                            onClick = onRestartOnboarding,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("restart_onboarding_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.restart_onboarding))
                        }
                    }
                }
            }

            // Learned Business Context
            item {
                Text(
                    text = "What Buddy Remembers (${state.memories.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (state.memories.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.no_memories_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.memories, key = { it.id }) { mem ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingMemory = mem },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = mem.parsedCategory.displayTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = mem.label,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                if (mem.detail.isNotBlank()) {
                                    Text(
                                        text = mem.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                mem.typicalPrice?.let { p ->
                                    val unitStr = mem.unit?.let { " per $it" }.orEmpty()
                                    Text(
                                        text = "Usual price: ${formatMoney(p, state.profile.currencySymbol)}$unitStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete memory",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Custom Record Sections
            if (state.customTypes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Custom Record Sections (${state.customTypes.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                items(state.customTypes, key = { it.id }) { ct ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ct.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                if (ct.fieldNames.isNotEmpty()) {
                                    Text(
                                        text = "Fields: ${ct.fieldNames.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteCustomType(ct.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete custom section",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Account & Sign Out
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Signed in as ${userEmail ?: "Google Account"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.sign_out))
                        }
                    }
                }
            }
        }
    }

    if (showAddMemory || editingMemory != null) {
        MemoryEditorSheet(
            initialMemory = editingMemory,
            currencySymbol = state.profile.currencySymbol,
            onSave = { mem ->
                onSaveMemory(mem)
                showAddMemory = false
                editingMemory = null
            },
            onDelete = editingMemory?.let { existing ->
                {
                    onDeleteMemory(existing.id)
                    editingMemory = null
                }
            },
            onDismiss = {
                showAddMemory = false
                editingMemory = null
            }
        )
    }

    if (showProfileEditor) {
        ProfileEditorSheet(
            profile = state.profile,
            onSave = { owner, farm, cur, goal ->
                onUpdateProfile(owner, farm, cur, goal)
                showProfileEditor = false
            },
            onDismiss = { showProfileEditor = false }
        )
    }
}
