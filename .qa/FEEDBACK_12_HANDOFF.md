# Telegram Archive — Device feedback QA

Branch isolated from v0.25. The source patch for settings, chat pinning, media classification, favorites composer, circle player and help is in the accompanying handoff archive. It has not yet passed Android Gradle compilation.

Known test evidence: host Java AST parser 46/46, duplicate method audit, synthetic media SQL migration 8 cases, historical host policy tests, ZIP extractor 7 cases.

Do NOT release to users until Android assembleDebug and instrumentation tests complete. Do NOT change main or user databases from this QA branch.

Required device regressions: sliding drawer close and focus, saved composer bottom/insets, pinned chats backup restore, GIF voice and sticker classification, round floating drag, back-to-media crash stack trace, photo/video decoding on API 26/27/30 and Android 17.
