package local.icepractice.mixin;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(RenderPipelines.class) public interface IceRenderPipelinesAccessor {
 @Accessor("LINES_SNIPPET") static RenderPipeline.Snippet icepractice$lines(){throw new AssertionError();}
 @Accessor("DEBUG_FILLED_SNIPPET") static RenderPipeline.Snippet icepractice$filled(){throw new AssertionError();}
 @Invoker("register") static RenderPipeline icepractice$register(RenderPipeline p){throw new AssertionError();}
}
