package local.icepractice;

import java.util.*;
import net.minecraft.network.chat.*;

public final class MiningProfitTest {
    public static void main(String[] args) throws Exception {
        if(MiningItemRarity.color("COMMON")!=0xffffffff||MiningItemRarity.color("UNCOMMON")!=0xff55ff55||MiningItemRarity.color("RARE")!=0xff5555ff||MiningItemRarity.color("EPIC")!=0xffaa00aa||MiningItemRarity.color("LEGENDARY")!=0xffffaa00||MiningItemRarity.color(null)!=0xffffffff)throw new AssertionError("Hypixel rarity colors");
        Component added=Component.literal("+123 items").withStyle(s->s.withHoverEvent(new HoverEvent.ShowText(Component.literal("Added items:\n+1,234 Glacite (Mining Sack)\n+20 \uE051 Rough Ruby Gemstone (Gemstone Sack)\n+9 Wheat (Farming Sack)"))));
        Component removed=Component.literal("-2 items").withStyle(s->s.withHoverEvent(new HoverEvent.ShowText(Component.literal("Removed items:\n-2 Glacite (Mining Sack)"))));
        Component message=Component.literal("[Sacks] ").append(added).append(removed);
        Map<String,Long> found=new HashMap<>();MiningProfitClient.collect(message,found);
        if(!found.equals(Map.of("Glacite",1234L,"Rough Ruby Gemstone",20L)))throw new AssertionError("Unexpected parsed entries: "+found);
        if(!MiningProfitClient.normalizeName("\uE051 Flawed Peridot Gemstone").equals("Flawed Peridot Gemstone"))throw new AssertionError("Gemstone name normalization failed");
        var inputs=Map.of("Rough Peridot Gemstone",200L,"Flawed Peridot Gemstone",3L,"Fine Peridot Gemstone",1L,"Enchanted Glacite",2L);
        var removals=Map.of("Rough Peridot Gemstone",160L,"Flawed Peridot Gemstone",80L,"Glacite",320L);
        var uncompressed=MiningProfitCompression.conversionDeltas(inputs,removals);
        if(!uncompressed.equals(Map.of("Rough Peridot Gemstone",40L,"Flawed Peridot Gemstone",-77L,"Fine Peridot Gemstone",1L,"Enchanted Glacite",2L,"Glacite",-320L)))throw new AssertionError("Chained conversion incorrect: "+uncompressed);
        var direct=MiningProfitCompression.conversionDeltas(Map.of("Fine Onyx Gemstone",1L),Map.of("Rough Onyx Gemstone",6400L));
        if(!direct.equals(Map.of("Fine Onyx Gemstone",1L,"Rough Onyx Gemstone",-6400L)))throw new AssertionError("Direct rough-to-fine conversion wrong");
        var mixed=MiningProfitCompression.conversionDeltas(Map.of("Glacite",80L,"Enchanted Glacite",3L),Map.of("Glacite",320L));
        if(!mixed.equals(Map.of("Glacite",-240L,"Enchanted Glacite",3L)))throw new AssertionError("Compactor and direct enchanted drop not separated: "+mixed);
        var compact=MiningProfitCompression.conversionDeltas(Map.of("Enchanted Glacite",1L),Map.of());
        if(!compact.equals(Map.of("Enchanted Glacite",1L)))throw new AssertionError("Direct enchanted drop discarded");
        var screenshot=MiningProfitCompression.conversionDeltas(Map.of("Enchanted Glacite",28L,"Glacite",4434L,"Enchanted Hard Stone",1L,"Hard Stone",402L),Map.of("Glacite",4480L,"Hard Stone",576L));
        if(!screenshot.equals(Map.of("Enchanted Glacite",28L,"Glacite",-46L,"Enchanted Hard Stone",1L,"Hard Stone",-174L)))throw new AssertionError("Screenshot sack conversion incorrect: "+screenshot);
        var file=java.nio.file.Files.createTempFile("profit-gems-", ".json");
        try {
            java.nio.file.Files.writeString(file,"{\"accounts\":{\"test\":{\"timeNanos\":42,\"items\":{\"\\uE051 Flawed Peridot Gemstone\":20,\"Flawed Peridot Gemstone\":3}}}}");
            var path=MiningProfitClient.class.getDeclaredField("path");path.setAccessible(true);path.set(null,file);
            var owner=MiningProfitClient.class.getDeclaredField("owner");owner.setAccessible(true);owner.set(null,"test");
            var load=MiningProfitClient.class.getDeclaredMethod("loadOwner");load.setAccessible(true);load.invoke(null);
            var total=MiningProfitClient.class.getDeclaredField("total");total.setAccessible(true);
            if(!((Map<?,?>)total.get(null)).equals(Map.of("Flawed Peridot Gemstone",23L)))throw new AssertionError("Saved gemstone totals not preserved");
        } finally { java.nio.file.Files.deleteIfExists(file); }
        var api=IceGameContext.class.getDeclaredField("apiSkyblock");api.setAccessible(true);api.setBoolean(null,true);
        var mining=MiningProfitClient.class.getDeclaredField("lastMining");mining.setAccessible(true);mining.setLong(null,System.nanoTime());
        var onMessage=MiningProfitClient.class.getDeclaredMethod("message",Component.class);onMessage.setAccessible(true);
        var pending=MiningProfitClient.class.getDeclaredField("pending");pending.setAccessible(true);
        onMessage.invoke(null,message);if(((List<?>)pending.get(null)).size()!=1)throw new AssertionError("Mining batch was not queued");
        onMessage.invoke(null,Component.literal("  TUNGSTEN CORPSE LOOT! "));if(!((List<?>)pending.get(null)).isEmpty())throw new AssertionError("Nearby corpse batch not discarded");
        onMessage.invoke(null,message);if(!((List<?>)pending.get(null)).isEmpty())throw new AssertionError("Corpse window batch not discarded");
        System.out.println("Mining sack hover parsing and corpse exclusion passed.");
    }
}
