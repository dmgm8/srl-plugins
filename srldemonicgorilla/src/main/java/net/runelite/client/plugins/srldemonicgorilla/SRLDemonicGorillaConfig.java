package net.runelite.client.plugins.srldemonicgorilla;

import net.runelite.api.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("demonicGorillaAP")
public interface SRLDemonicGorillaConfig extends Config
{
    @ConfigItem(
            position = 1,
            keyName = "meleeWeapon",
            name = "Melee weapon ID",
            description = ""
    )
    default int meleeWeapon() { return ItemID.ARCLIGHT; }

    @ConfigItem(
            position = 2,
            keyName = "rangeWeapon",
            name = "Ranged weapon ID",
            description = ""
    )
    default int rangeWeapon() { return ItemID.RUNE_CROSSBOW; }

    @ConfigItem(
            position = 3,
            keyName = "useTickDelay",
            name = "Use Tick Delay",
            description = ""
    )
    default boolean useTickDelay() { return true; }

    @ConfigItem(
            position = 4,
            keyName = "tickDelayMin",
            name = "Tick Delay Min",
            description = ""
    )
    default int clickDelayMin() { return 1; }

    @ConfigItem(
            position = 5,
            keyName = "tickDelayMax",
            name = "Tick Delay Max",
            description = ""
    )
    default int clickDelayMax() { return 3; }

    @ConfigItem(
            position = 6,
            keyName = "boostPrayer",
            name = "Use Eagle Eye/Mystic Might boost ",
            description = ""
    )
    default boolean usePrayerBoost() { return true; }

    @ConfigItem(
            position = 7,
            keyName = "useAssist",
            name = "Automatically switch overhead prayers",
            description = ""
    )
    default boolean useAssist() { return true; }


    @ConfigItem(
            position = 8,
            keyName = "showDebugMsg",
            name = "Show Debug messages in-game",
            description = ""
    )
    default boolean showDebugMsg() { return false; }
}
