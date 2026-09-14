# Google Play monetization setup guide

Last checked: 2026-09-05

This guide takes Yoga Alarm from a local Android app to a Play-distributed app that can
accept real Pro purchases. Google changes Console labels occasionally; use the linked
official documentation if a menu has moved.

## Understand the two consoles

Use **Google Play Console first**:

- Developer and business verification.
- Merchant/payments profile and payouts.
- App creation and Play App Signing.
- Subscriptions and the lifetime purchase.
- Internal testing and production release.

Use **Google Cloud Console later**:

- Secure server-side purchase verification.
- Google Play Developer API credentials.
- Real-time Developer Notifications (RTDN) through Pub/Sub.
- The small Yoga Alarm entitlement backend.

Creating a Cloud project alone does not publish the app or allow it to earn money.

## Permanent decisions — do not improvise in the Console

```text
Android package: com.lurremcfly.yogaalarm

Subscription product: yoga_alarm_pro
  Monthly base plan: monthly — USD 7.99/month
  Yearly base plan:  yearly  — USD 19.99/year

One-time product: yoga_alarm_pro_lifetime
  Purchase option: buy — USD 29.99 once
```

- The app itself is free to download and sells digital Pro access in-app.
- Monthly and yearly are auto-renewing base plans of the same subscription.
- Lifetime is a non-consumable `Buy` purchase, not a subscription.
- There is no launch trial or introductory offer.
- Use a seven-day subscription grace period.
- Use Google's automatically calculated account-hold duration.
- Enable resubscription.
- The existing three-choice paywall remains visually unchanged.
- Google Play supplies localized production prices.
- Yoga Alarm does not require a customer account.

Product IDs and activated base-plan IDs cannot be casually renamed or reused. Stop and
check this section before activating anything.

Official references:

- [Create and set up an app](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Create and manage subscriptions](https://support.google.com/googleplay/android-developer/answer/140504)
- [One-time products](https://support.google.com/googleplay/android-developer/answer/16430488)
- [Prepare Google Play Billing](https://developer.android.com/google/play/billing/getting-ready)

## Phase A — prepare the business information

Have these available before registering:

- Swedish AB's exact legal name.
- Organization number.
- D-U-N-S number matching the AB.
- Registered physical address; not a PO box.
- Company website.
- Public support email address.
- Authorized representative's details.
- Company bank-account details.
- Identity and company documents Google may request.
- A Google account controlled by the company owner.

Use a dedicated company-controlled Google account rather than an everyday personal inbox
if practical. Enable strong two-step verification and save recovery methods securely.

## Phase B — create or verify the Play organization account

Skip creation if the correct organization account already exists, but verify every item.

1. Open [Google Play Console](https://play.google.com/console/).
2. Create a developer account.
3. Select **Organization**, not Personal.
4. Enter the Swedish AB details exactly as they appear in official records and D-U-N-S.
5. Enter the company website and public developer contact details.
6. Pay the developer registration fee shown by Google.
7. Complete email, phone, identity, organization, and website verification when requested.
8. Open **Developer account → About you** and confirm:
   - Account type is Organization.
   - Legal name and address are correct.
   - D-U-N-S belongs to the AB.
   - Public developer name is the intended publisher name.
   - Website and support contact are correct.

Google requires a D-U-N-S number for organization accounts. See
[account-type requirements](https://support.google.com/googleplay/android-developer/answer/13634885).

### Stop if anything is wrong

Country, account type, and some identity fields are difficult to change after setup. Do
not continue using personal details if the publisher and revenue should belong to the AB.

## Phase C — create the merchant/payments profile

The account owner performs this step.

1. In Play Console, open **Settings → Payments profile** or follow the monetization prompt
   to create a merchant account.
2. Select **Create payments profile**.
3. Set the profile country to **Sweden**.
4. Use the Swedish AB's exact legal name and physical registered address.
5. Enter the authorized primary contact.
6. Enter the public business information:
   - Website.
   - Customer-support email.
   - Appropriate product/business category.
   - A recognizable statement descriptor such as `YOGA ALARM` if accepted.
7. Review every value, especially country and legal entity.
8. Submit and complete any additional verification.

The payments profile is automatically linked to Play Console. Google warns that the
business country cannot simply be changed later. Follow the official
[payments-profile instructions](https://support.google.com/googleplay/android-developer/answer/7161426).

## Phase D — add the payout bank account

1. Open **Settings → Payments settings**.
2. Under **How you get paid**, choose **Choose payment method**.
3. Select **Add payment method**.
4. Enter the AB bank details exactly as registered at the bank.
5. Complete bank verification if Google requests it.
6. Make the verified account the primary payout method.
7. Complete any tax information shown in the payments profile.

Google pays merchant proceeds monthly under its payout schedule. The bank must meet
Google's country/currency requirements; EEA merchants may have additional SEPA options.
See [merchant bank requirements](https://support.google.com/googleplay/android-developer/answer/7161440).

Ask the AB's accountant how Play sales, VAT reports, service fees, currency conversion,
and merchant payouts should be booked. Do not treat gross customer price as net revenue.

## Phase E — review service-fee enrollment

1. Open **Developer account → Associated developer accounts**.
2. Declare associated developer accounts truthfully, even if the answer is none.
3. Create the required account group if Play asks for one.
4. Review and enroll in any service-fee program for which the AB and app qualify.
5. Save the accepted terms and effective date in the company's private records.

Google's fee programs and regional rates can change. Confirm the rate shown in the account
rather than hardcoding a single percentage into financial plans. See Google's current
[service-fee overview](https://support.google.com/googleplay/android-developer/answer/112622).

## Phase F — create Yoga Alarm in Play Console

1. From Play Console Home, select **Create app**.
2. Default language: choose the language used for the first store listing. English is the
   practical primary listing; Swedish can be added as a translation.
3. App name: **Yoga Alarm**.
4. Select **App**, not Game.
5. Select **Free**. Pro is sold through in-app products.
6. Enter the public support email.
7. Accept the Developer Program Policies and US export-law declarations.
8. Accept the Play App Signing terms.
9. Select **Create app**.

Selecting Free is intentional. The app should remain free to install even though it has
paid digital features.

### Verify before the first upload

- Package must be `com.lurremcfly.yogaalarm`.
- The first uploaded artifact permanently associates that package with this app entry.
- Do not upload an old `com.yogaalarm.prototype` artifact.
- Do not upload a release signed with an unprotected debug key.

## Phase G — create an internal testing track

1. Open **Test and release → Testing → Internal testing**.
2. Open the **Testers** tab.
3. Create an email list such as `Yoga Alarm internal`.
4. Add the Google accounts that will install test builds.
5. Add a support/feedback email.
6. Save the tester list.

Internal testing supports up to 100 testers and normally distributes new AABs quickly.
See [internal testing setup](https://support.google.com/googleplay/android-developer/answer/9845334).

## Checkpoint 1 — return to the local project

Before uploading an AAB, return to this project to:

1. Create and securely back up the upload signing key.
2. Keep passwords and keystore files outside Git.
3. Configure release signing without committing secrets.
4. Build a signed AAB for `com.lurremcfly.yogaalarm`.
5. Inspect the release manifest and bundle.

Choose Google Play App Signing for the app-signing key. Retain control and backups of the
separate upload key. The current debug APK is not the production artifact.

Expected file after the local release build:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Phase H — upload the first internal AAB

1. Return to **Internal testing**.
2. Select **Create new release**.
3. Complete Play App Signing setup if prompted.
4. Upload the signed `app-release.aab`.
5. Confirm Play reports package `com.lurremcfly.yogaalarm`.
6. Confirm version code is accepted and there are no blocking bundle errors.
7. Add concise release notes such as `Initial internal billing setup`.
8. Select **Review release**.
9. Resolve blocking errors.
10. Select **Start rollout to Internal testing**.

The first upload fixes the package name for this Play app. Google uses the AAB to generate
device-specific APKs, which should be smaller than the universal debug APK.

## Phase I — create the Pro subscription

1. Open **Monetize with Play → Products → Subscriptions**.
2. Select **Create subscription**.
3. Product ID: enter exactly `yoga_alarm_pro`.
4. Name: **Yoga Alarm Pro**.
5. Description: use clear benefit copy, for example:
   `Unlock all poses and alarm sounds, choose 1–10 poses, and customize hold duration.`
6. Complete tax/compliance fields using the closest accurate digital-service category.
7. Save the subscription.

Do not create separate `pro_monthly` and `pro_yearly` subscription products.

### Add the monthly base plan

1. Inside `yoga_alarm_pro`, select **Add base plan**.
2. Base plan ID: `monthly`.
3. Type: **Auto-renewing**.
4. Billing period: **Monthly**.
5. Grace period: **7 days**.
6. Account hold: use **Automatically calculated**.
7. Resubscribe: **Enabled**.
8. Select the intended launch regions.
9. Use USD 7.99 as the approved reference launch price and review every localized price.
10. Make new supported countries available only if that matches the app-distribution policy.
11. Save, review, and **Activate** the base plan.

### Add the yearly base plan

1. Select **Add base plan** again.
2. Base plan ID: `yearly`.
3. Type: **Auto-renewing**.
4. Billing period: **Yearly**.
5. Grace period: **7 days**.
6. Account hold: **Automatically calculated**.
7. Resubscribe: **Enabled**.
8. Match the monthly plan's intended regions.
9. Use USD 19.99 as the approved reference launch price and review localized prices.
10. Save, review, and **Activate** the base plan.

Do not add trials, introductory offers, prepaid plans, or installment plans for launch.

## Phase J — create the lifetime product

1. Open **Monetize with Play → Products → One-time products**.
2. Select **Create one-time product**.
3. Product ID: enter exactly `yoga_alarm_pro_lifetime`.
4. Name: **Yoga Alarm Pro Lifetime**.
5. Description: `One-time purchase that unlocks all Yoga Alarm Pro features.`
6. Complete the tax/compliance classification accurately.
7. Create its purchase option:
   - Purchase option ID: `buy`.
   - Purchase type: **Buy**.
   - Multiple quantities: **Off**.
   - It is permanent digital Pro access, not Rent.
8. Select the intended regions.
9. Use USD 29.99 as the approved reference price and review localized prices.
10. Do not create a discount or preorder offer.
11. Save and **Activate** the purchase option/product.

If the Console still presents the legacy in-app-product interface rather than purchase
options, create the same product as a non-consumable one-time product and do not invent an
additional product ID.

## Phase K — configure license testing

Internal-track access and free billing test instruments are separate settings. Use the same
Google accounts for both.

1. At the developer-account level, open **Settings → License testing**.
2. Add the internal testers' Gmail addresses or Google Group.
3. Use the normal licensed-test response unless deliberately testing an error response.
4. Save.
5. Confirm all three products/base plans are active, not Draft.

Licensed testers can make test purchases without a real charge when the app and products
are properly published to a test track. See
[billing license testing](https://support.google.com/googleplay/android-developer/answer/6062777).

## Checkpoint 2 — return to local billing implementation

At this point, provide the following Console evidence to the implementation work:

- Screenshot showing subscription product `yoga_alarm_pro`.
- Screenshot showing active `monthly` and `yearly` base plans.
- Screenshot showing active lifetime product and `buy` purchase option.
- Countries/regions chosen for each.
- The internal tester email account, without exposing its password.
- Confirmation that the first internal AAB is published.

The local app must then be updated to:

- Query one subscription product and select monthly/yearly by base-plan ID.
- Query the lifetime one-time product and `buy` purchase option.
- Keep the existing three-card frontend.
- Display Play-localized prices.
- Remove debug Pro activation from release builds.
- Send purchase tokens to the verification backend before permanently granting Pro.

That implementation will require a new version code and a second internal-track AAB.

## Phase L — install and test through Google Play

1. On the phone, sign into Play Store with a licensed tester account.
2. Open the internal-test opt-in link from Play Console.
3. Join the test.
4. Remove a sideloaded build first if its signing certificate conflicts.
5. Install Yoga Alarm from the Play Store test listing, not from the downloaded APK.
6. Confirm the paywall shows three choices and localized prices.
7. Test monthly, yearly, and lifetime individually.
8. Test canceling checkout.
9. Test pending payment completing and canceling.
10. Test Restore Purchases after reinstall.
11. Test Manage Subscription.
12. Confirm no test order grants Pro while still pending.

Do not use real personal purchases to validate logic when license-test instruments are
available.

## Phase M — complete the store and policy setup

Before production, complete the Dashboard and **Policy → App content** tasks:

- Main store listing, descriptions, icon, screenshots, category, and contact details.
- Privacy-policy URL that is publicly accessible without login.
- Data Safety declaration matching the final network/backend behavior.
- Health apps declaration for movement/exercise functionality.
- Content-rating questionnaire.
- Ads declaration: No.
- App-access instructions: no login; explain reviewer access to Free and Pro testing.
- Exact-alarm declaration for the alarm-clock core purpose if presented.
- Full-screen-intent declaration for user-created ringing alarms if presented.
- Target audience and content answers.
- Countries/regions for the free app itself.

Use [`docs/google-play-policy-declarations.md`](google-play-policy-declarations.md) as a
draft, but update its old product IDs and backend statements before submission.

Google requires every published app to complete the Health apps declaration. See
[Health apps declaration guidance](https://support.google.com/googleplay/android-developer/answer/14738291).

## Phase N — Google Cloud Console, after the backend is designed

Do not create random Cloud resources merely to finish a checklist. The approved minimal
direction is a small purchase-verification service, likely using Cloud Run plus Pub/Sub and
a minimal datastore. Confirm the backend design before provisioning it.

### Create the production Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project owned by the AB, for example:
   - Project name: `Yoga Alarm Production`.
   - Project ID: choose carefully; project IDs are permanent and globally unique.
3. Link the company Cloud Billing account.
4. Open **Billing → Budgets & alerts**.
5. Create a small monthly alert budget with notifications at sensible thresholds.

Budget alerts do not automatically cap spending. See
[Cloud budget guidance](https://docs.cloud.google.com/billing/docs/how-to/budgets).

### Enable only the required APIs

Enable these when implementation begins:

- Google Play Android Developer API.
- Pub/Sub API.
- Cloud Run Admin API if Cloud Run is selected.
- Artifact Registry and Cloud Build APIs if deploying containers through them.
- Secret Manager API if secrets are required.
- The selected database API only after the datastore is chosen.

Avoid enabling unrelated APIs.

### Create the billing service identity

1. Open **IAM & Admin → Service Accounts**.
2. Create a narrowly named service account such as `yoga-alarm-billing`.
3. If the backend runs in Google Cloud, attach this identity directly to the service.
4. Avoid downloading a long-lived JSON key unless there is no safer deployment option.
5. In Play Console, open **Users and permissions**.
6. Invite the service-account email.
7. Scope it to Yoga Alarm where possible.
8. Grant only the billing permissions required by Google's API setup, including:
   - View financial data, orders, and cancellation survey responses.
   - Manage orders and subscriptions.

Google documents this two-console setup in the
[Google Play Developer API getting-started guide](https://developers.google.com/android-publisher/getting_started).

### Configure RTDN with Pub/Sub

1. In Cloud Console, open **Pub/Sub → Topics**.
2. Create a topic such as `yoga-alarm-rtdn`.
3. On that topic, grant
   `google-play-developer-notifications@system.gserviceaccount.com` the
   **Pub/Sub Publisher** role.
4. Deploy the secure backend endpoint before creating the delivery subscription.
5. Create an authenticated Pub/Sub push subscription targeting the backend RTDN endpoint.
6. Configure dead-letter/retry handling during backend implementation.
7. In Play Console, open the app's monetization setup and enter the full Pub/Sub topic name.
8. Select notifications for subscriptions and voided purchases.
9. Send a test notification from Play Console.
10. Confirm the backend receives it and then queries the Google Play Developer API.

RTDN only reports that something changed; the backend must query Google for the authoritative
purchase state. Follow Google's
[RTDN setup and billing preparation](https://developer.android.com/google/play/billing/getting-ready).

## Phase O — production release and first real revenue

Real revenue requires all of the following:

- Verified organization developer account.
- Verified merchant/payments profile and payout bank account.
- Active products and regional prices.
- Production app billing connected to those products.
- Secure purchase verification and entitlement lifecycle.
- Completed store listing and policy declarations.
- Successful internal testing.
- Any closed-testing or production-access requirement shown for this account.
- A production release available in at least one buyer-supported country.

When ready:

1. Promote the tested release to the required closed or production track.
2. Review Play's pre-launch report and resolve blocking crashes, ANRs, and policy findings.
3. Submit the release for review.
4. After approval, use a small staged rollout rather than 100% immediately.
5. Monitor crashes, alarm reliability, refunds, purchases, and reviews.
6. Confirm the first real order in **Order management**.
7. Confirm it appears in financial reporting and later in the merchant payout.

Google Play Console is the sales channel and merchant interface. Google Cloud powers the
backend reliability layer; it does not replace the Play production-review process.

## Information to record privately

Keep these outside the public Git repository:

- Developer account ID.
- Payments profile ID.
- Merchant profile and bank-verification records.
- D-U-N-S and company verification evidence.
- App signing and upload key backup details.
- Cloud project ID and billing account.
- Service-account email and permissions.
- Pub/Sub topic and subscription names.
- Production backend URL.
- Play review correspondence and accepted service-fee terms.

Never commit keystores, passwords, service-account JSON keys, bank information, tax records,
or personal identity documents.
