package com.ytone.longcare.core.ui.permission

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ytone.longcare.common.utils.PermissionGuideId
import com.ytone.longcare.common.utils.PermissionGuideItem
import com.ytone.longcare.core.ui.R

/** Settings actions report launch success only; returning refreshes observable state, never a grant assumption. */
@Composable
fun BackgroundSettingsDialog(
    initialItems: List<PermissionGuideItem>,
    refreshItems: () -> List<PermissionGuideItem>,
    onItemAction: (PermissionGuideItem) -> Boolean,
    onDismiss: () -> Unit,
    @StringRes dismissLabelRes: Int = R.string.common_close,
    onConfirm: (() -> Unit)? = null,
    @StringRes confirmLabelRes: Int = R.string.background_settings_start_service,
    onCheckSystemPermissions: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settingsUnavailableMessage = stringResource(R.string.permission_settings_unavailable)
    var items by remember { mutableStateOf(initialItems) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        items = refreshItems()
    }

    // Match the business screens' blue/white identity without changing the app-wide theme.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dialogColors = MaterialTheme.colorScheme.copy(
        primary = if (dark) Color(0xFFA8C7FF) else Color(0xFF245FDB),
        onPrimary = if (dark) Color(0xFF123263) else Color.White,
        primaryContainer = if (dark) Color(0xFF213B5E) else Color(0xFFEAF2FF),
        onPrimaryContainer = if (dark) Color(0xFFCFE0FF) else Color(0xFF245FDB),
        secondaryContainer = if (dark) Color(0xFF163E38) else Color(0xFFE6F5EF),
        onSecondaryContainer = if (dark) Color(0xFF9DDDC7) else Color(0xFF17735A),
        surface = if (dark) Color(0xFF17202D) else Color.White,
        onSurface = if (dark) Color(0xFFE3EAF3) else Color(0xFF1F2D42),
        onSurfaceVariant = if (dark) Color(0xFFB5C1D1) else Color(0xFF596A80),
        surfaceContainerLow = if (dark) Color(0xFF1D2939) else Color(0xFFF6F8FC),
        outlineVariant = if (dark) Color(0xFF354459) else Color(0xFFE3EAF3),
    )
    val title = stringResource(R.string.background_settings_title)
    MaterialTheme(colorScheme = dialogColors) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .semantics { paneTitle = title },
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = dialogColors.surface),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(
                                dialogColors.primary,
                                lerp(dialogColors.primary, dialogColors.onPrimary, 0.06f),
                            )))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(dialogColors.surface, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = dialogColors.primary)
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = dialogColors.onPrimary,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                    }
                    Column(modifier = Modifier.weight(1f, fill = false).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        ) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.background_settings_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            items.forEach { item ->
                                BackgroundSettingsRow(item = item, onAction = {
                                    if (!onItemAction(item)) {
                                        Toast.makeText(context, settingsUnavailableMessage, Toast.LENGTH_SHORT).show()
                                    }
                                })
                                Spacer(Modifier.height(8.dp))
                            }
                            TextButton(
                                onClick = { showDetails = !showDetails },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(
                                    if (showDetails) R.string.background_settings_hide_details
                                    else R.string.background_settings_show_details,
                                ))
                                Icon(
                                    imageVector = if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                                )
                            }
                            if (showDetails) {
                                items.forEach { item -> BackgroundSettingsDetails(item) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = dialogColors.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = dialogColors.onSurfaceVariant),
                            ) {
                                Text(stringResource(dismissLabelRes))
                            }
                            if (onCheckSystemPermissions != null) {
                                Button(
                                    onClick = onCheckSystemPermissions,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.background_settings_check_system_permissions))
                                }
                            }
                            if (onConfirm != null) {
                                Button(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(confirmLabelRes))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundSettingsRow(item: PermissionGuideItem, onAction: () -> Unit) {
    val title = stringResource(item.titleRes)
    // Public system states stay visible; manufacturer instructions remain manual checks.
    val summaryRes = when (item.id) {
        PermissionGuideId.BATTERY_OPTIMIZATION -> item.statusRes ?: R.string.background_settings_battery_summary
        PermissionGuideId.BACKGROUND_ACTIVITY -> item.statusRes ?: R.string.background_settings_background_activity_summary
        PermissionGuideId.AUTO_START -> R.string.background_settings_auto_start_summary
        PermissionGuideId.MANUFACTURER_POPUP -> R.string.background_settings_popup_summary
    }
    val icon = when (item.id) {
        PermissionGuideId.BATTERY_OPTIMIZATION -> Icons.Outlined.BatteryFull
        PermissionGuideId.BACKGROUND_ACTIVITY -> Icons.Outlined.Tune
        PermissionGuideId.AUTO_START -> Icons.Outlined.Autorenew
        PermissionGuideId.MANUFACTURER_POPUP -> Icons.Outlined.Notifications
    }
    val actionDescription = stringResource(R.string.background_settings_open_description, title)
    val colors = MaterialTheme.colorScheme
    val (iconContainer, iconColor) = if (item.id == PermissionGuideId.BATTERY_OPTIMIZATION) {
        colors.secondaryContainer to colors.onSecondaryContainer
    } else {
        colors.primaryContainer to colors.onPrimaryContainer
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(stringResource(summaryRes)) },
        leadingContent = {
            Box(
                modifier = Modifier.size(36.dp).background(iconContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onAction)
            .semantics { contentDescription = actionDescription },
    )
}

@Composable
private fun BackgroundSettingsDetails(item: PermissionGuideItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = stringResource(item.titleRes),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        val message = stringResource(item.messageRes)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
