package net.runelite.client.plugins.srldagannoths;

import net.runelite.client.config.Button;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("srldags")
public interface SRLDagannothsConfig extends Config
{
    @ConfigItem(
            keyName = "Test",
            name = "Use testing enviroment",
            description = ""
    )
    default boolean useTestStuff()
    {
        return true;
    }

}
