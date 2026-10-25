# Telegram Archive 0.46 — source QA pending Android build

This branch was created from QA 0.45. It does **not** yet contain the 0.46 source modifications, nor a 0.46 APK. Do not build or announce a 0.46 release from it yet.

The current edited source is available as a ChatGPT handoff bundle in the ongoing user conversation: `TelegramArchive_0.46_SOURCE_QA_AND_ASTRA_HANDOFF.zip`.

SHA-256 of handoff ZIP: `30dceb97116dcfb72e6eee6ef472d4ed344775681fc77b065e58bf480bb3e3ae`.

Scope: round video freeze detection and fullscreen fallback, tutorial audio async controls and seek, missing synthetic MP4 instrumentation fixture, a new help chapter and five locally passing watchdog unit tests.

Next: upload/review changed source, run `gradle assembleCompactQa testCompactQaUnitTest`, then Android emulator smoke tests. Do not call this fix verified until Android instrumentation has passed. Preserve main and all personal archives.
