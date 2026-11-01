package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MonetizationTestingSection(
    testingMode: Boolean,
    proAccess: Boolean,
    trialDaysRemaining: Int?,
    enabled: Boolean,
    onTestingModeChange: (Boolean) -> Unit,
    onProAccessChange: (Boolean) -> Unit,
    onTrialDaysRemainingChange: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Hairline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Testing mode", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
            Switch(checked = testingMode, enabled = enabled, onCheckedChange = onTestingModeChange)
        }
        if (testingMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Test Pro access", modifier = Modifier.weight(1f), color = Ink)
                Switch(checked = proAccess, enabled = enabled, onCheckedChange = onProAccessChange)
            }
            Text(when {
                proAccess -> "Pro features are on for testing."
                trialDaysRemaining != null -> "Trial testing is on."
                else -> "Free features are on for testing."
            }, color = MutedInk)
            if (!proAccess) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Test trial", modifier = Modifier.weight(1f), color = Ink)
                    Switch(checked = trialDaysRemaining != null, enabled = enabled,
                        onCheckedChange = { onTrialDaysRemainingChange(if (it) 5 else null) })
                }
                if (trialDaysRemaining != null) {
                    Text("Trial day", color = Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { day ->
                            FilterChip(
                                selected = trialDaysRemaining == 6 - day,
                                onClick = { onTrialDaysRemainingChange(6 - day) },
                                enabled = enabled,
                                label = { Text("Day $day") },
                            )
                        }
                    }
                    Text(
                        if (trialDaysRemaining == 0) "Trial complete · 0 alarm days left"
                        else "$trialDaysRemaining alarm ${if (trialDaysRemaining == 1) "day" else "days"} left · completes after real alarm days",
                        color = MutedInk,
                    )
                }
            }
        } else {
            Text("Turn on to test Free or Pro access on this device.", color = MutedInk)
        }
    }
}
