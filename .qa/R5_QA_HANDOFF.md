# Telegram Archive R5: QA optimization
- Branch based on verified R4 QA. R4 source restored from SHA-256 checked tar.xz.
- R5 adds "Навигатор пауз" and three JVM tests.
- Builds ordinary debug and separate R8/shrinkResources compactQa; neither replaces R4 packages silently.
- Compact app ID: org.openarchive.telegram.qa.compact. Data isolated from regular QA.
- No INET permission. No user chats, archives, or personal media in source.
- Device/UI and large import regression must be tested before Stable.
