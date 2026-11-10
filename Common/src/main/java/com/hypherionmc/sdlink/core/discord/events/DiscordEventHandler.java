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
import com.hypherionmc.sdlink.core.managers.PermissionChecker;
import com.hypherionmc.sdlink.core.services.SDLinkPlatform;
import com.hypherionmc.sdlink.util.PKUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
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

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("join-giveaway-wish-option")) {
            event.deferEdit().queue();

            String selectedLabel = event.getSelectedOptions().get(0).getLabel();

            Member member = event.getMember();

            assert member != null;
            SDLinkAccount account = DatabaseManager.INSTANCE.findBy("discordID", member.getId(), SDLinkAccount.class);

            GiveawayEntry giveawayEntry = new GiveawayEntry(account.getUuid(), member.getId(), account.getInGameName(), selectedLabel);
            DatabaseManager.INSTANCE.updateEntry(giveawayEntry);

            event.getHook().editOriginal(String.format("Berhasil mendaftarkan **%s** sebagai peserta giveaway. Reward yang dipilih: **%s**.", giveawayEntry.getUsername(), giveawayEntry.getWish()))
                    .setComponents()
                    .queue();

            String info = "Halo! Pendaftaran awal giveaway FesnukCraft kamu sudah berhasil.\n\n" +
                    "Satu langkah lagi agar resmi masuk ke dalam giveaway pool: kamu perlu membangun base terbaikmu di server!\n\n" +
                    "Detail lengkap dan ketentuan bisa kamu cek di thread berikut:\n" +
                    "🔗 https://discord.com/channels/1206212469148749834/1562463504106127391/1562463509512585288\n\n" +
                    "Good luck, dan selamat bermain! 🏰";

            event.getUser().openPrivateChannel()
                    .flatMap(channel -> channel.sendMessage(info)) // Rantai RestAction
                    .queue(
                            message -> System.out.println("DM terkirim!"),
                            failure -> event.getHook().sendMessage(info)
                                    .setEphemeral(true)
                                    .queue()
                    );
        }
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
