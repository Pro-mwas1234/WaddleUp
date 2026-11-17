package com.example.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.model.PowerUpType
import com.example.model.WaddleHumor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.random.Random

data class StepTrackerState(
    val sensorAvailable: Boolean = false,
    val sensorActive: Boolean = false,
    val sensorName: String = "No step sensor detected",
    val sensorStepsToday: Long = 0L,
    val manualStepsToday: Long = 0L,
    val powerUpBonusStepsToday: Long = 0L,
    val totalTodaySteps: Long = 0L,
    val weeklySteps: Long = 0L,
    val bestDailySteps: Long = 0L,
    val todayDate: String = WaddleHumor.todayDateKey(),
    val weekStartDate: String = WaddleHumor.weekStartDateKey(),
    val dailyHistory: Map<String, Long> = emptyMap(),
    val lastManualWalkNote: String? = null,
    val powerUpInventory: Map<PowerUpType, Int> = emptyMap(),
    val activePowerUp: PowerUpType? = null,
    val powerUpExpiresAtMs: Long = 0L,
    val unclaimedCrates: Int = 0
)

/**
 * Tracks real hardware pedometer steps using Android's SensorManager (TYPE_STEP_COUNTER with
 * TYPE_STEP_DETECTOR fallback), plus manual walk logging, power-up multipliers/bursts,
 * mystery crate drops, and personal best tracking.
 */
class StepSensorTracker(
    context: Context,
    private val userId: String
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val prefs = appContext.getSharedPreferences("waddle_steps_$userId", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<StepTrackerState> = _state.asStateFlow()

    private var isRegistered = false

    private fun loadInitialState(): StepTrackerState {
        val today = WaddleHumor.todayDateKey()
        val weekStart = WaddleHumor.weekStartDateKey()
        val savedDate = prefs.getString(KEY_ACTIVE_DATE, today) ?: today
        val history = loadHistoryMap().toMutableMap()

        // Starter kit: give new players 1 Snail's Pace and 1 Mystery Crate to open!
        val initializedStarter = prefs.getBoolean(KEY_STARTER_GRANTED, false)
        if (!initializedStarter) {
            val starterInv = mapOf(PowerUpType.SNAILS_PACE to 1)
            saveInventory(starterInv)
            prefs.edit()
                .putBoolean(KEY_STARTER_GRANTED, true)
                .putInt(KEY_UNCLAIMED_CRATES, 1)
                .putString(KEY_LAST_FREE_CRATE_DATE, today)
                .apply()
        }

        val sensorSteps: Long
        val manualSteps: Long
        val bonusSteps: Long
        var unclaimed = prefs.getInt(KEY_UNCLAIMED_CRATES, 0).coerceAtLeast(0)

        if (savedDate == today) {
            sensorSteps = prefs.getLong(KEY_SENSOR_TODAY, 0L).coerceAtLeast(0L)
            manualSteps = prefs.getLong(KEY_MANUAL_TODAY, 0L).coerceAtLeast(0L)
            bonusSteps = prefs.getLong(KEY_BONUS_TODAY, 0L).coerceAtLeast(0L)
        } else {
            val prevTotal = (
                prefs.getLong(KEY_SENSOR_TODAY, 0L) +
                    prefs.getLong(KEY_MANUAL_TODAY, 0L) +
                    prefs.getLong(KEY_BONUS_TODAY, 0L)
                ).coerceAtLeast(0L)
            if (prevTotal > 0L) {
                history[savedDate] = maxOf(history[savedDate] ?: 0L, prevTotal)
            }
            val lastFreeDate = prefs.getString(KEY_LAST_FREE_CRATE_DATE, "")
            if (lastFreeDate != today) {
                unclaimed = (unclaimed + 1).coerceAtMost(5)
            }
            prefs.edit()
                .putString(KEY_ACTIVE_DATE, today)
                .putLong(KEY_SENSOR_TODAY, 0L)
                .putLong(KEY_MANUAL_TODAY, 0L)
                .putLong(KEY_BONUS_TODAY, 0L)
                .putLong(KEY_DAY_BASELINE, -1L)
                .putLong(KEY_BEFORE_REBOOT, 0L)
                .putLong(KEY_NEXT_CRATE_THRESHOLD, CRATE_INTERVAL_STEPS)
                .putInt(KEY_UNCLAIMED_CRATES, unclaimed)
                .putString(KEY_LAST_FREE_CRATE_DATE, today)
                .apply()
            sensorSteps = 0L
            manualSteps = 0L
            bonusSteps = 0L
        }

        val totalToday = (sensorSteps + manualSteps + bonusSteps).coerceIn(0L, MAX_DAILY_STEPS)
        val bestDaily = maxOf(
            prefs.getLong(KEY_BEST_DAILY_STEPS, 0L),
            totalToday,
            history.values.maxOrNull() ?: 0L
        ).coerceIn(0L, MAX_DAILY_STEPS)
        prefs.edit().putLong(KEY_BEST_DAILY_STEPS, bestDaily).apply()

        history[today] = totalToday
        val pruned = pruneHistory(history)
        saveHistoryMap(pruned)

        val (activePowerUp, expiresAt) = loadActivePowerUp()

        val available = stepCounterSensor != null || stepDetectorSensor != null
        val sensorLabel = when {
            stepCounterSensor != null -> "Hardware Step Counter"
            stepDetectorSensor != null -> "Hardware Step Detector"
            else -> "No step sensor on this device — log walks manually"
        }

        return StepTrackerState(
            sensorAvailable = available,
            sensorActive = false,
            sensorName = sensorLabel,
            sensorStepsToday = sensorSteps,
            manualStepsToday = manualSteps,
            powerUpBonusStepsToday = bonusSteps,
            totalTodaySteps = totalToday,
            weeklySteps = computeWeeklySteps(pruned),
            bestDailySteps = bestDaily,
            todayDate = today,
            weekStartDate = weekStart,
            dailyHistory = pruned,
            powerUpInventory = loadInventory(),
            activePowerUp = activePowerUp,
            powerUpExpiresAtMs = expiresAt,
            unclaimedCrates = unclaimed
        )
    }

    fun startListening() {
        val sensor = stepCounterSensor ?: stepDetectorSensor
        if (sensor == null) {
            Log.w(TAG, "No step sensor available on device")
            _state.update { it.copy(sensorAvailable = false, sensorActive = false) }
            return
        }
        if (isRegistered) return
        val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        isRegistered = ok
        _state.update { it.copy(sensorAvailable = true, sensorActive = ok) }
        Log.d(TAG, "Step sensor listener registered=$ok (${sensor.name})")
    }

    fun stopListening() {
        if (isRegistered) {
            sensorManager.unregisterListener(this)
            isRegistered = false
            _state.update { it.copy(sensorActive = false) }
        }
    }

    fun mergeCloudHistory(
        cloudTodayDate: String,
        cloudTodaySteps: Long,
        cloudBestDailySteps: Long,
        cloudHistory: Map<String, Long>
    ) {
        val today = WaddleHumor.todayDateKey()
        val merged = loadHistoryMap().toMutableMap()
        cloudHistory.forEach { (dateKey, steps) ->
            val current = merged[dateKey] ?: 0L
            if (steps > current) {
                merged[dateKey] = steps
            }
        }
        val localToday = _state.value.totalTodaySteps
        val updatedBest = maxOf(
            _state.value.bestDailySteps,
            cloudBestDailySteps,
            merged.values.maxOrNull() ?: 0L
        ).coerceIn(0L, MAX_DAILY_STEPS)
        prefs.edit().putLong(KEY_BEST_DAILY_STEPS, updatedBest).apply()

        if (cloudTodayDate == today && cloudTodaySteps > localToday) {
            val delta = cloudTodaySteps - localToday
            val updatedManual = (_state.value.manualStepsToday + delta).coerceAtLeast(0L)
            prefs.edit().putLong(KEY_MANUAL_TODAY, updatedManual).apply()
            merged[today] = cloudTodaySteps
            val pruned = pruneHistory(merged)
            saveHistoryMap(pruned)
            _state.update {
                it.copy(
                    manualStepsToday = updatedManual,
                    totalTodaySteps = cloudTodaySteps.coerceIn(0L, MAX_DAILY_STEPS),
                    weeklySteps = computeWeeklySteps(pruned),
                    bestDailySteps = maxOf(updatedBest, cloudTodaySteps),
                    dailyHistory = pruned
                )
            }
        } else {
            merged[today] = maxOf(merged[today] ?: 0L, localToday)
            val pruned = pruneHistory(merged)
            saveHistoryMap(pruned)
            _state.update {
                it.copy(
                    weeklySteps = computeWeeklySteps(pruned),
                    bestDailySteps = updatedBest,
                    dailyHistory = pruned
                )
            }
        }
    }

    fun addManualWalk(steps: Int, note: String) {
        if (steps <= 0) return
        rolloverDayIfNeeded()
        tickPowerUpExpiry()

        val active = _state.value.activePowerUp
        val multiplier = if (active != null) active.multiplier else 1
        val extraBonus = if (multiplier > 1) steps.toLong() * (multiplier - 1) else 0L

        val today = WaddleHumor.todayDateKey()
        val sensorToday = prefs.getLong(KEY_SENSOR_TODAY, 0L)
        val currentManual = prefs.getLong(KEY_MANUAL_TODAY, 0L)
        val currentBonus = prefs.getLong(KEY_BONUS_TODAY, 0L)

        val newManual = (currentManual + steps.toLong()).coerceAtMost(MAX_DAILY_STEPS)
        val newBonus = (currentBonus + extraBonus).coerceAtMost(MAX_DAILY_STEPS)
        val totalToday = (sensorToday + newManual + newBonus).coerceIn(0L, MAX_DAILY_STEPS)

        val noteWithBoost = if (extraBonus > 0L && active != null) {
            "$note (+$extraBonus ${active.title} bonus!)"
        } else note

        commitStepTotals(
            today = today,
            sensorToday = sensorToday,
            manualToday = newManual,
            bonusToday = newBonus,
            totalToday = totalToday,
            manualNote = noteWithBoost
        )
    }

    fun activatePowerUp(type: PowerUpType): Boolean {
        rolloverDayIfNeeded()
        tickPowerUpExpiry()
        val inventory = loadInventory().toMutableMap()
        val count = inventory[type] ?: 0
        if (count <= 0) return false

        inventory[type] = count - 1
        saveInventory(inventory)

        val now = System.currentTimeMillis()
        val expiresAt = if (type.durationMs > 0L) now + type.durationMs else 0L
        if (expiresAt > 0L) {
            prefs.edit()
                .putString(KEY_ACTIVE_POWERUP, type.name)
                .putLong(KEY_POWERUP_EXPIRES_MS, expiresAt)
                .apply()
        } else {
            prefs.edit()
                .remove(KEY_ACTIVE_POWERUP)
                .putLong(KEY_POWERUP_EXPIRES_MS, 0L)
                .apply()
        }

        val today = WaddleHumor.todayDateKey()
        val sensorToday = prefs.getLong(KEY_SENSOR_TODAY, 0L)
        val manualToday = prefs.getLong(KEY_MANUAL_TODAY, 0L)
        val currentBonus = prefs.getLong(KEY_BONUS_TODAY, 0L)
        val newBonus = (currentBonus + type.instantBonusSteps.toLong()).coerceAtMost(MAX_DAILY_STEPS)
        val totalToday = (sensorToday + manualToday + newBonus).coerceIn(0L, MAX_DAILY_STEPS)

        _state.update {
            it.copy(
                powerUpInventory = inventory,
                activePowerUp = if (expiresAt > 0L) type else null,
                powerUpExpiresAtMs = expiresAt
            )
        }

        commitStepTotals(
            today = today,
            sensorToday = sensorToday,
            manualToday = manualToday,
            bonusToday = newBonus,
            totalToday = totalToday,
            manualNote = null
        )
        return true
    }

    fun claimMysteryCrate(): PowerUpType? {
        val currentCrates = prefs.getInt(KEY_UNCLAIMED_CRATES, 0)
        if (currentCrates <= 0) return null

        val reward = PowerUpType.entries[Random.nextInt(PowerUpType.entries.size)]
        val inventory = loadInventory().toMutableMap()
        inventory[reward] = (inventory[reward] ?: 0) + 1
        saveInventory(inventory)

        val remaining = (currentCrates - 1).coerceAtLeast(0)
        prefs.edit().putInt(KEY_UNCLAIMED_CRATES, remaining).apply()

        _state.update {
            it.copy(
                powerUpInventory = inventory,
                unclaimedCrates = remaining
            )
        }
        return reward
    }

    fun tickPowerUpExpiry() {
        val active = _state.value.activePowerUp ?: return
        val expiresAt = _state.value.powerUpExpiresAtMs
        if (expiresAt in 1..System.currentTimeMillis()) {
            prefs.edit()
                .remove(KEY_ACTIVE_POWERUP)
                .putLong(KEY_POWERUP_EXPIRES_MS, 0L)
                .apply()
            _state.update {
                it.copy(
                    activePowerUp = null,
                    powerUpExpiresAtMs = 0L
                )
            }
            Log.d(TAG, "Power-up expired: ${active.name}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.isEmpty()) return
        rolloverDayIfNeeded()
        tickPowerUpExpiry()
        val today = WaddleHumor.todayDateKey()

        val previousSensorToday = prefs.getLong(KEY_SENSOR_TODAY, 0L).coerceAtLeast(0L)
        val newSensorToday: Long = when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val sinceBoot = event.values[0].toLong().coerceAtLeast(0L)
                val lastBoot = prefs.getLong(KEY_LAST_BOOT_VALUE, -1L)
                var baseline = prefs.getLong(KEY_DAY_BASELINE, -1L)
                var beforeReboot = prefs.getLong(KEY_BEFORE_REBOOT, 0L)

                if (baseline < 0L) {
                    baseline = sinceBoot
                } else if (lastBoot >= 0L && sinceBoot < lastBoot) {
                    beforeReboot += (lastBoot - baseline).coerceAtLeast(0L)
                    baseline = sinceBoot
                }

                prefs.edit()
                    .putLong(KEY_LAST_BOOT_VALUE, sinceBoot)
                    .putLong(KEY_DAY_BASELINE, baseline)
                    .putLong(KEY_BEFORE_REBOOT, beforeReboot)
                    .apply()

                (beforeReboot + (sinceBoot - baseline)).coerceAtLeast(0L)
            }

            Sensor.TYPE_STEP_DETECTOR -> {
                val delta = event.values[0].toLong().coerceAtLeast(1L)
                previousSensorToday + delta
            }

            else -> return
        }

        val deltaSteps = (newSensorToday - previousSensorToday).coerceAtLeast(0L)
        val active = _state.value.activePowerUp
        val extraPowerUpDelta = if (active != null && active.multiplier > 1) {
            deltaSteps * (active.multiplier - 1)
        } else 0L

        val manualToday = prefs.getLong(KEY_MANUAL_TODAY, 0L).coerceAtLeast(0L)
        val bonusToday = (prefs.getLong(KEY_BONUS_TODAY, 0L).coerceAtLeast(0L) + extraPowerUpDelta)
            .coerceAtMost(MAX_DAILY_STEPS)
        val totalToday = (newSensorToday + manualToday + bonusToday).coerceIn(0L, MAX_DAILY_STEPS)

        commitStepTotals(
            today = today,
            sensorToday = newSensorToday,
            manualToday = manualToday,
            bonusToday = bonusToday,
            totalToday = totalToday,
            manualNote = null
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun commitStepTotals(
        today: String,
        sensorToday: Long,
        manualToday: Long,
        bonusToday: Long,
        totalToday: Long,
        manualNote: String?
    ) {
        val history = loadHistoryMap().toMutableMap()
        history[today] = totalToday
        val pruned = pruneHistory(history)

        // Check if user crossed a 1,000-step mystery crate threshold
        var nextThreshold = prefs.getLong(KEY_NEXT_CRATE_THRESHOLD, CRATE_INTERVAL_STEPS)
        var unclaimed = prefs.getInt(KEY_UNCLAIMED_CRATES, 0)
        while (totalToday >= nextThreshold && unclaimed < 5) {
            unclaimed++
            nextThreshold += CRATE_INTERVAL_STEPS
        }

        val bestDaily = maxOf(prefs.getLong(KEY_BEST_DAILY_STEPS, 0L), totalToday)
            .coerceIn(0L, MAX_DAILY_STEPS)

        prefs.edit()
            .putString(KEY_ACTIVE_DATE, today)
            .putLong(KEY_SENSOR_TODAY, sensorToday)
            .putLong(KEY_MANUAL_TODAY, manualToday)
            .putLong(KEY_BONUS_TODAY, bonusToday)
            .putLong(KEY_BEST_DAILY_STEPS, bestDaily)
            .putLong(KEY_NEXT_CRATE_THRESHOLD, nextThreshold)
            .putInt(KEY_UNCLAIMED_CRATES, unclaimed)
            .apply()
        saveHistoryMap(pruned)

        _state.update {
            it.copy(
                sensorStepsToday = sensorToday,
                manualStepsToday = manualToday,
                powerUpBonusStepsToday = bonusToday,
                totalTodaySteps = totalToday,
                weeklySteps = computeWeeklySteps(pruned),
                bestDailySteps = bestDaily,
                todayDate = today,
                weekStartDate = WaddleHumor.weekStartDateKey(),
                dailyHistory = pruned,
                lastManualWalkNote = manualNote ?: it.lastManualWalkNote,
                unclaimedCrates = unclaimed
            )
        }
    }

    private fun rolloverDayIfNeeded() {
        val today = WaddleHumor.todayDateKey()
        val savedDate = prefs.getString(KEY_ACTIVE_DATE, today) ?: today
        if (savedDate != today) {
            val prevTotal = (
                prefs.getLong(KEY_SENSOR_TODAY, 0L) +
                    prefs.getLong(KEY_MANUAL_TODAY, 0L) +
                    prefs.getLong(KEY_BONUS_TODAY, 0L)
                ).coerceAtLeast(0L)
            val history = loadHistoryMap().toMutableMap()
            if (prevTotal > 0L) {
                history[savedDate] = maxOf(history[savedDate] ?: 0L, prevTotal)
            }
            history[today] = 0L
            val pruned = pruneHistory(history)
            saveHistoryMap(pruned)

            var unclaimed = prefs.getInt(KEY_UNCLAIMED_CRATES, 0)
            if (prefs.getString(KEY_LAST_FREE_CRATE_DATE, "") != today) {
                unclaimed = (unclaimed + 1).coerceAtMost(5)
            }

            val lastBoot = prefs.getLong(KEY_LAST_BOOT_VALUE, -1L)
            prefs.edit()
                .putString(KEY_ACTIVE_DATE, today)
                .putLong(KEY_SENSOR_TODAY, 0L)
                .putLong(KEY_MANUAL_TODAY, 0L)
                .putLong(KEY_BONUS_TODAY, 0L)
                .putLong(KEY_DAY_BASELINE, lastBoot)
                .putLong(KEY_BEFORE_REBOOT, 0L)
                .putLong(KEY_NEXT_CRATE_THRESHOLD, CRATE_INTERVAL_STEPS)
                .putInt(KEY_UNCLAIMED_CRATES, unclaimed)
                .putString(KEY_LAST_FREE_CRATE_DATE, today)
                .apply()

            _state.update {
                it.copy(
                    sensorStepsToday = 0L,
                    manualStepsToday = 0L,
                    powerUpBonusStepsToday = 0L,
                    totalTodaySteps = 0L,
                    weeklySteps = computeWeeklySteps(pruned),
                    todayDate = today,
                    weekStartDate = WaddleHumor.weekStartDateKey(),
                    dailyHistory = pruned,
                    unclaimedCrates = unclaimed
                )
            }
        }
    }

    private fun loadActivePowerUp(): Pair<PowerUpType?, Long> {
        val raw = prefs.getString(KEY_ACTIVE_POWERUP, null)
        val expires = prefs.getLong(KEY_POWERUP_EXPIRES_MS, 0L)
        val type = PowerUpType.fromRaw(raw)
        if (type == null || expires <= System.currentTimeMillis()) {
            prefs.edit().remove(KEY_ACTIVE_POWERUP).putLong(KEY_POWERUP_EXPIRES_MS, 0L).apply()
            return null to 0L
        }
        return type to expires
    }

    private fun loadInventory(): Map<PowerUpType, Int> {
        val raw = prefs.getString(KEY_INVENTORY_JSON, null)
        val json = try {
            if (raw != null) JSONObject(raw) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        return PowerUpType.entries.associateWith { type ->
            json.optInt(type.name, 0).coerceAtLeast(0)
        }
    }

    private fun saveInventory(inv: Map<PowerUpType, Int>) {
        val json = JSONObject()
        inv.forEach { (k, v) -> json.put(k.name, v.coerceAtLeast(0)) }
        prefs.edit().putString(KEY_INVENTORY_JSON, json.toString()).apply()
    }

    private fun computeWeeklySteps(history: Map<String, Long>): Long {
        val weekKeys = WaddleHumor.currentWeekDateKeys()
        return history.filterKeys { it in weekKeys }.values.sum().coerceIn(0L, 2_100_000L)
    }

    private fun pruneHistory(history: Map<String, Long>): Map<String, Long> {
        if (history.size <= 14) return history
        return history.entries
            .sortedByDescending { it.key }
            .take(14)
            .associate { it.key to it.value }
    }

    private fun loadHistoryMap(): Map<String, Long> {
        val raw = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, json.optLong(k, 0L).coerceAtLeast(0L))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveHistoryMap(history: Map<String, Long>) {
        val json = JSONObject()
        history.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_HISTORY_JSON, json.toString()).apply()
    }

    private companion object {
        const val TAG = "StepSensorTracker"
        const val KEY_ACTIVE_DATE = "active_date"
        const val KEY_SENSOR_TODAY = "sensor_today"
        const val KEY_MANUAL_TODAY = "manual_today"
        const val KEY_BONUS_TODAY = "bonus_today"
        const val KEY_BEST_DAILY_STEPS = "best_daily_steps"
        const val KEY_DAY_BASELINE = "day_baseline"
        const val KEY_LAST_BOOT_VALUE = "last_boot_value"
        const val KEY_BEFORE_REBOOT = "before_reboot"
        const val KEY_HISTORY_JSON = "daily_history_json"
        const val KEY_INVENTORY_JSON = "powerup_inventory_json"
        const val KEY_ACTIVE_POWERUP = "active_powerup"
        const val KEY_POWERUP_EXPIRES_MS = "powerup_expires_ms"
        const val KEY_UNCLAIMED_CRATES = "unclaimed_crates"
        const val KEY_NEXT_CRATE_THRESHOLD = "next_crate_threshold"
        const val KEY_LAST_FREE_CRATE_DATE = "last_free_crate_date"
        const val KEY_STARTER_GRANTED = "starter_granted"
        const val CRATE_INTERVAL_STEPS = 1000L
        const val MAX_DAILY_STEPS = 300_000L
    }
}
