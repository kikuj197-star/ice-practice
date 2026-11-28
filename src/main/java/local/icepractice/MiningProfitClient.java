package local.icepractice;

import com.google.gson.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Mining-only estimate based on positive sack notices, never inventory totals. */
public final class MiningProfitClient {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ENTRY=Pattern.compile("([+-])([\\d,]+) (.+?) \\(([^\\n]+?)\\)");
    private static final Pattern CORPSE=Pattern.compile("(?i)\\b(?:LAPIS|UMBER|TUNGSTEN|VANGUARD) CORPSE LOOT!");
    private static final long IDLE=60_000_000_000L, DELAY=12_000_000_000L, EVIDENCE=15_000_000_000L;
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ice-profit-prices");t.setDaemon(true);return t;});
    private record Price(String id,Double npc,Double bazaar,int color){}
    private record Batch(long time,Map<String,Long> items){}
    private record HoverKey(boolean added,Map<String,Long> items){}
    private static volatile Map<String,Price> prices=Map.of();
    private static volatile String priceStatus="loading";
    private static final Map<String,Long> session=new HashMap<>(),total=new HashMap<>();
    private static final List<Batch> pending=new ArrayList<>();
    private static Path path,legacyPath;private static String owner=""; private static boolean enabled=true,totalMode=false,bazaar=true;
    private static int limit=10,background=0; private static float x=.02f,y=.12f,scale=1f;
    private static long sessionTime,totalTime,lastEvent,lastTick,lastInput,lastMining,lastCorpse,lastSave,lastFetch;
    private static long lastSackTime;private static Map<String,Long> lastSackAdded=Map.of(),lastSackRemoved=Map.of();
    private static int excluded;
    private static void msg(String s){Minecraft c=Minecraft.getInstance();if(c.player!=null)c.player.sendSystemMessage(Component.literal("[Mining Profit] "+s));}
    public static void initialize(){
        legacyPath=Minecraft.getInstance().gameDirectory.toPath().resolve("config/icepractice/mining-profit-v3.json");path=legacyPath.resolveSibling("mining-profit-v4.json");if(!Files.exists(legacyPath))legacyPath=legacyPath.resolveSibling("mining-profit-v2.json");if(!Files.exists(legacyPath))legacyPath=legacyPath.resolveSibling("mining-profit.json");load();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR,Identifier.fromNamespaceAndPath("icepractice","mining_profit"),(g,d)->render(g,false));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->{if(!overlay)message(message);return true;});
        ClientTickEvents.END_CLIENT_TICK.register(MiningProfitClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c->{flushPending(true);save();NETWORK.shutdownNow();});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{flushPending(true);lastEvent=0;lastTick=0;lastInput=0;lastMining=0;lastSackTime=0;lastSackAdded=Map.of();lastSackRemoved=Map.of();save();});
        ClientCommandRegistrationCallback.EVENT.register((d,r)->d.register(ClientCommands.literal("miningprofit")
            .executes(c->{Minecraft mc=Minecraft.getInstance();mc.execute(()->mc.setScreen(new MiningProfitScreen(mc.screen)));return 1;})
            .then(ClientCommands.literal("reset").executes(c->{resetSession();msg("Session reset.");return 1;}))
            .then(ClientCommands.literal("limit").then(ClientCommands.argument("count",IntegerArgumentType.integer(1,40)).executes(c->{limit=IntegerArgumentType.getInteger(c,"count");save();return 1;})))
            .then(ClientCommands.literal("status").executes(c->{msg("Prices: "+priceStatus+"; filtered batches: "+excluded+"; pending: "+pending.size()+". Sack messages and Hypixel Mod API are required.");return 1;}))));
        refreshPrices();
    }
    static void blockUpdate(Minecraft c,BlockPos p,BlockState old,BlockState state){
        if(!IceGameContext.verifiedHypixelSkyblock()||c.player==null||old.equals(state)||old.isAir()||!(state.isAir()||state.is(Blocks.BEDROCK))||c.player.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)>100)return;
        long now=System.nanoTime();if(lastInput!=0&&now-lastInput<3_000_000_000L)lastMining=now;
    }
    private static void message(Component c){
        if(!IceGameContext.verifiedHypixelSkyblock())return;String text=c.getString();long now=System.nanoTime();
        if(CORPSE.matcher(text).find()){lastCorpse=now;for(Iterator<Batch> it=pending.iterator();it.hasNext();)if(now-it.next().time<DELAY){it.remove();excluded++;}return;}
        if(!text.startsWith("[Sacks]"))return;
        Map<String,Long> added=new HashMap<>(),removed=new HashMap<>();collectChanges(c,added,removed,new HashSet<>());
        if(added.isEmpty())return;
        if(lastSackTime!=0&&now-lastSackTime<250_000_000L&&lastSackAdded.equals(added)&&lastSackRemoved.equals(removed))return;
        lastSackTime=now;lastSackAdded=Map.copyOf(added);lastSackRemoved=Map.copyOf(removed);
        Map<String,Long> additions=MiningProfitCompression.conversionDeltas(added,removed);
        if(additions.isEmpty())return;
        if(lastMining==0||now-lastMining>EVIDENCE||lastCorpse!=0&&now-lastCorpse<DELAY){excluded++;return;}
        pending.add(new Batch(now,additions));
    }
    static void collect(Component c,Map<String,Long> out){collectChanges(c,out,new HashMap<>(),new HashSet<>());}
    private static void collectChanges(Component c,Map<String,Long> added,Map<String,Long> removed,Set<HoverKey> seen){
        if(c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText h){String s=h.value().getString();boolean plus=s.startsWith("Added items:"),minus=s.startsWith("Removed items:");if(plus||minus){Map<String,Long> parsed=new HashMap<>();Matcher m=ENTRY.matcher(s);while(m.find()){if(plus&&!m.group(1).equals("+")||minus&&!m.group(1).equals("-"))continue;String sack=m.group(4).toLowerCase(Locale.ROOT);if(!(sack.contains("mining")||sack.contains("gemstone")))continue;try{long n=Long.parseLong(m.group(2).replace(",",""));String name=normalizeName(m.group(3));if(n>0&&!name.isEmpty())parsed.merge(name,n,Long::sum);}catch(NumberFormatException ignored){}}if(!parsed.isEmpty()&&seen.add(new HoverKey(plus,Map.copyOf(parsed))))parsed.forEach((k,v)->(plus?added:removed).merge(k,v,Long::sum));}}
        for(Component child:c.getSiblings())collectChanges(child,added,removed,seen);
    }
    private static void tick(Minecraft c){long now=System.nanoTime();
        if(c.player!=null){String uuid=c.player.getUUID().toString();if(!uuid.equals(owner)){flushPending(true);save();owner=uuid;total.clear();totalTime=0;session.clear();sessionTime=0;loadOwner();lastEvent=0;}if(c.screen==null&&(c.options.keyAttack.isDown()||c.options.keyUse.isDown()))lastInput=now;}
        if(lastTick!=0&&lastEvent!=0&&IceGameContext.verifiedHypixelSkyblock()){long delta=Math.max(0,Math.min(now,lastEvent+IDLE)-lastTick);sessionTime+=delta;totalTime+=delta;}lastTick=now;
        flushPending(false);if(now-lastSave>30_000_000_000L){save();lastSave=now;}if(now-lastFetch>300_000_000_000L)refreshPrices();
    }
    private static void flushPending(boolean force){long now=System.nanoTime();for(Iterator<Batch> it=pending.iterator();it.hasNext();){Batch b=it.next();if(!force&&now-b.time<DELAY)continue;it.remove();if(force&&now-b.time<DELAY){excluded++;continue;}if(lastEvent==0||b.time-lastEvent>IDLE){long initial=Math.min(IDLE,Math.max(0,now-b.time));sessionTime+=initial;totalTime+=initial;}b.items.forEach((k,v)->{session.merge(k,v,Long::sum);total.merge(k,v,Long::sum);});lastEvent=b.time;}}
    private static void refreshPrices(){lastFetch=System.nanoTime();NETWORK.execute(()->{try{
        HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();JsonObject items=fetch(http,"https://api.hypixel.net/v2/resources/skyblock/items"),bz=fetch(http,"https://api.hypixel.net/v2/skyblock/bazaar");
        JsonObject products=bz.getAsJsonObject("products");Map<String,Price> out=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        for(JsonElement element:items.getAsJsonArray("items")){JsonObject item=element.getAsJsonObject();if(!item.has("name")||!item.has("id"))continue;String name=normalizeName(item.get("name").getAsString()),id=item.get("id").getAsString();Double npc=item.has("npc_sell_price")?item.get("npc_sell_price").getAsDouble():null;Double sell=null;if(products.has(id)){JsonObject q=products.getAsJsonObject(id).getAsJsonObject("quick_status");if(q!=null&&q.has("buyPrice")){double v=q.get("buyPrice").getAsDouble();if(v>0)sell=v;}}String tier=item.has("tier")&&!item.get("tier").isJsonNull()?item.get("tier").getAsString():null;Price p=new Price(id,npc,sell,MiningItemRarity.color(tier));if(out.containsKey(name)){ambiguous.add(name);}else out.put(name,p);}
        ambiguous.forEach(out::remove);prices=Map.copyOf(out);priceStatus="updated";
    }catch(Exception e){priceStatus="unavailable (retrying)";System.err.println("[Mining Profit] Prices unavailable: "+e.getClass().getSimpleName());}});}
    private static JsonObject fetch(HttpClient c,String url)throws Exception{HttpRequest q=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent","IcePractice/0.8").GET().build();HttpResponse<String> r=c.send(q,HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new IOException("HTTP "+r.statusCode());JsonObject j=JsonParser.parseString(r.body()).getAsJsonObject();if(!j.has("success")||!j.get("success").getAsBoolean())throw new IOException("API unavailable");return j;}
    static void resetSession(){session.clear();sessionTime=0;pending.clear();lastEvent=0;lastTick=System.nanoTime();}
    private static JsonObject root(){try{if(Files.exists(path))return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){System.err.println("[Mining Profit] Could not read settings: "+e);}return new JsonObject();}
    private static void load(){JsonObject j=root();if(!Files.exists(path)&&legacyPath!=null&&Files.exists(legacyPath)){try{j=JsonParser.parseString(Files.readString(legacyPath,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){System.err.println("[Mining Profit] Old settings unavailable: "+e);}}try{if(j.has("enabled"))enabled=j.get("enabled").getAsBoolean();if(j.has("totalMode"))totalMode=j.get("totalMode").getAsBoolean();if(j.has("bazaar"))bazaar=j.get("bazaar").getAsBoolean();if(j.has("limit"))limit=Math.clamp(j.get("limit").getAsInt(),1,40);if(j.has("background"))background=Math.clamp(j.get("background").getAsInt(),0,100);if(j.has("x"))x=Math.clamp(j.get("x").getAsFloat(),0,1);if(j.has("y"))y=Math.clamp(j.get("y").getAsFloat(),0,1);if(j.has("scale"))scale=Math.clamp(j.get("scale").getAsFloat(),.5f,2f);}catch(Exception e){System.err.println("[Mining Profit] Invalid setting: "+e);}}
    private static void loadOwner(){JsonObject j=root();if(!j.has("accounts"))return;JsonObject accounts=j.getAsJsonObject("accounts");if(!accounts.has(owner))return;try{JsonObject a=accounts.getAsJsonObject(owner);totalTime=a.get("timeNanos").getAsLong();for(var e:a.getAsJsonObject("items").entrySet())total.merge(normalizeName(e.getKey()),e.getValue().getAsLong(),Long::sum);}catch(Exception e){System.err.println("[Mining Profit] Invalid total: "+e);}}
    static void save(){if(path==null)return;try{JsonObject j=root();j.addProperty("enabled",enabled);j.addProperty("totalMode",totalMode);j.addProperty("bazaar",bazaar);j.addProperty("limit",limit);j.addProperty("background",background);j.addProperty("x",x);j.addProperty("y",y);j.addProperty("scale",scale);JsonObject accounts=j.has("accounts")?j.getAsJsonObject("accounts"):new JsonObject();if(!owner.isEmpty()){JsonObject a=new JsonObject(),items=new JsonObject();total.forEach(items::addProperty);a.add("items",items);a.addProperty("timeNanos",totalTime);accounts.add(owner,a);}j.add("accounts",accounts);Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(tmp,GSON.toJson(j),StandardCharsets.UTF_8);try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){System.err.println("[Mining Profit] Save failed: "+e);}}
    static String normalizeName(String input){StringBuilder out=new StringBuilder();input.codePoints().filter(cp->Character.getType(cp)!=Character.PRIVATE_USE).forEach(out::appendCodePoint);return out.toString().trim().replaceAll("\\s+"," ");}
    private static String rowLabel(Map.Entry<String,Long> e){return String.format(Locale.ROOT,"%,d",e.getValue())+"x "+e.getKey();}
    private static Map<String,Long> data(){return totalMode?total:session;}
    private static List<Map.Entry<String,Long>> rows(Map<String,Price> p){List<Map.Entry<String,Long>> r=new ArrayList<>();for(var e:data().entrySet())if(e.getValue()>0)r.add(e);r.sort(Comparator.<Map.Entry<String,Long>>comparingDouble(e->{double v=value(e,p);return Double.isNaN(v)?Double.POSITIVE_INFINITY:-v;}).thenComparing(Map.Entry::getKey));return r;}
    static int hudWidth(){Minecraft c=Minecraft.getInstance();Map<String,Price> p=prices;List<Map.Entry<String,Long>> r=rows(p);int label=0,valueWidth=0;for(int i=0;i<Math.min(limit,r.size());i++){var e=r.get(i);double v=value(e,p);label=Math.max(label,c.font.width(rowLabel(e)));valueWidth=Math.max(valueWidth,c.font.width(Double.isNaN(v)?"N/A":format(v)));}return Math.max(150,label+10+valueWidth);}
    static void render(GuiGraphicsExtractor g,boolean preview){Minecraft c=Minecraft.getInstance();if(!preview&&(!enabled||c.player==null||!IceGameContext.verifiedHypixelMining()||c.screen!=null))return;Map<String,Price> p=prices;List<Map.Entry<String,Long>> rows=rows(p);double sum=0;int missing=0;boolean adjustment=false;for(var e:data().entrySet()){if(e.getValue()==0)continue;double v=value(e,p);if(Double.isNaN(v))missing++;else sum+=v;if(e.getValue()<0)adjustment=true;}long elapsed=totalMode?totalTime:sessionTime;boolean paused=lastEvent==0||System.nanoTime()-lastEvent>IDLE;int lines=Math.min(limit,rows.size()),end=hudWidth();String title="Mining Profit — "+(totalMode?"Total":"Session")+" ("+(bazaar?"BZ order":"NPC")+")",profit="Profit: "+format(sum)+(missing>0?" + ?":""),rate="Per hour: "+(elapsed>0?format(sum*3_600_000_000_000.0/elapsed):"--"),time="Time: "+formatTime(elapsed)+(paused?" (Paused)":"")+(excluded>0?"  Filtered: "+excluded:"")+(adjustment?"  Adjusted":"");g.pose().pushMatrix();g.pose().translate(x*g.guiWidth(),y*g.guiHeight());g.pose().scale(scale,scale);if(background>0){int width=Math.max(end,Math.max(Math.max(c.font.width(title),c.font.width(profit)),Math.max(c.font.width(rate),c.font.width(time))));g.fill(-4,-4,width+4,48+lines*11,((background*255/100)<<24));}g.text(c.font,title,0,0,0xffffff55,true);int line=11;for(int i=0;i<lines;i++){var e=rows.get(i);double val=value(e,p);String quantity=String.format(Locale.ROOT,"%,d",e.getValue())+"x ";g.text(c.font,quantity,0,line,0xffaaaaaa,true);Price item=p.get(e.getKey());g.text(c.font,e.getKey(),c.font.width(quantity),line,item==null?0xffffffff:item.color(),true);String s=Double.isNaN(val)?"N/A":format(val);g.text(c.font,s,end-c.font.width(s),line,0xffffaa00,true);line+=11;}g.text(c.font,profit,0,line,0xffffff55,true);line+=11;g.text(c.font,rate,0,line,0xffffff55,true);line+=11;g.text(c.font,time,0,line,0xff55ffff,true);g.pose().popMatrix();}
    private static double value(Map.Entry<String,Long> e,Map<String,Price> p){Price q=p.get(e.getKey());if(q==null)return Double.NaN;Double unit=bazaar?q.bazaar:q.npc;return unit==null?Double.NaN:unit*e.getValue();}
    private static String format(double n){if(n>=1e9)return String.format(Locale.ROOT,"%.2fB",n/1e9);if(n>=1e6)return String.format(Locale.ROOT,"%.2fM",n/1e6);if(n>=1e3)return String.format(Locale.ROOT,"%.1fK",n/1e3);return String.format(Locale.ROOT,"%.0f",n);}
    private static String formatTime(long n){long s=n/1_000_000_000L;return (s/3600)+"h "+(s/60%60)+"m "+(s%60)+"s";}
    static boolean enabled(){return enabled;}static void enabled(boolean v){enabled=v;save();}static boolean totalMode(){return totalMode;}static void totalMode(boolean v){totalMode=v;save();}static boolean bazaar(){return bazaar;}static void bazaar(boolean v){bazaar=v;save();}static int limit(){return limit;}static void limit(int v){limit=Math.clamp(v,1,40);save();}static int background(){return background;}static void background(int v){background=Math.clamp(v,0,100);save();}static float x(){return x;}static float y(){return y;}static float scale(){return scale;}static void layout(float a,float b,float s){x=Math.clamp(a,0,1);y=Math.clamp(b,0,1);scale=Math.clamp(s,.5f,2);}
    private MiningProfitClient(){}
}
