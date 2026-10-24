package com.wabihari.aichatbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

public final class AIMapGenerator {

private final JavaPlugin plugin;
private final HttpClient httpClient;

public AIMapGenerator(JavaPlugin plugin) {
    this.plugin = plugin;
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
}

public void generate(Player player, String prompt) {
    UUID playerId = player.getUniqueId();

    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        try {
            String accountId = plugin.getConfig()
                    .getString("aimap.cloudflare.account-id", "");
            String apiToken = plugin.getConfig()
                    .getString("aimap.cloudflare.api-token", "");
            String model = plugin.getConfig()
                    .getString("aimap.cloudflare.model",
                            "@cf/bytedance/stable-diffusion-xl-lightning");

            if (accountId.isBlank() || apiToken.isBlank()) {
                sendMessage(playerId,
                        "§cCloudflare ist noch nicht konfiguriert.");
                return;
            }

            JsonObject body = new JsonObject();
            body.addProperty("prompt", prompt);

            String endpoint = "https://api.cloudflare.com/client/v4/accounts/"
                    + accountId + "/ai/run/" + model;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                plugin.getLogger().warning(
                        "Cloudflare image request failed: HTTP "
                                + response.statusCode());
                sendMessage(playerId,
                        "§cDie Bildgenerierung ist fehlgeschlagen. Prüfe die Serverkonsole.");
                return;
            }

            byte[] imageBytes = response.body();

            if (imageBytes.length == 0) {
                sendMessage(playerId, "§cDas Bild war leer.");
                return;
            }

            // Im nächsten Schritt wandeln wir das Bild
            // in eine echte Minecraft-Karte um.
            sendMessage(playerId,
                    "§aBild erfolgreich generiert! Die Minecraft-Kartenfunktion folgt als Nächstes.");

        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "AI map generation failed: "
                            + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage());

            sendMessage(playerId,
                    "§cFehler bei der Bildgenerierung. Prüfe die Serverkonsole.");
        }
    });
}

private void sendMessage(UUID playerId, String message) {
    Bukkit.getScheduler().runTask(plugin, () -> {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    });
}

}
