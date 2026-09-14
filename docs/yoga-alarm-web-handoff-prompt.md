# First prompt for `LurreMcFly/yoga-alarm-web`

Use the following as the first prompt in the new repository:

---

Build the first production-ready version of the Yoga Alarm website in this repository.

## Product background

Yoga Alarm is an Android alarm clock that only becomes quiet while the user holds simple yoga or mobility poses in front of the phone camera. Camera frames are processed on-device and are never recorded or uploaded. The product should feel like a calm morning ritual: premium, direct, and reassuring rather than aggressive or fitness-oriented.

The Android application ID is `com.lurremcfly.yogaalarm`. Android launches first; an iOS app is planned later. The canonical public domain is `https://yogaalarm.com`, hosted on PythonAnywhere.

## Goal for this iteration

Create a polished, working Flask website that can be deployed now and provides every public page needed to complete the initial Google Play setup. This does not need to be a complete long-term marketing site.

Required routes:

- `/` — minimal landing page
- `/privacy` — public privacy policy
- `/terms` — terms of use and movement/health disclaimer
- `/support` — support and contact information
- `/download` — redirect to the configured Google Play listing when available; until then, show a deliberate “Coming soon on Google Play” page rather than a broken link

All routes must be public without login, work over HTTPS after deployment, return useful pages on mobile, and use stable URLs suitable for Play Console.

## Visual direction

Share the Android app's design language:

- airy pale mint, blue, and soft lilac background gradients
- dark forest green primary text and controls
- fresh lime green accent
- white rounded cards with subtle borders and soft shadows
- generous spacing and clear, bold typography
- minimal motion and no visual clutter

The voice is concise and human. Lead with **“Wake up moving.”** Avoid guilt, punishment, medical claims, exaggerated promises, and generic wellness jargon.

Reuse the existing pose illustrations from the Android repository. Their current source files are:

```text
web-tester/public/poses/mountain.png
web-tester/public/poses/warrior-two.png
web-tester/public/poses/tree.png
web-tester/public/poses/chair.png
web-tester/public/poses/forward-fold.png
web-tester/public/poses/triangle.png
web-tester/public/poses/goddess.png
web-tester/public/poses/wide-legged-fold.png
```

Copy them into a clearly named static images directory in this repository while preserving the source PNGs. Optimize delivery if helpful, but do not redesign or replace the illustrations. If these source files are not available in the current workspace, create the destination structure and a clear asset checklist, then ask me to copy them; do not invent substitute art.

Use the poses as a restrained product visual—for example, a horizontal routine of three pose cards—not as a crowded gallery.

## Landing-page content

Keep the page short. Include:

1. A header with Yoga Alarm branding and one primary Android download CTA.
2. A hero with “Wake up moving.” and a one-sentence explanation.
3. A simple three-step explanation: alarm rings, camera recognizes the pose, alarm fades while the pose is held.
4. A product/routine visual using the pose PNGs.
5. A prominent privacy promise: camera analysis happens on-device; video is never recorded or uploaded.
6. A compact Free versus Pro summary.
7. A short FAQ covering camera privacy, phone placement, accounts, and Android availability.
8. A footer linking to Privacy, Terms, Support, and Download.

Current commercial offer:

- Free: unlimited alarms, three-pose routines, Mountain/Warrior II/Tree, 20-second holds, and two alarm sounds.
- Pro: all eight poses, 1–10 poses, 10–60-second hold choices, and all alarm sounds.
- Pro prices currently shown in the app: USD 7.99 monthly, USD 19.99 yearly, or USD 29.99 lifetime. State that purchases are completed and managed through Google Play. Do not build web checkout.
- Do not advertise a free trial.

Keep iOS to one subtle “iOS planned” mention. Do not let it compete with the Android launch CTA.

## Privacy, terms, and support requirements

The legal pages must describe the app as it actually works at release, not speculative future features.

Privacy policy facts:

- no Yoga Alarm account is required
- camera frames and pose landmarks are processed ephemerally on-device
- video is never recorded, retained, or uploaded by Yoga Alarm
- alarm times, repeat days, routine choices, snooze settings, and preferences are stored locally on the device
- no ads or analytics in the current release
- Google Play processes purchases; Yoga Alarm never receives payment-card details
- users can remove local data by deleting alarms, clearing app storage, or uninstalling
- include an effective date, last-updated date, developer identity, public support email, and policy-change statement

Terms must include a plain-language safety disclaimer: Yoga Alarm is a movement alarm, not a medical device; users should move within their ability and stop if they feel pain, dizziness, or discomfort. Include purchase/subscription terms, cancellation through Google Play, intellectual-property language, warranty/liability language appropriate for a small consumer app, governing-law/contact placeholders that require my confirmation, and a last-updated date. Keep the language readable and do not claim that the page is formal legal advice.

Support must show a real public support email and include concise help for camera permission, alarm/full-screen permission, battery restrictions, restoring Pro, and contacting support. Do not build a contact form or collect messages in a database for this version.

Centralize these values so they are changed once:

- public support email — initially an obvious `TODO` configuration value until I supply it
- developer/legal name — initially an obvious `TODO` configuration value until I confirm it
- Google Play listing URL — initially unset
- site base URL — `https://yogaalarm.com`
- policy effective/updated dates

Do not deploy with unresolved `TODO` values. Document them prominently in the README and add a small test or validation that fails a production readiness check while required public values are missing.

## Technical constraints

- Use Flask and keep the architecture intentionally small and understandable.
- Target deployment on PythonAnywhere using its WSGI configuration.
- Prefer server-rendered HTML and CSS. Use JavaScript only where it materially improves the page.
- Do not add accounts, a database, cookies, analytics, ad pixels, a CMS, email capture, or a backend API.
- Make the site responsive down to 320px, accessible by keyboard, readable with strong contrast, and respectful of reduced-motion settings.
- Use semantic HTML, useful alt text, visible focus states, canonical metadata, Open Graph metadata, favicon/app icon if available, `robots.txt`, and a useful 404 page.
- Avoid remote font or tracking dependencies when system fonts can achieve the design.
- Add sensible security headers without breaking PythonAnywhere deployment.

## Repository deliverables

- working Flask application and templates
- organized static CSS and pose assets
- a small dependency file suitable for PythonAnywhere
- basic route tests for `/`, `/privacy`, `/terms`, `/support`, and `/download`
- README with local setup, configuration, tests, PythonAnywhere WSGI setup, custom-domain/DNS steps, HTTPS verification, and deployment steps
- a short pre-launch checklist confirming public URLs, real contact details, mobile layout, policy copy, Google Play URL, and no unresolved placeholders

Start by inspecting the repository and any existing files. Preserve useful work. Then implement the smallest polished version that satisfies the acceptance criteria. Run focused tests and visually check the landing page at phone and desktop widths. At the end, report what is complete, what requires my values or PythonAnywhere access, and the exact public URLs to enter in Google Play Console.

## Acceptance criteria

- The site runs locally with one documented command.
- The five required routes work and have a consistent premium design.
- The landing page clearly communicates the product in under ten seconds.
- Privacy, Terms, and Support are substantive and usable for Google Play review.
- No video, analytics, account, checkout, or form infrastructure is introduced.
- The site is ready to deploy once I supply the public support email and legal/developer name.

---
