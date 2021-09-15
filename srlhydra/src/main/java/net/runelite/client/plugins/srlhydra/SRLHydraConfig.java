package net.runelite.client.plugins.srlhydra;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("srlhydra")
public interface SRLHydraConfig extends Config {

    @ConfigItem(
            keyName = "usePrayerTick",
            name = "Pray flick protection prayers?",
            description = "",
            warning = "Enabling this has a chance that you will still get hit!",
            position = 10
    )
    default boolean usePrayerTick() { return false; }
}
