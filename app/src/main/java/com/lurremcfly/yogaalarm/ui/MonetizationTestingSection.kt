package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun MonetizationTestingSection(
    testingMode: Boolean,
    proAccess: Boolean,
    enabled: Boolean,
    onTestingModeChange: (Boolean) -> Unit,
    onProAccessChange: (Boolean) -> Unit,
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
            Text(if (proAccess) "Pro features are on for testing." else "Free features are on for testing.", color = MutedInk)
        } else {
            Text("Turn on to test Free or Pro access on this device.", color = MutedInk)
        }
    }
}
