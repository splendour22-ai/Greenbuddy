package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BuddyAiService"

data class BuddyAiResult(
    val userTranscript: String? = null,
    val replyText: String,
    val intentType: String,
    val proposedActivity: FarmActivity? = null,
    val proposedMemories: List<LearnedMemory> = emptyList(),
    val proposedCustomType: CustomRecordType? = null,
    val markOnboardingComplete: Boolean = false,
    val nextOnboardingStep: Long? = null,
    val updatedOwnerName: String? = null,
    val updatedFarmName: String? = null,
    val updatedCurrency: String? = null,
    val updatedGoalNote: String? = null,
    val chartData: ChartCardData? = null,
    val correctionAction: String = "NONE",
    val correctionTargetId: String? = null,
    val quickReplies: List<String> = emptyList()
)

class BuddyAiService {

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "userTranscript" to Schema.string(
                description = "If audio input was provided, the verbatim short transcription of what the farmer said; otherwise omit.",
                nullable = true
            ),
            "replyText" to Schema.string(
                description = "Warm, friendly, short conversational reply from GreenHarvest Buddy in the farmer's language/style (English or mixed English/Pidgin)."
            ),
            "intentType" to Schema.enumeration(
                values = listOf(
                    "CHAT",
                    "RECORD_ACTIVITY",
                    "NEED_CLARIFICATION",
                    "LEARN_CONTEXT",
                    "PROPOSE_CUSTOM_TYPE",
                    "DATA_QUERY",
                    "UPDATE_CORRECTION",
                    "ONBOARDING_SUMMARY"
                ),
                description = "Primary intent of this turn."
            ),
            "proposedActivity" to Schema.obj(
                properties = mapOf(
                    "recordType" to Schema.enumeration(
                        values = listOf("SALE", "HARVEST", "EXPENSE", "LABOR", "CONSULTATION", "CUSTOM")
                    ),
                    "customTypeName" to Schema.string(nullable = true),
                    "title" to Schema.string(),
                    "itemOrCrop" to Schema.string(),
                    "plotOrUnit" to Schema.string(),
                    "quantity" to Schema.double(),
                    "unit" to Schema.string(),
                    "totalAmount" to Schema.double(),
                    "amountPaid" to Schema.double(),
                    "paymentStatus" to Schema.enumeration(
                        values = listOf("PAID", "PARTIAL", "UNPAID", "NA")
                    ),
                    "partyName" to Schema.string(),
                    "partyRole" to Schema.enumeration(
                        values = listOf("CUSTOMER", "SUPPLIER", "WORKER", "CLIENT", "NONE")
                    ),
                    "activityDate" to Schema.string(description = "YYYY-MM-DD format"),
                    "notes" to Schema.string(),
                    "extraFields" to Schema.array(items = Schema.string())
                ),
                optionalProperties = listOf("customTypeName"),
                nullable = true
            ),
            "proposedMemories" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "category" to Schema.enumeration(
                            values = listOf(
                                "CROP_LIVESTOCK",
                                "PLOT_PEN",
                                "CUSTOMER",
                                "SUPPLIER",
                                "PRICE_UNIT",
                                "WORKER",
                                "SERVICE",
                                "GOAL",
                                "REMINDER"
                            )
                        ),
                        "label" to Schema.string(),
                        "detail" to Schema.string(),
                        "unit" to Schema.string(nullable = true),
                        "typicalPrice" to Schema.double(nullable = true)
                    ),
                    optionalProperties = listOf("unit", "typicalPrice")
                ),
                nullable = true
            ),
            "proposedCustomType" to Schema.obj(
                properties = mapOf(
                    "name" to Schema.string(),
                    "description" to Schema.string(),
                    "fieldNames" to Schema.array(items = Schema.string())
                ),
                nullable = true
            ),
            "markOnboardingComplete" to Schema.boolean(),
            "nextOnboardingStep" to Schema.integer(nullable = true),
            "updatedOwnerName" to Schema.string(nullable = true),
            "updatedFarmName" to Schema.string(nullable = true),
            "updatedCurrency" to Schema.string(nullable = true),
            "updatedGoalNote" to Schema.string(nullable = true),
            "chartData" to Schema.obj(
                properties = mapOf(
                    "title" to Schema.string(),
                    "chartType" to Schema.enumeration(values = listOf("BAR", "BREAKDOWN")),
                    "points" to Schema.array(
                        items = Schema.obj(
                            properties = mapOf(
                                "label" to Schema.string(),
                                "value" to Schema.double(),
                                "subtitle" to Schema.string()
                            )
                        )
                    )
                ),
                nullable = true
            ),
            "correctionAction" to Schema.enumeration(
                values = listOf("NONE", "EDIT_TARGET", "DELETE_TARGET")
            ),
            "correctionTargetId" to Schema.string(nullable = true),
            "quickReplies" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 short tappable reply suggestions for the farmer."
            )
        ),
        optionalProperties = listOf(
            "userTranscript",
            "proposedActivity",
            "proposedMemories",
            "proposedCustomType",
            "nextOnboardingStep",
            "updatedOwnerName",
            "updatedFarmName",
            "updatedCurrency",
            "updatedGoalNote",
            "chartData",
            "correctionTargetId"
        )
    )

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = responseSchema
                temperature = 0.45f
            }
        )
    }

    suspend fun sendTurn(
        userText: String?,
        audioBytes: ByteArray? = null,
        audioMimeType: String = "audio/mp4",
        profile: FarmProfile,
        memories: List<LearnedMemory>,
        customTypes: List<CustomRecordType>,
        activities: List<FarmActivity>,
        analytics: FarmAnalyticsSummary,
        recentMessages: List<ChatMessage>
    ): Result<BuddyAiResult> {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val systemContext = buildPromptContext(
                today = today,
                profile = profile,
                memories = memories,
                customTypes = customTypes,
                activities = activities,
                analytics = analytics,
                recentMessages = recentMessages
            )

            val response = withTimeout(60_000L) {
                structuredModel.generateContent(
                    content {
                        text(systemContext)
                        if (audioBytes != null && audioBytes.isNotEmpty()) {
                            inlineData(audioBytes, audioMimeType)
                            text(
                                if (userText.isNullOrBlank()) {
                                    "Listen to the farmer's voice note above, transcribe it into userTranscript, and respond warmly as GreenHarvest Buddy."
                                } else {
                                    "Farmer message (accompanied by voice): $userText"
                                }
                            )
                        } else {
                            text("Farmer's latest message: ${userText.orEmpty()}")
                        }
                    }
                )
            }

            val rawJson = response.text
                ?: throw IllegalStateException("GreenHarvest Buddy returned an empty response.")
            Result.success(parseResponse(rawJson, profile.userId, today))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Buddy AI timed out", e)
            Result.failure(IllegalStateException("That took a little too long. Please try sending again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Buddy AI call failed", e)
            Result.failure(e)
        }
    }

    private fun buildPromptContext(
        today: String,
        profile: FarmProfile,
        memories: List<LearnedMemory>,
        customTypes: List<CustomRecordType>,
        activities: List<FarmActivity>,
        analytics: FarmAnalyticsSummary,
        recentMessages: List<ChatMessage>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You are GreenHarvest Buddy, a warm, friendly, conversational AI farm business partner for ${profile.farmName}.")
        sb.appendLine("CURRENT DATE: $today")
        sb.appendLine("FARMER NAME: ${profile.ownerName}")
        sb.appendLine("CURRENCY: ${profile.currencySymbol}")
        sb.appendLine("ONBOARDING COMPLETED: ${profile.onboardingCompleted} (current step: ${profile.onboardingStep}/7)")
        profile.dailyGoalNote?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("FARM GOAL: $it")
        }
        sb.appendLine()
        sb.appendLine("PERSONALITY & RULES:")
        sb.appendLine("- Warm, encouraging, everyday language (no robotic jargon), short messages (2-4 sentences). Occasional fitting emojis are fine, not too many.")
        sb.appendLine("- Understand casual expressions, shorthand, and mixed English/Pidgin; reply in the same language/style the farmer uses.")
        sb.appendLine("- NEVER invent figures, dates, customers, or totals. Use ONLY the verified farm facts below. If an important detail (amount, crop, party, or date when unclear) is missing, set intentType='NEED_CLARIFICATION' and ask a short friendly follow-up.")
        sb.appendLine("- Stay focused on farm business. If the farmer drifts off-topic, politely and warmly steer back to GreenHarvest Farms.")
        sb.appendLine("- If the farmer says you got something wrong or seems frustrated, apologize briefly, fix it using correctionAction='EDIT_TARGET' or 'DELETE_TARGET' with the matching activity ID (or propose the corrected activity), and learn from the correction.")
        sb.appendLine()
        sb.appendLine("ONBOARDING & LEARNING:")
        sb.appendLine("- If ONBOARDING COMPLETED is false, interview the farmer in a relaxed, chatty way ONE question at a time (never a long form). Cover: (1) preferred name/farm/currency, (2) crops/livestock grown, (3) plots/ponds/pens, (4) usual customers & suppliers, (5) selling units (bags/kg/tons/crates) & typical prices, (6) workers/staff & consultation services, (7) goals. Advance nextOnboardingStep by 1 when answered. Once you have enough basics (step >= 5 or user says done), summarize what you learned in proposedMemories + set intentType='ONBOARDING_SUMMARY' and markOnboardingComplete=true so they can confirm & save.")
        sb.appendLine("- During daily activity updates, stay naturally curious (MAX 1-2 short follow-ups, never interrupting urgent logging), e.g., asking if a plot is their usual tomato plot, if a buyer is a regular customer to add to memory, or if a price is the normal price.")
        sb.appendLine("- When the farmer reports a new type of event that doesn't fit SALE, HARVEST, EXPENSE, LABOR, or CONSULTATION (and isn't already in Custom Record Types below), e.g., 'vaccinated 200 chickens', set intentType='PROPOSE_CUSTOM_TYPE', chat warmly about creating that section first, populate proposedCustomType (name, description, fieldNames) AND proposedActivity (with recordType='CUSTOM', customTypeName matching the proposed name, and extraFields formatted as 'Key: Value') so they can approve creating it!")
        sb.appendLine()
        sb.appendLine("LEARNED BUSINESS MEMORY (${memories.size} items):")
        if (memories.isEmpty()) {
            sb.appendLine("- None saved yet.")
        } else {
            memories.take(40).forEach { mem ->
                val pricePart = mem.typicalPrice?.let { " | typical price: ${formatMoney(it, profile.currencySymbol)}" } ?: ""
                val unitPart = mem.unit?.let { "/$it" } ?: ""
                sb.appendLine("- [${mem.parsedCategory.name}] ${mem.label}: ${mem.detail}$pricePart$unitPart")
            }
        }
        sb.appendLine()
        sb.appendLine("CUSTOM RECORD TYPES:")
        if (customTypes.isEmpty()) {
            sb.appendLine("- None yet.")
        } else {
            customTypes.forEach { ct ->
                sb.appendLine("- ${ct.name}: ${ct.description} (fields: ${ct.fieldNames.joinToString(", ")})")
            }
        }
        sb.appendLine()
        sb.appendLine("VERIFIED FARM ANALYTICS (Ground Truth — never invent numbers):")
        sb.appendLine("- Total Revenue: ${formatMoney(analytics.totalRevenue, profile.currencySymbol)}")
        sb.appendLine("- Total Expenses: ${formatMoney(analytics.totalExpenses, profile.currencySymbol)}")
        sb.appendLine("- Net Profit: ${formatMoney(analytics.netProfit, profile.currencySymbol)}")
        sb.appendLine("- Total Owed to You (Unpaid/Partial): ${formatMoney(analytics.totalUnpaidReceivables, profile.currencySymbol)}")
        if (analytics.cropRevenueMap.isNotEmpty()) {
            sb.appendLine("- Revenue by Crop/Item: " + analytics.cropRevenueMap.entries.sortedByDescending { it.value }
                .joinToString(", ") { "${it.key}: ${formatMoney(it.value, profile.currencySymbol)}" })
        }
        if (analytics.debtorBalances.isNotEmpty()) {
            sb.appendLine("- Who Owes You: " + analytics.debtorBalances.entries.sortedByDescending { it.value }
                .joinToString(", ") { "${it.key}: ${formatMoney(it.value, profile.currencySymbol)}" })
        }
        if (analytics.monthlyNetMap.isNotEmpty()) {
            sb.appendLine("- Monthly Revenue vs Expense: " + analytics.monthlyNetMap.entries
                .joinToString("; ") { "${it.key}: Rev ${formatMoney(it.value.first, profile.currencySymbol)} / Exp ${formatMoney(it.value.second, profile.currencySymbol)}" })
        }
        sb.appendLine()
        sb.appendLine("RECENT FARM ACTIVITIES (most recent 20, with IDs for corrections):")
        if (activities.isEmpty()) {
            sb.appendLine("- None recorded yet.")
        } else {
            activities.take(20).forEach { act ->
                sb.appendLine("- [id=${act.id}] ${act.activityDate} | ${act.recordType}${act.customTypeName?.let { "($it)" } ?: ""} | ${act.title} | crop=${act.itemOrCrop} | plot=${act.plotOrUnit} | qty=${act.quantity} ${act.unit} | total=${act.totalAmount} | paid=${act.amountPaid} | status=${act.paymentStatus} | party=${act.partyName}")
            }
        }
        sb.appendLine()
        sb.appendLine("RECENT CHAT HISTORY:")
        recentMessages.takeLast(10).forEach { msg ->
            sb.appendLine("${msg.sender}: ${msg.text}")
        }
        return sb.toString()
    }

    private fun parseResponse(rawJson: String, userId: String, defaultDate: String): BuddyAiResult {
        val json = JSONObject(rawJson)
        val userTranscript = json.optString("userTranscript").takeIf { it.isNotBlank() && it != "null" }
        val replyText = json.optString("replyText").ifBlank { "Got it! Let me know how you'd like to continue." }
        val intentType = json.optString("intentType").ifBlank { "CHAT" }

        val proposedActivity = json.optJSONObject("proposedActivity")?.let { actJson ->
            val typeStr = RecordType.fromRaw(actJson.optString("recordType")).name
            val total = actJson.optDouble("totalAmount", 0.0).let { if (it.isNaN()) 0.0 else it.coerceAtLeast(0.0) }
            val paid = actJson.optDouble("amountPaid", 0.0).let { if (it.isNaN()) 0.0 else it.coerceAtLeast(0.0) }
            val extraList = mutableListOf<String>()
            actJson.optJSONArray("extraFields")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty()) extraList.add(v)
                }
            }
            FarmActivity(
                userId = userId,
                recordType = typeStr,
                customTypeName = actJson.optString("customTypeName").takeIf { it.isNotBlank() && it != "null" },
                title = actJson.optString("title").ifBlank { actJson.optString("itemOrCrop").ifBlank { typeStr } },
                itemOrCrop = actJson.optString("itemOrCrop"),
                plotOrUnit = actJson.optString("plotOrUnit"),
                quantity = actJson.optDouble("quantity", 0.0).let { if (it.isNaN()) 0.0 else it.coerceAtLeast(0.0) },
                unit = actJson.optString("unit"),
                totalAmount = total,
                amountPaid = paid,
                paymentStatus = PaymentStatus.fromRaw(actJson.optString("paymentStatus")).name,
                partyName = actJson.optString("partyName"),
                partyRole = PartyRole.fromRaw(actJson.optString("partyRole")).name,
                activityDate = actJson.optString("activityDate").ifBlank { defaultDate },
                notes = actJson.optString("notes"),
                extraFields = extraList
            )
        }

        val proposedMemories = mutableListOf<LearnedMemory>()
        json.optJSONArray("proposedMemories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val label = m.optString("label").trim()
                if (label.isNotEmpty()) {
                    val priceVal = if (m.has("typicalPrice") && !m.isNull("typicalPrice")) {
                        m.optDouble("typicalPrice").takeIf { !it.isNaN() && it >= 0.0 }
                    } else null
                    proposedMemories.add(
                        LearnedMemory(
                            userId = userId,
                            category = MemoryCategory.fromRaw(m.optString("category")).name,
                            label = label,
                            detail = m.optString("detail"),
                            unit = m.optString("unit").takeIf { it.isNotBlank() && it != "null" },
                            typicalPrice = priceVal
                        )
                    )
                }
            }
        }

        val proposedCustomType = json.optJSONObject("proposedCustomType")?.let { ct ->
            val name = ct.optString("name").trim()
            if (name.isNotEmpty()) {
                val fields = mutableListOf<String>()
                ct.optJSONArray("fieldNames")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val f = arr.optString(i).trim()
                        if (f.isNotEmpty()) fields.add(f)
                    }
                }
                CustomRecordType(
                    userId = userId,
                    name = name,
                    description = ct.optString("description"),
                    fieldNames = fields
                )
            } else null
        }

        val chartData = json.optJSONObject("chartData")?.let { chart ->
            val pointsArr = chart.optJSONArray("points") ?: JSONArray()
            val pts = mutableListOf<ChartPoint>()
            for (i in 0 until pointsArr.length()) {
                val p = pointsArr.optJSONObject(i) ?: continue
                val v = p.optDouble("value", 0.0)
                if (!v.isNaN()) {
                    pts.add(
                        ChartPoint(
                            label = p.optString("label").ifBlank { "Item" },
                            value = v.coerceAtLeast(0.0),
                            subtitle = p.optString("subtitle")
                        )
                    )
                }
            }
            if (pts.isNotEmpty()) {
                ChartCardData(
                    title = chart.optString("title").ifBlank { "Farm Breakdown" },
                    chartType = chart.optString("chartType").ifBlank { "BAR" },
                    points = pts
                )
            } else null
        }

        val quickReplies = mutableListOf<String>()
        json.optJSONArray("quickReplies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val r = arr.optString(i).trim()
                if (r.isNotEmpty()) quickReplies.add(r)
            }
        }

        return BuddyAiResult(
            userTranscript = userTranscript,
            replyText = replyText,
            intentType = intentType,
            proposedActivity = proposedActivity,
            proposedMemories = proposedMemories,
            proposedCustomType = proposedCustomType,
            markOnboardingComplete = json.optBoolean("markOnboardingComplete", false),
            nextOnboardingStep = if (json.has("nextOnboardingStep") && !json.isNull("nextOnboardingStep")) {
                json.optLong("nextOnboardingStep")
            } else null,
            updatedOwnerName = json.optString("updatedOwnerName").takeIf { it.isNotBlank() && it != "null" },
            updatedFarmName = json.optString("updatedFarmName").takeIf { it.isNotBlank() && it != "null" },
            updatedCurrency = json.optString("updatedCurrency").takeIf { it.isNotBlank() && it != "null" },
            updatedGoalNote = json.optString("updatedGoalNote").takeIf { it.isNotBlank() && it != "null" },
            chartData = chartData,
            correctionAction = json.optString("correctionAction").ifBlank { "NONE" },
            correctionTargetId = json.optString("correctionTargetId").takeIf { it.isNotBlank() && it != "null" },
            quickReplies = quickReplies.take(3)
        )
    }
}
