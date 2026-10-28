/*
 * This file is part of sdlink-core, licensed under the MIT License (MIT).
 * Copyright HypherionSA and Contributors
 */
package com.hypherionmc.sdlink.core.discord.commands.slash.giveaway;

import com.hypherionmc.sdlink.core.database.GiveawayEntry;
import com.hypherionmc.sdlink.core.database.SDLinkAccount;
import com.hypherionmc.sdlink.core.discord.commands.slash.SDLinkSlashCommand;
import com.hypherionmc.sdlink.core.managers.DatabaseManager;
import com.hypherionmc.sdlink.core.managers.GiveawayStorage;
import com.hypherionmc.sdlink.core.managers.PremiumDetector;
import com.hypherionmc.sdlink.util.translations.SDText;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

/**
 * @author HypherionSA
 * Slash command that lets users join a giveaway by submitting a wish via a modal
 */
public class JoinGiveawayCommand extends SDLinkSlashCommand {

    public JoinGiveawayCommand() {
        super(false);

        this.name = "joingiveaway";
        this.help = SDText.translate("command.giveaway.join").toString();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String discordId = event.getUser().getId();

        if (GiveawayStorage.INSTANCE.load().stream().anyMatch(e -> discordId.equals(e.getDiscordId()))) {
            event.reply(SDText.translate("command.giveaway.already_joined").toString()).setEphemeral(true).queue();
            return;
        }

        SDLinkAccount account = DatabaseManager.INSTANCE.getCollection(SDLinkAccount.class).stream()
                .filter(a -> a.getDiscordID() != null && a.getDiscordID().equals(discordId))
                .findFirst()
                .orElse(null);

        boolean isPremium = account != null && PremiumDetector.isPremium(account.getUuid(), account.getInGameName());

        if (!isPremium) {
            GiveawayEntry entry = new GiveawayEntry();
            entry.setDiscordId(discordId);
            entry.setDiscordUsername(event.getUser().getName());
            entry.setMinecraftUuid(account == null ? "" : account.getUuid());
            entry.setMinecraftUsername(account == null ? "" : account.getInGameName());
            entry.setPremium(false);
            entry.setWish(SDText.translate("command.giveaway.wish_non_premium").toString());
            entry.setPrize("");

            GiveawayStorage.INSTANCE.upsert(entry);

            event.reply(SDText.translate("command.giveaway.auto_joined").toString()).setEphemeral(true).queue();
            return;
        }

        TextInput wish = TextInput.create("giveaway_wish", SDText.translate("command.giveaway.wish").toString(), TextInputStyle.PARAGRAPH)
                .setPlaceholder(SDText.translate("command.giveaway.wish_placeholder").toString())
                .setRequired(true)
                .setMaxLength(500)
                .build();

        Modal modal = Modal.create("join_giveaway", SDText.translate("command.giveaway.modal_title").toString())
                .addComponents(ActionRow.of(wish))
                .build();

        event.replyModal(modal).queue();
    }
}
