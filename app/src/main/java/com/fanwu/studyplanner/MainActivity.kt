package com.fanwu.studyplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.fanwu.studyplanner.ui.calendar.CalendarScreen
import com.fanwu.studyplanner.ui.chat.ChatScreen
import com.fanwu.studyplanner.ui.settings.SettingsScreen
import com.fanwu.studyplanner.ui.theme.StudyPlannerTheme

private const val SCREEN_CALENDAR = "calendar"
private const val SCREEN_CHAT = "chat"
private const val SCREEN_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyPlannerTheme {
                var screen by rememberSaveable { mutableStateOf(SCREEN_CALENDAR) }
                when (screen) {
                    SCREEN_CHAT -> ChatScreen(onBack = { screen = SCREEN_CALENDAR })
                    SCREEN_SETTINGS -> SettingsScreen(onBack = { screen = SCREEN_CALENDAR })
                    else -> CalendarScreen(
                        onOpenChat = { screen = SCREEN_CHAT },
                        onOpenSettings = { screen = SCREEN_SETTINGS }
                    )
                }
            }
        }
    }
}
