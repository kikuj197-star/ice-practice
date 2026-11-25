package local.icepractice;
import java.util.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Paged waypoint and colour editor, opened with /iceway. */
public final class IceWaypointScreen extends Screen {
 private final Screen parent; private int page,filter;private static final int PAGE=6;
 private IceWaypointScreen(Screen parent){super(Component.literal("炸冰坐标管理"));this.parent=parent;}
 public static void register(){ClientCommandRegistrationCallback.EVENT.register((d,r)->{d.register(ClientCommands.literal("iceway").executes(c->open()));d.register(ClientCommands.literal("iceconfig").executes(c->open()));});}
 private static int open(){Minecraft c=Minecraft.getInstance();c.execute(()->c.setScreen(new IceWaypointScreen(c.screen)));return 1;}
 private List<IceWaypoints.Point> points(){return IceWaypoints.config.waypoints.stream().filter(p->filter==0||p.type!=null&&p.type.ordinal()==filter-1).toList();}
 private static Component text(String s){return Component.literal(s);}
 private void button(String s,int x,int y,int w,Runnable f){addRenderableWidget(Button.builder(text(s),b->f.run()).bounds(x,y,w,20).build());}
 @Override protected void init(){int w=Math.min(440,width-20),x=(width-w)/2,y=18;var cfg=IceWaypoints.config;addRenderableWidget(new StringWidget(x,y,w,18,title,font));y+=24;
  button("坐标: "+(cfg.enabled?"开":"关"),x,y,100,()->{cfg.enabled=!cfg.enabled;IceWaypoints.save();rebuildWidgets();});button("标签: "+(cfg.simpleLabels?"简易":"详细"),x+102,y,115,()->{cfg.simpleLabels=!cfg.simpleLabels;IceWaypoints.save();rebuildWidgets();});button("颜色/字号",x+219,y,100,()->minecraft.setScreen(new Colors(this)));button("IceAngle 设置",x+321,y,w-321,()->minecraft.setScreen(new IceAngleConfigScreen(this)));y+=24;
  String[] filters={"全部","站点","瞄点","清除"};button("筛选: "+filters[filter],x,y,100,()->{filter=(filter+1)%4;page=0;rebuildWidgets();});button("新增坐标",x+102,y,100,()->minecraft.setScreen(new Editor(this,null)));button("上一页",x+204,y,75,()->{page=Math.max(0,page-1);rebuildWidgets();});button("下一页",x+281,y,75,()->{if((page+1)*PAGE<points().size())page++;rebuildWidgets();});addRenderableWidget(new StringWidget(x+358,y,w-358,20,text((page+1)+"/"+Math.max(1,(points().size()+PAGE-1)/PAGE)),font));y+=24;
  List<IceWaypoints.Point> data=points();for(int i=0;i<PAGE;i++){int index=page*PAGE+i;if(index>=data.size())break;var p=data.get(index);String row=(p.enabled?"● ":"○ ")+p.type+" "+p.name+"  "+p.x+","+p.y+","+p.z;button(row,x,y+i*23,w,()->minecraft.setScreen(new Editor(this,p)));}
  button("完成",x,Math.min(height-26,y+PAGE*23+3),w, this::onClose);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 private static final class Editor extends Screen {
  final Screen parent; final IceWaypoints.Point original; IceWaypoints.Type type;boolean enabled;EditBox name,x,y,z,group;String error="";
  Editor(Screen parent,IceWaypoints.Point p){super(text(p==null?"新增坐标":"修改坐标"));this.parent=parent;original=p;type=p==null?IceWaypoints.Type.STAND:p.type;enabled=p==null||p.enabled;}
  EditBox field(int x,int y,int w,String hint,String value){EditBox e=new EditBox(font,x,y,w,20,text(hint));e.setMaxLength(120);e.setHint(text(hint));e.setValue(value);return addRenderableWidget(e);}
  void btn(String s,int x,int y,int w,Runnable f){addRenderableWidget(Button.builder(text(s),b->f.run()).bounds(x,y,w,20).build());}
  @Override protected void init(){int w=Math.min(360,width-20),left=(width-w)/2,top=30;var p=original;addRenderableWidget(new StringWidget(left,top,w,18,title,font));top+=25;name=field(left,top,w,"名称",p==null?"":p.name);top+=25;group=field(left,top,w,"分组",p==null?"Custom":p.group);top+=25;int col=(w-8)/3;x=field(left,top,col,"X",p==null?"":String.valueOf(p.x));y=field(left+col+4,top,col,"Y",p==null?"":String.valueOf(p.y));z=field(left+2*(col+4),top,col,"Z",p==null?"":String.valueOf(p.z));top+=25;Button t=addRenderableWidget(Button.builder(text("类型: "+type),b->{type=IceWaypoints.Type.values()[(type.ordinal()+1)%3];b.setMessage(text("类型: "+type));}).bounds(left,top,w/2-2,20).build());addRenderableWidget(Button.builder(text("显示: "+(enabled?"开":"关")),b->{enabled=!enabled;b.setMessage(text("显示: "+(enabled?"开":"关")));}).bounds(left+w/2+2,top,w/2-2,20).build());top+=25;btn("保存",left,top,w/3-2,()->save());btn("删除",left+w/3,top,w/3-2,()->{if(original!=null){IceWaypoints.config.waypoints.remove(original);IceWaypoints.save();}onClose();});btn("取消",left+2*w/3,top,w/3, this::onClose);top+=24;addRenderableWidget(new StringWidget(left,top,w,20,text(error),font));}
  void save(){try{int px=Integer.parseInt(x.getValue().trim()),py=Integer.parseInt(y.getValue().trim()),pz=Integer.parseInt(z.getValue().trim());String n=name.getValue().trim();if(n.isEmpty())throw new IllegalArgumentException();IceWaypoints.Point p=original==null?new IceWaypoints.Point():original;p.name=n;p.group=group.getValue().trim();p.x=px;p.y=py;p.z=pz;p.type=type;p.enabled=enabled;if(original==null)IceWaypoints.config.waypoints.add(p);IceWaypoints.save();onClose();}catch(IllegalArgumentException e){error="请输入名称和有效的整数坐标";var c=minecraft;if(c.player!=null)c.player.sendSystemMessage(text("[IceWaypoints] "+error));}}
  @Override public void onClose(){minecraft.setScreen(parent);}
 }
 private static final class Colors extends Screen {
  final Screen parent;EditBox stand,aim,clear;float chosenScale;Colors(Screen parent){super(text("描边颜色与标签大小"));this.parent=parent;chosenScale=Math.clamp(IceWaypoints.config.labelScale,.5f,3f);}
  EditBox field(int x,int y,String value){EditBox e=new EditBox(font,x,y,100,20,text("RRGGBB"));e.setMaxLength(6);e.setValue(value);return addRenderableWidget(e);}
  @Override protected void init(){int x=(width-320)/2,y=35;addRenderableWidget(new StringWidget(x,y,320,20,title,font));y+=30;addRenderableWidget(new StringWidget(x,y,70,20,text("站点"),font));stand=field(x+90,y,IceWaypoints.config.standColor);y+=26;addRenderableWidget(new StringWidget(x,y,70,20,text("瞄点"),font));aim=field(x+90,y,IceWaypoints.config.aimColor);y+=26;addRenderableWidget(new StringWidget(x,y,70,20,text("清除"),font));clear=field(x+90,y,IceWaypoints.config.clearColor);y+=27;addRenderableWidget(new AbstractSliderButton(x,y,320,20,text("标签大小: "+Math.round(chosenScale*100)+"%"),(chosenScale-.5)/2.5){protected void updateMessage(){setMessage(text("标签大小: "+Math.round((.5+value*2.5)*100)+"%"));}protected void applyValue(){chosenScale=(float)(.5+value*2.5);}});y+=27;addRenderableWidget(Button.builder(text("保存"),b->save()).bounds(x,y,155,20).build());addRenderableWidget(Button.builder(text("取消"),b->onClose()).bounds(x+165,y,155,20).build());}
  void save(){String a=stand.getValue(),b=aim.getValue(),c=clear.getValue();if(!a.matches("(?i)[0-9a-f]{6}")||!b.matches("(?i)[0-9a-f]{6}")||!c.matches("(?i)[0-9a-f]{6}")){if(minecraft.player!=null)minecraft.player.sendSystemMessage(text("[IceWaypoints] 请输入六位十六进制颜色"));return;}IceWaypoints.config.standColor=a.toUpperCase(Locale.ROOT);IceWaypoints.config.aimColor=b.toUpperCase(Locale.ROOT);IceWaypoints.config.clearColor=c.toUpperCase(Locale.ROOT);IceWaypoints.config.labelScale=chosenScale;IceWaypoints.save();onClose();}
  @Override public void onClose(){minecraft.setScreen(parent);}
 }
}
