package local.icepractice;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class IceAngleConfigScreen extends Screen {
 private final Screen parent;private EditBox color,opacity;
 public IceAngleConfigScreen(Screen parent){super(Component.literal("IceAngle 设置"));this.parent=parent;}
 private static Component text(String s){return Component.literal(s);}
 @Override protected void init(){int x=(width-300)/2,y=32;addRenderableWidget(new StringWidget(x,y,300,20,title,font));y+=30;addRenderableWidget(new StringWidget(x,y,100,20,text("高亮颜色"),font));color=new EditBox(font,x+105,y,100,20,text("RRGGBB"));color.setMaxLength(6);color.setValue(IceAngleClient.colorHex());addRenderableWidget(color);y+=27;addRenderableWidget(new StringWidget(x,y,100,20,text("透明度 5–100"),font));opacity=new EditBox(font,x+105,y,100,20,text("50"));opacity.setValue(String.valueOf(IceAngleClient.opacity()));addRenderableWidget(opacity);y+=29;addRenderableWidget(Button.builder(text("保存颜色与透明度"),b->save()).bounds(x,y,300,20).build());y+=25;addRenderableWidget(Button.builder(text("拖动 HUD / 调整大小"),b->minecraft.setScreen(new HudEditor(this))).bounds(x,y,300,20).build());y+=25;addRenderableWidget(Button.builder(text("完成"),b->onClose()).bounds(x,y,300,20).build());}
 private void save(){try{int alpha=Integer.parseInt(opacity.getValue().trim());if(!IceAngleClient.configure(color.getValue().trim(),alpha))throw new IllegalArgumentException();if(minecraft.player!=null)minecraft.player.sendSystemMessage(text("[IceAngle] 设置已保存"));}catch(IllegalArgumentException ex){if(minecraft.player!=null)minecraft.player.sendSystemMessage(text("[IceAngle] 请输入六位颜色和 5–100 的透明度"));}}
 @Override public void onClose(){minecraft.setScreen(parent);}
 private static final class HudEditor extends Screen {
  final Screen parent;boolean dragging;double dx,dy;
  HudEditor(Screen parent){super(text("拖动 HUD 调整位置"));this.parent=parent;}
  @Override protected void init(){int x=(width-320)/2;addRenderableWidget(new StringWidget(x,10,320,20,title,font));addRenderableWidget(new AbstractSliderButton(x,35,220,20,text(""),(IceAngleClient.hudScale()-.5)/1.5){protected void updateMessage(){setMessage(text("大小: "+Math.round((.5+value*1.5)*100)+"%"));}protected void applyValue(){IceAngleClient.setHud(IceAngleClient.hudX(),IceAngleClient.hudY(),(float)(.5+value*1.5));} });addRenderableWidget(Button.builder(text("完成"),b->onClose()).bounds(x+225,35,95,20).build());}
  @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){super.extractRenderState(g,mouseX,mouseY,delta);IceAngleClient.drawHud(g,"IceAngle 预览 — 拖动这里","Current forecast: 189 | /iceangle status",true);}
  @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){if(super.mouseClicked(e,doubleClick))return true;float px=IceAngleClient.hudX()*width,py=IceAngleClient.hudY()*height,s=IceAngleClient.hudScale();if(e.button()==0&&e.x()>=px-3*s&&e.x()<=px+295*s&&e.y()>=py-3*s&&e.y()<=py+30*s){dragging=true;dx=e.x()-px;dy=e.y()-py;return true;}return false;}
  @Override public boolean mouseDragged(MouseButtonEvent e,double x,double y){if(dragging){float s=IceAngleClient.hudScale();double px=Math.max(0,Math.min(Math.max(0,width-295*s),e.x()-dx)),py=Math.max(0,Math.min(Math.max(0,height-30*s),e.y()-dy));IceAngleClient.setHud((float)(px/width),(float)(py/height),s);return true;}return super.mouseDragged(e,x,y);}
  @Override public boolean mouseReleased(MouseButtonEvent e){if(dragging){dragging=false;return true;}return super.mouseReleased(e);}
  @Override public void onClose(){IceAngleClient.saveHud();minecraft.setScreen(parent);}
 }
}
