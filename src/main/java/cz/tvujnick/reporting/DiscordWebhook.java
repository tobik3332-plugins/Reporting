package cz.tvujnick.reporting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    public static void sendEmbed(String webhookUrl, String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("TVUJ_WEBHOOK_URL_ZDE")) return;

        Bukkit.getScheduler().runTaskAsynchronously(Reporting.getInstance(), () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                JsonObject embed = new JsonObject();
                embed.addProperty("title", title);
                embed.addProperty("description", description);
                embed.addProperty("color", color);

                JsonArray embeds = new JsonArray();
                embeds.add(embed);

                JsonObject json = new JsonObject();
                json.add("embeds", embeds);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                connection.getInputStream().close();
            } catch (Exception e) {
                Reporting.getInstance().getLogger().warning("Nepodarilo se odeslat Discord webhook: " + e.getMessage());
            }
        });
    }
}
