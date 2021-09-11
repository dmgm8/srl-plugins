package net.runelite.client.plugins.srlhunllef;

import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Prayer;

@Getter(AccessLevel.PACKAGE)
public enum PrayerEnum
{
    PIETY("PIETY",Prayer.PIETY),
    AUGURY("AUGURY", Prayer.AUGURY),
    RIGOUR("RIGOUR",Prayer.RIGOUR),
    EAGLE_EYE("EAGLE_EYE",Prayer.EAGLE_EYE),
    MYSTIC_MIGHT("MYSTIC_MIGHT",Prayer.MYSTIC_MIGHT);

    final private String name;
    final private Prayer prayer;

    PrayerEnum(String name, Prayer prayer)
    {
        this.name = name;
        this.prayer = prayer;
    }

    @Override
    public String toString()
    {
        return this.name;
    }

}
