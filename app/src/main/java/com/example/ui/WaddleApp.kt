package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.authStateFlow
import com.example.data.AppUpdateChecker
import com.example.data.AppUpdateState
import com.example.data.StepSensorTracker
import com.example.data.WaddleRepository
import com.example.model.AvatarConfig
import com.example.model.Mascot
import com.example.model.UiState
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.CelebrationOverlay
import com.example.ui.components.launchSquadShareSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SquadsScreen
import com.example.ui.screens.TodayScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

object WaddleRoutes {
    const val TODAY = "today"
    const val COMPETE = "compete"
    const val SQUADS = "squads"
    const val PROFILE = "profile"
}

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val waddleDestinations = listOf(
    NavDestination(
        route = WaddleRoutes.TODAY,
        labelRes = R.string.nav_today,
        selectedIcon = Icons.AutoMirrored.Filled.DirectionsWalk,
        unselectedIcon = Icons.AutoMirrored.Outlined.DirectionsWalk
    ),
    NavDestination(
        route = WaddleRoutes.COMPETE,
        labelRes = R.string.nav_compete,
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents
    ),
    NavDestination(
        route = WaddleRoutes.SQUADS,
        labelRes = R.string.nav_squads,
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups
    ),
    NavDestination(
        route = WaddleRoutes.PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val authFlowUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var refreshNonce by remember { mutableIntStateOf(0) }
    val user = remember(authFlowUser, refreshNonce) { authFlowUser ?: auth.currentUser }
    if (user == null) {
        AuthScreen(onAuthSuccess = { refreshNonce++ })
    } else {
        key(user.uid) {
            val defaultName = user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Waddler"
            WaddleMainShell(
                currentUserId = user.uid,
                defaultDisplayName = defaultName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaddleMainShell(
    currentUserId: String,
    defaultDisplayName: String
) {
    val viewModel: WaddleViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = WaddleRepository(db)
                val tracker = StepSensorTracker(app, currentUserId)
                WaddleViewModel(
                    repository = repo,
                    stepTracker = tracker,
                    currentUserId = currentUserId,
                    defaultDisplayName = defaultDisplayName
                )
            }
        }
    )

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val updatesDir = remember(context) { File(context.cacheDir, "updates") }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: WaddleRoutes.TODAY

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val stepState by viewModel.stepState.collectAsStateWithLifecycle()
    val squadsState by viewModel.squadsState.collectAsStateWithLifecycle()
    val activeSquad by viewModel.activeSquad.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val leaderboardPeriod by viewModel.leaderboardPeriod.collectAsStateWithLifecycle()
    val nudgesState by viewModel.nudgesState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val powerUpRemainingSeconds by viewModel.powerUpRemainingSeconds.collectAsStateWithLifecycle()
    val activeCelebration by viewModel.activeCelebration.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()

    val unreadNudgesCount = (nudgesState as? UiState.Success)?.data?.count { !it.acknowledged } ?: 0
    val snackbarHostState = remember { SnackbarHostState() }

    // If user returns from granting "Install unknown apps", immediately auto-launch the installer
    DisposableEffect(lifecycleOwner, updateState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val ready = updateState as? AppUpdateState.ReadyToInstall
                if (ready != null && ready.needsInstallPermission && AppUpdateChecker.canInstallPackages(context)) {
                    viewModel.refreshInstallPermissionState(canInstall = true)
                    AppUpdateChecker.launchApkInstaller(context, File(ready.apkFilePath))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.checkForUpdates(
            manual = false,
            isSkippedTag = { tag -> AppUpdateChecker.isTagSkipped(context, tag) }
        )
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row {
                            Text(
                                text = "🦆 WaddleUp",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        if (updateState is AppUpdateState.UpdateAvailable ||
                            updateState is AppUpdateState.Downloading ||
                            updateState is AppUpdateState.ReadyToInstall
                        ) {
                            IconButton(
                                onClick = viewModel::openUpdateDialog,
                                modifier = Modifier.testTag("topbar_update_available")
                            ) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(
                                        imageVector = Icons.Filled.SystemUpdate,
                                        contentDescription = "App update available",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        val squadToShare = activeSquad
                        if (squadToShare != null) {
                            IconButton(
                                onClick = { launchSquadShareSheet(context, squadToShare) },
                                modifier = Modifier.testTag("topbar_share_squad")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share squad invite"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        waddleDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    NavTabIcon(
                                        dest = dest,
                                        selected = selected,
                                        unreadNudgesCount = unreadNudgesCount
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
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
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        waddleDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    NavTabIcon(
                                        dest = dest,
                                        selected = selected,
                                        unreadNudgesCount = unreadNudgesCount
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = WaddleRoutes.TODAY
                    ) {
                        composable(WaddleRoutes.TODAY) {
                            TodayScreen(
                                profileState = profileState,
                                stepState = stepState,
                                squadsState = squadsState,
                                powerUpRemainingSeconds = powerUpRemainingSeconds,
                                onStartSensor = viewModel::startStepSensor,
                                onStopSensor = viewModel::stopStepSensor,
                                onLogManualWalk = viewModel::logManualWalk,
                                onOpenCrate = viewModel::openMysteryCrate,
                                onActivatePowerUp = viewModel::activatePowerUp,
                                onReplayCelebration = viewModel::replayCelebration,
                                onNavigateToSquads = { navigateToTab(WaddleRoutes.SQUADS) },
                                onNavigateToProfile = { navigateToTab(WaddleRoutes.PROFILE) }
                            )
                        }

                        composable(WaddleRoutes.COMPETE) {
                            LeaderboardScreen(
                                currentUserId = currentUserId,
                                squadsState = squadsState,
                                activeSquad = activeSquad,
                                leaderboardState = leaderboardState,
                                period = leaderboardPeriod,
                                onSelectSquad = viewModel::selectSquad,
                                onSelectPeriod = viewModel::setLeaderboardPeriod,
                                onSendNudge = viewModel::sendNudge,
                                onNavigateToSquads = { navigateToTab(WaddleRoutes.SQUADS) }
                            )
                        }

                        composable(WaddleRoutes.SQUADS) {
                            SquadsScreen(
                                squadsState = squadsState,
                                nudgesState = nudgesState,
                                isBusy = isBusy,
                                onCreateSquad = viewModel::createSquad,
                                onJoinSquad = viewModel::joinSquad,
                                onLeaveSquad = viewModel::leaveSquad,
                                onAcknowledgeNudge = viewModel::acknowledgeNudge,
                                onDeleteNudge = viewModel::deleteNudge,
                                onOpenLeaderboardForSquad = { squadId ->
                                    viewModel.selectSquad(squadId)
                                    navigateToTab(WaddleRoutes.COMPETE)
                                }
                            )
                        }

                        composable(WaddleRoutes.PROFILE) {
                            ProfileScreen(
                                profileState = profileState,
                                stepState = stepState,
                                squadsState = squadsState,
                                isBusy = isBusy,
                                updateState = updateState,
                                onCheckUpdates = {
                                    AppUpdateChecker.clearSkippedTag(context)
                                    viewModel.checkForUpdates(manual = true)
                                },
                                onInstallUpdate = viewModel::openUpdateDialog,
                                onSaveProfile = viewModel::updateProfile
                            )
                        }
                    }
                }
            }
        }

        val currentProfile = (profileState as? UiState.Success)?.data
        val currentMascot = Mascot.fromRaw(currentProfile?.mascot)
        val currentAvatar = AvatarConfig.decode(currentProfile?.avatarCode, currentMascot)
        CelebrationOverlay(
            event = activeCelebration,
            avatarConfig = currentAvatar,
            displayName = currentProfile?.displayName ?: defaultDisplayName,
            onDismiss = viewModel::dismissCelebration
        )

        if (showUpdateDialog) {
            AppUpdateDialog(
                updateState = updateState,
                onStartDownload = { release ->
                    AppUpdateChecker.clearSkippedTag(context)
                    viewModel.downloadUpdate(
                        release = release,
                        updatesDir = updatesDir,
                        canInstallPackages = { AppUpdateChecker.canInstallPackages(context) },
                        onReadyToInstall = { apkFile ->
                            AppUpdateChecker.launchApkInstaller(context, apkFile)
                        }
                    )
                },
                onCancelDownload = viewModel::cancelUpdateDownload,
                onInstallDownloaded = { ready ->
                    if (AppUpdateChecker.canInstallPackages(context)) {
                        viewModel.refreshInstallPermissionState(canInstall = true)
                        AppUpdateChecker.launchApkInstaller(context, File(ready.apkFilePath))
                    } else {
                        AppUpdateChecker.openUnknownSourcesSettings(context)
                    }
                },
                onDismiss = { release ->
                    if (release != null) {
                        AppUpdateChecker.markTagSkipped(context, release.tagName)
                    }
                    viewModel.dismissUpdateDialog()
                }
            )
        }
    }
}

@Composable
private fun NavTabIcon(
    dest: NavDestination,
    selected: Boolean,
    unreadNudgesCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest.route == WaddleRoutes.SQUADS && unreadNudgesCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(unreadNudgesCount.coerceAtMost(99).toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
        }
    } else {
        Icon(imageVector = icon, contentDescription = stringResource(dest.labelRes))
    }
}
