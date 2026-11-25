package local.icepractice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import local.icepractice.mixin.IceRenderPipelinesAccessor;
import local.icepractice.mixin.IceRenderTypeAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Own Fabric renderer: waypoint outlines and text are visible through terrain. */
public final class IceWorldRenderer {
 private static final RenderType LINES=lines(), SURFACE=surface();
 public static void initialize(){LevelRenderEvents.COLLECT_SUBMITS.register(IceWorldRenderer::render);}
 private static RenderType lines(){RenderPipeline p=IceRenderPipelinesAccessor.icepractice$register(RenderPipeline.builder(IceRenderPipelinesAccessor.icepractice$lines()).withLocation(Identifier.fromNamespaceAndPath("icepractice","waypoint_lines")).withVertexBinding(0,DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.LINES).withDepthStencilState(Optional.empty()).build());return IceRenderTypeAccessor.icepractice$create("icepractice_waypoint_lines",RenderSetup.builder(p).setLayeringTransform(LayeringTransform.NO_LAYERING).createRenderSetup());}
 private static RenderType surface(){RenderPipeline p=IceRenderPipelinesAccessor.icepractice$register(RenderPipeline.builder(IceRenderPipelinesAccessor.icepractice$filled()).withLocation(Identifier.fromNamespaceAndPath("icepractice","aim_surface")).build());return IceRenderTypeAccessor.icepractice$create("icepractice_aim_surface",RenderSetup.builder(p).createRenderSetup());}
 private static void render(LevelRenderContext ctx){Minecraft c=Minecraft.getInstance();if(c.player==null||c.level==null)return;Vec3 camera=ctx.levelState().cameraRenderState.pos;PoseStack pose=ctx.poseStack();
  List<IceWaypoints.Point> visible=IceGameContext.showWaypoints()?IceWaypoints.config.waypoints.stream().filter(p->IceWaypoints.draw(p,c)).toList():List.of();
  if(!visible.isEmpty()){pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);ctx.submitNodeCollector().submitCustomGeometry(pose,LINES,(entry,buffer)->{for(var p:visible)box(entry,buffer,p.x,p.y,p.z,0xff000000|IceWaypoints.color(p.type));});pose.popPose();
   for(var p:visible){if(p.type==IceWaypoints.Type.CLEAR)continue;String label=IceWaypoints.label(p);double dx=p.x+.5-camera.x,dy=p.y+1.35-camera.y,dz=p.z+.5-camera.z;double d=Math.sqrt(dx*dx+dy*dy+dz*dz);float scale=IceWaypoints.labelWorldScale(d,IceWaypoints.config.labelScale);pose.pushPose();pose.translate(dx,dy,dz);pose.mulPose(ctx.levelState().cameraRenderState.orientation);pose.scale(scale,-scale,scale);var text=Component.literal(label).getVisualOrderText();ctx.submitNodeCollector().submitText(pose,-c.font.width(text)/2f,0,text,false,Font.DisplayMode.SEE_THROUGH,0xf000f0,0xffffffff,0x40000000,0);pose.popPose();}}
  List<IceAngleClient.Quad> quads=IceAngleClient.quads();if(!quads.isEmpty()){float[] rgb=IceAngleClient.color();int a=(int)(IceAngleClient.alpha()*255),color=(a<<24)|((int)(rgb[0]*255)<<16)|((int)(rgb[1]*255)<<8)|(int)(rgb[2]*255);pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);ctx.submitNodeCollector().submitCustomGeometry(pose,SURFACE,(entry,buffer)->{for(var q:quads)for(Vec3 v:q.vertices())buffer.addVertex(entry,(float)v.x,(float)v.y,(float)v.z).setColor(color);});pose.popPose();}
 }
 private static void box(PoseStack.Pose p,VertexConsumer b,int x,int y,int z,int color){float a=x-.002f,c=y-.002f,d=z-.002f,e=x+1.002f,f=y+1.002f,g=z+1.002f;line(p,b,color,a,c,d,e,c,d);line(p,b,color,e,c,d,e,c,g);line(p,b,color,e,c,g,a,c,g);line(p,b,color,a,c,g,a,c,d);line(p,b,color,a,f,d,e,f,d);line(p,b,color,e,f,d,e,f,g);line(p,b,color,e,f,g,a,f,g);line(p,b,color,a,f,g,a,f,d);line(p,b,color,a,c,d,a,f,d);line(p,b,color,e,c,d,e,f,d);line(p,b,color,e,c,g,e,f,g);line(p,b,color,a,c,g,a,f,g);}
 private static void line(PoseStack.Pose p,VertexConsumer b,int color,float ax,float ay,float az,float bx,float by,float bz){org.joml.Vector3f n=new org.joml.Vector3f(bx-ax,by-ay,bz-az).normalize();b.addVertex(p,ax,ay,az).setColor(color).setNormal(p,n.x(),n.y(),n.z()).setLineWidth(3);b.addVertex(p,bx,by,bz).setColor(color).setNormal(p,n.x(),n.y(),n.z()).setLineWidth(3);}
 private IceWorldRenderer(){}
}
