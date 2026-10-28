/*
 * This file is part of sdlink-core, licensed under the MIT License (MIT).
 * Copyright HypherionSA and Contributors
 */
package com.hypherionmc.sdlink.core.database;

import lombok.Getter;
import lombok.Setter;

/**
 * @author HypherionSA
 * Represents a giveaway entry submitted via the /joingiveaway modal
 */
@Getter
@Setter
public class GiveawayEntry {

    private String discordId;
    private String discordUsername;
    private String minecraftUuid;
    private String minecraftUsername;
    private boolean isPremium;
    private String wish;
    private String prize;
    private String otherWish;
}
