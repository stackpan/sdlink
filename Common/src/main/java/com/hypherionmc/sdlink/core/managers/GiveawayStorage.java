/*
 * This file is part of sdlink-core, licensed under the MIT License (MIT).
 * Copyright HypherionSA and Contributors
 */
package com.hypherionmc.sdlink.core.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypherionmc.sdlink.core.database.GiveawayEntry;
import com.hypherionmc.sdlink.core.discord.BotController;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author HypherionSA
 * Persists giveaway entries to sdlinkstorage/giveawayentries.json as a flat JSON array
 */
public final class GiveawayStorage {

    public static final GiveawayStorage INSTANCE = new GiveawayStorage();

    private static final Path FILE = Path.of("sdlinkstorage", "giveawayentries.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GiveawayStorage() {
    }

    public List<GiveawayEntry> load() {
        if (!Files.exists(FILE)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            List<GiveawayEntry> entries = GSON.fromJson(reader, new TypeToken<List<GiveawayEntry>>() {
            }.getType());
            return entries == null ? new ArrayList<>() : entries;
        } catch (Exception e) {
            BotController.INSTANCE.getLogger().error("Failed to load giveaway entries", e);
            return new ArrayList<>();
        }
    }

    public void save(List<GiveawayEntry> entries) {
        try {
            if (FILE.getParent() != null) {
                Files.createDirectories(FILE.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(entries, writer);
            }
        } catch (IOException e) {
            BotController.INSTANCE.getLogger().error("Failed to save giveaway entries", e);
        }
    }

    public void upsert(GiveawayEntry entry) {
        List<GiveawayEntry> entries = load();
        entries.removeIf(e -> entry.getDiscordId().equals(e.getDiscordId()));
        entries.add(entry);
        save(entries);
    }
}
