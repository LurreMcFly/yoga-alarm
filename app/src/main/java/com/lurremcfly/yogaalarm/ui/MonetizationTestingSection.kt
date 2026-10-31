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
    proAccess: Boolean,
    enabled: Boolean,
    onProAccessChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Hairline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Test Pro access", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
            Switch(checked = proAccess, enabled = enabled, onCheckedChange = onProAccessChange)
        }
        Text(if (proAccess) "Pro is on for this test build." else "Free access is on for this test build.", color = MutedInk)
    }
}
