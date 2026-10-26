package com.fanwu.studyplanner

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.domain.ConversationMode
import com.fanwu.studyplanner.ui.calendar.CalendarScreen
import com.fanwu.studyplanner.ui.chat.ChatScreen
import com.fanwu.studyplanner.ui.features.*
import com.fanwu.studyplanner.ui.focus.FocusScreen
import com.fanwu.studyplanner.focus.FocusNotifications
import com.fanwu.studyplanner.reminders.EventNotifications
import com.fanwu.studyplanner.ui.reminders.ReminderScreen
import com.fanwu.studyplanner.ui.reminders.ReminderEventScreen
import com.fanwu.studyplanner.ui.features.ScheduleScreen
import com.fanwu.studyplanner.ui.settings.SettingsScreen
import com.fanwu.studyplanner.ui.stats.StatsScreen
import com.fanwu.studyplanner.ui.tasks.TasksScreen
import com.fanwu.studyplanner.ui.theme.*

class MainActivity : ComponentActivity() {
    private var focusRequest by mutableStateOf(0)
    private var eventRequest by mutableStateOf(0)
    private var requestedEventId by mutableStateOf<Long?>(null)

    private fun consumeEvent(intent: Intent) {
        if (intent.hasExtra(EventNotifications.OPEN_EVENT)) {
            requestedEventId = intent.getLongExtra(EventNotifications.OPEN_EVENT, -1).takeIf { it > 0 }
            eventRequest++
            intent.removeExtra(EventNotifications.OPEN_EVENT)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra(FocusNotifications.OPEN_FOCUS, false)) {
            focusRequest++
            intent.removeExtra(FocusNotifications.OPEN_FOCUS)
        }
        consumeEvent(intent)
        setContent { StudyPlannerTheme { StudyPlannerApp(focusRequest, eventRequest, requestedEventId) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeEvent(intent)
        if (intent.getBooleanExtra(FocusNotifications.OPEN_FOCUS, false)) {
            focusRequest++
            intent.removeExtra(FocusNotifications.OPEN_FOCUS)
        }
    }
}

@Composable
private fun StudyPlannerApp(focusRequest: Int = 0, eventRequest: Int = 0, requestedEventId: Long? = null) {
    var tab by rememberSaveable { mutableStateOf("calendar") }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var tasksFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var chatMode by rememberSaveable { mutableStateOf<String?>(null) }
    var eventId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(focusRequest) { if (focusRequest > 0) page = "focus" }
    LaunchedEffect(eventRequest) {
        if (eventRequest > 0 && requestedEventId != null) {
            eventId = requestedEventId
            tab = "calendar"
            page = "reminder_event"
        }
    }
    val back = { page = null }
    val openChat: (ConversationMode?) -> Unit = { mode -> chatMode = mode?.name; page = "chat" }
    val openTasks: (String?) -> Unit = { name -> tasksFilter = name; page = "tasks" }

    BackHandler(page != null) { page = null }
    when (val currentPage = page) {
        "chat" -> ChatScreen(onBack = back, initialMode = chatMode?.let(ConversationMode::valueOf))
        "tasks" -> TasksScreen(onBack = back, planFilter = tasksFilter)
        "schedule" -> ScheduleScreen(onBack = back, onImport = { openChat(ConversationMode.IMPORT_SCHEDULE) })
        "focus" -> FocusScreen(onBack = back)
        "reminders" -> ReminderScreen(onBack = back)
        "goals" -> GoalsScreen(onBack = back)
        "backup" -> BackupScreen(onBack = back)
        "semester" -> SemesterScreen(onBack = back)
        "insights" -> ReviewScreen(onBack = back)
        "reminder_event" -> ReminderEventScreen(eventId ?: -1, onBack = back)
        null -> Scaffold(
            containerColor = BrutalPaper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().background(BrutalWhite).border(2.dp, BrutalBlack)
                        .navigationBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("calendar", "日历", Icons.Outlined.CalendarMonth),
                        Triple("stats", "统计", Icons.Outlined.BarChart),
                        Triple("profile", "我的", Icons.Outlined.Person)
                    ).forEach { (key, label, icon) ->
                        Column(
                            Modifier.weight(1f).background(if (tab == key) BrutalYellow else BrutalWhite)
                                .then(if (tab == key) Modifier.border(2.dp, BrutalBlack) else Modifier)
                                .selectable(tab == key, role = Role.Tab, onClick = { tab = key })
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(icon, null, tint = BrutalBlack)
                            Text(label, color = BrutalBlack, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    "stats" -> StatsScreen(onOpenTasks = openTasks, onOpenFeature = { page = it })
                    "profile" -> SettingsScreen(onOpenFeature = { page = it }, onOpenChat = { openChat(null) }, onOpenSchedule = { page = "schedule" })
                    else -> CalendarScreen(
                        onOpenChat = { openChat(null) }, onOpenStats = { tab = "stats" },
                        onOpenTasks = { openTasks(null) }, onOpenSchedule = { page = "schedule" },
                        onOpenFocus = { page = "focus" }
                    )
                }
            }
        }
        else -> { LaunchedEffect(currentPage) { page = null } }
    }
}
