package com.example.fio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fio.data.model.ProposedScheduleChange
import com.example.fio.data.model.formatTimestamp
import com.example.fio.ui.theme.LocalFioColors

enum class FioScreen(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    CALENDAR("Calendar & Schedule", Icons.Default.CalendarMonth),
    STUDY("Study Companion", Icons.AutoMirrored.Filled.MenuBook),
    TUTOR("Tutor & Practice", Icons.Default.School),
    TIMER("Timer & Lockdown", Icons.Default.Timer),
    NOTES("Notes & Graph", Icons.Default.EditNote),
    HEALTH("Sleep & Meals", Icons.Default.Favorite),
    STATS("Stats & Life Score", Icons.Default.Analytics),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun FioTopBar(
    title: String,
    onOpenNav: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalFioColors.current
    Surface(
        color = colors.surface,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenNav,
                    modifier = Modifier.testTag("nav_drawer_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Navigation Menu",
                        tint = colors.textPrimary
                    )
                }

                // Obsidian Eye Logo Indicator
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Fio Logo",
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
            FioDivider()
        }
    }
}

@Composable
fun FioDivider(modifier: Modifier = Modifier) {
    val colors = LocalFioColors.current
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = colors.border
    )
}

@Composable
fun FioCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalFioColors.current.surface,
    borderColor: Color = LocalFioColors.current.border,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(padding)
    ) {
        Column(content = content)
    }
}

@Composable
fun FioBadge(
    text: String,
    color: Color = LocalFioColors.current.primary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FioSidebarDrawer(
    currentScreen: FioScreen,
    profileName: String = "",
    onSelectScreen: (FioScreen) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFioColors.current
    Surface(
        color = colors.surface,
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 12.dp)
        ) {
            // Header with App Identity & User Profile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.primaryContainer)
                        .border(1.dp, colors.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Fio Eye Logo",
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (profileName.isNotBlank()) profileName else "Fio",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (profileName.isNotBlank()) "Personal Life Planner" else "effio • to realize & know",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            FioDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Navigation Items
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PLANNING & FOCUS",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                NavItem(
                    screen = FioScreen.DASHBOARD,
                    currentScreen = currentScreen,
                    onSelectScreen = onSelectScreen,
                    onClose = onClose,
                    customLabel = if (profileName.isNotBlank()) profileName else null
                )
                NavItem(FioScreen.CALENDAR, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.TIMER, currentScreen, onSelectScreen, onClose)

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ACADEMICS & STUDY",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                NavItem(FioScreen.STUDY, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.TUTOR, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.NOTES, currentScreen, onSelectScreen, onClose)

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "HEALTH & INSIGHTS",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                NavItem(FioScreen.HEALTH, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.STATS, currentScreen, onSelectScreen, onClose)
            }

            FioDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavItem(FioScreen.SETTINGS, currentScreen, onSelectScreen, onClose)
        }
    }
}

@Composable
private fun NavItem(
    screen: FioScreen,
    currentScreen: FioScreen,
    onSelectScreen: (FioScreen) -> Unit,
    onClose: () -> Unit,
    customLabel: String? = null
) {
    val colors = LocalFioColors.current
    val isSelected = screen == currentScreen
    val background = if (isSelected) colors.primaryContainer else Color.Transparent
    val textColor = if (isSelected) colors.primary else colors.textPrimary
    val iconColor = if (isSelected) colors.primary else colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable {
                onSelectScreen(screen)
                onClose()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("nav_item_${screen.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = customLabel ?: screen.title,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = customLabel ?: screen.title,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// Dialog for AI Calendar Proposal Confirmation (Section 1 requirement)
@Composable
fun AiScheduleProposalDialog(
    proposal: ProposedScheduleChange,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFioColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Calendar Proposal",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = proposal.reason,
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                FioCard(
                    backgroundColor = colors.surfaceElevated,
                    borderColor = colors.border,
                    padding = PaddingValues(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FioBadge(text = proposal.action, color = colors.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = proposal.title,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Time: ${formatTimestamp(proposal.startTimeMillis)} - ${formatTimestamp(proposal.endTimeMillis)}",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Type: ${proposal.eventType.name}",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Do you want to confirm and apply this change to your schedule?",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("confirm_proposal_button")
            ) {
                Text("Confirm & Apply", color = Color.White, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("decline_proposal_button")
            ) {
                Text("Decline", color = colors.textSecondary, fontSize = 13.sp)
            }
        }
    )
}
