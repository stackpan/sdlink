/*
 * This file is part of sdlink-core, licensed under the MIT License (MIT).
 * Copyright HypherionSA and Contributors
 */
package com.hypherionmc.sdlink.core.discord.events;

import com.hypherionmc.craterlib.core.event.CraterEventBus;
import com.hypherionmc.sdlink.api.accounts.MinecraftAccount;
import com.hypherionmc.sdlink.api.events.SDLinkReadyEvent;
import com.hypherionmc.sdlink.compat.rolesync.RoleSync;
import com.hypherionmc.sdlink.core.config.SDLinkConfig;
import com.hypherionmc.sdlink.core.database.GiveawayEntry;
import com.hypherionmc.sdlink.core.database.SDLinkAccount;
import com.hypherionmc.sdlink.core.discord.BotController;
import com.hypherionmc.sdlink.core.discord.commands.slash.general.ServerStatusSlashCommand;
import com.hypherionmc.sdlink.core.discord.hooks.BotReadyHooks;
import com.hypherionmc.sdlink.core.discord.hooks.DiscordMessageHooks;
import com.hypherionmc.sdlink.core.discord.hooks.DiscordRoleHooks;
import com.hypherionmc.sdlink.core.discord.hooks.MinecraftCommandHook;
import com.hypherionmc.sdlink.core.managers.CacheManager;
import com.hypherionmc.sdlink.core.managers.ChannelManager;
import com.hypherionmc.sdlink.core.managers.DatabaseManager;
import com.hypherionmc.sdlink.core.managers.GiveawayStorage;
import com.hypherionmc.sdlink.core.managers.PermissionChecker;
import com.hypherionmc.sdlink.core.managers.PremiumDetector;
import com.hypherionmc.sdlink.core.services.SDLinkPlatform;
import com.hypherionmc.sdlink.util.PKUtil;
import com.hypherionmc.sdlink.util.translations.SDText;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateRolesEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.CloseCode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author HypherionSA
 * Class to provide Hooks for Discord Events, such as message received, and login
 * NOTE TO DEVELOPERS: Don't add ANY LOGIC IN HERE. Rather implement it in a seperate class,
 * and use these hooks to trigger that code
 */
public final class DiscordEventHandler extends ListenerAdapter {

    private boolean isStuckInNotReady = false;

    /**
     * Discord yeeted the bot connection
     */
    @Override
    public void onShutdown(ShutdownEvent event) {
        CloseCode code = event.getCloseCode();

        if (code == null) {
            BotController.INSTANCE.getLogger().error("Got disconnected from discord for an unknown reason. Code: {}", event.getCode());
            return;
        }

        if (code == CloseCode.DISALLOWED_INTENTS) {
            BotController.INSTANCE.getLogger().error("Your bot is missing a required setup step, and cannot continue. Please review https://sdlink.fdd-docs.com/installation/bot-creation/#privileged-gateway-intents to fix this");
            return;
        }

        BotController.INSTANCE.getLogger().error("Disconnected from discord with error {}", event.getCloseCode().name());
    }

    /**
     * The bot received a message
     */
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor() == event.getJDA().getSelfUser())
            return;

        if (event.isFromType(ChannelType.PRIVATE)) {
            DiscordMessageHooks.checkVerification(event);
            return;
        }

        if (!event.isFromGuild())
            return;

        if (!event.isWebhookMessage()) {
            MinecraftCommandHook.discordMessageEvent(event);
        }

        if (SDLinkConfig.INSTANCE.chatConfig.pluralKitCompat && PKUtil.PK_USERS.contains(event.getAuthor().getId())) {
            new Thread(() -> {
                try {
                    Thread.sleep(SDLinkConfig.INSTANCE.chatConfig.pluralKitCompatMessageDelay);
                } catch (InterruptedException e) {
                    BotController.INSTANCE.getLogger().error("Unexpected InterruptedException", e);
                }

                DiscordMessageHooks.discordMessageEvent(event);
            }).start();
        } else
            DiscordMessageHooks.discordMessageEvent(event);

    }

    /**
     * The bot is connected to discord and ready to begin sending messages
     */
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        if (event.getJDA().getStatus() != JDA.Status.CONNECTED && event.getJDA().getStatus() != JDA.Status.DISCONNECTED) {
            isStuckInNotReady = true;
            startReadyDetection(event.getJDA());
        }

        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            isStuckInNotReady = false;
            BotController.INSTANCE.getLogger().info("Successfully connected to discord");

            PermissionChecker.checkBotSetup();
            ChannelManager.loadChannels();
            BotReadyHooks.startActivityUpdates(event);
            BotReadyHooks.startTopicUpdates();
            CacheManager.loadCache();
            CraterEventBus.INSTANCE.postEvent(new SDLinkReadyEvent());
        }
    }

    /**
     * A button was clicked.
     */
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getComponentId().equals("sdrefreshbtn")) {
            event.deferEdit().queue(s -> s.editOriginalEmbeds(ServerStatusSlashCommand.runStatusCommand()).queue());
        }
    }

    /**
     * A modal was submitted. Handles the /joingiveaway wish submission.
     */
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if ("giveaway_other_wish".equals(modalId)) {
            String discordId = event.getUser().getId();

            String otherWish = event.getValues().stream()
                    .filter(value -> value.getId().equals("giveaway_other_wish"))
                    .map(ModalMapping::getAsString)
                    .findFirst()
                    .orElse("");

            GiveawayEntry entry = new GiveawayEntry();
            entry.setDiscordId(discordId);
            entry.setOtherWish(otherWish);

            GiveawayStorage.INSTANCE.upsert(entry);

            event.getHook().sendMessage(SDText.translate("command.giveaway.other_wish_saved", otherWish).toString()).setEphemeral(true).queue();
            return;
        }

        if (!"join_giveaway".equals(modalId)) {
            return;
        }

        String discordId = event.getUser().getId();
        String discordUsername = event.getUser().getName();

        String wish = event.getValues().stream()
                .filter(value -> value.getId().equals("giveaway_wish"))
                .map(ModalMapping::getAsString)
                .findFirst()
                .orElse("");

        SDLinkAccount account = DatabaseManager.INSTANCE.getCollection(SDLinkAccount.class).stream()
                .filter(a -> a.getDiscordID() != null && a.getDiscordID().equals(discordId))
                .findFirst()
                .orElse(null);

        String minecraftUuid = "";
        String minecraftUsername = "";
        boolean isPremium = false;

        if (account != null) {
            minecraftUuid = account.getUuid();
            minecraftUsername = account.getInGameName();
            isPremium = PremiumDetector.isPremium(minecraftUuid, minecraftUsername);
        }

        GiveawayEntry entry = new GiveawayEntry();
        entry.setDiscordId(discordId);
        entry.setDiscordUsername(discordUsername);
        entry.setMinecraftUuid(minecraftUuid);
        entry.setMinecraftUsername(minecraftUsername);
        entry.setPremium(isPremium);
        entry.setWish(wish);
        entry.setPrize("");

        GiveawayStorage.INSTANCE.upsert(entry);

        StringSelectMenu selectMenu = StringSelectMenu.create("giveaway_prize_select")
                .setPlaceholder(SDText.translate("command.giveaway.select_prize").toString())
                .addOptions(SelectOption.of("Minecraft: Dungeons 1", "minecraft_dungeons_1"))
                .addOptions(SelectOption.of("1720 Minecoin", "minecoin_1720"))
                .addOptions(SelectOption.of("Wynncraft HERO Rank", "wynncraft_hero"))
                .addOptions(SelectOption.of("Wynncraft 20 cosmetics crates", "wynncraft_crates"))
                .addOptions(SelectOption.of("Hypixel VIP+ Rank", "hypixel_vip_plus"))
                .addOptions(SelectOption.of("Hypixel 1800 SkyBlock Gems", "hypixel_skyblock_gems"))
                .addOptions(SelectOption.of("Hypixel Jungle Companion Bundle", "hypixel_jungle_companion"))
                .addOptions(SelectOption.of("DonutSMP 12000 Shards", "donutsmp_shards"))
                .addOptions(SelectOption.of("PvP Club VIP Rank", "pvp_club_vip"))
                .addOptions(SelectOption.of("Hoplite Starter Pack", "hoplite_starter"))
                .addOptions(SelectOption.of("Hoplite 2500 Gems", "hoplite_gems"))
                .addOptions(SelectOption.of("Other", "other"))
                .setMinValues(1)
                .setMaxValues(1)
                .build();

        event.getHook().sendMessage(SDText.translate("command.giveaway.select_prize_message").toString())
                .addComponents(ActionRow.of(selectMenu))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("giveaway_prize_select")) {
            return;
        }

        String discordId = event.getUser().getId();
        String selectedPrize = event.getValues().get(0);

        if ("other".equals(selectedPrize)) {
            TextInput otherWish = TextInput.create("giveaway_other_wish", SDText.translate("command.giveaway.other_wish_label").toString(), TextInputStyle.PARAGRAPH)
                    .setPlaceholder(SDText.translate("command.giveaway.other_wish_placeholder").toString())
                    .setRequired(true)
                    .setMaxLength(50)
                    .build();

            Modal modal = Modal.create("giveaway_other_wish", SDText.translate("command.giveaway.other_wish_title").toString())
                    .addComponents(ActionRow.of(otherWish))
                    .build();

            event.replyModal(modal).queue();
            return;
        }

        GiveawayEntry entry = new GiveawayEntry();
        entry.setDiscordId(discordId);
        entry.setPrize(selectedPrize);

        GiveawayStorage.INSTANCE.upsert(entry);

        event.getHook().editOriginal(SDText.translate("command.giveaway.prize_selected", selectedPrize).toString()).queue();
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadUserCache();
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadUserCache();
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadUserCache();
        }

        if (event.getUser().isBot() || !(SDLinkConfig.INSTANCE.accessControl.enabled || SDLinkConfig.INSTANCE.accessControl.optionalVerification))
            return;

        try {
            List<SDLinkAccount> accounts = DatabaseManager.INSTANCE.getCollection(SDLinkAccount.class);
            Optional<SDLinkAccount> account = accounts.stream().filter(a -> a.getDiscordID() != null && a.getDiscordID().equalsIgnoreCase(event.getUser().getId())).findFirst();
            account.ifPresent(a -> DatabaseManager.INSTANCE.deleteEntry(a, SDLinkAccount.class));
        } catch (Exception e) {
            BotController.INSTANCE.getLogger().error("Failed to remove linked account", e);
        }
    }

    @Override
    public void onRoleCreate(@NotNull RoleCreateEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadRoleCache();
        }
    }

    @Override
    public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadRoleCache();
        }
    }

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadChannelCache();
        }
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        if (event.getJDA().getStatus() == JDA.Status.CONNECTED) {
            CacheManager.loadChannelCache();
        }
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (event.getUser().isBot())
            return;

        CacheManager.loadUserCache();

        if (!SDLinkConfig.INSTANCE.accessControl.enabled && !SDLinkConfig.INSTANCE.accessControl.optionalVerification)
            return;

        try {
            List<SDLinkAccount> accounts = DatabaseManager.INSTANCE.getCollection(SDLinkAccount.class);
            Optional<SDLinkAccount> account = accounts.stream().filter(a -> a.getDiscordID() != null && a.getDiscordID().equalsIgnoreCase(event.getUser().getId())).findFirst();

            account.ifPresent(a -> {
                MinecraftAccount acc = MinecraftAccount.of(a);

                if (acc != null) {
                    if (SDLinkConfig.INSTANCE.accessControl.banPlayerOnDiscordBan) {
                        SDLinkPlatform.minecraftHelper.banPlayer(acc);
                    }
                }

                DatabaseManager.INSTANCE.deleteEntry(a, SDLinkAccount.class);
            });
        } catch (Exception e) {
            BotController.INSTANCE.getLogger().error("Failed to remove linked account", e);
        }
    }

    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        DiscordRoleHooks.INSTANCE.onRoleAdded(event);

        event.getRoles().forEach(role -> {
            RoleSync.INSTANCE.roleAddedToMember(event.getMember(), role, event.getGuild());
        });
    }

    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        DiscordRoleHooks.INSTANCE.onRoleRemoved(event);

        event.getRoles().forEach(role -> {
            RoleSync.INSTANCE.roleRemovedFromMember(event.getMember(), role, event.getGuild(), new ArrayList<>());
        });
    }

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        CacheManager.loadEmoteCache();
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        CacheManager.loadEmoteCache();
    }

    @Override
    public void onEmojiUpdateName(EmojiUpdateNameEvent event) {
        CacheManager.loadEmoteCache();
    }

    @Override
    public void onEmojiUpdateRoles(EmojiUpdateRolesEvent event) {
        CacheManager.loadEmoteCache();
    }

    private void startReadyDetection(JDA jda) {
        BotController.INSTANCE.updatesManager.scheduleAtFixedRate(() -> {
            if (isStuckInNotReady && jda.getStatus() == JDA.Status.CONNECTED) {
                onReady(new ReadyEvent(jda));
                isStuckInNotReady = false;
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
}
