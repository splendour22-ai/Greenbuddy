package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FarmRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun unwrapFirestoreError(error: Throwable, uid: String, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    fun observeProfile(userId: String): Flow<FarmProfile?> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId"
        emitAll(
            userDoc(userId).snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(FarmProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    unwrapFirestoreError(error, userId, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observeActivities(userId: String): Flow<List<FarmActivity>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/activities"
        emitAll(
            userDoc(userId).collection("activities")
                .orderBy("activityDate", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FarmActivity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<FarmActivity> { it.activityDate }
                                .thenByDescending { it.createdAt?.seconds ?: 0L }
                        )
                }
                .catch { error ->
                    unwrapFirestoreError(error, userId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMemories(userId: String): Flow<List<LearnedMemory>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/learned_memories"
        emitAll(
            userDoc(userId).collection("learned_memories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(LearnedMemory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.label.lowercase() }
                }
                .catch { error ->
                    unwrapFirestoreError(error, userId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeCustomRecordTypes(userId: String): Flow<List<CustomRecordType>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/custom_record_types"
        emitAll(
            userDoc(userId).collection("custom_record_types")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CustomRecordType::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    unwrapFirestoreError(error, userId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeChatMessages(userId: String): Flow<List<ChatMessage>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/chat_messages"
        emitAll(
            userDoc(userId).collection("chat_messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    unwrapFirestoreError(error, userId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun ensureProfileInitialized(defaultOwnerName: String): Result<FarmProfile> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            docPath = ref.path
            val existingSnap = ref.get().await()
            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    FarmProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) return Result.success(existing)
            }
            val newProfile = FarmProfile(
                userId = uid,
                ownerName = defaultOwnerName.trim().ifBlank { "Farmer" }.take(80),
                farmName = "GreenHarvest Farms",
                currencySymbol = "₦",
                onboardingCompleted = false,
                onboardingStep = 0L
            )
            ref.set(newProfile.toCreateMap(uid)).await()
            Result.success(newProfile.copy(id = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        ownerName: String? = null,
        farmName: String? = null,
        currencySymbol: String? = null,
        onboardingCompleted: Boolean? = null,
        onboardingStep: Long? = null,
        dailyGoalNote: String? = null
    ): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            docPath = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            ownerName?.let { updates["ownerName"] = it.trim().ifBlank { "Farmer" }.take(80) }
            farmName?.let { updates["farmName"] = it.trim().ifBlank { "GreenHarvest Farms" }.take(100) }
            currencySymbol?.let { updates["currencySymbol"] = it.trim().ifBlank { "₦" }.take(10) }
            onboardingCompleted?.let { updates["onboardingCompleted"] = it }
            onboardingStep?.let { updates["onboardingStep"] = it.coerceIn(0L, 20L) }
            dailyGoalNote?.let { updates["dailyGoalNote"] = it.trim().take(300) }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addActivity(activity: FarmActivity): Result<String> {
        var docPath = "users/unknown/activities"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("activities").document()
            docPath = docRef.path
            docRef.set(activity.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateActivity(activity: FarmActivity): Result<Unit> {
        var docPath = "users/unknown/activities/${activity.id}"
        return try {
            val uid = requireUserId()
            require(activity.id.isNotBlank()) { "Activity ID must not be blank" }
            val docRef = userDoc(uid).collection("activities").document(activity.id)
            docPath = docRef.path
            docRef.update(activity.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> {
        var docPath = "users/unknown/activities/$activityId"
        return try {
            val uid = requireUserId()
            require(activityId.isNotBlank()) { "Activity ID must not be blank" }
            val docRef = userDoc(uid).collection("activities").document(activityId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMemory(memory: LearnedMemory): Result<String> {
        var docPath = "users/unknown/learned_memories"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("learned_memories").document()
            docPath = docRef.path
            docRef.set(memory.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMemory(memory: LearnedMemory): Result<Unit> {
        var docPath = "users/unknown/learned_memories/${memory.id}"
        return try {
            val uid = requireUserId()
            require(memory.id.isNotBlank()) { "Memory ID must not be blank" }
            val docRef = userDoc(uid).collection("learned_memories").document(memory.id)
            docPath = docRef.path
            docRef.update(memory.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var docPath = "users/unknown/learned_memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank()) { "Memory ID must not be blank" }
            val docRef = userDoc(uid).collection("learned_memories").document(memoryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addCustomRecordType(customType: CustomRecordType): Result<String> {
        var docPath = "users/unknown/custom_record_types"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("custom_record_types").document()
            docPath = docRef.path
            docRef.set(customType.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomRecordType(typeId: String): Result<Unit> {
        var docPath = "users/unknown/custom_record_types/$typeId"
        return try {
            val uid = requireUserId()
            require(typeId.isNotBlank()) { "Custom type ID must not be blank" }
            val docRef = userDoc(uid).collection("custom_record_types").document(typeId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addChatMessage(message: ChatMessage): Result<String> {
        var docPath = "users/unknown/chat_messages"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chat_messages").document()
            docPath = docRef.path
            docRef.set(message.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateChatMessageCard(
        messageId: String,
        linkedActivityId: String? = null,
        cardSummary: String? = null
    ): Result<Unit> {
        var docPath = "users/unknown/chat_messages/$messageId"
        return try {
            val uid = requireUserId()
            require(messageId.isNotBlank()) { "Message ID must not be blank" }
            val docRef = userDoc(uid).collection("chat_messages").document(messageId)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            linkedActivityId?.let { updates["linkedActivityId"] = it.take(128) }
            cardSummary?.let { updates["cardSummary"] = it.take(300) }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
