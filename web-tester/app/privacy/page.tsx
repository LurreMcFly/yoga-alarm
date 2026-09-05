import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Privacy Policy | Yoga Alarm',
  description: 'How Yoga Alarm handles camera data, alarms, settings, and purchases.',
};

export default function PrivacyPolicy() {
  return (
    <main className="privacy-page">
      <article className="privacy-card">
        <a className="privacy-brand" href="/">Yoga Alarm</a>
        <p className="privacy-kicker">Privacy policy</p>
        <h1>Your camera stays on your phone.</h1>
        <p className="privacy-lead">
          Yoga Alarm uses your camera to recognize simple movement poses. Camera frames are
          processed on your device and are never recorded, saved, or uploaded by Yoga Alarm.
        </p>

        <section>
          <h2>What the app accesses</h2>
          <ul>
            <li><strong>Camera:</strong> used only while you test or complete a pose routine.</li>
            <li><strong>Notifications and alarms:</strong> used to ring and display alarms at the time you choose.</li>
            <li><strong>Vibration and wake lock:</strong> used to alert you and keep the routine screen awake.</li>
            <li><strong>Google Play Billing:</strong> used to offer, restore, and manage optional Pro purchases.</li>
          </ul>
        </section>

        <section>
          <h2>Data stored on your device</h2>
          <p>
            Alarm times, repeat days, routine choices, snooze settings, permission-readiness
            state, and local Pro test state are stored locally on your device. Yoga Alarm does
            not require an account and does not operate an advertising or analytics backend.
          </p>
        </section>

        <section>
          <h2>Purchases</h2>
          <p>
            Purchases are processed by Google Play under Google&apos;s terms and privacy policy.
            Yoga Alarm reads purchase status from Google Play to unlock Pro. It does not receive
            or store your payment-card details.
          </p>
        </section>

        <section>
          <h2>Sharing, retention, and deletion</h2>
          <p>
            Yoga Alarm does not sell personal data or share camera footage. Local app data stays
            on your device until you change it, clear the app&apos;s storage, or uninstall the app.
            Google Play retains purchase records according to Google&apos;s own policies.
          </p>
        </section>

        <section>
          <h2>Health notice</h2>
          <p>
            Yoga Alarm is a movement alarm, not a medical device. It does not diagnose, treat,
            cure, or prevent any condition. Move within your own ability and stop if you feel pain,
            dizziness, or discomfort.
          </p>
        </section>

        <section>
          <h2>Children</h2>
          <p>
            Yoga Alarm is intended for adults and is not directed to children under 13.
          </p>
        </section>

        <section>
          <h2>Changes and contact</h2>
          <p>
            This policy may be updated as the app changes. For privacy questions, use the developer
            contact shown on Yoga Alarm&apos;s Google Play listing.
          </p>
        </section>

        <footer>Effective September 3, 2026 · Yoga Alarm by LurreMcFly</footer>
      </article>
    </main>
  );
}
