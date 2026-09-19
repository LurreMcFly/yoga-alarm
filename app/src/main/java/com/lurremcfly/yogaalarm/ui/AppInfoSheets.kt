package com.lurremcfly.yogaalarm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lurremcfly.yogaalarm.BuildConfig
import com.lurremcfly.yogaalarm.model.ProPlan
import java.util.Locale

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
                    .height(54.dp),
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
    activePlan: ProPlan?,
    prices: Map<ProPlan, String>,
    billingReady: Boolean,
    purchaseInProgress: Boolean,
    billingMessage: String?,
    onDismiss: () -> Unit,
    onPurchase: (ProPlan) -> Unit,
    onRestorePurchases: () -> Unit,
    onManageSubscription: () -> Unit,
    onActivateTestPlan: (ProPlan) -> Unit,
) {
    var selectedPlan by remember(activePlan) { mutableStateOf(activePlan ?: ProPlan.YEARLY) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SoftSurface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 34.dp),
        ) {
            Text("Make every morning yours", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlock all poses and sounds, choose 1–10 poses, and set holds from 10–60 seconds.",
                color = MutedInk,
                fontSize = 15.sp,
            )
            if (activePlan != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(999.dp), color = AlarmLime.copy(alpha = 0.55f)) {
                    Text(
                        buildString {
                            append("PRO ACTIVE · ${activePlan.displayName.uppercase(Locale.US)}")
                            if (BuildConfig.DEBUG) append(" TEST")
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Forest,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            ProPlan.entries.forEach { plan ->
                val selected = selectedPlan == plan
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlan = plan },
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) Color(0xFFF1FFD9) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AlarmLime else Hairline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (plan != ProPlan.MONTHLY) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (plan == ProPlan.YEARLY) "SAVE 79%" else "BEST VALUE",
                                        color = Forest,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(plan.billingPeriod, color = MutedInk, fontSize = 13.sp)
                        }
                        Text(
                            prices[plan] ?: plan.fallbackPrice,
                            color = Ink,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = {
                    if (BuildConfig.DEBUG) onActivateTestPlan(selectedPlan) else onPurchase(selectedPlan)
                },
                enabled = if (BuildConfig.DEBUG) {
                    true
                } else {
                    activePlan == null && billingReady && prices.containsKey(selectedPlan) && !purchaseInProgress
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    when {
                        purchaseInProgress -> "Opening Google Play…"
                        BuildConfig.DEBUG && activePlan == null -> "Activate test Pro"
                        activePlan != null && !BuildConfig.DEBUG -> "Pro is active"
                        activePlan == null -> "Upgrade to Pro"
                        else -> "Choose ${selectedPlan.displayName}"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRestorePurchases) {
                    Text("Restore purchases", color = Forest, fontWeight = FontWeight.SemiBold)
                }
                if (activePlan != null && activePlan != ProPlan.LIFETIME && !BuildConfig.DEBUG) {
                    TextButton(onClick = onManageSubscription) {
                        Text("Manage subscription", color = Forest, fontWeight = FontWeight.SemiBold)
                    }
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
            Text(
                if (BuildConfig.DEBUG) {
                    "Test upgrade only · No charge in this debug APK"
                } else if (!billingReady) {
                    "Connecting to Google Play…"
                } else {
                    "Payment is handled securely by Google Play. Subscriptions renew until cancelled."
                },
                modifier = Modifier.fillMaxWidth(),
                color = MutedInk,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
