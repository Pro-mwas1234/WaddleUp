package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AvatarConfig
import com.example.model.FriendNudge
import com.example.model.Mascot
import com.example.model.NudgeType
import com.example.model.PowerUpType
import com.example.model.Squad
import com.example.model.SquadMember
import com.example.model.UserProfile
import com.example.model.WaddleHumor
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class WaddleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.GET, "users/$userId")
                throw error
            }

    fun observeMySquads(userId: String): Flow<List<Squad>> =
        db.collection("squads")
            .whereArrayContains("memberIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Squad::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, "squads")
                throw error
            }

    fun observeSquadMembers(squad: Squad): Flow<List<SquadMember>> {
        if (squad.id.isBlank() || squad.memberIds.isEmpty()) {
            return flowOf(emptyList())
        }
        val memberFlows = squad.memberIds.map { memberId ->
            val path = "squads/${squad.id}/members/$memberId"
            db.collection("squads").document(squad.id)
                .collection("members").document(memberId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(SquadMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logAndRethrowIfNeeded(error, OperationType.GET, path)
                    emit(null)
                }
        }
        return combine(memberFlows) { array -> array.filterNotNull() }
    }

    fun observeIncomingNudges(userId: String): Flow<List<FriendNudge>> =
        db.collection("users").document(userId)
            .collection("nudges")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FriendNudge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                logAndRethrowIfNeeded(error, OperationType.LIST, "users/$userId/nudges")
                throw error
            }

    suspend fun ensureUserProfile(userId: String, defaultDisplayName: String): Result<UserProfile> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val safeName = sanitizeDisplayName(defaultDisplayName)
            val today = WaddleHumor.todayDateKey()
            val weekStart = WaddleHumor.weekStartDateKey()
            val defaultAvatar = AvatarConfig.decode(null, Mascot.DUCK).encode()
            val payload = mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "mascot" to Mascot.DUCK.name,
                "avatarCode" to defaultAvatar,
                "bio" to WaddleHumor.funnyExcuses.first(),
                "dailyGoal" to 8000L,
                "todaySteps" to 0L,
                "todayDate" to today,
                "weeklySteps" to 0L,
                "weekStartDate" to weekStart,
                "bestDailySteps" to 0L,
                "activePowerUp" to "",
                "powerUpExpiresAtMs" to 0L,
                "dailyHistory" to mapOf(today to 0L),
                "totalNudgesSent" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val createdSnap = docRef.get().await()
            val profile = createdSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    avatarCode = defaultAvatar,
                    todayDate = today,
                    weekStartDate = weekStart
                )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncStepsToCloud(
        userId: String,
        profile: UserProfile,
        todaySteps: Long,
        todayDate: String,
        weeklySteps: Long,
        weekStartDate: String,
        bestDailySteps: Long,
        activePowerUp: PowerUpType?,
        powerUpExpiresAtMs: Long,
        dailyHistory: Map<String, Long>,
        squadIds: List<String>
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val safeToday = todaySteps.coerceIn(0L, 300_000L)
            val safeWeekly = weeklySteps.coerceIn(0L, 2_100_000L)
            val safeBest = maxOf(bestDailySteps, safeToday).coerceIn(0L, 300_000L)
            val safePowerUpName = activePowerUp?.name ?: ""
            val safeExpires = if (activePowerUp != null) powerUpExpiresAtMs.coerceAtLeast(0L) else 0L
            val safeAvatar = sanitizeAvatarCode(profile.avatarCode, Mascot.fromRaw(profile.mascot))
            val prunedHistory = dailyHistory.entries
                .sortedByDescending { it.key }
                .take(14)
                .associate { it.key to it.value.coerceIn(0L, 300_000L) }

            db.collection("users").document(uid).update(
                mapOf(
                    "todaySteps" to safeToday,
                    "todayDate" to todayDate,
                    "weeklySteps" to safeWeekly,
                    "weekStartDate" to weekStartDate,
                    "bestDailySteps" to safeBest,
                    "activePowerUp" to safePowerUpName,
                    "powerUpExpiresAtMs" to safeExpires,
                    "dailyHistory" to prunedHistory,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            for (squadId in squadIds) {
                if (squadId.isBlank()) continue
                val memberRef = db.collection("squads").document(squadId)
                    .collection("members").document(uid)
                val memberSnap = memberRef.get().await()
                if (memberSnap.exists()) {
                    memberRef.update(
                        mapOf(
                            "displayName" to sanitizeDisplayName(profile.displayName),
                            "mascot" to Mascot.fromRaw(profile.mascot).name,
                            "avatarCode" to safeAvatar,
                            "bio" to sanitizeBio(profile.bio),
                            "dailyGoal" to profile.dailyGoal.coerceIn(1000L, 100_000L),
                            "todaySteps" to safeToday,
                            "todayDate" to todayDate,
                            "weeklySteps" to safeWeekly,
                            "weekStartDate" to weekStartDate,
                            "activePowerUp" to safePowerUpName,
                            "powerUpExpiresAtMs" to safeExpires,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    memberRef.set(
                        mapOf(
                            "userId" to uid,
                            "squadId" to squadId,
                            "displayName" to sanitizeDisplayName(profile.displayName),
                            "mascot" to Mascot.fromRaw(profile.mascot).name,
                            "avatarCode" to safeAvatar,
                            "bio" to sanitizeBio(profile.bio),
                            "dailyGoal" to profile.dailyGoal.coerceIn(1000L, 100_000L),
                            "todaySteps" to safeToday,
                            "todayDate" to todayDate,
                            "weeklySteps" to safeWeekly,
                            "weekStartDate" to weekStartDate,
                            "activePowerUp" to safePowerUpName,
                            "powerUpExpiresAtMs" to safeExpires,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        mascot: Mascot,
        avatarConfig: AvatarConfig,
        bio: String,
        dailyGoal: Long,
        squadIds: List<String>
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanName = sanitizeDisplayName(displayName)
            val cleanBio = sanitizeBio(bio)
            val cleanGoal = dailyGoal.coerceIn(1000L, 100_000L)
            val cleanAvatar = avatarConfig.encode().take(64)

            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "mascot" to mascot.name,
                    "avatarCode" to cleanAvatar,
                    "bio" to cleanBio,
                    "dailyGoal" to cleanGoal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            for (squadId in squadIds) {
                if (squadId.isBlank()) continue
                val memberRef = db.collection("squads").document(squadId)
                    .collection("members").document(uid)
                val snap = memberRef.get().await()
                if (snap.exists()) {
                    memberRef.update(
                        mapOf(
                            "displayName" to cleanName,
                            "mascot" to mascot.name,
                            "avatarCode" to cleanAvatar,
                            "bio" to cleanBio,
                            "dailyGoal" to cleanGoal,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createSquad(
        userId: String,
        profile: UserProfile,
        squadName: String,
        motto: String
    ): Result<Squad> {
        val cleanName = squadName.trim().take(40)
        require(cleanName.length >= 2) { "Squad name must be at least 2 characters." }
        val cleanMotto = motto.trim().take(80)
        val squadRef = db.collection("squads").document()
        val squadId = squadRef.id
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }

            val inviteCode = generateUniqueInviteCode()
            val today = profile.todayDate.ifBlank { WaddleHumor.todayDateKey() }
            val weekStart = profile.weekStartDate.ifBlank { WaddleHumor.weekStartDateKey() }
            val mascot = Mascot.fromRaw(profile.mascot)
            val safeAvatar = sanitizeAvatarCode(profile.avatarCode, mascot)

            val batch = db.batch()
            batch.set(
                squadRef,
                mapOf(
                    "creatorId" to uid,
                    "name" to cleanName,
                    "inviteCode" to inviteCode,
                    "motto" to cleanMotto,
                    "memberIds" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("squad_invites").document(inviteCode),
                mapOf(
                    "squadId" to squadId,
                    "squadName" to cleanName,
                    "creatorId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                squadRef.collection("members").document(uid),
                mapOf(
                    "userId" to uid,
                    "squadId" to squadId,
                    "displayName" to sanitizeDisplayName(profile.displayName),
                    "mascot" to mascot.name,
                    "avatarCode" to safeAvatar,
                    "bio" to sanitizeBio(profile.bio),
                    "dailyGoal" to profile.dailyGoal.coerceIn(1000L, 100_000L),
                    "todaySteps" to profile.todaySteps.coerceIn(0L, 300_000L),
                    "todayDate" to today,
                    "weeklySteps" to profile.weeklySteps.coerceIn(0L, 2_100_000L),
                    "weekStartDate" to weekStart,
                    "activePowerUp" to (PowerUpType.fromRaw(profile.activePowerUp)?.name ?: ""),
                    "powerUpExpiresAtMs" to profile.powerUpExpiresAtMs.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            Result.success(
                Squad(
                    id = squadId,
                    creatorId = uid,
                    name = cleanName,
                    inviteCode = inviteCode,
                    motto = cleanMotto,
                    memberIds = listOf(uid)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "squads/$squadId")
            Result.failure(e)
        }
    }

    suspend fun joinSquadByCode(
        userId: String,
        profile: UserProfile,
        rawCode: String
    ): Result<String> {
        val code = rawCode.trim().uppercase().replace("[^A-Z0-9]".toRegex(), "")
        if (!INVITE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Invite code must be 6 letters/numbers (like DUCK42)."))
        }
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }

            val inviteSnap = db.collection("squad_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalStateException("No squad found with code $code. Double-check with your friend!"))
            }
            val squadId = inviteSnap.getString("squadId")
                ?: return Result.failure(IllegalStateException("Invite record is missing squad ID."))
            val squadName = inviteSnap.getString("squadName") ?: "Squad"

            val squadRef = db.collection("squads").document(squadId)
            val memberRef = squadRef.collection("members").document(uid)
            val today = profile.todayDate.ifBlank { WaddleHumor.todayDateKey() }
            val weekStart = profile.weekStartDate.ifBlank { WaddleHumor.weekStartDateKey() }
            val mascot = Mascot.fromRaw(profile.mascot)
            val safeAvatar = sanitizeAvatarCode(profile.avatarCode, mascot)

            val batch = db.batch()
            batch.update(
                squadRef,
                mapOf(
                    "memberIds" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                memberRef,
                mapOf(
                    "userId" to uid,
                    "squadId" to squadId,
                    "displayName" to sanitizeDisplayName(profile.displayName),
                    "mascot" to mascot.name,
                    "avatarCode" to safeAvatar,
                    "bio" to sanitizeBio(profile.bio),
                    "dailyGoal" to profile.dailyGoal.coerceIn(1000L, 100_000L),
                    "todaySteps" to profile.todaySteps.coerceIn(0L, 300_000L),
                    "todayDate" to today,
                    "weeklySteps" to profile.weeklySteps.coerceIn(0L, 2_100_000L),
                    "weekStartDate" to weekStart,
                    "activePowerUp" to (PowerUpType.fromRaw(profile.activePowerUp)?.name ?: ""),
                    "powerUpExpiresAtMs" to profile.powerUpExpiresAtMs.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(squadName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "squad_invites/$code")
            Result.failure(e)
        }
    }

    suspend fun leaveSquad(userId: String, squadId: String): Result<Unit> {
        val docPath = "squads/$squadId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val squadRef = db.collection("squads").document(squadId)
            val memberRef = squadRef.collection("members").document(uid)
            val batch = db.batch()
            batch.update(
                squadRef,
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.delete(memberRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendNudge(
        senderProfile: UserProfile,
        recipientId: String,
        squadId: String,
        nudgeType: NudgeType,
        customMessage: String? = null
    ): Result<Unit> {
        val nudgeRef = db.collection("users").document(recipientId).collection("nudges").document()
        return try {
            val uid = requireUserId()
            val mascot = Mascot.fromRaw(senderProfile.mascot)
            val safeAvatar = sanitizeAvatarCode(senderProfile.avatarCode, mascot)
            val message = (customMessage?.trim().takeIf { !it.isNullOrEmpty() } ?: nudgeType.defaultMessage).take(120)
            nudgeRef.set(
                mapOf(
                    "senderId" to uid,
                    "senderName" to sanitizeDisplayName(senderProfile.displayName),
                    "senderMascot" to mascot.name,
                    "senderAvatarCode" to safeAvatar,
                    "recipientId" to recipientId,
                    "squadId" to squadId,
                    "message" to message,
                    "nudgeType" to nudgeType.name,
                    "acknowledged" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "totalNudgesSent" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, nudgeRef.path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeNudge(userId: String, nudgeId: String): Result<Unit> {
        val docPath = "users/$userId/nudges/$nudgeId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            db.collection("users").document(uid)
                .collection("nudges").document(nudgeId)
                .update(
                    mapOf(
                        "acknowledged" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNudge(userId: String, nudgeId: String): Result<Unit> {
        val docPath = "users/$userId/nudges/$nudgeId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            db.collection("users").document(uid)
                .collection("nudges").document(nudgeId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun generateUniqueInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(5) {
            val code = (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
            val exists = db.collection("squad_invites").document(code).get().await().exists()
            if (!exists) return code
        }
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun sanitizeDisplayName(name: String): String =
        name.trim().ifBlank { "Waddler" }.take(30)

    private fun sanitizeBio(bio: String): String =
        bio.trim().take(80)

    private fun sanitizeAvatarCode(raw: String, fallbackMascot: Mascot): String =
        AvatarConfig.decode(raw, fallbackMascot).encode().take(64)

    private fun logAndRethrowIfNeeded(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    private companion object {
        val INVITE_REGEX = Regex("^[A-Z0-9]{6}$")
    }
}
