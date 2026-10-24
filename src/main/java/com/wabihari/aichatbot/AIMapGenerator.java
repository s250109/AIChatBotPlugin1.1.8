package com.wabihari.aichatbot;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
                        .getString(
                                "aimap.cloudflare.model",
                                "@cf/bytedance/stable-diffusion-xl-lightning"
                        );

                if (accountId.isBlank() || apiToken.isBlank()) {
                    sendMessage(playerId,
                            "§cCloudflare is not configured.");
                    return;
                }

                JsonObject body = new JsonObject();
                body.addProperty("prompt", prompt);

                String endpoint =
                        "https://api.cloudflare.com/client/v4/accounts/"
                                + accountId
                                + "/ai/run/"
                                + model;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(120))
                        .header("Authorization", "Bearer " + apiToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                body.toString()))
                        .build();

                HttpResponse<byte[]> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofByteArray()
                );

                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {

                    plugin.getLogger().warning(
                            "Cloudflare image request failed: HTTP "
                                    + response.statusCode());

                    sendMessage(playerId,
                            "§cImage generation failed. "
                                    + "Check the server console.");
                    return;
                }

                byte[] imageBytes = response.body();

                if (imageBytes.length == 0) {
                    sendMessage(playerId, "§cDas Bild ist leer.");
                    return;
                }

                BufferedImage original;

                try (ByteArrayInputStream input =
                             new ByteArrayInputStream(imageBytes)) {

                    original = ImageIO.read(input);
                }

                if (original == null) {
                    plugin.getLogger().warning(
                            "Cloudflare returned data that could not "
                                    + "be decoded as an image.");

                    sendMessage(playerId,
                            "§cThe AI ​​response is not a readable image.");
                    return;
                }

                BufferedImage mapImage =
                        new BufferedImage(
                                128,
                                128,
                                BufferedImage.TYPE_INT_RGB
                        );

                Graphics2D graphics = mapImage.createGraphics();

                try {
                    graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR
                    );

                    graphics.setRenderingHint(
                            RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY
                    );

                    graphics.drawImage(
                            original,
                            0,
                            0,
                            128,
                            128,
                            null
                    );
                } finally {
                    graphics.dispose();
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerId);

                    if (onlinePlayer == null
                            || !onlinePlayer.isOnline()) {
                        return;
                    }

                    try {
                        MapView map = Bukkit.createMap(
                                onlinePlayer.getWorld()
                        );

                        for (MapRenderer renderer :
                                map.getRenderers()) {
                            map.removeRenderer(renderer);
                        }

                        map.addRenderer(new MapRenderer(false) {
                            private boolean drawn = false;

                            @Override
                            public void render(
                                    MapView view,
                                    MapCanvas canvas,
                                    Player viewer
                            ) {
                                if (drawn) {
                                    return;
                                }

                                canvas.drawImage(0, 0, mapImage);
                                drawn = true;
                            }
                        });

                        ItemStack item = new ItemStack(
                                Material.FILLED_MAP
                        );

                        MapMeta meta = (MapMeta) item.getItemMeta();

                        if (meta == null) {
                            sendMessage(playerId,
                                    "§cThe map metadata is missing.");
                            return;
                        }

                        meta.setMapView(map);
                        item.setItemMeta(meta);

                        var leftovers = onlinePlayer
                                .getInventory()
                                .addItem(item);

                        leftovers.values().forEach(leftover ->
                                onlinePlayer.getWorld().dropItemNaturally(
                                        onlinePlayer.getLocation(),
                                        leftover
                                )
                        );

                        onlinePlayer.sendMessage(
                                "§aYour AI map is finished!"
                        );

                    } catch (Exception exception) {
                        plugin.getLogger().warning(
                                "Could not create Minecraft map: "
                                        + exception.getMessage()
                        );

                        onlinePlayer.sendMessage(
                                "§cThe Minecraft map could "
                                        + "not be created."
                        );
                    }
                });

            } catch (Exception exception) {
                plugin.getLogger().warning(
                        "AI map generation failed: "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + exception.getMessage()
                );

                sendMessage(playerId,
                        "§cError during image generation. "
                                + "Check the server console.");
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
