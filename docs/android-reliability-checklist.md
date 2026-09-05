# Android reliability release checkpoint

Run this matrix with the API 36 build and the production package
`com.lurremcfly.yogaalarm`. A package rename creates a fresh installation, so earlier
prototype results do not replace this pass.

## Alarm delivery

- [ ] Screen locked and off
- [ ] App removed from recents
- [ ] Process killed by Android while alarm remains scheduled
- [ ] Device rebooted after setting an alarm
- [ ] Samsung battery saver and restricted-background modes
- [ ] Silent mode and Do Not Disturb
- [ ] Bluetooth speaker and headphones connected/disconnected before ringing
- [ ] Two alarms scheduled close together
- [ ] One-time alarm disables itself after firing
- [ ] Repeating alarm schedules its next selected weekday
- [ ] Every configured snooze works and the button disappears after the final snooze

An Android Settings **Force stop** intentionally prevents all app alarms and receivers
until the user manually opens the app again. This cannot be bypassed by application code.

## Clock changes

- [ ] Manual clock moved forward past an alarm
- [ ] Manual clock moved backward before an alarm
- [ ] Timezone changed with an enabled alarm
- [ ] Automatic daylight-saving transition
- [ ] 12/24-hour system display changes

## Camera and routine recovery

- [ ] Camera permission denied, then granted from the app
- [ ] Camera permission revoked in Settings and then restored
- [ ] Camera/model startup failure shows **Try camera again** and retry succeeds
- [ ] Home button pressed during a routine, then app resumed
- [ ] Screen stays awake for the complete routine
- [ ] Dark room and partial-body framing
- [ ] Brief tracking loss preserves the accepted pose
- [ ] Sustained tracking loss pauses the timer
- [ ] Stop always silences audio and vibration
- [ ] Camera is released after completion, snooze, stop, and leaving a test routine
- [ ] Done exits; retry keeps accumulated hold time and restarts only the camera
- [ ] Camera startup keeps the UI responsive; unavailable/stalled frames offer retry
- [ ] Selfie preview stays mirrored and overlays align, including on cameras with padded image rows
- [ ] All eight poses work close enough to comfortably read the phone, including dim light
- [ ] Paused countdown stays visible; the next guide/instruction appears during transitions
- [ ] Real-alarm audio does not restart when starting/retrying the camera
- [ ] Home/resume restores alarm volume without advancing a pose in the background
- [ ] Notification snooze stops audio while the UI is bound; the snoozed alarm rings later
- [ ] Reopening the app while snoozed does not cancel the pending snooze
- [ ] A second alarm during a routine gets its own configuration and correct audio
- [ ] Activity recreation preserves editor drafts and an in-progress routine's held time

## Editor and audio previews

- [ ] All ten sound choices are reachable on a small screen and with large system text
- [ ] Preview toggles off, stops after ten seconds, and stops when backgrounded or dismissed
- [ ] Rapid sound switching does not play multiple tracks or freeze the UI
- [ ] Saving/enabling displays time until ringing; failed scheduling leaves the switch off

## Devices

- [ ] Samsung Galaxy S25
- [ ] Second Samsung model already used for responsive testing
- [ ] At least one non-Samsung Android device
- [ ] One Android 16/API 36 device or emulator
- [ ] One older supported device near Android 8/API 26 when available
