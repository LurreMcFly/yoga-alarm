package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    trialStatus: String? = null,
    onRestorePurchases: () -> Unit = {},
    subscriptionAutoRenewing: Boolean?,
    weekStartsOnSunday: Boolean,
    onWeekStartsOnSundayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenPro: () -> Unit,
    onManageSubscription: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenHelp: () -> Unit,
    monetizationTesting: @Composable (() -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SoftSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Settings", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preferences", color = MutedInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Hairline),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "First day of week",
                            modifier = Modifier.weight(1f),
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(shape = RoundedCornerShape(999.dp), color = SoftSurface) {
                            Row(Modifier.padding(4.dp).selectableGroup()) {
                                listOf(false to "Monday", true to "Sunday").forEach { (startsOnSunday, label) ->
                                    val selected = weekStartsOnSunday == startsOnSunday
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .selectable(
                                                selected = selected,
                                                onClick = { onWeekStartsOnSundayChange(startsOnSunday) },
                                                role = Role.RadioButton,
                                            )
                                            .semantics { contentDescription = label },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (selected) AlarmLime else Color.Transparent,
                                    ) {
                                        Text(
                                            label.take(1),
                                            modifier = Modifier.width(48.dp).heightIn(min = 48.dp)
                                                .padding(vertical = 12.dp),
                                            color = if (selected) Forest else MutedInk,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Hairline),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Yoga Alarm Pro", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (entitlement == null) trialStatus?.let { Text(it, color = Forest) }
                    TextButton(onClick = onRestorePurchases) { Text("Restore purchases", color = Forest) }
                    when (entitlement) {
                        ProEntitlement.LIFETIME -> {
                            Text("Lifetime Pro active", color = Forest, fontWeight = FontWeight.SemiBold)
                            Text("One-time purchase. No renewal or expiry date.", color = MutedInk, fontSize = 14.sp)
                        }
                        ProEntitlement.SUBSCRIPTION -> {
                            Text("Subscription Pro active", color = Forest, fontWeight = FontWeight.SemiBold)
                        }
                        null -> {
                            Text("More poses, sounds, and a routine that’s yours.", color = MutedInk, fontSize = 14.sp)
                            Button(
                                onClick = onOpenPro,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                            ) {
                                Text(if (trialStatus != null) "Keep Pro" else "Get Pro", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (entitlement == ProEntitlement.SUBSCRIPTION || subscriptionAutoRenewing != null) {
                        Text(
                            when (subscriptionAutoRenewing) {
                                false -> "Subscription renewal canceled. View your end date in Google Play."
                                true -> "Renews automatically. View your renewal date or cancel in Google Play."
                                null -> "View your renewal date or cancel in Google Play."
                            },
                            color = MutedInk,
                            fontSize = 14.sp,
                        )
                        OutlinedButton(
                            onClick = onManageSubscription,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest),
                            border = BorderStroke(1.dp, Hairline),
                        ) {
                            Text("Manage subscription", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            if (entitlement == ProEntitlement.LIFETIME)
                                "Your lifetime Pro access is unaffected by subscription cancellation."
                            else "Pro stays active until your paid period ends.",
                            color = MutedInk,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Help & info", color = MutedInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Hairline),
                ) {
                    Column {
                        TextButton(
                            onClick = onOpenHelp,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            Text("How to use Yoga Alarm", modifier = Modifier.fillMaxWidth(), color = Forest, fontSize = 15.sp)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Hairline)
                        TextButton(
                            onClick = onOpenPrivacy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            Text("Privacy & safety", modifier = Modifier.fillMaxWidth(), color = Forest, fontSize = 15.sp)
                        }
                    }
                }
            }
            if (BuildConfig.PRO_TRIAL_INTERNAL_TESTING) monetizationTesting?.invoke()
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
            if (BuildConfig.MONETIZATION_ANALYTICS_ENABLED) {
                Spacer(Modifier.height(18.dp))
                Text("Usage measurement", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "This version uses Firebase Analytics to understand setup, completed alarm days and purchase flows. It uses a pseudonymous installation identifier and app/device information. Custom events exclude camera data, pose details, alarm names and alarm times. See the privacy policy for collection and retention details.",
                    color = MutedInk, fontSize = 14.sp,
                )
            }
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
    catalogSettled: Boolean = true,
    canPurchase: Boolean = true,
    verifyingPurchase: Boolean = false,
    pendingPayment: Boolean = false,
    trialActive: Boolean = false,
    trialExhausted: Boolean = false,
    customizationSummary: String? = null,
    entryFeature: String? = null,
    onViewFreeSettings: () -> Unit = {},
    onPlanSelected: (ProPlan) -> Unit = {},
) {
    var selectedPlan by rememberSaveable { mutableStateOf<ProPlan?>(null) }
    val plan = selectedPlan
    val planOrder = listOf(ProPlan.LIFETIME, ProPlan.YEARLY, ProPlan.MONTHLY)
    LaunchedEffect(catalogSettled, prices) {
        if (catalogSettled && selectedPlan == null) selectedPlan = planOrder.firstOrNull { it in prices }
    }
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
                Text(when {
                    trialExhausted -> "Keep your custom routine"
                    trialActive -> "Keep your morning routine yours"
                    else -> "Make every morning yours"
                }, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Unlock all poses and sounds, choose 1–10 poses, and set holds from 5–60 seconds.",
                    color = MutedInk,
                    fontSize = 15.sp,
                )
                entryFeature?.let { Text(it, color = Forest, modifier = Modifier.padding(top = 8.dp)) }
                customizationSummary?.let { Text(it, color = MutedInk, modifier = Modifier.padding(top = 8.dp)) }
                if (trialActive || trialExhausted) TextButton(onClick = onViewFreeSettings) { Text("View free settings") }
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
                    planOrder.forEach { plan ->
                        val selected = selectedPlan == plan
                        val available = plan in prices
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected,
                                    enabled = available && !purchaseInProgress && !verifyingPurchase && entitlement == null,
                                    role = Role.RadioButton,
                                    onClick = { selectedPlan = plan; onPlanSelected(plan) },
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
                                    enabled = available && !purchaseInProgress && !verifyingPurchase && entitlement == null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Forest),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(plan.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (plan != ProPlan.MONTHLY) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (plan == ProPlan.YEARLY) "ANNUAL" else "ONE-TIME",
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
                if (plan != null && entitlement == null) {
                    Text(
                        "${plan.displayName} · ${prices[plan] ?: "Currently unavailable"} ${plan.billingPeriod}",
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { selectedPlan?.let(onPurchase) },
                    enabled = canPurchase && entitlement == null && billingReady && selectedPlan in prices && !purchaseInProgress && !verifyingPurchase && !pendingPayment,
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
                            verifyingPurchase -> "Checking purchase…"
                            pendingPayment -> "Payment pending"
                            purchaseInProgress -> "Opening Google Play…"
                            !billingReady -> "Connecting to Google Play…"
                            selectedPlan == null && loadingPrices -> "Loading plans…"
                            selectedPlan !in prices -> "Plan unavailable"
                            !canPurchase -> "Checking purchase status…"
                            selectedPlan == ProPlan.LIFETIME -> "Buy with Google Play"
                            else -> "Subscribe with Google Play"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (entitlement == null && plan != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (plan) {
                            ProPlan.MONTHLY -> "Renews monthly. Cancel anytime in Google Play."
                            ProPlan.YEARLY -> "Renews annually. Cancel anytime in Google Play."
                            ProPlan.LIFETIME -> "One-time payment. No subscription."
                        },
                        color = MutedInk,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (trialExhausted) TextButton(onClick = onDismiss) { Text("Continue free", color = Forest) }
            }
        }
    }
}
