/*
 * This file is part of sdlink-core, licensed under the MIT License (MIT).
 * Copyright HypherionSA and Contributors
 */
package com.hypherionmc.sdlink.core.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypherionmc.sdlink.core.discord.BotController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * @author HypherionSA
 * Detects whether a Minecraft UUID is a premium (paid) account via Mojang's session server API
 */
public final class PremiumDetector {

    private static final String MOJANG_SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/profile/%s";

    private PremiumDetector() {
    }

    /**
     * Queries Mojang's session server to determine if the given UUID is a premium account.
     * Offline (cracked) UUIDs are name-based and will not be found on the session server.
     *
     * @param uuid     The Minecraft UUID to check
     * @param username The expected Minecraft username
     * @return true if the UUID is a premium account matching the username, false otherwise
     */
    public static boolean isPremium(String uuid, String username) {
        if (uuid == null || uuid.isBlank() || username == null || username.isBlank()) {
            return false;
        }

        try {
            URL url = new URL(String.format(MOJANG_SESSION_SERVER, uuid));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JsonObject profile = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (!profile.has("name")) {
                    return false;
                }

                String profileName = profile.get("name").getAsString();
                return profileName.equalsIgnoreCase(username);
            }
        } catch (Exception e) {
            BotController.INSTANCE.getLogger().error("Failed to check premium status for UUID {}", uuid, e);
            return false;
        }
    }
}
