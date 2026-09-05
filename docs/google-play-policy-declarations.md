# Google Play policy declarations

Prepared for package `com.lurremcfly.yogaalarm` on September 3, 2026. Re-check this file if analytics, accounts, cloud sync, crash reporting, ads, or any network SDK is added.

## Data Safety

- Does the app collect or share required user-data types? **No**, for the current build.
- Camera frames are processed ephemerally on-device by MediaPipe. They are not recorded, retained, or transmitted.
- Alarm configuration, routine choices, permission-readiness flags, and debug Pro state are stored only in app-private local preferences.
- The app has no account system, backend, ads, analytics, or crash-reporting SDK.
- Google Play handles purchase transactions. The app reads purchase and entitlement status through Play Billing and never receives payment-card information.
- Data deletion: users can delete individual alarms, clear app storage, or uninstall the app. There is no Yoga Alarm account to delete.
- Privacy policy URL: `https://yoga-alarm.polite-eel-9991.chatgpt.site/privacy`

Confirm the final merged release manifest still has no `INTERNET` or `ACCESS_NETWORK_STATE` permission before submitting this answer.

## Health apps declaration

- Health features: **Yes**.
- Select the closest **Activity and fitness / exercise or movement** category offered by the current form.
- The app guides users through short yoga or mobility poses and validates approximate pose shape.
- The app does not collect, store, or transmit health data and does not integrate with Health Connect.
- The app is not a medical device and makes no diagnostic, treatment, cure, or prevention claims.

Store-listing disclaimer:

> Yoga Alarm is a movement alarm, not a medical device. Move within your own ability and stop if you feel pain, dizziness, or discomfort.

## Exact alarm permission

- Permission: `USE_EXACT_ALARM`.
- Core purpose: Yoga Alarm is an alarm clock. Its user-requested wake alarms must ring at the exact selected time.
- If Play presents an exact-alarm declaration, select the alarm-clock/core-alarm use case.

## Full-screen intent

- Permission: `USE_FULL_SCREEN_INTENT`.
- Core purpose: show an actively ringing alarm over the lock screen so the user can snooze or begin the required movement routine.
- This is used only for user-created alarms, not marketing or general notifications.

## Other App content answers

- Ads: **No**.
- App access: no login is required. Give reviewers instructions for testing Pro with Play license-test products once those products are active.
- Target audience recommendation: adults / 18 and over. Do not select children unless the product and policy work is deliberately expanded for them.
- Content rating: complete the questionnaire truthfully; the current app contains no violence, sexual content, gambling, or user-generated content.

## Billing and subscriptions

- Products: monthly, yearly, and lifetime Pro.
- Show Play-localized price, billing period, auto-renewal behavior, and restore/manage-purchase actions before purchase.
- Monthly and yearly subscriptions renew until canceled through Google Play. Lifetime is a one-time purchase.
- Play Console product IDs must match the app: `pro_monthly`, `pro_yearly`, and `pro_lifetime`.

## Asset evidence

Keep a private release evidence folder containing:

- Suno subscription/payment proof and the terms that applied when each alarm track was generated.
- Generation/export dates and original filenames for all alarm tracks.
- The source prompts or generation records for pose illustrations and the app icon.
- A copy of the relevant OpenAI terms in effect when those images were generated.

Do not publish receipts or personal account details inside the repository.
