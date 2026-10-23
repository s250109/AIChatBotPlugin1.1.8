AIChatbotPlugin 1.1.0 - source project

FEATURES
- /ai <message>
- Uses the Ollama HTTP chat API; no API key is needed when connecting to a normal local Ollama instance.
- Optional Bearer token for a protected compatible endpoint.
- Async HTTP requests, configurable cooldown and timeout.

IMPORTANT FOR MINEHUT
This source project does not make a PC-local Ollama server automatically reachable from Minehut. In config.yml, http://localhost:11434 means the Minehut server itself, not your Windows PC. To use Ollama from Minehut, you need a securely reachable endpoint hosted elsewhere or a suitable tunnel/reverse proxy. Do not expose an unauthenticated Ollama endpoint to the public internet.

BUILD
Requires JDK 21 and Maven. Run:
  mvn clean package
Output:
  target/AIChatbotPlugin1.1.0.jar

Then upload the JAR to the Minehut server's plugins area if the server type/plan supports custom plugins and the target Minecraft/Paper version is compatible.

VERSION NOTE
This project currently compiles against Paper API 1.21.4 and declares api-version 1.21. The user reported server version 26.2; compatibility with that version has not been verified. Update the Paper API dependency and api-version to a version actually supported by the server before relying on it.
