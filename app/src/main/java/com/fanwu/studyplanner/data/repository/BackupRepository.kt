package com.fanwu.studyplanner.data.repository

import androidx.room.withTransaction
import com.fanwu.studyplanner.BuildConfig
import com.fanwu.studyplanner.data.local.*
import com.fanwu.studyplanner.domain.*
import com.fanwu.studyplanner.reminders.ReminderRuntime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.*
import java.util.UUID

@Serializable
data class PlannerBackup(
    val schemaVersion: Int, val exportedAt: Long, val appVersion: String,
    val origin: String, val events: List<CalendarEvent>, val conversations: List<ConversationEntity>,
    val messages: List<ChatMessageEntity>, val sessions: List<FocusSessionEntity>,
    val intervals: List<FocusIntervalEntity>, val documents: List<PlannerDocument>,
    val reminders: ReminderSettingsEntity? = null
)

enum class RestorePolicy { SKIP, OVERWRITE }
data class RestorePreview(val backup: PlannerBackup, val before: PlannerBackup, val docsBefore: List<PlannerDocument>,
                          val duplicates: Int, val conflicts: Int, val blockingReason: String? = null)

class BackupRepository(private val db: AppDatabase) {
    private val kinds = setOf("goal", "semester", "course", "review", "agent_preferences")

    private suspend fun snapshot(origin: String) = PlannerBackup(schemaVersion = 1, exportedAt = 0, appVersion = BuildConfig.VERSION_NAME,
        origin = origin, events = db.calendarEventDao().getAll().map { it.toDomain() },
        conversations = db.conversationDao().all(), messages = db.chatMessageDao().all(),
        sessions = db.focusDao().allSessions(), intervals = db.focusDao().allIntervals(),
        documents = db.documentDao().all().filter { it.kind in kinds }, reminders = db.reminderDao().settings())

    suspend fun export(): String = db.withTransaction {
        val origin = db.documentDao().get("backup-origin")?.payload ?: UUID.randomUUID().toString().also {
            db.documentDao().put(PlannerDocument("backup-origin", "internal", it))
        }
        val b = snapshot(origin)
        val export = b.copy(exportedAt = System.currentTimeMillis(), sessions = b.sessions.map { row ->
            val s = row.toFocusSession()
            s.copy(taskId = s.taskId?.takeIf { id -> b.events.any { it.id == id } }).toEntity()
        })
        validate(export)
        plannerJson.encodeToString(export).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 10 MB，请先在系统文件中保留原数据库，再联系开发者扩大格式容量" } }
    }

    fun parse(text: String): PlannerBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件不能超过 10 MB" }
        return plannerJson.decodeFromString<PlannerBackup>(text).also(::validate)
    }

    fun validate(b: PlannerBackup) {
        require(b.schemaVersion == 1) { "不支持备份版本 ${b.schemaVersion}，请更新应用" }
        require(b.origin.isNotBlank() && b.origin.length <= 100 && b.exportedAt > 0 && b.appVersion.isNotBlank()) { "备份头信息无效" }
        require(b.events.size + b.messages.size + b.intervals.size + b.documents.size + b.conversations.size + b.sessions.size <= 50000) { "记录数量过多" }
        fun <T> unique(values: List<T>) = require(values.distinct().size == values.size) { "备份含重复主键" }
        unique(b.events.map { it.id }); unique(b.conversations.map { it.id }); unique(b.messages.map { it.id })
        unique(b.sessions.map { it.id }); unique(b.intervals.map { it.id }); unique(b.documents.map { it.id })
        b.events.forEach { e ->
            require(e.id > 0 && e.title.isNotBlank() && e.title.length <= 1000)
            require(LocalDate.parse(e.date).toString() == e.date)
            require(Regex("\\d{2}:\\d{2}").matches(e.startTime) && Regex("\\d{2}:\\d{2}").matches(e.endTime))
            require(e.hasValidTimeRange()) { "备份中的事件时间无效" }
            require(e.completedAt == null || (e.isDone && e.completedAt > 0))
        }
        b.conversations.forEach { require(it.id > 0 && it.name.isNotBlank() && it.createdAt > 0) }
        b.messages.forEach { m -> require(m.id > 0 && m.timestamp > 0 && m.role in listOf("user", "assistant", "system") && b.conversations.any { it.id == m.conversationId }) { "消息归属不存在" } }
        b.sessions.forEach { row ->
            val s = row.toFocusSession()
            require(s.id == row.id && s.focusedMs == row.focusedMs && s.startedAt == row.startedAt && s.endedAt == row.endedAt && s.outcome == row.outcome)
            require(row.activeSlot == (if (s.phase.isTerminal) null else 1))
            require(s.focusDurationMs in 1..10_800_000 && s.breakDurationMs in 0..3_600_000 && s.focusedMs in 0..s.focusDurationMs && s.startedAt > 0)
            ZoneId.of(s.zoneId)
            require(s.taskId == null || b.events.any { it.id == s.taskId }) { "专注关联任务不存在；请先解除损坏的关联" }
        }
        require(b.sessions.count { it.activeSlot != null } <= 1)
        b.intervals.forEach { i ->
            require(i.id.isNotBlank() && i.startedAt > 0 && i.durationMs in 1..10_800_000 && b.sessions.any { it.id == i.sessionId }) { "专注时间片无效" }
            ZoneId.of(i.zoneId)
        }
        b.sessions.forEach { s -> require(b.intervals.filter { it.sessionId == s.id }.sumOf { it.durationMs } == s.focusedMs) { "专注记录与时间片不一致" } }
        b.documents.forEach { d ->
            require(d.id.isNotBlank() && d.id != "backup-origin" && !d.id.startsWith("restore:") && d.kind in kinds)
            when (d.kind) {
                "agent_preferences" -> { require(d.id == "agent-preferences"); d.decode<com.fanwu.studyplanner.data.agent.AgentPreferences>().validate() }
                "goal" -> d.decode<GoalRevision>().also { require(it.id == d.id); it.validate() }
                "semester" -> d.decode<Semester>().also { require(it.id == d.id); it.validate() }
                "course" -> d.decode<RecurringCourse>().also { c ->
                    require(c.id == d.id)
                    val semester = b.documents.firstOrNull { it.kind == "semester" && it.id == c.semesterId }?.decode<Semester>()
                    require(semester != null) { "重复课程所属学期不存在" }; expandCourse(c, semester)
                    c.exceptions.forEach { LocalDate.parse(it) }; c.from?.let(LocalDate::parse); c.until?.let(LocalDate::parse)
                }
                "review" -> d.decode<ReviewDraft>().also { r ->
                    require(r.id == d.id && LocalDate.parse(r.week).dayOfWeek == DayOfWeek.MONDAY)
                    r.events.forEach { require(it.type == EventType.STUDY_TASK && it.title.isNotBlank()); LocalDate.parse(it.date); require(LocalTime.parse(it.startTime) < LocalTime.parse(it.endTime)) }
                }
            }
        }
        b.events.forEach { e -> require(e.recurringCourseId == null || b.documents.any { it.kind == "course" && it.id == e.recurringCourseId }) { "课程规则引用不存在" } }
        b.reminders?.let { require(it.id == 1); it.toDomain().validate() }
    }

    private fun eventKey(e: CalendarEvent) = "${e.title}|${e.date}|${e.startTime}|${e.type}"
    private fun receipt(origin: String, type: String, id: Any) = "restore:$origin:$type:$id"
    private fun matchingDocument(d: PlannerDocument, all: List<PlannerDocument>): PlannerDocument? =
        all.firstOrNull { it.id == d.id } ?: if (d.kind == "goal") {
            val incoming = d.decode<GoalRevision>()
            all.firstOrNull { it.kind == "goal" && it.decode<GoalRevision>().let { g -> g.goalId == incoming.goalId && g.effectiveWeek == incoming.effectiveWeek } }
        } else null

    suspend fun preview(b: PlannerBackup): RestorePreview = db.withTransaction {
        validate(b)
        val current = snapshot("local")
        val docs = db.documentDao().all()
        var same = 0; var changed = 0
        b.events.forEach { e ->
            val targetId = docs.firstOrNull { it.id == receipt(b.origin, "event", e.id) }?.payload?.toLongOrNull()
                ?: e.id.takeIf { docs.any { it.id == "backup-origin" && it.payload == b.origin } }
            val old = current.events.firstOrNull { it.id == targetId } ?: current.events.firstOrNull { eventKey(it) == eventKey(e) }
            if (old != null) { if (old.copy(id = e.id) == e) same++ else changed++ }
        }
        b.documents.forEach { d -> matchingDocument(d, current.documents)?.let { if (it == d) same++ else changed++ } }
        b.conversations.forEach { c -> if (current.conversations.any { it.name == c.name && it.createdAt == c.createdAt }) same++ }
        b.sessions.forEach { s -> if (current.sessions.any { it.id == s.id }) same++ }
        val immutableConflict = b.sessions.any { incoming -> current.sessions.any { it.id == incoming.id && it.focusedMs != incoming.focusedMs } }
        RestorePreview(b, current, docs, same, changed, if (immutableConflict) "相同专注记录存在不同累计时长，无法安全合并。请使用更新的备份。" else null)
    }

    suspend fun restore(p: RestorePreview, policy: RestorePolicy): String = ReminderRuntime.calendarMutation {
        db.withTransaction {
            check(snapshot("local") == p.before && db.documentDao().all() == p.docsBefore) { "数据已变化，请重新选择备份以更新预览" }
            check(db.focusDao().active() == null) { "请先结束当前专注，再恢复备份" }
            validate(p.backup)
            val b = p.backup
            val eventIds = mutableMapOf<Long, Long>()
            var added = 0; var overwritten = 0; var skipped = 0
            b.events.forEach { e ->
                val rid = receipt(b.origin, "event", e.id)
                val mapped = db.documentDao().get(rid)?.payload?.toLongOrNull()
                    ?: e.id.takeIf { db.documentDao().get("backup-origin")?.payload == b.origin }
                val old = mapped?.let { db.calendarEventDao().getById(it) } ?: db.calendarEventDao().getAll().firstOrNull { eventKey(it.toDomain()) == eventKey(e) }
                val id = if (old == null) { added++; db.calendarEventDao().insert(e.copy(id = 0).toEntity()) }
                    else { if (policy == RestorePolicy.OVERWRITE) { db.calendarEventDao().update(e.copy(id = old.id).toEntity()); overwritten++ } else skipped++; old.id }
                eventIds[e.id] = id
                db.documentDao().put(PlannerDocument(rid, "internal", id.toString()))
            }
            b.conversations.forEach { c ->
                val rid = receipt(b.origin, "conversation", c.id)
                val mapped = db.documentDao().get(rid)?.payload?.toLongOrNull()
                    ?: c.id.takeIf { db.documentDao().get("backup-origin")?.payload == b.origin }
                val old = db.conversationDao().all().firstOrNull { it.id == mapped } ?: db.conversationDao().all().firstOrNull { it.name == c.name && it.createdAt == c.createdAt }
                val id = old?.id ?: db.conversationDao().insert(c.copy(id = 0))
                if (old != null && policy == RestorePolicy.OVERWRITE) db.conversationDao().rename(id, c.name)
                db.documentDao().put(PlannerDocument(rid, "internal", id.toString()))
                b.messages.filter { it.conversationId == c.id }.forEach { m ->
                    val mr = receipt(b.origin, "message", m.id)
                    val localMessages = db.chatMessageDao().getByConversation(id)
                    val messageId = localMessages.firstOrNull { it.role == m.role && it.content == m.content && it.timestamp == m.timestamp }?.id
                        ?: db.chatMessageDao().insert(m.copy(id = 0, conversationId = id))
                    db.documentDao().put(PlannerDocument(mr, "internal", messageId.toString()))
                }
            }
            b.sessions.forEach { row ->
                val existing = db.focusDao().allSessions().firstOrNull { it.id == row.id }
                // 已记录时间使用不可变 UUID，不重复累计或覆盖专注时间。
                if (existing == null) {
                    val s = row.toFocusSession()
                    val restored = s.copy(taskId = s.taskId?.let { eventIds[it] },
                        phase = if (s.phase.isTerminal) s.phase else FocusPhase.ABANDONED,
                        outcome = if (s.phase.isTerminal) s.outcome else "ABANDONED",
                        endedAt = s.endedAt ?: b.exportedAt, recoveryMessage = "从备份恢复；未完成的一轮已结束，保留已记录的专注时间")
                    db.focusDao().insert(restored.toEntity())
                    b.intervals.filter { it.sessionId == s.id }.forEach { db.focusDao().insertInterval(it) }
                } else {
                    require(existing.focusedMs == row.focusedMs) { "相同专注记录有不同累计时长，已回滚，请使用更新的备份" }
                    val local = existing.toFocusSession()
                    if (local.taskId == null || db.calendarEventDao().getById(local.taskId) == null) {
                        row.toFocusSession().taskId?.let { originalId -> eventIds[originalId]?.let { restoredId ->
                            db.focusDao().update(local.copy(taskId = restoredId).toEntity())
                        } }
                    }
                }
            }
            b.documents.forEach { d ->
                val old = matchingDocument(d, db.documentDao().all())
                if (old == null || policy == RestorePolicy.OVERWRITE) {
                    if (old != null && old.id != d.id) db.documentDao().delete(old.id)
                    db.documentDao().put(d)
                }
            }
            if (policy == RestorePolicy.OVERWRITE || db.reminderDao().settings() == null) b.reminders?.let { db.reminderDao().saveSettings(it) }
            validate(snapshot("local").copy(exportedAt = System.currentTimeMillis(), sessions = db.focusDao().allSessions().map { row ->
                val s = row.toFocusSession()
                s.copy(taskId = s.taskId?.takeIf { id -> db.calendarEventDao().getById(id) != null }).toEntity()
            }))
            "恢复完成：日程新增 $added 项、覆盖 $overwritten 项、跳过 $skipped 项；对话、目标、学期与专注记录已合并。"
        }
    }

    companion object { const val MAX_BYTES = 10 * 1024 * 1024 }
}
