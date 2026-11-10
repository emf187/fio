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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.fio.data.model.CourseSection
import com.example.fio.data.model.ProposedScheduleChange
import com.example.fio.data.model.formatTimestamp
import com.example.fio.ui.theme.FioType
import com.example.fio.ui.theme.LocalFioColors

/**
 * Quiet haptic ticks, used app-wide so touches feel physical: every
 * long-press action, habit logging, timer controls and confirmations.
 * One vibration each — never buzzing loops.
 */
class FioHaptics(private val view: View) {
    /** Standard long-press feedback (chips, cards, sessions). */
    fun longPress() {
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    }

    /** Light tick for small taps: logging a habit, copy, toggles. */
    fun tick() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    /** Positive confirmation: habit goal reached, timer finished. */
    fun confirm() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    /** Warning pulse: broken lockdown, deleted data. */
    fun warn() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }
}

@Composable
fun rememberFioHaptics(): FioHaptics {
    val view = androidx.compose.ui.platform.LocalView.current
    return remember { FioHaptics(view) }
}

enum class FioScreen(val title: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Home),
    CALENDAR("Calendar", Icons.Default.CalendarMonth),
    TIMETABLE("Timetable", Icons.Default.Schedule),
    STUDY("Study", Icons.AutoMirrored.Filled.MenuBook),
    TUTOR("Tutor", Icons.Default.School),
    CALCULATOR("Calculator", Icons.Default.Calculate),
    TIMER("Focus timer", Icons.Default.Timer),
    NOTES("Notes", Icons.Default.EditNote),
    GALLERY("Gallery", Icons.Default.PhotoLibrary),
    HEALTH("Health", Icons.Default.Favorite),
    STATS("Progress", Icons.Default.Insights),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun FioTopBar(
    title: String,
    onOpenNav: () -> Unit,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalFioColors.current
    Surface(
        color = colors.background,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showMenu) {
                IconButton(
                    onClick = onOpenNav,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("nav_drawer_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open menu",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (showMenu) 4.dp else 10.dp)
                    .clickable(onClick = onOpenNav)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun FioDivider(modifier: Modifier = Modifier) {
    val colors = LocalFioColors.current
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.7.dp,
        color = colors.border.copy(alpha = 0.65f)
    )
}

@Composable
fun FioCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalFioColors.current.surface,
    borderColor: Color = LocalFioColors.current.border,
    padding: PaddingValues = PaddingValues(18.dp),
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    // Quiet tonal block, no outline: rhythm comes from spacing, not boxes.
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .padding(padding)
    ) {
        Column(content = content)
    }
}

@Composable
fun FioBadge(
    text: String,
    color: Color = LocalFioColors.current.accent,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp
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
            .width(290.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp, horizontal = 10.dp)
        ) {
            // Header with user profile (no logo � clean)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = if (profileName.isNotBlank()) profileName else "Fio",
                        color = colors.textPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        text = "Private and offline",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            FioDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Navigation Items
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Your day")

                NavItem(
                    screen = FioScreen.DASHBOARD,
                    currentScreen = currentScreen,
                    onSelectScreen = onSelectScreen,
                    onClose = onClose,
                    customLabel = if (profileName.isNotBlank()) profileName else null
                )
                NavItem(FioScreen.CALENDAR, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.TIMETABLE, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.TIMER, currentScreen, onSelectScreen, onClose)

                Spacer(modifier = Modifier.height(10.dp))
                SectionLabel("Study")

                NavItem(FioScreen.STUDY, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.TUTOR, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.CALCULATOR, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.NOTES, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.GALLERY, currentScreen, onSelectScreen, onClose)

                Spacer(modifier = Modifier.height(10.dp))
                SectionLabel("Health and progress")

                NavItem(FioScreen.HEALTH, currentScreen, onSelectScreen, onClose)
                NavItem(FioScreen.STATS, currentScreen, onSelectScreen, onClose)
            }

            FioDivider(modifier = Modifier.padding(vertical = 10.dp))

            NavItem(FioScreen.SETTINGS, currentScreen, onSelectScreen, onClose)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LocalFioColors.current.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
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
    val background = if (isSelected) colors.surfaceElevated else Color.Transparent
    val textColor = if (isSelected) colors.textPrimary else colors.textSecondary
    val iconColor = if (isSelected) colors.accent else colors.textMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable {
                onSelectScreen(screen)
                onClose()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("nav_item_${screen.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = customLabel ?: screen.title,
            tint = iconColor,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = customLabel ?: screen.title,
            color = textColor,
            fontSize = 14.5.sp,
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
                shape = RoundedCornerShape(12.dp),
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

// ---------------------------------------------------------------------------
// Fio design system primitives (v2)
// ---------------------------------------------------------------------------

/** Large quiet screen heading. No card, no border � type carries the hierarchy. */
@Composable
fun FioScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalFioColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = FioType.display, color = colors.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = subtitle, style = FioType.small, color = colors.textMuted)
            }
        }
        trailing?.invoke()
    }
}

/** Section heading with an optional plain-text action. */
@Composable
fun FioSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalFioColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = FioType.section,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = FioType.label,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * The one primary action in a view. In the dark theme it is a white pill with
 * ink text � the reference's unmistakable action treatment, and always legible.
 */
@Composable
fun FioPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    loading: Boolean = false
) {
    val colors = LocalFioColors.current
    val fill = if (enabled) colors.textPrimary else colors.surfaceElevated
    val content = if (enabled) colors.background else colors.textMuted
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fill)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .heightIn(min = if (compact) 36.dp else 44.dp)
            .padding(
                horizontal = if (compact) 10.dp else 20.dp,
                vertical = if (compact) 8.dp else 11.dp
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(if (compact) 14.dp else 16.dp), strokeWidth = 2.dp, color = content)
            Spacer(modifier = Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(if (compact) 15.dp else 17.dp)
            )
            Spacer(modifier = Modifier.width(if (compact) 5.dp else 8.dp))
        }
        Text(
            text = text,
            style = FioType.label,
            fontSize = if (compact) 12.sp else 13.sp,
            color = content,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Quiet companion action: tonal pill, no border. */
@Composable
fun FioSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    loading: Boolean = false
) {
    val colors = LocalFioColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceElevated)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .heightIn(min = if (compact) 36.dp else 44.dp)
            .padding(
                horizontal = if (compact) 10.dp else 18.dp,
                vertical = if (compact) 8.dp else 10.dp
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(if (compact) 14.dp else 16.dp), strokeWidth = 2.dp, color = colors.primary)
            Spacer(modifier = Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) colors.textPrimary else colors.textMuted,
                modifier = Modifier.size(if (compact) 15.dp else 16.dp)
            )
            Spacer(modifier = Modifier.width(if (compact) 5.dp else 7.dp))
        }
        Text(
            text = text,
            style = FioType.label,
            fontSize = if (compact) 12.sp else 13.sp,
            color = if (enabled) colors.textPrimary else colors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** A settings/list row: title, a short plain-English subtitle, optional trailing. */
@Composable
fun FioRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalFioColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 7.dp)
                else Modifier.padding(vertical = 7.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = FioType.body, color = colors.textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(text = subtitle, style = FioType.caption, color = colors.textMuted)
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Selectable pill chip (filters, subject tabs, tags). */
@Composable
fun FioPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFioColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.textPrimary else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = FioType.label,
            color = if (selected) colors.background else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Thin mint progress line used in cards and stats. */
@Composable
fun FioProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val colors = LocalFioColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(999.dp))
                .background(colors.accent)
        )
    }
}

/**
 * Edit a subject's title and details. Opened by long-pressing a subject chip in
 * Study or Tutor, so the whole course can be described (course name, lecturer,
 * syllabus notes) without hunting through another screen.
 */
@Composable
fun CourseDetailsDialog(
    course: CourseSection,
    onDismiss: () -> Unit,
    onSave: (name: String, details: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = LocalFioColors.current
    var name by remember(course.id) { mutableStateOf(course.name) }
    var details by remember(course.id) { mutableStateOf(course.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Subject details", style = FioType.section, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Title", color = colors.textMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Details", color = colors.textMuted) },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            FioPrimaryButton(
                text = "Save",
                onClick = { onSave(name.trim(), details.trim()) }
            )
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = colors.error, fontSize = 13.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        }
    )
}
