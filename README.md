# Claude Code for Android

Native Android app: an agentic coding assistant powered by the Anthropic
API, plus offline local GGUF chat (llama.cpp). No root, no Termux, no
containers, no native binaries to fight — pure Kotlin + HTTPS.

## Why native instead of the Bun binary

The official `claude` CLI is a Bun single-binary that dies with SIGSYS
on strict Android seccomp policies with no recovery path. This app
implements the same agentic loop natively:

- **Agent tab**: system prompt + tool loop (read/write/edit files,
  list_dir, bash, web_fetch) over `POST /v1/messages` with SSE
  streaming, tool_use handling, session persistence. Scoped strictly
  to the app workspace.
- **Local tab**: offline GGUF chat via on-device llama-server
  (llama.cpp runtime downloaded on demand).
- **Models tab**: GGUF library (import file / URL / starter model),
  activation, server test.
- **Settings**: Anthropic API key (device-only storage), model,
  max tokens, plus local generation controls.

## Requirements

- An Anthropic API key (console.anthropic.com) for the Agent tab.
  The Local tab works fully offline.

## Build

Local (signed, needs `buildtools/claude-release.jks` — never committed):

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleRelease -x lint
```

CI (GitHub Actions): every push builds the unsigned release APK.
Pushing a `v*` tag signs it with repository secrets and publishes a
GitHub Release with the APK attached. Required secrets:

- `KEYSTORE_B64` — base64 of the release keystore
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
