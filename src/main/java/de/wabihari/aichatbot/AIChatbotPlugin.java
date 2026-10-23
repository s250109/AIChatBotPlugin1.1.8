package de.wabihari.aichatbot;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AIChatbotPlugin extends JavaPlugin {
    private HttpClient http;
    private final Map<UUID, AtomicLong> lastUse = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<String>> history = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        getLogger().info("AIChatbotPlugin enabled. Provider: " + getConfig().getString("ai.provider", "ollama"));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ai")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.GOLD + "Benutzung: /ai <Nachricht>");
            return true;
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, getConfig().getLong("ai.cooldown-seconds", 3)) * 1000L;
        AtomicLong previous = lastUse.computeIfAbsent(player.getUniqueId(), k -> new AtomicLong(0));
        long last = previous.get();
        if (now - last < cooldown) {
            player.sendMessage(ChatColor.RED + "Bitte warte noch kurz, bevor du /ai erneut benutzt.");
            return true;
        }
        previous.set(now);
        String prompt = String.join(" ", args).trim();
        player.sendMessage(ChatColor.GRAY + "[AI] Denke nach...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String answer = askOllama(player.getUniqueId(), prompt);
                String clean = answer.replaceAll("[\\r\\n]+", " ").trim();
                if (clean.length() > 240) clean = clean.substring(0, 237) + "...";
                final String reply = clean.isEmpty() ? "Ich konnte gerade keine Antwort erstellen." : clean;
                getServer().getScheduler().runTask(this, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.AQUA + "[AI] " + ChatColor.WHITE + reply);
                });
            } catch (Exception ex) {
                getLogger().warning("Ollama request failed: " + ex.getMessage());
                getServer().getScheduler().runTask(this, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.RED + "Die KI ist gerade nicht erreichbar. Bitte später erneut versuchen.");
                });
            }
        });
        return true;
    }

    private String askOllama(UUID id, String prompt) throws Exception {
        String baseUrl = getConfig().getString("ai.base-url", "http://localhost:11434").replaceAll("/+$", "");
        String model = getConfig().getString("ai.model", "llama3.2");
        String system = getConfig().getString("ai.system-prompt", "Du bist ein hilfreicher Minecraft-Assistent. Antworte kurz und freundlich.");
        int historySize = Math.max(0, getConfig().getInt("ai.history-size", 10));
        Deque<String> deque = history.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast("Spieler: " + prompt);
            while (deque.size() > historySize * 2) deque.removeFirst();
        }
        StringBuilder fullPrompt = new StringBuilder(system).append("\n\n");
        synchronized (deque) { for (String line : deque) fullPrompt.append(line).append('\n'); }
        fullPrompt.append("Assistent:");
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("prompt", fullPrompt.toString());
        body.addProperty("stream", false);
        JsonObject options = new JsonObject();
        options.addProperty("num_predict", Math.max(1, getConfig().getInt("ai.max-tokens", 300)));
        body.add("options", options);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("HTTP " + response.statusCode());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String answer = json.has("response") ? json.get("response").getAsString() : "";
        synchronized (deque) {
            deque.addLast("Assistent: " + answer);
            while (deque.size() > historySize * 2) deque.removeFirst();
        }
        return answer;
    }
}
