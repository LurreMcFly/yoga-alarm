package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.billing.ProEntitlement
import com.lurremcfly.yogaalarm.R
import com.lurremcfly.yogaalarm.model.ProPlan
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsSheet(
    entitlement: ProEntitlement?,
    subscriptionAutoRenewing: Boolean?,
    onDismiss: () -> Unit,
    onOpenPro: () -> Unit,
    onManageSubscription: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text("Settings", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text("Pro", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            when (entitlement) {
                ProEntitlement.LIFETIME -> {
                    Text("Lifetime Pro active", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("One-time purchase. No renewal or expiry date.", color = MutedInk)
                }
                ProEntitlement.SUBSCRIPTION -> {
                    Text("Subscription Pro active", color = Ink, fontWeight = FontWeight.SemiBold)
                }
                null -> {
                    TextButton(onClick = onOpenPro) {
                        Text("Get Pro", color = Forest)
                    }
                }
            }
            if (entitlement == ProEntitlement.SUBSCRIPTION || subscriptionAutoRenewing != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when (subscriptionAutoRenewing) {
                        false -> "Subscription renewal canceled."
                        true -> "Subscription renews automatically."
                        null -> "Check your subscription status in Google Play."
                    },
                    color = MutedInk,
                )
                TextButton(onClick = onManageSubscription) {
                    Text(
                        if (subscriptionAutoRenewing == false) "View subscription end date in Google Play"
                        else "View renewal date in Google Play",
                        color = Forest,
                    )
                }
                OutlinedButton(
                    onClick = onManageSubscription,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest),
                ) {
                    Text(
                        if (subscriptionAutoRenewing == false) "Manage subscription" else "Cancel subscription",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        entitlement == ProEntitlement.LIFETIME -> "Your lifetime Pro access is unaffected by subscription cancellation."
                        subscriptionAutoRenewing == false -> "Pro stays active until your paid period ends."
                        else -> "Confirm cancellation in Google Play. Pro stays active until your paid period ends."
                    },
                    color = MutedInk,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Hairline)
            TextButton(onClick = onOpenPrivacy) {
                Text("Privacy & safety", color = Forest)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = MutedInk,
                fontSize = 14.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyInfoSheet(
    onDismiss: () -> Unit,
    onReadPolicy: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text("Privacy & safety", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Text("Your camera stays private", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Pose detection runs on your device. Camera frames are never recorded, saved, or uploaded.",
                color = MutedInk,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text("Movement, not medical advice", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Yoga Alarm is a movement alarm, not a medical device. It does not diagnose, treat, cure, or prevent any condition.",
                color = MutedInk,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                onClick = onReadPolicy,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
            ) {
                Text("Read privacy policy", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallSheet(
    entitlement: ProEntitlement?,
    subscriptionAutoRenewing: Boolean?,
    prices: Map<ProPlan, String>,
    billingReady: Boolean,
    loadingPrices: Boolean,
    purchaseInProgress: Boolean,
    billingMessage: String?,
    onDismiss: () -> Unit,
    onPurchase: (ProPlan) -> Unit,
    onRestorePurchases: () -> Unit,
    onRetry: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    var preferredPlan by remember { mutableStateOf(ProPlan.YEARLY) }
    val selectedPlan = preferredPlan.takeIf { it in prices }
        ?: ProPlan.YEARLY.takeIf { it in prices }
        ?: ProPlan.entries.firstOrNull { it in prices }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SoftSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 16.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = Forest)
                }
                Text("Make every morning yours", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Unlock all poses and sounds, choose 1–10 poses, and set holds from 5–60 seconds.",
                    color = MutedInk,
                    fontSize = 15.sp,
                )
                if (entitlement != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(999.dp), color = AlarmLime.copy(alpha = 0.55f)) {
                        Text(
                            "PRO ACTIVE · ${entitlement.displayName.uppercase(Locale.US)}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Forest,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Column(Modifier.selectableGroup()) {
                    ProPlan.entries.forEach { plan ->
                        val selected = selectedPlan == plan
                        val available = plan in prices
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    enabled = available && !purchaseInProgress && entitlement == null,
                                    role = Role.RadioButton,
                                    onClick = { preferredPlan = plan },
                                ),
                            shape = RoundedCornerShape(22.dp),
                            color = if (selected) Color(0xFFF1FFD9) else Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AlarmLime else Hairline),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    enabled = available && !purchaseInProgress && entitlement == null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Forest),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(plan.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (plan != ProPlan.MONTHLY) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (plan == ProPlan.YEARLY) "ANNUAL" else "BEST VALUE",
                                                color = Forest,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Text(
                                        prices[plan]?.let { "$it ${plan.billingPeriod}" }
                                            ?: if (loadingPrices) "Loading price…" else "Currently unavailable",
                                        color = if (available) Ink else MutedInk,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (prices.isEmpty()) {
                    Text(
                        if (loadingPrices) "Loading Google Play prices…"
                        else "Plans are currently unavailable. Retry to load Google Play prices.",
                        color = MutedInk,
                    )
                }
                if ((!loadingPrices && ProPlan.entries.any { it !in prices }) || !billingReady) {
                    TextButton(onClick = onRetry, enabled = !purchaseInProgress) {
                        Text("Retry", color = Forest)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(onClick = onRestorePurchases, enabled = !purchaseInProgress) {
                        Text("Restore purchases", color = Forest, fontWeight = FontWeight.SemiBold)
                    }
                    if (entitlement == ProEntitlement.SUBSCRIPTION || subscriptionAutoRenewing != null) {
                        TextButton(onClick = onManageSubscription) {
                            Text("Manage or cancel subscription", color = Forest, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            if (entitlement == ProEntitlement.LIFETIME) "Your lifetime Pro access is unaffected by subscription cancellation. See subscription dates in Google Play."
                            else if (subscriptionAutoRenewing == false) "Renewal canceled. Pro stays active until your paid period ends. See the exact end date in Google Play."
                            else "Google Play shows your renewal date and cancellation options.",
                            color = MutedInk, fontSize = 12.sp, textAlign = TextAlign.Center,
                        )
                    }
                }
                billingMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth(),
                        color = MutedInk,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            HorizontalDivider(color = Hairline)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selectedPlan != null && entitlement == null) {
                    Text(
                        "${selectedPlan.displayName} · ${prices.getValue(selectedPlan)} ${selectedPlan.billingPeriod}",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { selectedPlan?.let(onPurchase) },
                    enabled = entitlement == null && billingReady && selectedPlan != null && !purchaseInProgress,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8ED8FF),
                        contentColor = Color(0xFF102A43),
                    ),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    if (entitlement == null && !purchaseInProgress && billingReady && selectedPlan != null) {
                        Image(painterResource(R.drawable.ic_google_play), contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        when {
                            entitlement != null -> "Pro is active"
                            purchaseInProgress -> "Opening Google Play…"
                            !billingReady -> "Connecting to Google Play…"
                            selectedPlan == null && loadingPrices -> "Loading plans…"
                            selectedPlan == null -> "Plans unavailable"
                            selectedPlan == ProPlan.LIFETIME -> "Buy with Google Play"
                            else -> "Subscribe with Google Play"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (entitlement == null && selectedPlan != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (selectedPlan) {
                            ProPlan.MONTHLY -> "Renews monthly. Cancel anytime in Google Play."
                            ProPlan.YEARLY -> "Renews annually. Cancel anytime in Google Play."
                            ProPlan.LIFETIME -> "One-time payment. No subscription."
                        },
                        color = MutedInk,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
