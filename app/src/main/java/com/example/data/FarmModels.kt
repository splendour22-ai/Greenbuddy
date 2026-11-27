package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.NumberFormat
import java.util.Locale

enum class RecordType(val label: String) {
    SALE("Sale"),
    HARVEST("Harvest"),
    EXPENSE("Expense"),
    LABOR("Labor"),
    CONSULTATION("Consultation"),
    CUSTOM("Custom");

    companion object {
        fun fromRaw(raw: String?): RecordType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EXPENSE
    }
}

enum class PaymentStatus(val label: String) {
    PAID("Paid"),
    PARTIAL("Partial"),
    UNPAID("Unpaid"),
    NA("N/A");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NA
    }
}

enum class PartyRole(val label: String) {
    CUSTOMER("Customer"),
    SUPPLIER("Supplier"),
    WORKER("Worker"),
    CLIENT("Client"),
    NONE("None");

    companion object {
        fun fromRaw(raw: String?): PartyRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

enum class MemoryCategory(val displayTitle: String) {
    CROP_LIVESTOCK("Crops & Livestock"),
    PLOT_PEN("Plots, Ponds & Pens"),
    CUSTOMER("Regular Customers"),
    SUPPLIER("Suppliers"),
    PRICE_UNIT("Typical Prices & Units"),
    WORKER("Workers & Staff"),
    SERVICE("Consultation Services"),
    GOAL("Farm Goals"),
    REMINDER("Patterns & Reminders");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GOAL
    }
}

enum class ChatSender {
    USER,
    BUDDY;

    companion object {
        fun fromRaw(raw: String?): ChatSender =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: BUDDY
    }
}

@Keep
@IgnoreExtraProperties
data class FarmProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerName: String = "Farmer",
    val farmName: String = "GreenHarvest Farms",
    val currencySymbol: String = "₦",
    val onboardingCompleted: Boolean = false,
    val onboardingStep: Long = 0L,
    val dailyGoalNote: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "ownerName" to ownerName.trim().ifBlank { "Farmer" }.take(80),
            "farmName" to farmName.trim().ifBlank { "GreenHarvest Farms" }.take(100),
            "currencySymbol" to currencySymbol.trim().ifBlank { "₦" }.take(10),
            "onboardingCompleted" to onboardingCompleted,
            "onboardingStep" to onboardingStep.coerceIn(0L, 20L),
            "dailyGoalNote" to dailyGoalNote?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class FarmActivity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordType: String = RecordType.SALE.name,
    val customTypeName: String? = null,
    val title: String = "",
    val itemOrCrop: String = "",
    val plotOrUnit: String = "",
    val quantity: Double = 0.0,
    val unit: String = "",
    val totalAmount: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paymentStatus: String = PaymentStatus.NA.name,
    val partyName: String = "",
    val partyRole: String = PartyRole.NONE.name,
    val activityDate: String = "",
    val notes: String = "",
    val extraFields: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedRecordType: RecordType get() = RecordType.fromRaw(recordType)
    val parsedPaymentStatus: PaymentStatus get() = PaymentStatus.fromRaw(paymentStatus)
    val parsedPartyRole: PartyRole get() = PartyRole.fromRaw(partyRole)
    val balanceDue: Double get() = (totalAmount - amountPaid).coerceAtLeast(0.0)

    fun toCreateMap(uid: String): Map<String, Any> {
        val safeType = parsedRecordType.name
        val safeStatus = parsedPaymentStatus.name
        val safeRole = parsedPartyRole.name
        val raw = mapOf(
            "userId" to uid,
            "recordType" to safeType,
            "customTypeName" to customTypeName?.trim()?.takeIf { it.isNotEmpty() && safeType == RecordType.CUSTOM.name }?.take(60),
            "title" to title.trim().ifBlank { itemOrCrop.ifBlank { safeType } }.take(140),
            "itemOrCrop" to itemOrCrop.trim().take(100),
            "plotOrUnit" to plotOrUnit.trim().take(100),
            "quantity" to quantity.coerceAtLeast(0.0),
            "unit" to unit.trim().take(40),
            "totalAmount" to totalAmount.coerceAtLeast(0.0),
            "amountPaid" to amountPaid.coerceAtLeast(0.0),
            "paymentStatus" to safeStatus,
            "partyName" to partyName.trim().take(100),
            "partyRole" to safeRole,
            "activityDate" to activityDate.trim().ifBlank { "2026-11-27" }.take(32),
            "notes" to notes.trim().take(500),
            "extraFields" to extraFields.map { it.trim().take(120) }.filter { it.isNotEmpty() }.take(15),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val safeType = parsedRecordType.name
        val raw = mapOf(
            "recordType" to safeType,
            "customTypeName" to customTypeName?.trim()?.takeIf { it.isNotEmpty() && safeType == RecordType.CUSTOM.name }?.take(60),
            "title" to title.trim().ifBlank { itemOrCrop.ifBlank { safeType } }.take(140),
            "itemOrCrop" to itemOrCrop.trim().take(100),
            "plotOrUnit" to plotOrUnit.trim().take(100),
            "quantity" to quantity.coerceAtLeast(0.0),
            "unit" to unit.trim().take(40),
            "totalAmount" to totalAmount.coerceAtLeast(0.0),
            "amountPaid" to amountPaid.coerceAtLeast(0.0),
            "paymentStatus" to parsedPaymentStatus.name,
            "partyName" to partyName.trim().take(100),
            "partyRole" to parsedPartyRole.name,
            "activityDate" to activityDate.trim().ifBlank { "2026-11-27" }.take(32),
            "notes" to notes.trim().take(500),
            "extraFields" to extraFields.map { it.trim().take(120) }.filter { it.isNotEmpty() }.take(15),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class LearnedMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = MemoryCategory.CROP_LIVESTOCK.name,
    val label: String = "",
    val detail: String = "",
    val unit: String? = null,
    val typicalPrice: Double? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedCategory: MemoryCategory get() = MemoryCategory.fromRaw(category)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "category" to parsedCategory.name,
            "label" to label.trim().ifBlank { "Note" }.take(100),
            "detail" to detail.trim().take(300),
            "unit" to unit?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
            "typicalPrice" to typicalPrice?.coerceAtLeast(0.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val raw = mapOf(
            "category" to parsedCategory.name,
            "label" to label.trim().ifBlank { "Note" }.take(100),
            "detail" to detail.trim().take(300),
            "unit" to unit?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
            "typicalPrice" to typicalPrice?.coerceAtLeast(0.0),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class CustomRecordType(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val fieldNames: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "name" to name.trim().ifBlank { "Custom Record" }.take(60),
            "description" to description.trim().take(200),
            "fieldNames" to fieldNames.map { it.trim().take(50) }.filter { it.isNotEmpty() }.take(15),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = ChatSender.BUDDY.name,
    val text: String = "",
    val linkedActivityId: String? = null,
    val cardSummary: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val parsedSender: ChatSender get() = ChatSender.fromRaw(sender)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "sender" to parsedSender.name,
            "text" to text.trim().ifBlank { "..." }.take(4000),
            "linkedActivityId" to linkedActivityId?.trim()?.takeIf { it.isNotEmpty() }?.take(128),
            "cardSummary" to cardSummary?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

// --- Non-persisted Interactive UI & AI Draft Models ---

data class ProposedActivityDraft(
    val draftId: String,
    val activity: FarmActivity,
    val status: ProposalStatus = ProposalStatus.PENDING,
    val savedActivityId: String? = null
)

data class ProposedMemoryDraft(
    val draftId: String,
    val memories: List<LearnedMemory>,
    val markOnboardingComplete: Boolean = false,
    val updatedOwnerName: String? = null,
    val updatedFarmName: String? = null,
    val updatedCurrency: String? = null,
    val updatedGoalNote: String? = null,
    val status: ProposalStatus = ProposalStatus.PENDING
)

data class ProposedCustomTypeDraft(
    val draftId: String,
    val customType: CustomRecordType,
    val initialActivity: FarmActivity? = null,
    val status: ProposalStatus = ProposalStatus.PENDING
)

data class ChartPoint(
    val label: String,
    val value: Double,
    val subtitle: String = ""
)

data class ChartCardData(
    val title: String,
    val chartType: String = "BAR",
    val points: List<ChartPoint>
)

enum class ProposalStatus {
    PENDING,
    CONFIRMED,
    DISMISSED
}

data class FarmAnalyticsSummary(
    val totalRevenue: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val totalUnpaidReceivables: Double,
    val totalHarvestUnits: Map<String, Double>,
    val cropRevenueMap: Map<String, Double>,
    val debtorBalances: Map<String, Double>,
    val monthlyNetMap: Map<String, Pair<Double, Double>>,
    val activityCount: Int
)

fun formatMoney(amount: Double, currencySymbol: String): String {
    val rounded = kotlin.math.round(amount).toLong()
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(rounded)
    return "$currencySymbol$formatted"
}

fun formatQuantity(qty: Double, unit: String): String {
    val cleanQty = if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)
    return if (unit.isBlank()) cleanQty else "$cleanQty $unit"
}
