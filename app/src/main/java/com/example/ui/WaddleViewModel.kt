package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StepSensorTracker
import com.example.data.StepTrackerState
import com.example.data.WaddleRepository
import com.example.model.AvatarConfig
import com.example.model.CelebrationEvent
import com.example.model.CelebrationMilestone
import com.example.model.FriendNudge
import com.example.model.Mascot
import com.example.model.NudgeType
import com.example.model.PowerUpType
import com.example.model.Squad
import com.example.model.SquadMember
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LeaderboardPeriod { DAILY, WEEKLY }

@OptIn(ExperimentalCoroutinesApi::class)
class WaddleViewModel(
    private val repository: WaddleRepository,
    private val stepTracker: StepSensorTracker,
    val currentUserId: String,
    private val defaultDisplayName: String
) : ViewModel() {

    val stepState: StateFlow<StepTrackerState> = stepTracker.state

    private val _selectedSquadId = MutableStateFlow<String?>(null)
    val selectedSquadId: StateFlow<String?> = _selectedSquadId.asStateFlow()

    private val _leaderboardPeriod = MutableStateFlow(LeaderboardPeriod.DAILY)
    val leaderboardPeriod: StateFlow<LeaderboardPeriod> = _leaderboardPeriod.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _powerUpRemainingSeconds = MutableStateFlow(0L)
    val powerUpRemainingSeconds: StateFlow<Long> = _powerUpRemainingSeconds.asStateFlow()

    private val _activeCelebration = MutableStateFlow<CelebrationEvent?>(null)
    val activeCelebration: StateFlow<CelebrationEvent?> = _activeCelebration.asStateFlow()

    private val celebrationQueue = ArrayDeque<CelebrationEvent>()

    private var lastSyncedSteps = -1L
    private var lastSyncedBest = -1L
    private var lastSyncedPowerUp: PowerUpType? = null
    private var lastSyncedPowerUpExpires = -1L
    private var lastSyncedSquadIds = emptyList<String>()
    private var cloudMergedOnce = false

    private var celebrationBaselineCaptured = false
    private var celebratedDailyDate: String? = null
    private var celebratedWeeklyKey: String? = null
    private var baselineBestSteps = 0L
    private var lastObservedSteps = -1L

    val profileState: StateFlow<UiState<UserProfile>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile>> { profile ->
                if (profile != null) {
                    if (!cloudMergedOnce) {
                        cloudMergedOnce = true
                        stepTracker.mergeCloudHistory(
                            cloudTodayDate = profile.todayDate,
                            cloudTodaySteps = profile.todaySteps,
                            cloudBestDailySteps = profile.bestDailySteps,
                            cloudHistory = profile.dailyHistory
                        )
                    }
                    UiState.Success(profile)
                } else {
                    UiState.Loading
                }
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing profile", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load profile"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val squadsState: StateFlow<UiState<List<Squad>>> =
        repository.observeMySquads(currentUserId)
            .map<List<Squad>, UiState<List<Squad>>> { squads ->
                if (_selectedSquadId.value == null && squads.isNotEmpty()) {
                    _selectedSquadId.value = squads.first().id
                } else if (_selectedSquadId.value != null && squads.none { it.id == _selectedSquadId.value }) {
                    _selectedSquadId.value = squads.firstOrNull()?.id
                }
                UiState.Success(squads)
            }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing squads", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load squads"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val activeSquad: StateFlow<Squad?> =
        combine(squadsState, _selectedSquadId) { state, selectedId ->
            val squads = (state as? UiState.Success)?.data ?: emptyList()
            squads.firstOrNull { it.id == selectedId } ?: squads.firstOrNull()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val leaderboardState: StateFlow<UiState<List<SquadMember>>> =
        activeSquad
            .flatMapLatest { squad ->
                if (squad == null) {
                    flowOf<UiState<List<SquadMember>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeSquadMembers(squad)
                        .map<List<SquadMember>, UiState<List<SquadMember>>> { members ->
                            UiState.Success(members)
                        }
                        .catch { error ->
                            val firestoreError = unwrapFirestoreError(error)
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing squad members", rootError)
                            emit(UiState.Error(rootError.message ?: "Failed to load leaderboard"))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val nudgesState: StateFlow<UiState<List<FriendNudge>>> =
        repository.observeIncomingNudges(currentUserId)
            .map<List<FriendNudge>, UiState<List<FriendNudge>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing nudges", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load nudges"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            val res = repository.ensureUserProfile(currentUserId, defaultDisplayName)
            res.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not initialize profile")
            }
        }

        // Sync local sensor + manual steps + power-up state to Firestore & all joined squads
        viewModelScope.launch {
            combine(stepTracker.state, profileState, squadsState) { steps, pState, sState ->
                Triple(steps, pState, sState)
            }.collect { (steps, pState, sState) ->
                val profile = (pState as? UiState.Success)?.data ?: return@collect
                val squads = (sState as? UiState.Success)?.data ?: emptyList()
                val squadIds = squads.map { it.id }

                val stepsChanged = steps.totalTodaySteps != lastSyncedSteps
                val bestChanged = steps.bestDailySteps != lastSyncedBest
                val powerUpChanged = steps.activePowerUp != lastSyncedPowerUp ||
                    steps.powerUpExpiresAtMs != lastSyncedPowerUpExpires
                val squadsChanged = squadIds != lastSyncedSquadIds
                if (!stepsChanged && !bestChanged && !powerUpChanged && !squadsChanged) return@collect

                delay(650)
                val latestSteps = stepTracker.state.value
                lastSyncedSteps = latestSteps.totalTodaySteps
                lastSyncedBest = latestSteps.bestDailySteps
                lastSyncedPowerUp = latestSteps.activePowerUp
                lastSyncedPowerUpExpires = latestSteps.powerUpExpiresAtMs
                lastSyncedSquadIds = squadIds

                repository.syncStepsToCloud(
                    userId = currentUserId,
                    profile = profile,
                    todaySteps = latestSteps.totalTodaySteps,
                    todayDate = latestSteps.todayDate,
                    weeklySteps = latestSteps.weeklySteps,
                    weekStartDate = latestSteps.weekStartDate,
                    bestDailySteps = latestSteps.bestDailySteps,
                    activePowerUp = latestSteps.activePowerUp,
                    powerUpExpiresAtMs = latestSteps.powerUpExpiresAtMs,
                    dailyHistory = latestSteps.dailyHistory,
                    squadIds = squadIds
                )
            }
        }

        // Countdown ticker for active power-ups
        viewModelScope.launch {
            while (true) {
                stepTracker.tickPowerUpExpiry()
                val current = stepTracker.state.value
                val remMs = if (current.activePowerUp != null && current.powerUpExpiresAtMs > 0L) {
                    (current.powerUpExpiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                } else 0L
                _powerUpRemainingSeconds.value = (remMs + 999L) / 1000L
                delay(500L)
            }
        }

        // Watch for Daily Goal, Weekly Target, and Personal Best milestones to launch celebrations
        viewModelScope.launch {
            combine(stepTracker.state, profileState) { steps, pState ->
                steps to (pState as? UiState.Success)?.data
            }.collect { (steps, profile) ->
                if (profile == null) return@collect

                if (!celebrationBaselineCaptured) {
                    celebrationBaselineCaptured = true
                    baselineBestSteps = maxOf(profile.bestDailySteps, steps.bestDailySteps, steps.totalTodaySteps)
                    if (steps.totalTodaySteps >= profile.dailyGoal) {
                        celebratedDailyDate = steps.todayDate
                    }
                    if (steps.weeklySteps >= profile.dailyGoal * 7L) {
                        celebratedWeeklyKey = steps.weekStartDate
                    }
                    lastObservedSteps = steps.totalTodaySteps
                    return@collect
                }

                if (steps.totalTodaySteps > lastObservedSteps) {
                    lastObservedSteps = steps.totalTodaySteps
                    val weeklyGoal = profile.dailyGoal * 7L

                    if (steps.weeklySteps >= weeklyGoal && celebratedWeeklyKey != steps.weekStartDate) {
                        celebratedWeeklyKey = steps.weekStartDate
                        enqueueCelebration(
                            CelebrationEvent(CelebrationMilestone.WEEKLY_TARGET, steps.weeklySteps)
                        )
                    }
                    if (steps.totalTodaySteps >= profile.dailyGoal && celebratedDailyDate != steps.todayDate) {
                        celebratedDailyDate = steps.todayDate
                        enqueueCelebration(
                            CelebrationEvent(CelebrationMilestone.DAILY_GOAL, steps.totalTodaySteps)
                        )
                    }
                    if (steps.totalTodaySteps > baselineBestSteps && steps.totalTodaySteps >= 500L) {
                        baselineBestSteps = steps.totalTodaySteps
                        enqueueCelebration(
                            CelebrationEvent(CelebrationMilestone.PERSONAL_BEST, steps.totalTodaySteps)
                        )
                    }
                } else {
                    lastObservedSteps = steps.totalTodaySteps
                    if (steps.totalTodaySteps < profile.dailyGoal && celebratedDailyDate == steps.todayDate) {
                        celebratedDailyDate = null
                    }
                }
            }
        }
    }

    private fun enqueueCelebration(event: CelebrationEvent) {
        if (_activeCelebration.value == null) {
            _activeCelebration.value = event
        } else {
            celebrationQueue.addLast(event)
        }
    }

    fun dismissCelebration() {
        _activeCelebration.value = celebrationQueue.removeFirstOrNull()
    }

    fun replayCelebration() {
        val profile = (profileState.value as? UiState.Success)?.data
        val goal = profile?.dailyGoal ?: 8000L
        val steps = stepTracker.state.value
        val event = when {
            steps.weeklySteps >= goal * 7L ->
                CelebrationEvent(CelebrationMilestone.WEEKLY_TARGET, steps.weeklySteps)
            steps.totalTodaySteps >= goal ->
                CelebrationEvent(CelebrationMilestone.DAILY_GOAL, steps.totalTodaySteps)
            else ->
                CelebrationEvent(
                    CelebrationMilestone.PERSONAL_BEST,
                    maxOf(steps.totalTodaySteps, steps.bestDailySteps, 1000L)
                )
        }
        _activeCelebration.value = event
    }

    fun activatePowerUp(type: PowerUpType) {
        val ok = stepTracker.activatePowerUp(type)
        if (!ok) {
            _events.tryEmit("You're out of ${type.title}! Walk 1,000 steps for a Mystery Crate.")
            return
        }
        val msg = when {
            type.instantBonusSteps > 0 && type.durationMs > 0L ->
                "${type.emoji} ${type.title} unleashed! +${type.instantBonusSteps} steps & ${type.multiplier}x boost!"
            type.instantBonusSteps > 0 ->
                "${type.emoji} ${type.title}! +${type.instantBonusSteps} instant burst steps!"
            else ->
                "${type.emoji} ${type.title} activated! ${type.multiplier}x steps for ${type.durationMs / 60_000} min!"
        }
        _events.tryEmit(msg)
    }

    fun openMysteryCrate() {
        val reward = stepTracker.claimMysteryCrate()
        if (reward != null) {
            _events.tryEmit("🎁 Crate opened! Unlocked ${reward.emoji} ${reward.title}!")
        } else {
            _events.tryEmit("No Mystery Crates ready yet — keep waddling!")
        }
    }

    fun startStepSensor() {
        stepTracker.startListening()
    }

    fun stopStepSensor() {
        stepTracker.stopListening()
    }

    fun logManualWalk(steps: Int, label: String) {
        val active = stepTracker.state.value.activePowerUp
        val mult = active?.multiplier ?: 1
        stepTracker.addManualWalk(steps, label)
        if (mult > 1 && active != null) {
            val boosted = steps * mult
            _events.tryEmit("+$boosted steps logged (${active.emoji} ${mult}x ${active.title}!) for \"$label\"")
        } else {
            _events.tryEmit("+$steps steps logged for \"$label\"!")
        }
    }

    fun selectSquad(squadId: String) {
        _selectedSquadId.value = squadId
    }

    fun setLeaderboardPeriod(period: LeaderboardPeriod) {
        _leaderboardPeriod.value = period
    }

    fun createSquad(name: String, motto: String, onCreated: (Squad) -> Unit = {}) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            _events.tryEmit("Squad name needs at least 2 characters!")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createSquad(currentUserId, profile, cleanName, motto)
            _isBusy.value = false
            result.onSuccess { squad ->
                _selectedSquadId.value = squad.id
                _events.tryEmit("Created squad \"${squad.name}\"! Code: ${squad.inviteCode}")
                onCreated(squad)
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Failed to create squad")
            }
        }
    }

    fun joinSquad(inviteCode: String, onJoined: () -> Unit = {}) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.joinSquadByCode(currentUserId, profile, inviteCode)
            _isBusy.value = false
            result.onSuccess { squadName ->
                _events.tryEmit("Joined \"$squadName\"! Time to out-waddle everyone.")
                onJoined()
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Could not join squad")
            }
        }
    }

    fun leaveSquad(squadId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.leaveSquad(currentUserId, squadId)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Left the squad. Your sneakers remain loyal.")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Failed to leave squad")
            }
        }
    }

    fun sendNudge(recipientId: String, recipientName: String, squadId: String, nudgeType: NudgeType, customMsg: String?) {
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val result = repository.sendNudge(
                senderProfile = profile,
                recipientId = recipientId,
                squadId = squadId,
                nudgeType = nudgeType,
                customMessage = customMsg
            )
            result.onSuccess {
                _events.tryEmit("${nudgeType.emoji} Nudge launched at $recipientName!")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Failed to send nudge")
            }
        }
    }

    fun acknowledgeNudge(nudgeId: String) {
        viewModelScope.launch {
            repository.acknowledgeNudge(currentUserId, nudgeId)
        }
    }

    fun deleteNudge(nudgeId: String) {
        viewModelScope.launch {
            repository.deleteNudge(currentUserId, nudgeId)
        }
    }

    fun updateProfile(
        displayName: String,
        mascot: Mascot,
        avatarConfig: AvatarConfig,
        bio: String,
        dailyGoal: Long
    ) {
        val squadIds = (squadsState.value as? UiState.Success)?.data?.map { it.id } ?: emptyList()
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(
                userId = currentUserId,
                displayName = displayName,
                mascot = mascot,
                avatarConfig = avatarConfig,
                bio = bio,
                dailyGoal = dailyGoal,
                squadIds = squadIds
            )
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Avatar & persona saved! Looking ridiculously athletic.")
            }.onFailure { err ->
                _events.tryEmit(err.message ?: "Failed to save profile")
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "WaddleViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
