package com.lurremcfly.yogaalarm.model

enum class ProPlan(
    val displayName: String,
    val fallbackPrice: String,
    val billingPeriod: String,
    val productId: String,
    val productType: String,
) {
    MONTHLY("Monthly", "$7.99", "per month", "pro_monthly", "subs"),
    YEARLY("Yearly", "$19.99", "per year", "pro_yearly", "subs"),
    LIFETIME("Lifetime", "$29.99", "one-time", "pro_lifetime", "inapp"),
}
