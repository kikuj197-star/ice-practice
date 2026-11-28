package local.icepractice;

import java.util.*;

/** Accounts for conversions visible in a single aggregated sack update. */
final class MiningProfitCompression {
    private record Rule(String source,String target,long ratio){}
    private static final List<Rule> RULES=rules();
    private static List<Rule> rules(){
        List<Rule> r=new ArrayList<>();
        String[] gems={"Ruby","Amber","Amethyst","Jade","Sapphire","Topaz","Jasper","Opal","Aquamarine","Citrine","Onyx","Peridot"};
        for(String gem:gems)r.add(new Rule("Rough "+gem+" Gemstone","Flawed "+gem+" Gemstone",80));
        for(String gem:gems)r.add(new Rule("Flawed "+gem+" Gemstone","Fine "+gem+" Gemstone",80));
        for(String gem:gems)r.add(new Rule("Rough "+gem+" Gemstone","Fine "+gem+" Gemstone",6400));
        String[] ordinary={"Glacite","Mithril","Titanium","Tungsten","Umber","Coal","Diamond","Emerald","Ice","Redstone","Lapis Lazuli","Quartz","Obsidian","Cobblestone"};
        for(String name:ordinary)r.add(new Rule(name,"Enchanted "+name,160));
        r.add(new Rule("Iron Ingot","Enchanted Iron",160));r.add(new Rule("Gold Ingot","Enchanted Gold",160));
        r.add(new Rule("Iron Ingot","Enchanted Iron Ingot",160));r.add(new Rule("Gold Ingot","Enchanted Gold Ingot",160));r.add(new Rule("Redstone Dust","Enchanted Redstone Dust",160));
        r.add(new Rule("Hard Stone","Enchanted Hard Stone",576));
        for(String name:new String[]{"Coal","Diamond","Emerald","Lapis Lazuli"})r.add(new Rule("Enchanted "+name,"Enchanted "+name+" Block",160));
        r.add(new Rule("Enchanted Iron Ingot","Enchanted Iron Block",160));r.add(new Rule("Enchanted Gold Ingot","Enchanted Gold Block",160));
        return List.copyOf(r);
    }
    static Map<String,Long> conversionDeltas(Map<String,Long> added,Map<String,Long> removed){
        Map<String,Long> result=new HashMap<>(added),available=new HashMap<>(removed),outputs=new HashMap<>(added);
        for(Rule r:RULES){long n=Math.min(outputs.getOrDefault(r.target,0L),available.getOrDefault(r.source,0L)/r.ratio);if(n<=0)continue;result.merge(r.source,-n*r.ratio,Long::sum);available.compute(r.source,(k,v)->v-n*r.ratio);outputs.compute(r.target,(k,v)->v-n);}
        result.values().removeIf(n->n==0);return result;
    }
    private MiningProfitCompression(){}
}
