# Checkpoint chat proposal — 7 November 2026

Status: discussed with Vasili; not implemented or deployed.

## Requested experience

After the driver starts monitoring, open the chat for the selected checkpoint.
Keep the vehicle position, monitoring status and call alert accessible above
the conversation. Drivers can report local observations such as a temporary
computer outage. Use one shared room per checkpoint as the initial proposal.

## Constraints for implementation

- Messages are driver reports with visible timestamps, not verified official
  announcements. They must not change the official call state or trigger a
  checkpoint-call warning.
- Never use the tracked vehicle registration number as a public nickname.
- Choose the account/identity and recovery model before implementing server
  authorization. A display nickname alone cannot enforce blocks or bans.
- Provide terms acceptance before posting, clear rules, reporting of both
  messages and users, user blocking, and moderator removal/ban tools.
- Moderation must be operated continuously; a report button alone is
  insufficient. Assign an operator and response process before enabling chat.
- Enforce authorization, rate limits and abuse controls on the server. Start
  with text-only messages; attachments and private messages are outside this
  initial proposal.
- Review privacy disclosures, account deletion where applicable, Play Data
  safety and content-rating declarations before release. Hosting costs and
  moderation responsibility remain undecided.

Google Play policy references checked on 7 November 2026:

- https://support.google.com/googleplay/android-developer/answer/9876937?hl=ru
- https://support.google.com/googleplay/android-developer/answer/12923286?hl=en

No chat backend, user accounts, messages, public rooms or moderation queue
were created in the UI-cleanup change.
