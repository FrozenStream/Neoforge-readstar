package git.frozenstream.readstar;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * ReadStar 自定义渲染管线注册。
 * 仿照原版 {@link RenderPipelines} 模式，将管线定义集中管理。
 */
public class ReadstarRenderPipelines {

    // ========== 自定义顶点格式 ==========

    /** Position(center, vec3) + UV0(vec2) + Color(RGBA8) + Offset(billboard, vec3) */
    public static final VertexFormat POSITION_TEX_COLOR_OFFSET = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("Offset", GpuFormat.RGB32_FLOAT)
            .build();

    /** Offset 属性的 VertexFormatElement，用于按元素偏移构建顶点缓冲 */
    public static final VertexFormatElement OFFSET_ELEMENT = POSITION_TEX_COLOR_OFFSET.getElements().get(3);

    // ========== 管线 ==========

    /**
     * 星点纹理管线：使用 star_fov shader，通过 FovCompensation 保持星点屏幕大小不受 FOV 影响。
     * 依赖：GLOBALS (DynamicTransforms) + MATRICES_PROJECTION (ProjMat) + Sampler0
     */
    public static final RenderPipeline STAR_TEXTURED;

    /** 彗尾管线：TRIANGLE_STRIP + POSITION_COLOR + TRANSLUCENT */
    public static final RenderPipeline COMET_TAIL;

    /** 光晕管线：POSITION_TEX_COLOR + OVERLAY，灰度纹理 × 顶点色 */
    public static final RenderPipeline HALO;

    /** 大气叠加管线：POSITION + TRANSLUCENT，全天空半透明叠加大气散射色 */
    public static final RenderPipeline ATMOSPHERE_OVERLAY;

    /** 天球图管线：POSITION_TEX_COLOR + OVERLAY，使用双半球天球图纹理叠加到原版天空底色上 */
    public static final RenderPipeline CELESTIAL_SPHERE;

    static {
        STAR_TEXTURED = RenderPipeline
                .builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "star_textured"))
                .withVertexShader(Identifier.fromNamespaceAndPath(ReadStar.MODID, "core/star_fov"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(ReadStar.MODID, "core/star_fov"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexBinding(0, POSITION_TEX_COLOR_OFFSET)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build();

        COMET_TAIL = RenderPipeline
                .builder()
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/comet_tail"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_color"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
                .build();

        HALO = RenderPipeline
                .builder()
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/halo"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build();

        ATMOSPHERE_OVERLAY = RenderPipeline
                .builder()
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/atmosphere_overlay"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
                .build();

        CELESTIAL_SPHERE = RenderPipeline
                .builder(RenderPipelines.GLOBALS_SNIPPET)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(Identifier.fromNamespaceAndPath(ReadStar.MODID, "pipeline/celestial_sphere"))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("minecraft", "core/position_tex_color"))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
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
