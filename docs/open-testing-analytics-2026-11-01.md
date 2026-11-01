# Open testing: optional analytics without forecast UI

This change targets the existing `com.pylikv.queuewatch` app, based on main
`64531e68e2061c74911f4af9cf52c82db19687a3`. It does not replace the separate
`com.pylikv.queuewatch.forecast` APK. Existing pipeline-applied tracking fixes
are materialized before adding the feature.

## Release behavior

- No forecast card, predicted deadline or model choice in the public UI.
- Consent defaults off. Enabling opens a disclosure and requires a separate
  affirmative action. Dismissal and refusal do not grant consent.
- The switch is available on setup, tracking and restored-tracking screens.
- No consent means no analytics session or installation identity is created.
- Revocation clears pending events and calculation history. Captured consent
  grants prevent old asynchronous clients from resurrecting events after a
  disable/re-enable cycle. A request already transmitted can finish.
- The random installation code is retained locally for deletion requests.
  Consent and analytics preferences are excluded from Android backup/transfer.
- Both existing algorithms run during consented active tracking. Point
  algorithms and HTTP event schemas are unchanged. Ordinary observations and
  predictions are limited to once per minute; state changes and actual calls
  are not suppressed by that interval.
- The optional analytics path does not control alerts or tracking persistence.

## Play Console draft

The current open-test release, available version code and upload certificate
must be verified in the actual Console before submission. Code 20 / 1.1.0 is
provisional; `-PqueuewatchVersionCode=N` can select a verified unused higher code.
No claim is made that this candidate has already been uploaded or published.

Data safety must describe the actual optional collection, including random
installation/session IDs and queue-monitoring activity. Proposed categories
are "Device or other IDs" and relevant "App activity" entries, for Analytics.
These are a draft for reconciliation with the Console's current questionnaire,
all existing app behavior and actual data handling. Do not mark collection as
fully anonymous. No registration number or GPS coordinate is included in the
forecast telemetry payload.

HTTPS and existing Supabase access controls are retained. Supabase is used as
infrastructure on the developer's behalf; sharing declarations must follow the
service-provider rules and the actual arrangement, not the mere absence of ads.
There is no user account. Deletion requests use the contact in the privacy
policy and the locally visible installation code; no automatic server deletion
endpoint or fixed server retention period is claimed.

The privacy policy must be published at its existing main-branch URL before
rolling out this build. Verify matching disclosures, consent flow and the
Data safety form in Console. Existing foreground-service declarations are not
changed by this feature and still need to match the running app.

Release notes (ru-RU):
Добавлено добровольное участие в проверке точности будущего прогноза очереди.
Отправка статистики выключена по умолчанию и включается только после согласия.
Прогнозы в этой версии не отображаются. Отслеживание и уведомления доступны без
участия в статистике.
