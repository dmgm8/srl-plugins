package net.runelite.client.plugins.srlhunllef;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("srlhunllef")
public interface HunllefConfig extends Config {

    @ConfigItem(
            keyName = "useOffensive",
            name = "Use offensive prayers?",
            description = "",
            warning = "This will be disabled if you do not have any Egniol Potions",
            position = 10
    )
    default boolean useOffensive() { return true; }

    @ConfigItem(
            keyName = "rangeOffensive",
            name = "Range Offensive",
            description = "",
            position = 13
    )
    default PrayerEnum rangeOffensive()
    {
        return PrayerEnum.RIGOUR;
    }

    @ConfigItem(
            keyName = "mageOffensive",
            name = "Mage Offensive",
            description = "",
            position = 12
    )
    default PrayerEnum mageOffensive()
    {
        return PrayerEnum.AUGURY;
    }

    @ConfigItem(
            keyName = "meleeOffensive",
            name = "Melee Offensive",
            description = "",
            position = 11
    )
    default PrayerEnum meleeOffensive()
    {
        return PrayerEnum.PIETY;
    }

    @ConfigItem(
            keyName = "usePrayerTick",
            name = "Pray flick protection prayers?",
            description = "",
            warning = "Enabling this has a chance that you will still get hit!",
            position = 20
    )
    default boolean usePrayerTick() { return false; }
}
