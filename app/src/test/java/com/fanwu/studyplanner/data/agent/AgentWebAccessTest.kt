package com.fanwu.studyplanner.data.agent

import com.fanwu.studyplanner.data.remote.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class AgentWebAccessTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val url = "https://docs.example.com/guide"
    private fun initial() = AgentRun(1, "web", listOf(ApiMessage("user", "先查资料再安排学习")))
    private fun tool(id: String, name: String = "web_search") = ApiMessage("assistant", toolCalls = listOf(ToolCall(id = id, function = FunctionCall(name, "{}"))))
    private fun response(message: ApiMessage) = ChatCompletionResponse(listOf(ChatChoice(message)))

    @Test fun searchUsesExistingCredentialAndReturnsBoundedSourcesAndUnknownDates() = runBlocking<Unit> {
        val api = object : DeepSeekWebApi {
            override suspend fun search(authorization: String, request: WebSearchRequest): WebSearchResponse {
                assertEquals("Bearer fake", authorization)
                assertEquals("考试 官方大纲", request.query)
                assertEquals(5, request.count)
                assertFalse(request.needContent)
                return WebSearchResponse(listOf(WebSearchEntry("官方资料", url, "摘要", "资料".repeat(1000)), WebSearchEntry("无效地址", "file:///private")))
            }
            override suspend fun fetch(authorization: String, request: WebFetchRequest): WebFetchResponse = error("不应读取网页")
        }
        val result = DeepSeekAgentWebAccess(api).search("fake", " 考试 官方大纲 ")
        assertEquals(1, result.sources.size)
        assertNull(result.sources.single().publishedAt)
        val entry = json.parseToJsonElement(result.content).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertTrue(entry["truncated"]!!.jsonPrimitive.boolean)
        assertEquals(1000, entry["summary"]!!.jsonPrimitive.content.length)
        assertFalse(result.content.contains("Bearer"))
    }

    @Test fun fetchKeepsProvenanceAndMarksTruncationInsteadOfClaimingFullText() = runBlocking<Unit> {
        val api = object : DeepSeekWebApi {
            override suspend fun search(authorization: String, request: WebSearchRequest): WebSearchResponse = error("不应搜索")
            override suspend fun fetch(authorization: String, request: WebFetchRequest): WebFetchResponse {
                assertEquals("Bearer fake", authorization)
                assertEquals(url, request.url)
                return WebFetchResponse("原始网页", "a".repeat(10_000))
            }
        }
        val result = DeepSeekAgentWebAccess(api).fetch("fake", "$url#part")
        val content = json.parseToJsonElement(result.content).jsonObject
        assertEquals(url, result.sources.single().url)
        assertEquals(6000, content["content"]!!.jsonPrimitive.content.length)
        assertTrue(content["truncated"]!!.jsonPrimitive.boolean)
        assertTrue(content["untrustedContent"]!!.jsonPrimitive.boolean)
    }

    @Test fun emptySearchResultsRemainEmptyAndDoNotInventAReference() = runBlocking<Unit> {
        val api = object : DeepSeekWebApi {
            override suspend fun search(authorization: String, request: WebSearchRequest) = WebSearchResponse(emptyList())
            override suspend fun fetch(authorization: String, request: WebFetchRequest): WebFetchResponse = error("无结果时不应读取网页")
        }
        val result = DeepSeekAgentWebAccess(api).search("fake", "不存在的资料")
        assertTrue(result.sources.isEmpty())
        assertTrue(json.parseToJsonElement(result.content).jsonObject["results"]!!.jsonArray.isEmpty())
    }

    @Test fun invalidQueriesAndPrivateOrCredentialUrlsAreRejectedBeforeNetworking() = runBlocking<Unit> {
        listOf("http://localhost/a", "http://127.0.0.1/a", "http://10.0.0.1", "https://192.168.1.1", "http://169.254.1.1", "http://172.18.0.1", "http://[::1]", "https://name:password@example.com", "file:///data/a", "https://server.internal/a", "https://example.com:8080/a").forEach {
            assertTrue(it, runCatching { publicWebUrl(it) }.isFailure)
        }
        assertEquals("https://example.com/a?b=1", publicWebUrl("https://example.com/a?b=1#c"))
        val api = object : DeepSeekWebApi {
            override suspend fun search(authorization: String, request: WebSearchRequest): WebSearchResponse = error("应在联网前拒绝")
            override suspend fun fetch(authorization: String, request: WebFetchRequest): WebFetchResponse = error("应在联网前拒绝")
        }
        assertTrue(runCatching { DeepSeekAgentWebAccess(api).search("fake", " ") }.isFailure)
        assertTrue(runCatching { DeepSeekAgentWebAccess(api).search("fake", "字".repeat(129)) }.isFailure)
    }

    @Test fun serviceFailuresReturnUsefulMessagesWithoutEchoingServerSecrets() = runBlocking<Unit> {
        for (failure in listOf(HttpException(Response.error<String>(402, "sensitive response".toResponseBody())), IOException("sensitive connection detail"))) {
            val api = object : DeepSeekWebApi {
                override suspend fun search(authorization: String, request: WebSearchRequest): WebSearchResponse = throw failure
                override suspend fun fetch(authorization: String, request: WebFetchRequest): WebFetchResponse = throw failure
            }
            val message = runCatching { DeepSeekAgentWebAccess(api).search("fake", "资料") }.exceptionOrNull()!!.message!!
            assertFalse(message.contains("sensitive"))
            assertTrue(message.contains(if (failure is HttpException) "余额不足" else "连接失败"))
        }
    }

    @Test fun sourceResultsReachNextDecisionAndSurviveSerializedRecovery() = runBlocking<Unit> {
        var requests = 0
        val source = AgentSource("参考资料", url, retrievedAt = "2026-10-30T00:00:00Z")
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                if (requests++ == 0) return response(tool("search"))
                assertEquals("search", request.messages.last().toolCallId)
                assertTrue(request.messages.last().content!!.contains("搜索结果"))
                return response(ApiMessage("assistant", "已查到资料，还需要你提供考试科目。"))
            }
        }
        val run = StudyAgent(api, { AgentToolResult("搜索结果", "已搜索", sources = listOf(source)) }, {}).run(initial(), "fake")
        assertEquals(1, run.webRequests)
        assertEquals(listOf(source), run.sources)
        assertEquals(run, json.decodeFromString<AgentRun>(json.encodeToString(run)))
    }

    @Test fun resumedWebBudgetCannotBeResetAndStillAllowsAnHonestFinalReply() = runBlocking<Unit> {
        var executed = 0
        val state = initial().copy(webRequests = 5, messages = initial().messages + ApiMessage("assistant", toolCalls = listOf(
            ToolCall(id = "a", function = FunctionCall("web_search", "{}")), ToolCall(id = "b", function = FunctionCall("web_fetch", "{}")))))
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                assertTrue(request.messages.last().content!!.contains("上限"))
                return response(ApiMessage("assistant", "联网额度已用完，这部分尚未核实。"))
            }
        }
        val run = StudyAgent(api, { executed++; AgentToolResult("{}", "查询") }, {}).run(state, "fake")
        assertEquals(1, executed)
        assertEquals(6, run.webRequests)
        assertEquals(AgentStatus.COMPLETED, run.status)
    }

    @Test fun disabledWebToolsAreNotAdvertisedOrExecutedEvenForPersistedCalls() = runBlocking<Unit> {
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse {
                assertTrue(request.tools!!.none { it.function.name in StudyAgent.WEB_TOOLS })
                return response(ApiMessage("assistant", "联网已关闭，请提供资料。"))
            }
        }
        val run = StudyAgent(api, { error("关闭后不得联网") }, {}, availableTools = StudyAgent.TOOLS.filter { it.function.name !in StudyAgent.WEB_TOOLS })
            .run(initial().copy(messages = initial().messages + tool("old")), "fake")
        assertEquals(0, run.webRequests)
        assertTrue(run.messages.any { it.role == "tool" && it.content!!.contains("已关闭") })
    }

    @Test fun cancellationReservesWebAttemptBeforeTheNetworkRequest() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        var saved = initial().copy(messages = initial().messages + tool("pending"))
        val api = object : DeepSeekApi {
            override suspend fun chatCompletions(authorization: String, request: ChatCompletionRequest): ChatCompletionResponse = error("应先恢复工具")
        }
        val job = launch {
            StudyAgent(api, { entered.complete(Unit); awaitCancellation() }, { saved = it }).run(saved, "fake")
        }
        entered.await(); job.cancelAndJoin()
        assertEquals(AgentStatus.CANCELLED, saved.status)
        assertEquals(1, saved.webRequests)
    }
}
