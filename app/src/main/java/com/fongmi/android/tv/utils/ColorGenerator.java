package com.fongmi.android.tv.utils;


import java.util.ArrayList;
import java.util.List;

public class ColorGenerator {

    private static final List<Integer> PALETTE_400;
    private static final List<Integer> PALETTE_700;

    static {
        PALETTE_400 = new ArrayList<>();
        PALETTE_400.add(MaterialColor.RED_400);
        PALETTE_400.add(MaterialColor.PINK_400);
        PALETTE_400.add(MaterialColor.PURPLE_400);
        PALETTE_400.add(MaterialColor.DEEP_PURPLE_400);
        PALETTE_400.add(MaterialColor.INDIGO_400);
        PALETTE_400.add(MaterialColor.BLUE_400);
        PALETTE_400.add(MaterialColor.LIGHT_BLUE_400);
        PALETTE_400.add(MaterialColor.CYAN_400);
        PALETTE_400.add(MaterialColor.TEAL_400);
        PALETTE_400.add(MaterialColor.GREEN_400);
        PALETTE_400.add(MaterialColor.LIGHT_GREEN_400);
        PALETTE_400.add(MaterialColor.LIME_400);
        PALETTE_400.add(MaterialColor.YELLOW_400);
        PALETTE_400.add(MaterialColor.AMBER_400);
        PALETTE_400.add(MaterialColor.ORANGE_400);
        PALETTE_400.add(MaterialColor.DEEP_ORANGE_400);
        PALETTE_400.add(MaterialColor.BROWN_400);
        PALETTE_400.add(MaterialColor.GREY_400);
        PALETTE_400.add(MaterialColor.BLUE_GREY_400);

        PALETTE_700 = new ArrayList<>();
        PALETTE_700.add(MaterialColor.RED_700);
        PALETTE_700.add(MaterialColor.PINK_700);
        PALETTE_700.add(MaterialColor.PURPLE_700);
        PALETTE_700.add(MaterialColor.DEEP_PURPLE_700);
        PALETTE_700.add(MaterialColor.INDIGO_700);
        PALETTE_700.add(MaterialColor.BLUE_700);
        PALETTE_700.add(MaterialColor.LIGHT_BLUE_700);
        PALETTE_700.add(MaterialColor.CYAN_700);
        PALETTE_700.add(MaterialColor.TEAL_700);
        PALETTE_700.add(MaterialColor.GREEN_700);
        PALETTE_700.add(MaterialColor.LIGHT_GREEN_700);
        PALETTE_700.add(MaterialColor.LIME_700);
        PALETTE_700.add(MaterialColor.YELLOW_700);
        PALETTE_700.add(MaterialColor.AMBER_700);
        PALETTE_700.add(MaterialColor.ORANGE_700);
        PALETTE_700.add(MaterialColor.DEEP_ORANGE_700);
        PALETTE_700.add(MaterialColor.BROWN_700);
        PALETTE_700.add(MaterialColor.GREY_700);
        PALETTE_700.add(MaterialColor.BLUE_GREY_700);
    }

    public static int get400(String key) {
        return PALETTE_400.get((key.hashCode() & Integer.MAX_VALUE) % PALETTE_400.size());
    }

    public static int get700(String key) {
        return PALETTE_700.get((key.hashCode() & Integer.MAX_VALUE) % PALETTE_700.size());
    }
}
