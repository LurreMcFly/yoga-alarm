package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.billing.BillingUiState
import com.lurremcfly.yogaalarm.model.AlarmConfig
import com.lurremcfly.yogaalarm.model.ProTrial

@Composable
internal fun MonetizationTestingSection(
    trial: ProTrial,
    billing: BillingUiState,
    alarms: List<AlarmConfig>,
    enabled: Boolean,
    nonPaying: Boolean,
    expiryBanner: Boolean,
    message: String?,
    onReset: () -> Unit,
    onAdvance: () -> Unit,
    onExpiryBannerChange: (Boolean) -> Unit,
    onNonPayingChange: (Boolean) -> Unit,
) {
    if (!BuildConfig.PRO_TRIAL_INTERNAL_TESTING) return
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmNonPaying by rememberSaveable { mutableStateOf(false) }
    val previewControlsEnabled = enabled && (billing.entitlement == null || nonPaying)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = Hairline)
        Text("Monetization testing", color = Ink, fontWeight = FontWeight.Bold)
        Text("Internal build only. Purchases and restoration still use Google Play.", color = MutedInk)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Test as non-paying user", modifier = Modifier.weight(1f), color = Ink)
            Switch(checked = nonPaying, enabled = enabled, onCheckedChange = {
                if (it) confirmNonPaying = true else onNonPayingChange(false)
            })
        }
        Text(
            if (nonPaying) "TEST MODE: paid access is ignored locally; checkout is disabled. Your real Play ownership is unchanged. Turn this off to use your purchase again."
            else "Already own Pro? Enable this to test preview and expiry, then start/reset the local preview.",
            color = MutedInk,
        )
        Text(
            "Enrollment: ${trial.enrollment} · retired: ${trial.retired}\n" +
                "Completed: ${trial.dates.size}/5 · remaining: ${trial.remaining}\n" +
                "Pending milestone: ${trial.pendingMilestone ?: "none"} · presented: ${trial.presented.sorted()}\n" +
                "Transition: ${trial.transition} · awaiting purchase: ${trial.awaitingPurchaseResolution}\n" +
                "Real Play entitlement: ${billing.entitlement ?: "none"}\n" +
                "Billing: ${billing.ownershipResolution} · connected: ${billing.connected}\n" +
                "Catalog settled: ${billing.catalogSettled} · offers: ${billing.offers.size} · can buy: ${billing.canPurchase}\n" +
                "Checkout: ${billing.purchasing} · verifying: ${billing.verifyingPurchases} · pending: ${billing.pendingPurchase}\n" +
                "Trusted offline paid access: ${billing.recoverablePaidAccess}",
            color = MutedInk,
        )
        Text("Release blocker: ClientPurchaseVerifier cannot recover trusted paid access after an offline cold start.", color = MutedInk)
        if (!enabled) Text("Controls paused during alarms, snooze, routines or billing activity.", color = MutedInk)
        OutlinedButton(onClick = { confirmReset = true }, enabled = previewControlsEnabled, modifier = Modifier.fillMaxWidth()) {
            Text("Start / reset local preview")
        }
        OutlinedButton(onClick = onAdvance, enabled = previewControlsEnabled && trial.active, modifier = Modifier.fillMaxWidth()) {
            Text("Advance one qualifying day")
        }
        Text("Advance uses a synthetic distinct date. It can convert future alarms on day five, but never rings an alarm or adds streak/history entries. Close Settings to see milestones.", color = MutedInk)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Expiry banner with optional upgrade", modifier = Modifier.weight(1f), color = Ink)
            Switch(checked = expiryBanner, onCheckedChange = onExpiryBannerChange, enabled = enabled)
        }
        Text(if (expiryBanner) "Banner selected. Reset and repeat to compare flows." else "Automatic paywall selected (current behavior). It waits for a safe Home screen and buyable Play offers.", color = MutedInk)
        message?.let { Text(it, color = Forest) }
        billing.message?.let { Text(it, color = MutedInk) }
        Text("Saved alarm customization", color = Ink, fontWeight = FontWeight.SemiBold)
        if (alarms.isEmpty()) Text("No saved alarms.", color = MutedInk)
        alarms.forEach { alarm ->
            Text(
                "${alarm.name} (${alarm.id})\nCurrent: ${alarm.customization.summary}\n" +
                    "Confirmed fallback: ${alarm.trialFallback?.summary ?: "none"}\n" +
                    "Trial customization: ${alarm.trialCustomization?.summary ?: "none"}\n" +
                    "Original restoration backup: ${alarm.trialOriginal?.summary ?: "none"}",
                color = MutedInk,
            )
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset local preview?") },
        text = { Text("Selects the preview cohort with five days left and clears trial milestones. Keeps alarms, schedules, history, purchases and current routine/sound edits. Keeps confirmed fallbacks, but discards old original-routine restoration backups. Nothing is restored automatically. To repeat conversion after expiry, edit a Pro routine and confirm its fallback again.") },
        confirmButton = {
            TextButton(enabled = previewControlsEnabled, onClick = { confirmReset = false; onReset() }) { Text("Reset preview") }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
    if (confirmNonPaying) AlertDialog(
        onDismissRequest = { confirmNonPaying = false },
        title = { Text("Test without paid access?") },
        text = { Text("Your real Play purchase is kept. This mode persists after restart and disables checkout. Start/reset the preview after enabling it. Tests use your real saved alarms: trial customizations can switch to their confirmed free fallbacks. Existing Pro alarms without a confirmed fallback are kept unchanged. Turning this off restores paid access, but never automatically restores or overwrites alarm edits.") },
        confirmButton = { TextButton(enabled = enabled, onClick = { confirmNonPaying = false; onNonPayingChange(true) }) { Text("Enable test mode") } },
        dismissButton = { TextButton(onClick = { confirmNonPaying = false }) { Text("Cancel") } },
    )
}
