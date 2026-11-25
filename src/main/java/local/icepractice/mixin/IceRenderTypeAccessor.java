package local.icepractice.mixin;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(RenderType.class) public interface IceRenderTypeAccessor {
 @Invoker("create") static RenderType icepractice$create(String name,RenderSetup setup){throw new AssertionError();}
}
