package git.frozenstream.readstar.skybox;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import git.frozenstream.readstar.Config;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.ReadStarClient;
import git.frozenstream.readstar.ReadstarRenderPipelines;
import git.frozenstream.readstar.elements.CelestialBody;
import git.frozenstream.readstar.elements.CelestialBodyManager;
import git.frozenstream.readstar.elements.Meteor;
import git.frozenstream.readstar.elements.MeteorCollector;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.*;

import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;


public class ReadstarSkyRenderer implements AutoCloseable {
    // ⚠️ DO NOT REMOVE: 以下注释掉的常量是原版参考值，保留以供对比和维护参考
    // private static final Identifier SUN_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/sun");
    private static final Identifier END_FLASH_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/end_flash");
    private static final Identifier END_SKY_LOCATION = Identifier.withDefaultNamespace("textures/environment/end_sky.png");
    // private static final float SKY_DISC_RADIUS = 512.0F;
    // private static final int SKY_VERTICES = 10;
    // private static final float SUN_SIZE = 30.0F;
    // private static final float SUN_HEIGHT = 100.0F;
    // private static final float MOON_SIZE = 20.0F;
    // private static final float MOON_HEIGHT = 100.0F;
    // private static final int SUNRISE_STEPS = 16;
    // private static final int END_SKY_QUAD_COUNT = 6;
    // private static final float END_FLASH_HEIGHT = 100.0F;
    // private static final float END_FLASH_SCALE = 60.0F;
    private static final Identifier CELESTIAL_SPHERE_LOCATION = Identifier.fromNamespaceAndPath(ReadStar.MODID, "textures/environment/test_dual_blurred_qtr.png");
    private final TextureAtlas celestialsAtlas;
    private final TextureAtlas starsAtlas;
    private final RenderTarget renderTarget;
    private GpuBuffer starBuffer;
    private final GpuBuffer topSkyBuffer;
    private final GpuBuffer bottomSkyBuffer;
    private final GpuBuffer endSkyBuffer;
    // private final GpuBuffer sunBuffer;
    private final Map<String, GpuBuffer> nonluminousBuffers = new HashMap<>();
    private final Map<String, GpuBuffer> luminousBuffers = new HashMap<>();
    private final GpuBuffer sunriseBuffer;
    private final GpuBuffer endFlashBuffer;
    private final GpuBuffer haloBuffer;
    private final RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
    private final AbstractTexture endSkyTexture;
    private final AbstractTexture celestialSphereTexture;
    private final GpuBuffer topCelestialSphereBuffer;
    private final GpuBuffer bottomCelestialSphereBuffer;
    private int starIndexCount;
    /** 最近一帧计算的有效星光亮度，供 renderHud 读取 */
    private float lastStarBrightness;

    /**
     * 天球星表数据记录，存储从 stars.json 解析的原始星数据，可复用。
     * @param name      恒星名称（如 "Sirius", "Canopus"）
     * @param direction 天球上的单位方向向量（归一化）
     * @param vmag      视星等（数值越小越亮）
     * @param color     颜色索引（0-6，映射到 environment/stars/color_* 纹理）
     */
    public record Star(String name, Vector3f direction, float vmag, int color) {}

    public ReadstarSkyRenderer(TextureManager textureManager, AtlasManager atlasManager, RenderTarget renderTarget) {
        this.celestialsAtlas = atlasManager.getAtlasOrThrow(ReadStarClient.CELESTIAL_ATLAS_INFO);
        this.starsAtlas = atlasManager.getAtlasOrThrow(ReadStarClient.STAR_ATLAS_INFO);
        this.renderTarget = renderTarget;
        this.endSkyBuffer = buildEndSky();
        this.endSkyTexture = this.getTexture(textureManager, END_SKY_LOCATION);
        this.endFlashBuffer = buildEndFlashQuad(this.celestialsAtlas);
        this.sunriseBuffer = this.buildSunriseFan();
        this.haloBuffer = buildHaloQuad(this.celestialsAtlas);
        this.celestialSphereTexture = this.getTexture(textureManager, CELESTIAL_SPHERE_LOCATION);

        try (ByteBufferBuilder builder = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(builder, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            this.buildSkyDisc(bufferBuilder, 16.0F);

            try (MeshData meshData = bufferBuilder.buildOrThrow()) {
                this.topSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Top sky vertex buffer", 32, meshData.vertexBuffer());
            }

            bufferBuilder = new BufferBuilder(builder, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            this.buildSkyDisc(bufferBuilder, -16.0F);

            try (MeshData meshData = bufferBuilder.buildOrThrow()) {
                this.bottomSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Bottom sky vertex buffer", 32, meshData.vertexBuffer());
            }
        }

        // 构建天球图纹理半球天穹网格（QUADS 经纬球）
        this.topCelestialSphereBuffer = buildCelestialSphereDome(true);
        this.bottomCelestialSphereBuffer = buildCelestialSphereDome(false);
    }

    /**
     * 按天体名称懒创建 non-luminous GPU 缓冲（月相天体：moon、mars 等）。
     * 精灵路径：environment/celestial/non-luminous/{bodyName}/{phaseName}
     */
    private GpuBuffer getOrCreateNonluminousBuffer(String bodyName) {
        return nonluminousBuffers.computeIfAbsent(bodyName,
                name -> buildBodyBuffer(name, "non-luminous"));
    }

    /**
     * 按天体名称懒创建 luminous GPU 缓冲（发光天体：sun 等）。
     * 精灵路径：environment/celestial/luminous/{bodyName}/{phaseName}
     */
    private GpuBuffer getOrCreateLuminousBuffer(String bodyName) {
        return luminousBuffers.computeIfAbsent(bodyName,
                this::buildLuminousBuffer);
    }

    /**
     * 构建 luminous 天体缓冲（单个精灵，无月相）。
     * 精灵路径：environment/celestial/luminous/{name}
     */
    private GpuBuffer buildLuminousBuffer(String name) {
        Identifier spriteId = Identifier.fromNamespaceAndPath(ReadStar.MODID,
                "environment/celestial/luminous/" + name);
        TextureAtlasSprite sprite = celestialsAtlas.getSprite(spriteId);
        if (sprite == celestialsAtlas.missingSprite()) {
            ReadStar.LOGGER.warn("Luminous sprite not found: {}", spriteId);
            return null;
        }
        return buildQuadBuffer(name, "luminous", List.of(sprite));
    }

    /**
     * 构建 non-luminous 天体缓冲（遍历 8 个月相）。
     * 精灵路径：environment/celestial/non-luminous/{name}/{phaseName}
     */
    private GpuBuffer buildBodyBuffer(String name, String category) {
        MoonPhase[] phases = MoonPhase.values();

        List<TextureAtlasSprite> sprites = new ArrayList<>();
        for (MoonPhase phase : phases) {
            Identifier spriteId = Identifier.fromNamespaceAndPath(ReadStar.MODID,
                    "environment/celestial/" + category + "/" + name + "/" + phase.getSerializedName());
            TextureAtlasSprite sprite = celestialsAtlas.getSprite(spriteId);
            if (sprite != celestialsAtlas.missingSprite()) {
                sprites.add(sprite);
            }
        }

        if (sprites.isEmpty()) {
            return null;
        }

        return buildQuadBuffer(name, category, sprites);
    }

    /** 用精灵列表构建 QUAD 的 GPU 缓冲 */
    private GpuBuffer buildQuadBuffer(String name, String category, List<TextureAtlasSprite> sprites) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        int totalBytes = sprites.size() * 4 * format.getVertexSize();
        try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(totalBytes)) {
            BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.QUADS, format);
            for (TextureAtlasSprite sprite : sprites) {
                buf.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV1());
                buf.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV1());
                buf.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV0());
                buf.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV0());
            }
            try (MeshData mesh = buf.buildOrThrow()) {
                ReadStar.LOGGER.info("Built {} buffer for '{}': {} sprites", category, name, sprites.size());
                return RenderSystem.getDevice().createBuffer(
                        () -> "Body/" + name + " (" + category + ") buffer", 32, mesh.vertexBuffer());
            }
        }
    }

    private AbstractTexture getTexture(TextureManager textureManager, Identifier location) {
        return textureManager.getTexture(location);
    }

    private GpuBuffer buildSunriseFan() {
        int vtxSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();

        GpuBuffer var16;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(18 * vtxSize)) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            int centerColor = ARGB.white(1.0F);
            int ringColor = ARGB.white(0.0F);
            bufferBuilder.addVertex(0.0F, 100.0F, 0.0F).setColor(centerColor);

            for (int i = 0; i <= 16; i++) {
                float angle = i * (float) (Math.PI * 2) / 16.0F;
                float sinAngle = Mth.sin(angle);
                float cosAngle = Mth.cos(angle);
                bufferBuilder.addVertex(sinAngle * 120.0F, cosAngle * 120.0F, -cosAngle * 40.0F).setColor(ringColor);
            }

            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                var16 = RenderSystem.getDevice().createBuffer(() -> "Sunrise/Sunset fan", 32, mesh.vertexBuffer());
            }
        }

        return var16;
    }

    private static GpuBuffer buildEndFlashQuad(TextureAtlas atlas) {
        return buildCelestialQuad("End flash quad", atlas.getSprite(END_FLASH_SPRITE));
    }

    /**
     * 构建光晕 quad 缓冲（POSITION_TEX_COLOR，灰度光晕纹理 × 顶点色）。
     * 初始顶点色为白色，运行时通过 DynamicTransforms 的 color 乘数着色。
     */
    private static GpuBuffer buildHaloQuad(TextureAtlas atlas) {
        Identifier haloId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/celestial/halo");
        TextureAtlasSprite sprite = atlas.getSprite(haloId);
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        int white = ARGB.color(255, 255, 255, 255);

        try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
            BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.QUADS, format);
            buf.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV0()).setColor(white);
            buf.addVertex( 1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV0()).setColor(white);
            buf.addVertex( 1.0F, 0.0F,  1.0F).setUv(sprite.getU1(), sprite.getV1()).setColor(white);
            buf.addVertex(-1.0F, 0.0F,  1.0F).setUv(sprite.getU0(), sprite.getV1()).setColor(white);
            try (MeshData mesh = buf.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "Halo quad", 32, mesh.vertexBuffer());
            }
        }
    }

    private static GpuBuffer buildCelestialQuad(String name, TextureAtlasSprite sprite) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;

        GpuBuffer var6;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV0());
            bufferBuilder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV0());
            bufferBuilder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV1());
            bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV1());

            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                var6 = RenderSystem.getDevice().createBuffer(() -> name, 32, mesh.vertexBuffer());
            }
        }

        return var6;
    }


    public void buildStarsBuffer(List<Star> stars) {
        int starCount = stars.size();
        VertexFormat format = ReadstarRenderPipelines.POSITION_TEX_COLOR_OFFSET;
        int vtxSize = format.getVertexSize();

        // 预计算元素偏移量
        // Format: Position(RGB32=12) + UV0(RG32=8) + Color(RGBA8=4) + Offset(RGB32=12) = 36 bytes
        int posOffset = 0;
        int uvOffset = 12;
        int colorOffset = 20;
        int offsetOffset = 24;

        // 预计光晕星数量（Vmag < 2.0 才有光晕）
        int glowStarCount = 0;
        for (Star star : stars) {
            if (star.vmag < 2.0f)
                glowStarCount++;
        }
        int totalQuads = starCount + glowStarCount;
        int totalVertices = totalQuads * 4; // QUADS 模式，每星 4 顶点

        // 如果没有星星数据，返回一个空的缓冲
        if (totalVertices == 0) {
            this.starIndexCount = 0;
            try (ByteBufferBuilder buf = ByteBufferBuilder.exactlySized(1)) {
                BufferBuilder builder = new BufferBuilder(buf, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                try (MeshData mesh = builder.buildOrThrow()) {
                    this.starBuffer = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer (empty)", 32, mesh.vertexBuffer());
                }
            }
        }

        var coreSize = Config.STAR_CORE_SIZE.get().floatValue();
        var glowSize = Config.STAR_GLOW_SIZE.get().floatValue();

        try (ByteBufferBuilder buf = ByteBufferBuilder.exactlySized(vtxSize * totalVertices)) {

            for (Star star : stars) {
                try {
                    float vmag = star.vmag;
                    int color = star.color;

                    // 球面位置（着色器内部计算 billboard 朝向）
                    Vector3f center = new Vector3f(star.direction).normalize(100.0F);

                    // 逐星亮度：普森(Pogson)星等-亮度公式 —— 人眼感知模型
                    // Δ5mag = 100× 亮度比 → brightness ∝ 10^(-0.4 × vmag)
                    // 以 vmag=1 为基准归一化，vmag<1 的亮星钳位不增亮
                    float alphaF = (float) Math.pow(10.0, -0.08 * Math.max(vmag - 1.0, 0.0));
                    float colorF = (float) Math.pow(10.0, -0.08 * Math.max(vmag - 1.0, 0.0));
                    int starAlpha = Math.min(255, Math.max(1, (int) (alphaF * 255.0f)));
                    int starColor = Math.min(255, Math.max(1, (int) (colorF * 255.0f)));
                    // 星点视大小衰减指数取 -0.3（比亮度平缓），保证暗星仍有最小可见尺寸
                    float sizeF = (float) Math.pow(10.0, -0.05 * Math.max(vmag - 2.0, 0.0));
                    float starSize = Math.max(sizeF, 0.3f) * coreSize;

                    // ---- 核心 quad（所有星）：4 顶点共享 center，Offset 区分角落 ----
                    Identifier coreId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/stars/color_" + color);
                    TextureAtlasSprite coreSprite = this.starsAtlas.getSprite(coreId);

                    StarBufferUtils.writeStarQuad(buf, vtxSize, posOffset, uvOffset, colorOffset, offsetOffset,
                            center,
                            coreSprite.getU0(), coreSprite.getV0(), coreSprite.getU1(), coreSprite.getV1(),
                            starColor, starAlpha,
                            starSize);

                    // ---- 光晕 quad（仅 Vmag < 2.0 的亮星） ----
                    if (vmag < 2.0f) {
                        String glowLevel;
                        if (vmag < 0.5f)
                            glowLevel = "glow_high";
                        else if (vmag < 1.5f)
                            glowLevel = "glow_med";
                        else
                            glowLevel = "glow_low";

                        Identifier glowId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/stars/" + glowLevel + "_" + color);
                        TextureAtlasSprite glowSprite = this.starsAtlas.getSprite(glowId);

                        StarBufferUtils.writeStarQuad(buf, vtxSize, posOffset, uvOffset, colorOffset, offsetOffset,
                                center,
                                glowSprite.getU0(), glowSprite.getV0(), glowSprite.getU1(), glowSprite.getV1(),
                                255, starAlpha,
                                glowSize);
                    }

                } catch (Exception e) {
                    ReadStar.LOGGER.warn("Failed to build star vertex: {}", e.getMessage());
                }
            }

            // 构建 GPU 缓冲
            ByteBufferBuilder.Result result = buf.build();
            if (result == null) {
                this.starIndexCount = 0;
                ReadStar.LOGGER.warn("Star buffer build returned null");
                try (ByteBufferBuilder emptyBuf = ByteBufferBuilder.exactlySized(1)) {
                    BufferBuilder b = new BufferBuilder(emptyBuf, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    try (MeshData mesh = b.buildOrThrow()) {
                        this.starBuffer = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer (fallback)", 32, mesh.vertexBuffer());
                    }
                }
            }

            // QUADS 模式：indexCount = vertexCount / 4 * 6
            this.starIndexCount = totalVertices / 4 * 6;
            ReadStar.LOGGER.info("Built star vertex buffer with {} indices ({} stars, {} with glow)",
                    this.starIndexCount, starCount, glowStarCount);
            this.starBuffer = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", 32, result.byteBuffer());
        }
    }

    private void buildSkyDisc(VertexConsumer builder, float yy) {
        float x = Math.signum(yy) * 512.0F;
        builder.addVertex(0.0F, yy, 0.0F);

        for (int i = -180; i <= 180; i += 45) {
            builder.addVertex(x * Mth.cos(i * (float) (Math.PI / 180.0)), yy,
                    512.0F * Mth.sin(i * (float) (Math.PI / 180.0)));
        }
    }

    /** 天穹网格细分参数 */
    private static final int DOME_STACKS = 16; // 纬度细分（天顶→地平线）
    private static final int DOME_SLICES = 32; // 经度细分（绕一圈）
    private static final float DOME_RADIUS = 100.0F; // 球半径，与星星渲染距离一致

    /**
     * 构建半球天穹网格（TRIANGLES），使用等距矩形投影（equirectangular）UV 映射。
     * 每个四边形拆为两个三角形，分别保证内侧面朝向球心（相机位置）。
     *
     * @param isTop true=上半球天穹，纹理取上半部分 v∈[0, 0.5]
     */
    private GpuBuffer buildCelestialSphereDome(boolean isTop) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        int cells = DOME_STACKS * DOME_SLICES;
        int vertexCount = cells * 6; // 每格 2 个三角形 = 6 顶点
        int white = ARGB.color(255, 255, 255, 255);

        float vScale = 0.5F;
        float vBase = isTop ? 0.0F : 1.0F; // 上半球从顶部(0)到中部(0.5)，下半球从底部(1.0)到中部(0.5)
        float vSign = isTop ? 1.0F : -1.0F; // 下半球 V 反向：天底→底部，地平线→中部

        try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(vertexCount * format.getVertexSize())) {
            BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.TRIANGLES, format);

            for (int i = 0; i < DOME_STACKS; i++) {
                float theta1 = i * (float)(Math.PI / 2) / DOME_STACKS;
                float theta2 = (i + 1) * (float)(Math.PI / 2) / DOME_STACKS;

                float y1 = DOME_RADIUS * Mth.cos(theta1);
                float y2 = DOME_RADIUS * Mth.cos(theta2);
                float r1 = DOME_RADIUS * Mth.sin(theta1);
                float r2 = DOME_RADIUS * Mth.sin(theta2);

                float v1 = vBase + vSign * theta1 / (float)(Math.PI / 2) * vScale;
                float v2 = vBase + vSign * theta2 / (float)(Math.PI / 2) * vScale;

                for (int j = 0; j < DOME_SLICES; j++) {
                    float phi1 = j * (float)(2 * Math.PI) / DOME_SLICES;
                    float phi2 = (j + 1) * (float)(2 * Math.PI) / DOME_SLICES;

                    float c1 = Mth.cos(phi1), s1 = Mth.sin(phi1);
                    float c2 = Mth.cos(phi2), s2 = Mth.sin(phi2);
                    float u1 = phi1 / (float)(2 * Math.PI);
                    float u2 = phi2 / (float)(2 * Math.PI);

                    float sy1 = isTop ? y1 : -y1;
                    float sy2 = isTop ? y2 : -y2;

                    // 4 个角点
                    float ulX = r1 * c1, ulY = sy1, ulZ = r1 * s1; // 天顶/天底侧
                    float llX = r2 * c1, llY = sy2, llZ = r2 * s1; // 地平线侧
                    float lrX = r2 * c2, lrY = sy2, lrZ = r2 * s2;
                    float urX = r1 * c2, urY = sy1, urZ = r1 * s2;

                    if (isTop) {
                        // 上半球：Y 正方向，ul/ur 在上，ll/lr 在下
                        // T1: 上左→下左→下右（逆时针）
                        buf.addVertex(ulX, ulY, ulZ).setUv(u1, v1).setColor(white);
                        buf.addVertex(llX, llY, llZ).setUv(u1, v2).setColor(white);
                        buf.addVertex(lrX, lrY, lrZ).setUv(u2, v2).setColor(white);
                        // T2: 上左→下右→上右（逆时针）
                        buf.addVertex(ulX, ulY, ulZ).setUv(u1, v1).setColor(white);
                        buf.addVertex(lrX, lrY, lrZ).setUv(u2, v2).setColor(white);
                        buf.addVertex(urX, urY, urZ).setUv(u2, v1).setColor(white);
                    } else {
                        // 下半球：Y 负方向（ul/ur 更负=屏幕更低），需反转绕组
                        // T1: 下左→上左→上右（逆时针，从球内看）
                        buf.addVertex(llX, llY, llZ).setUv(u1, v2).setColor(white);
                        buf.addVertex(ulX, ulY, ulZ).setUv(u1, v1).setColor(white);
                        buf.addVertex(urX, urY, urZ).setUv(u2, v1).setColor(white);
                        // T2: 下左→上右→下右（逆时针，从球内看）
                        buf.addVertex(llX, llY, llZ).setUv(u1, v2).setColor(white);
                        buf.addVertex(urX, urY, urZ).setUv(u2, v1).setColor(white);
                        buf.addVertex(lrX, lrY, lrZ).setUv(u2, v2).setColor(white);
                    }
                }
            }

            try (MeshData mesh = buf.buildOrThrow()) {
                String label = isTop ? "Top celestial dome" : "Bottom celestial dome";
                ReadStar.LOGGER.info("Built {} (triangles): {} cells, {} vertices", label, cells, vertexCount);
                return RenderSystem.getDevice().createBuffer(() -> label, 32, mesh.vertexBuffer());
            }
        }
    }

    private static GpuBuffer buildEndSky() {
        GpuBuffer var10;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder
                .exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);

            for (int i = 0; i < 6; i++) {
                Matrix4f pose = new Matrix4f();
                switch (i) {
                    case 1:
                        pose.rotationX((float) (Math.PI / 2));
                        break;
                    case 2:
                        pose.rotationX((float) (-Math.PI / 2));
                        break;
                    case 3:
                        pose.rotationX((float) Math.PI);
                        break;
                    case 4:
                        pose.rotationZ((float) (Math.PI / 2));
                        break;
                    case 5:
                        pose.rotationZ((float) (-Math.PI / 2));
                }

                bufferBuilder.addVertex(pose, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
                bufferBuilder.addVertex(pose, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
                bufferBuilder.addVertex(pose, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
                bufferBuilder.addVertex(pose, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
            }

            try (MeshData meshData = bufferBuilder.buildOrThrow()) {
                var10 = RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", 40,
                        meshData.vertexBuffer());
            }
        }

        return var10;
    }

    /** 银道坐标系 → 赤道坐标系 (J2000) 预旋转。
     *  天球纹理以银心为中心、银道面为赤道，需先旋转到赤道系再叠加 observer 旋转。 */
    private static final Quaternionf GALACTIC_TO_EQUATORIAL = buildGalacticToEquatorial();

    private static Quaternionf buildGalacticToEquatorial() {
        // 北银极在赤道系 (J2000): RA=192.85948°, Dec=+27.12825°
        float ngpRA = (float) Math.toRadians(192.85948);
        float ngpDec = (float) Math.toRadians(27.12825);
        Vector3f ngp = new Vector3f(
                (float)(Math.cos(ngpDec) * Math.cos(ngpRA)),
                (float) Math.sin(ngpDec),
                (float)(Math.cos(ngpDec) * Math.sin(ngpRA)));

        // 银心在赤道系 (J2000): RA=266.4051°, Dec=-28.9362°
        float gcRA = (float) Math.toRadians(266.4051);
        float gcDec = (float) Math.toRadians(-28.9362);
        Vector3f gc = new Vector3f(
                (float)(Math.cos(gcDec) * Math.cos(gcRA)),
                (float) Math.sin(gcDec),
                (float)(Math.cos(gcDec) * Math.sin(gcRA)));

        // 银道坐标系正交基在赤道系中的表示
        // 天穹本地：X(-1,0,0)=银心方向, Y(0,1,0)=北银极, Z(0,0,1)=Y×X
        // R × v_local = v_equatorial → 列向量为基向量在赤道系的坐标
        Vector3f zGal = new Vector3f(ngp).cross(gc).normalize(); // Z = NGP × GC（正交补全）

        // R = [ -gc | ngp | zGal ]  （因为 X_local=(-1,0,0) → R×(-1,0,0) = gc → col0 = -gc）
        Matrix3f rot = new Matrix3f();
        rot.setColumn(0, new Vector3f(-gc.x, -gc.y, -gc.z));
        rot.setColumn(1, new Vector3f(ngp));
        rot.setColumn(2, zGal);

        return rot.getUnnormalizedRotation(new Quaternionf());
    }

    /**
     * 渲染完整天球图，银道→赤道预旋转 + observer 天球坐标系旋转（仅旋转，不平移）。
     */
    public void renderSkyDisc(int skyColor, CelestialBody observer) {
        PoseStack poseStack = new PoseStack();
        // mulPose 是后乘：v' = M1 × M2 × v，即先 M2 后 M1
        // 1. observer 天球坐标系旋转（第二步：赤道→世界）
        if (observer != null) {
            Quaternionf frameQuat = observer.getLocalToWorldQuaternion();
            poseStack.mulPose(frameQuat);
        }
        // 2. 银道→赤道预旋转（第一步：顶点从银道→赤道）
        poseStack.mulPose(GALACTIC_TO_EQUATORIAL);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack),
                        new Vector4f(0.2F, 0.2F, 0.2F, 1.0F),
                        new Vector3f(), new Matrix4f());
        GpuTextureView colorTexture = this.renderTarget.getColorTextureView();
        GpuTextureView depthTexture = this.renderTarget.getDepthTextureView();
        int vertexCount = DOME_STACKS * DOME_SLICES * 6;

        // 上半球天穹
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky celestial top", colorTexture, Optional.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(ReadstarRenderPipelines.CELESTIAL_SPHERE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialSphereTexture.getTextureView(),
                    this.celestialSphereTexture.getSampler());
            renderPass.setVertexBuffer(0, this.topCelestialSphereBuffer.slice());
            renderPass.draw(vertexCount, 1, 0, 0);
        }

        // 下半球天穹
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky celestial bottom", colorTexture, Optional.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(ReadstarRenderPipelines.CELESTIAL_SPHERE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialSphereTexture.getTextureView(),
                    this.celestialSphereTexture.getSampler());
            renderPass.setVertexBuffer(0, this.bottomCelestialSphereBuffer.slice());
            renderPass.draw(vertexCount, 1, 0, 0);
        }

        modelViewStack.popMatrix();
    }

    public void extractRenderState(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state) {
        state.skybox = level.dimensionType().skybox();
        if (state.skybox != DimensionType.Skybox.NONE) {
            if (state.skybox == DimensionType.Skybox.END) {
                EndFlashState endFlashState = level.endFlashState();
                if (endFlashState != null) {
                    state.endFlashIntensity = endFlashState.getIntensity(partialTicks);
                    state.endFlashXAngle = endFlashState.getXAngle();
                    state.endFlashYAngle = endFlashState.getYAngle();
                }
            } else {
                EnvironmentAttributeProbe attributeProbe = camera.attributeProbe();
                state.sunAngle = attributeProbe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks)
                        * (float) (Math.PI / 180.0);
                state.moonAngle = attributeProbe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTicks)
                        * (float) (Math.PI / 180.0);
                state.starAngle = attributeProbe.getValue(EnvironmentAttributes.STAR_ANGLE, partialTicks)
                        * (float) (Math.PI / 180.0);
                state.rainBrightness = 1.0F - level.getRainLevel(partialTicks);
                state.starBrightness = attributeProbe.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partialTicks);
                state.sunriseAndSunsetColor = camera.attributeProbe()
                        .getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, partialTicks);
                state.moonPhase = attributeProbe.getValue(EnvironmentAttributes.MOON_PHASE, partialTicks);
                state.skyColor = attributeProbe.getValue(EnvironmentAttributes.SKY_COLOR, partialTicks);
                state.shouldRenderDarkDisc = this.shouldRenderDarkDisc(partialTicks, level);
            }
        }
    }


    private boolean shouldRenderDarkDisc(float deltaPartialTick, ClientLevel level) {
        return Minecraft.getInstance().player.getEyePosition(deltaPartialTick).y
                - level.getLevelData().getHorizonHeight(level) < 0.0;
    }

    public void renderDarkDisc() {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(0.0F, 12.0F, 0.0F);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack), new Vector4f(0.0F, 0.0F, 0.0F, 1.0F));
        GpuTextureView colorTexture = this.renderTarget.getColorTextureView();
        GpuTextureView depthTexture = this.renderTarget.getDepthTextureView();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky dark", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, this.bottomSkyBuffer.slice());
            renderPass.draw(10, 1, 0, 0);
        }

        modelViewStack.popMatrix();
    }

    /**
     * 全天空大气叠加层：POSITION TRIANGLE_FAN（复用 topSkyBuffer）+ TRANSLUCENT 混合。
     * 在星星和天体之后渲染，模拟大气散射在整个天空上的柔和着色。
     *
     * @param observer 观测者天体（取其 atmosphereHSV）
     */
    public void renderAtmosphereOverlay(CelestialBody observer, int skyColor) {
        if (observer == null || !observer.hasAtmosphere) return;

        int hsv = observer.atmosphereHSV;
        float v = CelestialBody.getValueFloat(hsv);
        if (v <= 0f) return;

        // 天空亮度（白天→1，夜晚→0），平滑过渡，无硬截断
        float skyBrightness = Math.max(ARGB.red(skyColor), Math.max(ARGB.green(skyColor), ARGB.blue(skyColor))) / 255f;

        float[] rgb = hsvToRgb(
                CelestialBody.getHueFloat(hsv),
                CelestialBody.getSaturationFloat(hsv),
                1.0f);
        // alpha：浓度 × 天空亮度 × 0.08，夜晚自动→0，黄昏平滑过渡
        float alpha = v * skyBrightness * 0.08f;

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(rgb[0], rgb[1], rgb[2], alpha), new Vector3f(), new Matrix4f());
        GpuTextureView colorTexture = this.renderTarget.getColorTextureView();
        GpuTextureView depthTexture = this.renderTarget.getDepthTextureView();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Atmosphere overlay", colorTexture, Optional.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(ReadstarRenderPipelines.ATMOSPHERE_OVERLAY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, this.topSkyBuffer.slice());
            renderPass.draw(10, 1, 0, 0);
        }
    }

    /**
     * HSV → RGB 转换。
     * @param hue 色相 (0.0~1.0)
     * @param saturation 饱和度 (0.0~1.0)
     * @param value 明度 (0.0~1.0)
     * @return float[3] {r, g, b}，各分量 0.0~1.0
     */
    private static float[] hsvToRgb(float hue, float saturation, float value) {
        hue = hue % 1.0f;
        if (hue < 0) hue += 1.0f;

        int h = (int) (hue * 6);
        float f = hue * 6 - h;
        float p = value * (1 - saturation);
        float q = value * (1 - f * saturation);
        float t = value * (1 - (1 - f) * saturation);

        return switch (h) {
            case 0 -> new float[] { value, t, p };
            case 1 -> new float[] { q, value, p };
            case 2 -> new float[] { p, value, t };
            case 3 -> new float[] { p, q, value };
            case 4 -> new float[] { t, p, value };
            default -> new float[] { value, p, q };
        };
    }

    public void renderCelestialAndStars(PoseStack poseStack, float rainBrightness, float starBrightness, CelestialBody observer, long gameTime) {
        CelestialBodyManager manager = CelestialBodyManager.getInstance();
        poseStack.pushPose();

        // ==== 计算有效星光亮度 ====
        // 有理函数映射 [0, ∞) → [0, 1): s·x/(s·x+1)
        float s = 20.0f;
        float effectiveBrightness = (s * starBrightness) / (s * starBrightness + 1.0f);
        float fov = Minecraft.getInstance().gameRenderer.mainCamera().getFov();
        // FOV 缩小时提升亮度（星星更大但各项发光不变 → 需要更亮）
        double brightnessFactor = 1.0 + Config.STAR_FOV_BRIGHTNESS_STRENGTH.get() * Math.max(0.0, (70.0 - fov) / 70.0);
        effectiveBrightness = effectiveBrightness * (float)brightnessFactor;
        this.lastStarBrightness = effectiveBrightness;

        // ===== 整体天球框架旋转（先确定世界坐标 → 再整体旋转） =====
        // Y = currentRotationVector (观测者天顶方向) → 标准 Y(0,1,0) 映射至此
        // Z = rotationAxis (天球极轴)              → 标准 Z(0,0,1) 映射至此
        // X = Y × Z
        boolean hasFrame = false;
        Vector3f observerPos = null;

        if (observer != null) {
            observerPos = observer.position;
            Quaternionf frameQuat = observer.getLocalToWorldQuaternion();
            poseStack.mulPose(frameQuat);
            hasFrame = true;
        }

        // ===== 在天球框架内各自指向世界坐标方向 =====
        if (hasFrame && observerPos != null) {
            // ---- ALL CELESTIAL BODIES（统一渲染 luminous + non-luminous） ----
            for (CelestialBody body : manager.getCelestialBodyTreeMap()) {
                if (body == observer) continue;
                if (body.hostStar == null) continue;

                Vector3f toWorld = new Vector3f(body.position).sub(observerPos);
                if (toWorld.lengthSquared() <= 0.0001f) continue;
                toWorld.normalize();

                ReadStar.LOGGER.debug("toWorld of {}: {}", body.name, toWorld.toString());

                GpuBuffer buffer;
                MoonPhase phase;
                if (body.luminance > 0) {
                    buffer = getOrCreateLuminousBuffer(body.name);
                    phase = MoonPhase.values()[0]; // 发光天体无月相，固定 full
                } else {
                    buffer = getOrCreateNonluminousBuffer(body.name);
                    phase = computeMoonPhase(observerPos, body);
                }

                if (buffer != null) {
                    float size = CelestialBodyManager.getApparentSize(observerPos, body);
                    poseStack.pushPose();
                    poseStack.mulPose(new Quaternionf().rotateTo(new Vector3f(0, 1, 0), toWorld));

                    // 发光体 + 观测者大气 → 渲染光晕
                    if (body.luminance > 0 && observer.hasAtmosphere && observer.atmosphereHSV != 0) {
                        int glowHSV = CelestialBody.computeGlowColor(body.starHSV, observer.atmosphereHSV);
                        renderGlow(glowHSV, size * 3f, rainBrightness, poseStack);
                    }

                    renderBody(body.name, buffer, phase, size, rainBrightness, poseStack);
                    poseStack.popPose();
                }
            }
        }

        // ---- COMETS（彗星尾部渲染） ----
        renderComets(manager, observerPos, rainBrightness, poseStack, gameTime);

        // ===== STARS (世界坐标已固定，被 frameQuat 整体旋转) =====
        if (effectiveBrightness > 0.0F) {
            this.renderStars(effectiveBrightness, poseStack);
        }

        poseStack.popPose();
    }

    /** 从天体几何计算月相，完整映射卫星绕行一周的 8 种月相 */
    private static MoonPhase computeMoonPhase(Vector3f observer, CelestialBody target) {
        if (target.hostStar == null)
            return MoonPhase.values()[0];

        // 观测者→天体 和 观测者→恒星 的方向（从地球看月亮和太阳）
        Vector3f obsToMoon = new Vector3f(target.position).sub(observer).normalize();
        Vector3f obsToSun  = new Vector3f(target.hostStar.position).sub(observer).normalize();

        // 相位角 φ = acos(dot): 0=新月(同向), π=满月(反向)
        float dot = obsToMoon.dot(obsToSun);
        double phi = Math.acos(dot);           // [0, π]
        double t   = phi / Math.PI;            // [0, 1]: 0=NEW, 1=FULL

        // 盈亏方向：obsToMoon × obsToSun 与轨道法线的点积
        // 轨道法线由轨道倾角 i 和升交点经度 Ω 决定
        double i     = target.orbit.inclination();
        double Omega = target.orbit.longitudeOfAscendingNode();
        Vector3f orbitNormal = new Vector3f(
            (float) (Math.sin(Omega) * Math.sin(i)),
            (float) (-Math.cos(Omega) * Math.sin(i)),
            (float) Math.cos(i)
        );
        Vector3f cross = new Vector3f(obsToMoon).cross(obsToSun);
        float side = cross.dot(orbitNormal);   // <0 = 盈(waxing), >0 = 亏(waning)

        int idx;
        if (side <= 0) {
            // 亏月 waning (full→new): t: 1→0
            // FULL(0) → GIBBOUS(1) → LAST_Q(2) → CRESCENT(3) → NEW(4)
            idx = (int) Math.round((1 - t) * 4);
        } else {
            // 盈月 waxing (new→full): t: 0→1
            // NEW(4) → CRESCENT(5) → FIRST_Q(6) → GIBBOUS(7) → FULL(0)
            idx = (int) Math.round(t * 4 + 4) % 8;
        }

        return MoonPhase.values()[Math.min(idx, 7)];
    }

    /**
     * 渲染所有活跃流星：头部 billboard 方块 + 尾迹矩形
     * 使用 STARS 管线绘制，不需外部贴图
     */
    public void buildAndRenderMeteors(PoseStack poseStack, float starBrightness, long gameTime) {
        var meteors = MeteorCollector.getInstance().activeMeteors;
        if (meteors.isEmpty()) return;

        // 只统计已到达起始时间的流星（未到达时跳过，避免负 elapsed 导致错误位置）
        int renderCount = 0;
        for (Meteor meteor : meteors) {
            if (gameTime >= meteor.startTick()) renderCount++;
        }
        if (renderCount == 0) return;

        VertexFormat format = DefaultVertexFormat.POSITION;
        int vtxSize = format.getVertexSize();

        // 每颗流星：头部 4 顶点 + 尾迹 4 顶点 = 8 顶点
        int totalQuads = renderCount * 2;
        int totalVertices = totalQuads * 4;
        int totalIndices = totalQuads * 6;

        try (var buf = ByteBufferBuilder.exactlySized(vtxSize * totalVertices)) {
            BufferBuilder builder = new BufferBuilder(buf, PrimitiveTopology.QUADS, format);

            for (Meteor meteor : meteors) {
                if (gameTime < meteor.startTick()) continue; // 起始时间未到，跳过
                float progress = meteor.getCurrentProgress(gameTime);
                Vector3f currentPos = new Vector3f(meteor.startPosition()).lerp(meteor.endPosition(), progress);

                float starDist = 100.0F;

                Vector3f center = new Vector3f(currentPos).normalize(starDist);
                Vector3f trailDir = new Vector3f(meteor.startPosition()).sub(meteor.endPosition()).normalize();
                Vector3f sideDir = new Vector3f(trailDir).cross(currentPos).normalize();

                float headSize = 0.1f;

                builder.addVertex(new Vector3f().add(trailDir).sub(sideDir).mul(headSize).add(center));
                builder.addVertex(new Vector3f().add(trailDir).add(sideDir).mul(headSize).add(center));
                builder.addVertex(new Vector3f().sub(trailDir).add(sideDir).mul(headSize).add(center));
                builder.addVertex(new Vector3f().sub(trailDir).sub(sideDir).mul(headSize).add(center));
                // ===== 尾迹：沿轨迹方向的矩形 =====
                Vector3f trail = new Vector3f(currentPos).lerp(meteor.startPosition(), progress*(1-progress)).normalize(starDist);
                
                float halfWid = 0.04f;
                Vector3f sOff = sideDir.mul(halfWid);
                
                builder.addVertex(new Vector3f(trail).sub(sOff));
                builder.addVertex(new Vector3f(trail).add(sOff));
                builder.addVertex(new Vector3f(center).add(sOff));
                builder.addVertex(new Vector3f(center).sub(sOff));
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                try (GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Meteors", 32, mesh.vertexBuffer())) {

                    Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
                    modelViewStack.pushMatrix();
                    modelViewStack.mul(poseStack.last().pose());
                    RenderPipeline renderPipeline = RenderPipelines.STARS;
                    GpuTextureView colorTexture = this.renderTarget.getColorTextureView();
                    GpuTextureView depthTexture = this.renderTarget.getDepthTextureView();
                    GpuBuffer indexBuffer = this.quadIndices.getBuffer(totalIndices);
                    GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                        .writeTransform(new Matrix4f(modelViewStack), new Vector4f(0.6f, 0.6f, 0.01f, starBrightness * 0.7f), new Vector3f(), new Matrix4f());

                    try (RenderPass renderPass = RenderSystem.getDevice()
                            .createCommandEncoder()
                            .createRenderPass(() -> "Meteors", colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
                        renderPass.setPipeline(renderPipeline);
                        RenderSystem.bindDefaultUniforms(renderPass);
                        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                        renderPass.setVertexBuffer(0, buffer.slice());
                        renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
                        renderPass.drawIndexed(totalIndices, 1, 0, 0, 0);
                    }

                    modelViewStack.popMatrix();
                }
            }
        }
    }

    /**
     * 通用天体渲染方法（luminous + non-luminous 共用）。
     *
     * @param name          天体的调试名称（仅用于 RenderPass 命名）
     * @param buffer        该天体的月相 GPU 缓冲（来自 getOrCreateBodyBuffer 懒创建）
     * @param phase         当前月相（0-7）
     * @param size          视大小
     * @param rainBrightness 雨天亮度衰减
     * @param poseStack     PoseStack 变换
     */
    private void renderBody(String name, GpuBuffer buffer, MoonPhase phase, float size, float rainBrightness, PoseStack poseStack) {
        int baseVertex = phase.index() * 4;
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(size, -1.0F, size);
        
        // 使用矩阵副本避免 DynamicUniforms 延迟读取时 modelViewStack 已被后续天体修改
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelViewStack), new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness));
        GpuTextureView color = this.renderTarget.getColorTextureView();
        GpuTextureView depth = this.renderTarget.getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Sky " + name, color, Optional.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, buffer.slice());
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(6, 1, 0, baseVertex, 0);
        }

        modelViewStack.popMatrix();
    }

    /**
     * 渲染发光体在大气中的光晕。
     * 使用灰度光晕纹理（halo.png，白色中心→透明边缘）+ POSITION_TEX_COLOR 管线，
     * 顶点色统一设为 glowHSV→RGB，通过纹理的 alpha 通道实现柔和衰减。
     *
     * @param glowHSV       光晕 HSV（由 computeGlowColor 计算）
     * @param size          光晕尺寸（通常为天体视大小的 3~5 倍）
     * @param rainBrightness 雨天衰减
     * @param poseStack     PoseStack 变换
     */
    private void renderGlow(int glowHSV, float size, float rainBrightness, PoseStack poseStack) {
        float h = CelestialBody.getHueFloat(glowHSV);
        float s = CelestialBody.getSaturationFloat(glowHSV);
        float v = CelestialBody.getValueFloat(glowHSV);

        // HSV → RGB，alpha 由 V × rainBrightness 控制
        float[] rgb = hsvToRgb(h, s, 1.0f);
        float alpha = v * rainBrightness * 0.2f;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(size, -1.0F, size);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack), new Vector4f(rgb[0], rgb[1], rgb[2], alpha), new Vector3f(),
                        new Matrix4f());
        GpuTextureView color = this.renderTarget.getColorTextureView();
        GpuTextureView depth = this.renderTarget.getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Glow", color, Optional.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(ReadstarRenderPipelines.HALO);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.haloBuffer.slice());
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(6, 1, 0, 0, 0);
        }

        modelViewStack.popMatrix();
    }

    private final float skyHeight = 100f;
    private final double AU = 1.496e11;
    // 尾部参数（所有彗星共享）
    private int dustSamples = 50;
    private float dustMaxDist = 0.6f * (float) AU;
    private float dustCurv = 0.35f;
    private float dustWobbleFreq = 0.06f, dustWaveFreq = 2.5f, dustWobbleAmp = 0.01f;

    private int ionSamples = 80;
    private float ionMaxDist = 1.0f * (float) AU;
    private float ionCurv = 0.03f;
    private float ionWobbleFreq = 0.14f, ionWaveFreq = 7.0f, ionWobbleAmp = 0.002f;
    /**
     * 遍历天体树，为所有 unstableDirtySnowball 彗星渲染尾部。
     * 使用世界空间 Bézier 曲线投影到天球绘制。
     */
    private void renderComets(CelestialBodyManager manager, Vector3f observerPos, float rainBrightness, PoseStack poseStack, long gameTick) {
        if (observerPos == null) return;

        // ==== 遍历所有 unstableDirtySnowball 天体 ====
        for (CelestialBody body : manager.getCelestialBodyTreeMap()) {
            if (!body.unstableDirtySnowball) continue;
            if (body.hostStar == null) continue;

            Vector3f cometPos = body.position;
            Vector3f sunPos = body.hostStar.position;
            // 轨道法线：从倾角 i 和升交点经度 Ω 计算
            double i = body.orbit.inclination();
            double omega = body.orbit.longitudeOfAscendingNode();
            Vector3f orbitNormal = new Vector3f(
                (float)(Math.sin(omega) * Math.sin(i)),
                (float)(-Math.cos(omega) * Math.sin(i)),
                (float)Math.cos(i)
            );
            renderOneComet(cometPos, sunPos, orbitNormal, observerPos, rainBrightness, poseStack, gameTick);
        }

        // ==== 测试彗星：太阳侧面近日点，确保可见 ====
        {
            Vector3f testComet = new Vector3f(0, 1.5e11f, 1.5e11f);
            Vector3f testSun = new Vector3f(0, 0, 0);
            Vector3f testOrbitNormal = new Vector3f(0, 1, 0);  // 默认轨道法线
            renderOneComet(testComet, testSun, testOrbitNormal, observerPos, rainBrightness, poseStack, gameTick);
        }
    }

    /** 为单颗彗星渲染尘埃尾 + 离子尾 + 标记 */
    private void renderOneComet(Vector3f cometPos, Vector3f sunPos, Vector3f orbitNormal, Vector3f observerPos,
            float rainBrightness, PoseStack poseStack, long tick) {

        Vector3f antiSun = new Vector3f(cometPos).sub(sunPos).normalize();
        // 尾巴弯曲方向在轨道平面内：antiSun × orbitNormal
        Vector3f rightDir = new Vector3f(antiSun).cross(orbitNormal);
        if (rightDir.lengthSquared() < 0.0001f) rightDir.set(orbitNormal).cross(antiSun).normalize();
        else rightDir.normalize();

        Vector3f wobbleDir = new Vector3f(antiSun).cross(rightDir).normalize();

        // ---- 尘埃尾主面 + 垂直鳍 ----
        buildTailRibbon("Dust tail", dustSamples, dustMaxDist, dustCurv,
                dustWobbleFreq, dustWaveFreq, dustWobbleAmp, false, 0f,
                0.25f, 5.0f, true, 10f, 200,
                0.92f, 0.85f, 0.75f, 0.25f, 0.40f, 0.80f,
                antiSun, rightDir, wobbleDir, cometPos, observerPos, tick, rainBrightness, poseStack);
        buildTailRibbon("Dust tail fin", dustSamples, dustMaxDist, dustCurv,
                dustWobbleFreq, dustWaveFreq, dustWobbleAmp, true, 0.35f,
                0.25f, 5.0f, true, 10f, 140,
                0.92f, 0.85f, 0.75f, 0.25f, 0.40f, 0.80f,
                antiSun, rightDir, wobbleDir, cometPos, observerPos, tick, rainBrightness, poseStack);

        // ---- 离子尾主面 + 垂直鳍 ----
        buildTailRibbon("Ion tail", ionSamples, ionMaxDist, ionCurv,
                ionWobbleFreq, ionWaveFreq, ionWobbleAmp, false, 0f,
                0.06f, 0.8f, false, 3f, 160,
                0.40f, 0.60f, 0.95f, 0.20f, 0.30f, 0.50f,
                antiSun, rightDir, wobbleDir, cometPos, observerPos, tick, rainBrightness, poseStack);
        buildTailRibbon("Ion tail fin", ionSamples, ionMaxDist, ionCurv,
                ionWobbleFreq, ionWaveFreq, ionWobbleAmp, true, 0.30f,
                0.06f, 0.8f, false, 3f, 100,
                0.40f, 0.60f, 0.95f, 0.20f, 0.30f, 0.50f,
                antiSun, rightDir, wobbleDir, cometPos, observerPos, tick, rainBrightness, poseStack);

        // ---- 彗星位置白色标记 ----
        Vector3f cometSkyDir = new Vector3f(cometPos).sub(observerPos).normalize().mul(skyHeight);
        Vector3f upRef = new Vector3f(0, 1, 0);
        Vector3f tangent1 = new Vector3f(cometSkyDir).cross(upRef);
        if (tangent1.lengthSquared() < 0.0001f) tangent1.set(1, 0, 0).cross(cometSkyDir);
        float tangent1Length = 0.2f;
        tangent1.normalize().mul(tangent1Length);
        Vector3f tangent2 = new Vector3f(cometSkyDir).cross(tangent1).normalize().mul(tangent1Length);

        int white = ARGB.color(255, 255, 255, 255);
        int markerVerts = 4;
        try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(
                markerVerts * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.TRIANGLE_STRIP,
                    DefaultVertexFormat.POSITION_COLOR);
            buf.addVertex(cometSkyDir.x + tangent1.x + tangent2.x,
                          cometSkyDir.y + tangent1.y + tangent2.y,
                          cometSkyDir.z + tangent1.z + tangent2.z).setColor(white);
            buf.addVertex(cometSkyDir.x + tangent1.x - tangent2.x,
                          cometSkyDir.y + tangent1.y - tangent2.y,
                          cometSkyDir.z + tangent1.z - tangent2.z).setColor(white);
            buf.addVertex(cometSkyDir.x - tangent1.x + tangent2.x,
                          cometSkyDir.y - tangent1.y + tangent2.y,
                          cometSkyDir.z - tangent1.z + tangent2.z).setColor(white);
            buf.addVertex(cometSkyDir.x - tangent1.x - tangent2.x,
                          cometSkyDir.y - tangent1.y - tangent2.y,
                          cometSkyDir.z - tangent1.z - tangent2.z).setColor(white);
            try (MeshData mesh = buf.buildOrThrow()) {
                GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Comet marker", 32, mesh.vertexBuffer());
                renderTailPass(buffer, markerVerts, "Comet marker", rainBrightness, poseStack);
                buffer.close();
            }
        }
    }

    /**
     * 构建一条彗尾 ribbon（TRIANGLE_STRIP）：Bézier → 天球投影 → 顶点缓冲 → 渲染。
     * isFin=true 时沿 dir×sideways 方向（垂直鳍），否则沿 sideways 方向（主平面）。
     * 宽度公式：hwA + (hwQuadratic ? t² : t) × hwB，鳍再乘以 finMult。
     * 颜色：fade=exp(-t²×fadeExp)，中心色→边缘色 lerp，alpha=fade×alphaMax。
     */
    private void buildTailRibbon(String label, int samples,
            float maxDist, float curv,
            float wobFreq, float wavFreq, float wobAmp,
            boolean isFin, float finMult,
            float hwA, float hwB, boolean hwQuadratic,
            float fadeExp, int alphaMax,
            float rC, float gC, float bC, float rE, float gE, float bE,
            Vector3f antiSun, Vector3f rightDir, Vector3f wobbleDir,
            Vector3f cometPos, Vector3f observerPos,
            long tick, float rainBrightness, PoseStack poseStack) {

        int verts = (samples + 1) * 2;
        try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(
                verts * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            BufferBuilder buf = new BufferBuilder(bb, PrimitiveTopology.TRIANGLE_STRIP,
                    DefaultVertexFormat.POSITION_COLOR);
            for (int k = 0; k <= samples; k++) {
                float t = (float) k / samples;
                float d = t * maxDist;
                float curveOff = curv * t * t * maxDist;
                float wobble = (float) Math.sin(tick * wobFreq + t * wavFreq * Math.PI * 2) * wobAmp * d;
                Vector3f worldPt = new Vector3f(antiSun).mul(d)
                        .add(rightDir.x * curveOff, rightDir.y * curveOff, rightDir.z * curveOff)
                        .add(wobbleDir.x * wobble, wobbleDir.y * wobble, wobbleDir.z * wobble)
                        .add(cometPos);
                Vector3f dir = new Vector3f(worldPt).sub(observerPos).normalize();

                // 横向偏移方向
                Vector3f sideways = new Vector3f(rightDir);
                float dot = sideways.dot(dir);
                sideways.sub(dir.x * dot, dir.y * dot, dir.z * dot);
                if (sideways.lengthSquared() < 0.0001f) {
                    sideways.set(dir).cross(1, 0, 0);
                    if (sideways.lengthSquared() < 0.0001f) sideways.set(0, 1, 0).cross(dir);
                }
                sideways.normalize();

                float halfWidth = hwA + (hwQuadratic ? t * t : t) * hwB;
                if (isFin) halfWidth *= finMult;

                Vector3f offsetDir;
                if (isFin) {
                    offsetDir = new Vector3f(dir).cross(sideways).normalize(); // 垂直鳍
                } else {
                    offsetDir = sideways; // 主平面
                }

                float fade = (float) Math.exp(-t * t * fadeExp);
                int alpha = (int)(fade * alphaMax);
                int r = (int)((rC * fade + rE * (1f - fade)) * 255);
                int g = (int)((gC * fade + gE * (1f - fade)) * 255);
                int b = (int)((bC * fade + bE * (1f - fade)) * 255);
                int color = ARGB.color(alpha, r, g, b);

                float cx = dir.x * skyHeight, cy = dir.y * skyHeight, cz = dir.z * skyHeight;
                float ox = offsetDir.x * halfWidth, oy = offsetDir.y * halfWidth, oz = offsetDir.z * halfWidth;
                buf.addVertex(cx + ox, cy + oy, cz + oz).setColor(color);
                buf.addVertex(cx - ox, cy - oy, cz - oz).setColor(color);
            }
            try (MeshData mesh = buf.buildOrThrow()) {
                GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label, 32, mesh.vertexBuffer());
                renderTailPass(buffer, verts, label, rainBrightness, poseStack);
                buffer.close();
            }
        }
    }

    /** 彗尾渲染 pass */
    private void renderTailPass(GpuBuffer buffer, int totalVerts, String label, float rainBrightness, PoseStack poseStack) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack),
                        new Vector4f(1, 1, 1, 1),
                        new Vector3f(), new Matrix4f());
        GpuTextureView color = this.renderTarget.getColorTextureView();
        GpuTextureView depth = this.renderTarget.getDepthTextureView();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> label, color, Optional.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(ReadstarRenderPipelines.COMET_TAIL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, buffer.slice());
            renderPass.draw(totalVerts, 1, 0, 0);
        }
        modelViewStack.popMatrix();
    }

    /**
     * 渲染星星：使用 star_fov 管线 + 星星图集纹理。
     * 通过 FovCompensation uniform 保持星点屏幕大小不受 FOV 变化影响。
     */
    private void renderStars(float starBrightness, PoseStack poseStack) {
        if (this.starIndexCount <= 0)
            return;

        // 计算 FOV 补偿系数：tan(currentFov/2) / tan(70°/2)
        // FOV 变小 → 投影放大物体 → compensation < 1 收缩 billboard 以保持屏幕大小
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        float currentFov = camera.getFov();
        float fovCompensation;
        if (currentFov > 0.1f) {
            double halfFovRad = Math.toRadians(currentFov / 2.0);
            double strength = Config.STAR_FOV_COMPENSATION_STRENGTH.get(); // 1.0 = 完全补偿
            fovCompensation = (float)(Math.tan(halfFovRad) / Math.tan(Math.toRadians(35.0)) * strength + (1.0 - strength));
        } else {
            fovCompensation = 1.0f;
        }

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        GpuTextureView colorTexture = this.renderTarget.getColorTextureView();
        GpuTextureView depthTexture = this.renderTarget.getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(this.starIndexCount);

        // 将 FovCompensation 编码到 TextureMat[0][0]（着色器中 #define FovCompensation TextureMat[0][0]）
        Matrix4f texMat = new Matrix4f();
        texMat.m00(fovCompensation);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack),
                        new Vector4f(starBrightness, starBrightness, starBrightness, starBrightness), new Vector3f(),
                        texMat);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Stars", colorTexture, Optional.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(ReadstarRenderPipelines.STAR_TEXTURED);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.starsAtlas.getTextureView(), this.starsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.starBuffer.slice());
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(this.starIndexCount, 1, 0, 0, 0);
        }

        modelViewStack.popMatrix();
    }

    public void renderSunriseAndSunset(PoseStack poseStack, float sunAngle, int sunriseAndSunsetColor) {
        float alpha = ARGB.alphaFloat(sunriseAndSunsetColor);
        if (!(alpha <= 0.001F)) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            float angle = Mth.sin(sunAngle) < 0.0F ? 180.0F : 0.0F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle + 90.0F));
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.mul(poseStack.last().pose());
            modelViewStack.scale(1.0F, 1.0F, alpha);
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(new Matrix4f(modelViewStack), ARGB.vector4fFromARGB32(sunriseAndSunsetColor), new Vector3f(),
                            new Matrix4f());
            GpuTextureView color = this.renderTarget.getColorTextureView();
            GpuTextureView depth = this.renderTarget.getDepthTextureView();

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Sunrise sunset", color, Optional.empty(), depth,
                            OptionalDouble.empty())) {
                renderPass.setPipeline(RenderPipelines.SUNRISE_SUNSET);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.setVertexBuffer(0, this.sunriseBuffer.slice());
                renderPass.draw(18, 1, 0, 0);
            }

            modelViewStack.popMatrix();
            poseStack.popPose();
        }
    }

    public void renderEndSky() {
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = autoIndices.getBuffer(36);
        GpuTextureView colorTexture = this.renderTarget.getColorTextureView();
        GpuTextureView depthTexture = this.renderTarget.getDepthTextureView();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(),
                        new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End sky", colorTexture, Optional.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.END_SKY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.endSkyTexture.getTextureView(), this.endSkyTexture.getSampler());
            renderPass.setVertexBuffer(0, this.endSkyBuffer.slice());
            renderPass.setIndexBuffer(indexBuffer, autoIndices.type());
            renderPass.drawIndexed(36, 1, 0, 0, 0);
        }
    }

    public void renderEndFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yAngle));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - xAngle));
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.scale(60.0F, 1.0F, 60.0F);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(modelViewStack), new Vector4f(intensity, intensity, intensity, intensity),
                        new Vector3f(), new Matrix4f());
        GpuTextureView color = this.renderTarget.getColorTextureView();
        GpuTextureView depth = this.renderTarget.getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End flash", color, Optional.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(),
                    this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.endFlashBuffer.slice());
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(6, 1, 0, 0, 0);
        }

        modelViewStack.popMatrix();
    }

    /**
     * 在望远镜视角下，于左上角显示高度角，并在对准的恒星位置跟随渲染 tooltip。
     * 由 ReadStarClient.onRenderGui 每帧调用。
     */
    public void renderHud(GuiGraphicsExtractor g, CelestialBody observer, List<Star> stars) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isScoping()) return; // 仅在使用望远镜时显示
        if (observer == null) return;
        if (stars == null || stars.isEmpty()) return; // 星表尚未加载

        // 获取玩家视线方向（世界坐标）→ 逆变换到天体局部坐标
        Vec3 worldLook = mc.player.getViewVector(1.0f);
        Quaternionf invQuat = new Quaternionf(observer.getLocalToWorldQuaternion()).conjugate();
        Vector3f celestialDir = invQuat.transform(
                new Vector3f((float) worldLook.x, (float) worldLook.y, (float) worldLook.z));

        // 星光不够亮 → 跳过
        if (this.lastStarBrightness < 0.15f) return;

        // 查找视线最近的恒星
        Star nearestStar = null;
        float bestDot = -2.0f;
        for (Star s : stars) {
            float dot = celestialDir.dot(s.direction);
            if (dot > bestDot) {
                bestDot = dot;
                nearestStar = s;
            }
        }

        Font font = mc.font;
        int dimColor = 0xCC888888;
        int brightColor = 0xCCFFFFFF;

        // 左上角：高度角
        float altitude = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, celestialDir.y))));
        g.text(font, String.format("Alt: %+.1f°", altitude), 10, 10, dimColor);

        // 对准某颗星时（夹角 < 2°），在星星的屏幕位置绘制跟随 tooltip
        float threshold = (float) Math.cos(Math.toRadians(1.0));
        if (nearestStar == null || bestDot <= threshold) return;

        // 星星方向变换到世界空间
        Quaternionf localToWorld = observer.getLocalToWorldQuaternion();
        Vector3f starWorldDir = new Quaternionf(localToWorld).transform(
                new Vector3f(nearestStar.direction));

        // 射线遮挡检测：玩家视线被地形/建筑挡住 → 不显示 tooltip
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 rayEnd = eyePos.add(starWorldDir.x * 256, starWorldDir.y * 256, starWorldDir.z * 256);
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                eyePos, rayEnd,
                net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player);
        if (mc.level.clip(ctx).getType() != net.minecraft.world.phys.HitResult.Type.MISS) return;

        // 摄像机基向量（世界坐标）
        Vector3f forward = new Vector3f((float) worldLook.x, (float) worldLook.y, (float) worldLook.z);
        Vec3 upVec = mc.player.getUpVector(1.0f);
        Vector3f up = new Vector3f((float) upVec.x, (float) upVec.y, (float) upVec.z);
        Vector3f right = new Vector3f(forward).cross(up).normalize();

        // 角偏移
        float dotF = starWorldDir.dot(forward);
        if (dotF <= 0) return; // 在身后
        float dotR = starWorldDir.dot(right);
        float dotU = starWorldDir.dot(up);

        // 映射到屏幕像素
        Camera camera = mc.gameRenderer.mainCamera();
        float fov = camera.getFov();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float aspectRatio = (float) screenW / screenH;
        float vFovRad = (float) Math.toRadians(fov);
        float hFovRad = 2f * (float) Math.atan(Math.tan(vFovRad / 2) * aspectRatio);

        float hAngle = (float) Math.atan2(dotR, dotF);
        float vAngle = (float) Math.atan2(dotU, dotF);

        int cx = screenW / 2;
        int cy = screenH / 2;
        int screenX = (int) (cx + hAngle / (hFovRad / 2f) * cx);
        int screenY = (int) (cy - vAngle / (vFovRad / 2f) * cy);

        // 在星星上方绘制 tooltip，避免遮挡
        Component tip = Component.translatable("hud.readstar.star_tooltip",
                nearestStar.name, nearestStar.vmag);
        int textW = font.width(tip.getString());
        int tipY = screenY - font.lineHeight - 5; // 星星上方
        g.textWithBackdrop(font, tip, screenX - textW / 2, tipY, textW, brightColor);
    }

    @Override
    public void close() {
        this.starBuffer.close();
        this.topSkyBuffer.close();
        this.bottomSkyBuffer.close();
        this.topCelestialSphereBuffer.close();
        this.bottomCelestialSphereBuffer.close();
        this.endSkyBuffer.close();
        this.sunriseBuffer.close();
        this.endFlashBuffer.close();
        this.haloBuffer.close();
        this.nonluminousBuffers.values().forEach(GpuBuffer::close);
        this.luminousBuffers.values().forEach(GpuBuffer::close);
    }
}
