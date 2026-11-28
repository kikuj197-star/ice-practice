package local.icepractice;

import java.util.Locale;

final class MiningItemRarity {
    static int color(String tier){
        if(tier==null)return 0xffffffff;
        return switch(tier.toUpperCase(Locale.ROOT)){
            case "UNCOMMON" -> 0xff55ff55;
            case "RARE" -> 0xff5555ff;
            case "EPIC" -> 0xffaa00aa;
            case "LEGENDARY" -> 0xffffaa00;
            case "MYTHIC" -> 0xffff55ff;
            case "DIVINE" -> 0xff55ffff;
            case "SPECIAL", "VERY_SPECIAL" -> 0xffff5555;
            default -> 0xffffffff;
        };
    }
    private MiningItemRarity(){}
}
