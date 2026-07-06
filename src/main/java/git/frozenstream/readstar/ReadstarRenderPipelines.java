package git.frozenstream.readstar;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * ReadStar 自定义渲染管线注册（1.21.1 兼容版本）。
 */
public class ReadstarRenderPipelines {

    /** 自定义顶点元素：billboard 偏移量 (vec3 float)，存储 rotation × (方向 × 星点大小) */
    public static final VertexFormatElement OFFSET_ELEMENT = VertexFormatElement.register(
            VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);

    // ========== 自定义顶点格式 ==========

    /** 自定义顶点格式：Position(center) + UV0 + Color + Offset（billboard 偏移量） */
    public static final VertexFormat POSITION_TEX_COLOR_OFFSET = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("Offset", OFFSET_ELEMENT)
            .build();

    // ========== Shader 实例（由 RegisterShadersEvent 填充） ==========

    /**
     * 自定义 shader 的 ResourceLocation，指向
     * {@code assets/readstar/shaders/core/star_fov.json}
     */
    public static final ResourceLocation STAR_FOV_SHADER = ResourceLocation.fromNamespaceAndPath(ReadStar.MODID, "star_fov");

    /** 星点纹理 shader 实例，在 {@link #registerAll(RegisterShadersEvent)} 中创建并赋值。 */
    public static ShaderInstance starTexturedShader;

    /**
     * 将所有自定义 shader 注册到 NeoForge。
     * 由 {@code RegisterShadersEvent} 事件处理器调用。
     */
    public static void registerAll(RegisterShadersEvent event) {
        try {
            starTexturedShader = new ShaderInstance(
                    event.getResourceProvider(),
                    STAR_FOV_SHADER,
                    POSITION_TEX_COLOR_OFFSET);
            event.registerShader(starTexturedShader, shader -> {
                starTexturedShader = shader;
            });
            ReadStar.LOGGER.info("Registered custom star shader: readstar:star_fov (fov-aware)");
        } catch (Exception e) {
            ReadStar.LOGGER.error("Failed to register star_fov shader", e);
            throw new RuntimeException("Failed to register star_fov shader", e);
        }
    }
}
