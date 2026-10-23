package com.fanwu.studyplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fanwu.studyplanner.ui.calendar.CalendarScreen
import com.fanwu.studyplanner.ui.chat.ChatScreen
import com.fanwu.studyplanner.ui.settings.SettingsScreen
import com.fanwu.studyplanner.ui.stats.StatsScreen
import com.fanwu.studyplanner.ui.tasks.TasksScreen
import com.fanwu.studyplanner.ui.theme.ApricotOrange
import com.fanwu.studyplanner.ui.theme.CreamBackground
import com.fanwu.studyplanner.ui.theme.NavDark
import com.fanwu.studyplanner.ui.theme.StudyPlannerTheme

private const val TAB_CALENDAR = "calendar"
private const val TAB_STATS = "stats"
private const val TAB_PROFILE = "profile"

private const val SUB_CHAT = "chat"
private const val SUB_TASKS = "tasks"

private data class BottomTab(val key: String, val icon: ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(TAB_CALENDAR, Icons.Default.CalendarMonth),
    BottomTab(TAB_STATS, Icons.Default.Equalizer),
    BottomTab(TAB_PROFILE, Icons.Default.Person)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyPlannerTheme {
                var tab by rememberSaveable { mutableStateOf(TAB_CALENDAR) }
                var subPage by rememberSaveable { mutableStateOf<String?>(null) }

                when (subPage) {
                    SUB_CHAT -> ChatScreen(onBack = { subPage = null })
                    SUB_TASKS -> TasksScreen(onBack = { subPage = null })
                    else -> Scaffold(
                        containerColor = CreamBackground,
                        bottomBar = { AppBottomBar(current = tab, onSelect = { tab = it }) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (tab) {
                                TAB_STATS -> StatsScreen(
                                    onOpenTasks = { subPage = SUB_TASKS }
                                )
                                TAB_PROFILE -> SettingsScreen()
                                else -> CalendarScreen(
                                    onOpenChat = { subPage = SUB_CHAT },
                                    onOpenStats = { tab = TAB_STATS }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 深色圆角悬浮底栏：白色图标 + 选中项下方橙点 */
@Composable
private fun AppBottomBar(current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(NavDark)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BOTTOM_TABS.forEach { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(item.key) }
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.key,
                    tint = if (current == item.key) Color.White else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (current == item.key) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(ApricotOrange)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
