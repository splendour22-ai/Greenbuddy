package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.BuddyAiService
import com.example.data.FarmRepository
import com.example.data.configureAppCheck
import com.example.ui.BuddyViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.screens.BuddyChatScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.MemorySettingsScreen
import com.example.ui.screens.RecordsScreen
import com.example.ui.theme.GreenHarvestTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AppTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    BUDDY(R.string.nav_buddy, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_buddy"),
    RECORDS(R.string.nav_records, Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, "tab_records"),
    INSIGHTS(R.string.nav_insights, Icons.Rounded.BarChart, Icons.Outlined.BarChart, "tab_insights"),
    MEMORY(R.string.nav_memory, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "tab_memory")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            GreenHarvestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var checkingAutoSignIn by remember { mutableStateOf(auth.currentUser == null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { checkingAutoSignIn = false },
                onUnauthenticated = { checkingAutoSignIn = false },
                scope = scope
            )
        } else {
            checkingAutoSignIn = false
        }
    }

    val user = currentUser
    when {
        checkingAutoSignIn && user == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        user == null -> {
            AuthScreen(onAuthSuccess = { checkingAutoSignIn = false })
        }
        else -> {
            val uid = user.uid
            key(uid) {
                AuthenticatedFarmApp(
                    currentUserId = uid,
                    ownerDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Farmer",
                    userEmail = user.email,
                    onSignOut = {
                        signOut(credentialManager = credentialManager, scope = scope)
                    }
                )
            }
        }
    }
}

@Composable
fun AuthenticatedFarmApp(
    currentUserId: String,
    ownerDisplayName: String,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val viewModel: BuddyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                BuddyViewModel(
                    repository = FarmRepository(db),
                    aiService = BuddyAiService(),
                    currentUserId = currentUserId,
                    defaultOwnerName = ownerDisplayName
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.BUDDY) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = selectedTab != AppTab.BUDDY) {
        selectedTab = AppTab.BUDDY
    }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentMod = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)

                    when (selectedTab) {
                        AppTab.BUDDY -> BuddyChatScreen(
                            state = state,
                            onSendMessage = viewModel::sendUserTextMessage,
                            onSendVoiceAudio = viewModel::sendVoiceAudioClip,
                            onConfirmActivity = viewModel::confirmActivityDraft,
                            onDismissActivity = viewModel::dismissActivityDraft,
                            onConfirmMemory = viewModel::confirmMemoryDraft,
                            onDismissMemory = viewModel::dismissMemoryDraft,
                            onApproveCustomType = viewModel::approveCustomTypeDraft,
                            onDismissCustomType = viewModel::dismissCustomTypeDraft,
                            onEditSavedActivity = viewModel::saveOrUpdateActivity,
                            onDeleteSavedActivity = viewModel::deleteActivity,
                            onNavigateToInsights = { selectedTab = AppTab.INSIGHTS },
                            onShowNotice = viewModel::showNotice,
                            modifier = contentMod
                        )
                        AppTab.RECORDS -> RecordsScreen(
                            state = state,
                            onSaveActivity = viewModel::saveOrUpdateActivity,
                            onMarkPaid = viewModel::markActivityPaid,
                            onDeleteActivity = viewModel::deleteActivity,
                            modifier = contentMod
                        )
                        AppTab.INSIGHTS -> InsightsScreen(
                            state = state,
                            onAskBuddy = { question ->
                                selectedTab = AppTab.BUDDY
                                viewModel.sendUserTextMessage(question)
                            },
                            onMarkPaid = viewModel::markActivityPaid,
                            modifier = contentMod
                        )
                        AppTab.MEMORY -> MemorySettingsScreen(
                            state = state,
                            userEmail = userEmail,
                            onSaveMemory = viewModel::saveOrUpdateMemory,
                            onDeleteMemory = viewModel::deleteMemory,
                            onDeleteCustomType = viewModel::deleteCustomRecordType,
                            onUpdateProfile = viewModel::updateProfile,
                            onRestartOnboarding = {
                                viewModel.restartOnboarding()
                                selectedTab = AppTab.BUDDY
                            },
                            onSignOut = onSignOut,
                            modifier = contentMod
                        )
                    }
                }
            }
        }
    }
}
