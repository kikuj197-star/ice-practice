package local.icepractice;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MiningProfitScreen extends Screen {
    private final Screen parent;
    MiningProfitScreen(Screen parent){super(Component.literal("挖矿收益统计"));this.parent=parent;}
    private static Component text(String s){return Component.literal(s);}
    private void button(String s,int x,int y,int w,Runnable f){addRenderableWidget(Button.builder(text(s),b->f.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){int x=(width-300)/2,y=32;addRenderableWidget(new StringWidget(x,y,300,20,title,font));y+=27;
        button("HUD："+(MiningProfitClient.enabled()?"开":"关"),x,y,145,()->{MiningProfitClient.enabled(!MiningProfitClient.enabled());rebuildWidgets();});
        button("统计："+(MiningProfitClient.totalMode()?"累计":"本次"),x+155,y,145,()->{MiningProfitClient.totalMode(!MiningProfitClient.totalMode());rebuildWidgets();});y+=24;
        button("价格："+(MiningProfitClient.bazaar()?"BZ 挂单":"NPC"),x,y,145,()->{MiningProfitClient.bazaar(!MiningProfitClient.bazaar());rebuildWidgets();});
        button("重置本次",x+155,y,145,()->{MiningProfitClient.resetSession();});y+=24;
        final int row=y;addRenderableWidget(new AbstractSliderButton(x,row,300,20,text(""),(MiningProfitClient.limit()-1)/39.0){protected void updateMessage(){setMessage(text("显示种类："+(1+Math.round(value*39))));}protected void applyValue(){MiningProfitClient.limit(1+(int)Math.round(value*39));}});y+=24;
        final int opacityRow=y;addRenderableWidget(new AbstractSliderButton(x,opacityRow,300,20,text(""),MiningProfitClient.background()/100.0){protected void updateMessage(){setMessage(text("背景不透明度："+Math.round(value*100)+"%"));}protected void applyValue(){MiningProfitClient.background((int)Math.round(value*100));}});y+=24;
        button("拖动 HUD / 调整大小",x,y,300,()->minecraft.setScreen(new Editor(this)));y+=24;button("完成",x,y,300,this::onClose);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    private static final class Editor extends Screen{
        private final Screen parent;private boolean dragging;private double dx,dy;
        Editor(Screen p){super(text("拖动收益 HUD"));parent=p;}
        @Override protected void init(){int x=(width-320)/2;addRenderableWidget(new StringWidget(x,10,320,20,title,font));addRenderableWidget(new AbstractSliderButton(x,35,220,20,text(""),(MiningProfitClient.scale()-.5)/1.5){protected void updateMessage(){setMessage(text("大小："+Math.round((.5+value*1.5)*100)+"%"));}protected void applyValue(){MiningProfitClient.layout(MiningProfitClient.x(),MiningProfitClient.y(),(float)(.5+value*1.5));}});addRenderableWidget(Button.builder(text("完成"),b->onClose()).bounds(x+225,35,95,20).build());}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){super.extractRenderState(g,mouseX,mouseY,delta);MiningProfitClient.render(g,true);}
        @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){float s=MiningProfitClient.scale();double px=MiningProfitClient.x()*width,py=MiningProfitClient.y()*height;if(e.x()>=px-4*s&&e.x()<=px+Math.max(200,MiningProfitClient.hudWidth())*s&&e.y()>=py-4*s&&e.y()<=py+(48+MiningProfitClient.limit()*11)*s){dragging=true;dx=e.x()-px;dy=e.y()-py;return true;}return super.mouseClicked(e,doubleClick);}
        @Override public boolean mouseDragged(MouseButtonEvent e,double x,double y){if(dragging){MiningProfitClient.layout((float)((e.x()-dx)/width),(float)((e.y()-dy)/height),MiningProfitClient.scale());return true;}return super.mouseDragged(e,x,y);}
        @Override public boolean mouseReleased(MouseButtonEvent e){dragging=false;return super.mouseReleased(e);}
        @Override public void onClose(){MiningProfitClient.save();minecraft.setScreen(parent);}
    }
}
