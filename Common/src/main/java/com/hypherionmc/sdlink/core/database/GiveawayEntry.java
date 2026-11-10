package com.hypherionmc.sdlink.core.database;

import com.hypherionmc.sdlink.core.jsondb.annotations.Document;
import com.hypherionmc.sdlink.core.jsondb.annotations.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@Document(collection = "giveawayentries")
public final class GiveawayEntry {

    @Id
    private String uuid;
    private String discordID;
    private String username;
    private String wish;
}
