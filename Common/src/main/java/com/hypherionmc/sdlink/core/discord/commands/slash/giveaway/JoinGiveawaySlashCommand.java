package com.hypherionmc.sdlink.core.discord.commands.slash.giveaway;

import com.hypherionmc.sdlink.core.config.SDLinkConfig;
import com.hypherionmc.sdlink.core.database.GiveawayEntry;
import com.hypherionmc.sdlink.core.database.SDLinkAccount;
import com.hypherionmc.sdlink.core.discord.commands.slash.SDLinkSlashCommand;
import com.hypherionmc.sdlink.core.managers.DatabaseManager;
import com.hypherionmc.sdlink.util.PremiumDetector;
import com.hypherionmc.sdlink.util.translations.SDText;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class JoinGiveawaySlashCommand extends SDLinkSlashCommand {

    public JoinGiveawaySlashCommand() {
        super(false);

        this.name = "joingiveaway";
        this.help = SDText.translate("Pendaftaran event Giveaway FesnukCraft").toString();
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(SDLinkConfig.INSTANCE.botConfig.silentReplies).queue();

        Member member = event.getMember();

        assert member != null;
        SDLinkAccount account = DatabaseManager.INSTANCE.findBy("discordID", member.getId(), SDLinkAccount.class);

        if (account == null) {
            event.getHook().sendMessage("Sepertinya kamu belum pernah bermain di FesnukCraft. Join ke server terlebih dahulu kemudian `/verify` supaya akun Discord terhubung dengan IGN Minecraft kamu.").queue();
            return;
        }

        GiveawayEntry entry = DatabaseManager.INSTANCE.findById(account.getUuid(), GiveawayEntry.class);
        if (entry != null) {
            event.getHook().sendMessage(String.format("Kamu sudah terdaftar sebagai peserta giveaway. IGN: **%s**, reward: **%s**", entry.getUsername(), entry.getWish())).queue();
            return;
        }

        boolean isPremium = PremiumDetector.isPremium(account.getUuid(), account.getInGameName());

        if (isPremium) {
            StringSelectMenu menu = StringSelectMenu.create("join-giveaway-wish-option")
                    .addOption("1720 Minecoin", "join-giveaway-wish-option-minecoin")
                    .addOption("Minecraft: Dungeons 1", "join-giveaway-wish-option-mcdg1")
                    .addOption("Wynncraft HERO Rank", "join-giveaway-wish-option-wynnrank")
                    .addOption("Wynncraft 20 Cosmetic Crates", "join-giveaway-wish-option-wynncrates")
                    .addOption("Hypixel VIP+ Rank", "join-giveaway-wish-option-hypixelrank")
                    .addOption("Hypixel 1800 SkyBlock Gems", "join-giveaway-wish-option-hypixelsbgems")
                    .addOption("Hypixel Jungle Companion Bundle", "join-giveaway-wish-option-hypixelbundle")
                    .addOption("DonutSMP 12000 Shards", "join-giveaway-wish-option-donutshards")
                    .addOption("PvP Club VIP Rank", "join-giveaway-wish-option-pvpclubrank")
                    .addOption("Hoplite Starter Pack", "join-giveaway-wish-option-hoplitestarter")
                    .addOption("Hoplite 2500 Gems", "join-giveaway-wish-option-hoplitegems")
                    .build();

            event.getHook().sendMessage("Pilih reward anda")
                    .addActionRow(menu)
                    .queue();
            return;
        }

        GiveawayEntry giveawayEntry = new GiveawayEntry(account.getUuid(), member.getId(), account.getInGameName(), "Minecraft Java & Bedrock");
        DatabaseManager.INSTANCE.updateEntry(giveawayEntry);

        event.getHook().sendMessage(String.format("Berhasil mendaftarkan **%s** sebagai peserta giveaway. Reward yang dipilih: **%s**.", giveawayEntry.getUsername(), giveawayEntry.getWish())).queue();

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
