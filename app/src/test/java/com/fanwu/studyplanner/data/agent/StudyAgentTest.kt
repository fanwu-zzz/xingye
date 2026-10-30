package com.fanwu.studyplanner.data.agent

import com.fanwu.studyplanner.data.remote.*
import com.fanwu.studyplanner.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class StudyAgentTest {
    private fun initial() = AgentRun(1, "run", listOf(ApiMessage("system", "规划测试"), ApiMessage("user", "安排学习")))
    private fun call(id: String, name: String = "query_tasks") = ApiMessage("assistant", toolCalls = listOf(ToolCall(id = id, function = FunctionCall(name, "{}"))), reasoningContent = "内部字段应完整回传但不展示")
    private fun response(message: ApiMessage) = ChatCompletionResponse(listOf(ChatChoice(message)))

    @Test fun toolFeedbackIsReturnedBeforeTheNextDecisionAndCheckpointed() = runBlocking<Unit> {
        var requests = 0
        var persisted = initial()
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                assertEquals("Bearer fake", authorization)
                if (requests++ == 0) return response(call("call-1"))
                assertEquals("call-1", request.messages.last().toolCallId)
                assertEquals("tool", request.messages.last().role)
                assertEquals("内部字段应完整回传但不展示", request.messages[2].reasoningContent)
                assertTrue(request.messages.last().content!!.contains("真实待办"))
                return response(ApiMessage("assistant", "请补充你每天的空闲时间。"))
            }
        }
        val result = StudyAgent(api, { AgentToolResult("真实待办：1项", "查询待办：1项") }, { persisted = it }).run(initial(), "fake")
        assertEquals(AgentStatus.COMPLETED, result.status)
        assertEquals(1, result.calls)
        assertEquals(result, persisted)
    }

    @Test fun toolErrorIsFeedbackAndDoesNotTerminateTheLoop() = runBlocking<Unit> {
        var requests = 0
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                if (requests++ == 0) return response(call("bad", "propose_plan"))
                assertTrue(request.messages.last().content!!.contains("时间冲突"))
                return response(ApiMessage("assistant", "需要调整到空闲时段。"))
            }
        }
        val result = StudyAgent(api, { error("时间冲突") }, {}).run(initial(), "fake")
        assertEquals(AgentStatus.COMPLETED, result.status)
        assertNull(result.draft)
    }

    @Test fun interruptedPendingToolIsRecoveredWithoutAskingModelAgainFirst() = runBlocking<Unit> {
        var executed = false
        val resumed = initial().copy(messages = initial().messages + call("pending"), requests = 1)
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                assertTrue(executed)
                return response(ApiMessage("assistant", "已读取。"))
            }
        }
        val decoded = Json.decodeFromString<AgentRun>(Json.encodeToString(resumed))
        val result = StudyAgent(api, { executed = true; AgentToolResult("{}", "恢复查询") }, {}).run(decoded, "fake")
        assertEquals(2, result.requests)
        assertEquals(1, result.calls)
    }

    @Test fun requestBudgetStopsAnUnproductiveAgent() = runBlocking<Unit> {
        var count = 0
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest) = response(call("call-${++count}"))
        }
        val result = StudyAgent(api, { AgentToolResult("{}", "查询") }, {}).run(initial(), "fake")
        assertEquals(AgentStatus.LIMIT_REACHED, result.status)
        assertEquals(StudyAgent.MAX_REQUESTS, count)
    }

    @Test fun cancellationSavesProgressAndNetworkFailureCanResume() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        var persisted = initial()
        val slow = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                entered.complete(Unit); awaitCancellation()
            }
        }
        val job = launch { StudyAgent(slow, { error("无工具") }, { persisted = it }).run(initial(), "fake") }
        entered.await(); job.cancelAndJoin()
        assertEquals(AgentStatus.CANCELLED, persisted.status)
        val broken = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse = throw IOException("offline")
        }
        val failed = StudyAgent(broken, { error("无工具") }, {}).run(persisted, "fake")
        assertEquals(AgentStatus.FAILED, failed.status)
        assertEquals(2, failed.requests)
    }

    @Test fun unlistedToolsNeverReachTheExecutor() = runBlocking<Unit> {
        var count = 0
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest) = response(if (count++ == 0) call("delete", "delete_all") else ApiMessage("assistant", "无法删除。"))
        }
        val result = StudyAgent(api, { error("不应调用执行器") }, {}).run(initial(), "fake")
        assertEquals(AgentStatus.COMPLETED, result.status)
        assertTrue(result.messages.any { it.role == "tool" && it.content!!.contains("此工具不可用") })
    }

    @Test fun localValidationRejectsConflictDuplicatesAndPreferenceOverflow() {
        val day = LocalDate.now().plusDays(1).toString()
        val event = CalendarEvent(title = "学习", date = day, startTime = "14:00", endTime = "15:00", type = EventType.STUDY_TASK)
        assertTrue(runCatching { validateAgentDraft(listOf(event), listOf(event.copy(id = 3, type = EventType.CLASS)), AgentPreferences()) }.isFailure)
        assertTrue(runCatching { validateAgentDraft(listOf(event, event), emptyList(), AgentPreferences()) }.isFailure)
        assertTrue(runCatching { validateAgentDraft(listOf(event), emptyList(), AgentPreferences(maxMinutesPerDay = 30)) }.isFailure)
        validateAgentDraft(listOf(event.copy(type = EventType.DEADLINE, startTime = "15:00")), emptyList(), AgentPreferences(maxMinutesPerDay = 30))
    }

    @Test fun pastTimeTodayIsRejectedAgainAtConfirmation() {
        val day = LocalDate.now()
        val event = CalendarEvent(title = "学习", date = day.toString(), startTime = "14:00", endTime = "14:30", type = EventType.STUDY_TASK)
        validateAgentDraft(listOf(event), emptyList(), AgentPreferences(), day, day.atTime(13, 0))
        assertTrue(runCatching { validateAgentDraft(listOf(event), emptyList(), AgentPreferences(), day, day.atTime(14, 1)) }.isFailure)
    }
}
