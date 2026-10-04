package com.lurremcfly.yogaalarm.model

enum class ProPlan(
    val displayName: String,
    val billingPeriod: String,
    val productId: String,
    val productType: String,
    val basePlanId: String? = null,
) {
    MONTHLY("Monthly", "per month", "yoga_alarm_pro", "subs", "monthly"),
    YEARLY("Yearly", "per year", "yoga_alarm_pro", "subs", "yearly"),
    LIFETIME("Lifetime", "one-time", "yoga_alarm_pro_lifetime", "inapp"),
}
