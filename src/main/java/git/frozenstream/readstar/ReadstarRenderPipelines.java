package git.frozenstream.readstar;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * ReadStar 自定义渲染管线注册。
 * 仿照原版 {@link RenderPipelines} 模式，将管线定义集中管理。
 */
public class ReadstarRenderPipelines {

    // ========== 自定义顶点元素 ==========

    /** 自定义顶点元素：billboard 偏移量 (vec3 float)，存储 rotation × (方向 × 星点大小) */
    public static final VertexFormatElement OFFSET_ELEMENT = VertexFormatElement.register(
            VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT, false, 3);

    // ========== 自定义顶点格式 ==========

    /** 自定义顶点格式：Position(center) + UV0 + Color + Offset */
    public static final VertexFormat POSITION_TEX_COLOR_OFFSET = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR)
            .add("Offset", OFFSET_ELEMENT)
            .build();

    // ========== 管线 ==========

    /**
     * 星点纹理管线：使用 star_fov shader，通过 FovCompensation 保持星点屏幕大小不受 FOV 影响。
     * 依赖：MATRICES_PROJECTION (ProjMat) + Sampler0 + DynamicTransforms.TextureMat[0][0]
     */
    public static final RenderPipeline STAR_TEXTURED;

    /** 彗尾管线：TRIANGLE_STRIP + POSITION_COLOR + TRANSLUCENT */
    public static final RenderPipeline COMET_TAIL;

    /** 光晕管线：POSITION_TEX_COLOR + OVERLAY，灰度纹理 × 顶点色 */
    public static final RenderPipeline HALO;

    /** 大气叠加管线：POSITION + TRANSLUCENT，全天空半透明叠加大气散射色 */
    public static final RenderPipeline ATMOSPHERE_OVERLAY;

    /** 天球图管线：POSITION_TEX，使用双半球天球图纹理替代原版纯色天空底色 */
    public static final RenderPipeline CELESTIAL_SPHERE;

    static {
        STAR_TEXTURED = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "star_textured"))
                .withVertexShader(Identifier.fromNamespaceAndPath(ReadStar.MODID, "core/star_fov"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(ReadStar.MODID, "core/star_fov"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexFormat(POSITION_TEX_COLOR_OFFSET, Mode.QUADS)
                .build();

        COMET_TAIL = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/comet_tail"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_color"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLE_STRIP)
                .build();

        HALO = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/halo"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
                .build();

        ATMOSPHERE_OVERLAY = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/atmosphere_overlay"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION, Mode.TRIANGLE_FAN)
                .build();

        CELESTIAL_SPHERE = RenderPipeline
                .builder(new RenderPipeline.Snippet[] { RenderPipelines.MATRICES_PROJECTION_SNIPPET })
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/celestial_sphere"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.TRIANGLES)
                .build();
    }

    /**
     * 将所有自定义管线注册到 NeoForge 管线注册表。
     * 由 {@code RegisterRenderPipelinesEvent} 事件处理器调用。
     */
    public static void registerAll(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(STAR_TEXTURED);
        ReadStar.LOGGER.info("Registered custom star pipeline: readstar:star_textured (fov-aware)");

        event.registerPipeline(COMET_TAIL);
        ReadStar.LOGGER.info("Registered custom comet tail pipeline: readstar:pipeline/comet_tail");

        event.registerPipeline(HALO);
        ReadStar.LOGGER.info("Registered halo pipeline: readstar:pipeline/halo");

        event.registerPipeline(ATMOSPHERE_OVERLAY);
        ReadStar.LOGGER.info("Registered atmosphere overlay pipeline: readstar:pipeline/atmosphere_overlay");

        event.registerPipeline(CELESTIAL_SPHERE);
        ReadStar.LOGGER.info("Registered celestial sphere pipeline: readstar:pipeline/celestial_sphere");
    }
}
