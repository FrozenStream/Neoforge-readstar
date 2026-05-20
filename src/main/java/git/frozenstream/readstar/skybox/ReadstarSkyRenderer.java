package git.frozenstream.readstar.skybox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import git.frozenstream.readstar.Config;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.ReadStarClient;
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
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.*;

import java.io.InputStreamReader;
import java.lang.Math;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class ReadstarSkyRenderer implements AutoCloseable {
    public static CelestialBody Observer;

    private static final Identifier SUN_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/sun");
    private static final Identifier END_FLASH_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/end_flash");
    private static final Identifier END_SKY_LOCATION = Identifier.withDefaultNamespace("textures/environment/end_sky.png");
    private static final float SKY_DISC_RADIUS = 512.0F;
    private static final int SKY_VERTICES = 10;
    private static final float SUN_SIZE = 30.0F;
    private static final float SUN_HEIGHT = 100.0F;
    private static final float MOON_SIZE = 20.0F;
    private static final float MOON_HEIGHT = 100.0F;
    private static final int SUNRISE_STEPS = 16;
    private static final int END_SKY_QUAD_COUNT = 6;
    private static final float END_FLASH_HEIGHT = 100.0F;
    private static final float END_FLASH_SCALE = 60.0F;
    private final TextureAtlas celestialsAtlas;
    private final TextureAtlas starsAtlas;
    private final GpuBuffer starBuffer;
    private final GpuBuffer topSkyBuffer;
    private final GpuBuffer bottomSkyBuffer;
    private final GpuBuffer endSkyBuffer;
    private final GpuBuffer sunBuffer;
    private final GpuBuffer moonBuffer;
    private final Map<String, GpuBuffer> otherMoonsBuffers;
    private final GpuBuffer sunriseBuffer;
    private final GpuBuffer endFlashBuffer;
    private final RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem
            .getSequentialBuffer(VertexFormat.Mode.QUADS);
    private final AbstractTexture endSkyTexture;
    private int starIndexCount;

    public ReadstarSkyRenderer(TextureManager textureManager, AtlasManager atlasManager, ResourceManager resourceManager) {
        this.celestialsAtlas = atlasManager.getAtlasOrThrow(ReadStarClient.CELESTIAL_ATLAS_INFO);
        this.starsAtlas = atlasManager.getAtlasOrThrow(ReadStarClient.STAR_ATLAS_INFO);
        this.starBuffer = this.buildStars(resourceManager);
        this.endSkyBuffer = buildEndSky();
        this.endSkyTexture = this.getTexture(textureManager, END_SKY_LOCATION);
        this.endFlashBuffer = buildEndFlashQuad(this.celestialsAtlas);
        this.sunBuffer = buildSunQuad(this.celestialsAtlas);
        this.moonBuffer = buildMoonPhases(this.celestialsAtlas);
        this.sunriseBuffer = this.buildSunriseFan();

        this.otherMoonsBuffers = buildOtherMoonsBuffers(resourceManager, celestialsAtlas);

        try (ByteBufferBuilder builder = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(builder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            this.buildSkyDisc(bufferBuilder, 16.0F);

            try (MeshData meshData = bufferBuilder.buildOrThrow()) {
                this.topSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Top sky vertex buffer", 32, meshData.vertexBuffer());
            }

            bufferBuilder = new BufferBuilder(builder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            this.buildSkyDisc(bufferBuilder, -16.0F);

            try (MeshData meshData = bufferBuilder.buildOrThrow()) {
                this.bottomSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Bottom sky vertex buffer", 32, meshData.vertexBuffer());
            }
        }
    }

    /**
     * 扫描 textures/environment/celestial/moons/ 下所有子目录（如 mars/、jupiter/），
     * 将每个子目录内的所有月相纹理合并为一个 GPU 缓冲，key 为子目录名。
     * 每个缓冲内的 quad 排列顺序与 MoonPhase 枚举的 index 对应（0–7）。
     * 用于 renderBody() 渲染除原版月亮外的其他天体的月相效果。
     */
    private Map<String, GpuBuffer> buildOtherMoonsBuffers(ResourceManager resourceManager, TextureAtlas atlas) {
        // 1. 只扫描 moons/ 子目录下的所有 .png 文件
        Map<Identifier, Resource> discovered = resourceManager.listResources(
                "textures/environment/celestial/moons",
                identifier -> identifier.getPath().endsWith(".png"));

        // 2. 按子目录名分组，例如 moons/mars/ 下的 8 个月相归为 "mars" 组
        Map<String, List<Identifier>> grouped = new HashMap<>();
        String moonsPrefix = "textures/environment/celestial/moons/";
        for (Identifier fullId : discovered.keySet()) {
            String path = fullId.getPath();
            String relative = path.substring(moonsPrefix.length());
            String subDir = relative.substring(0, relative.indexOf('/'));
            grouped.computeIfAbsent(subDir, k -> new ArrayList<>()).add(fullId);
        }

        // 3. 每个子目录内的所有精灵共享一个 GPU 缓冲
        Map<String, GpuBuffer> result = new HashMap<>();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;

        for (Map.Entry<String, List<Identifier>> entry : grouped.entrySet()) {
            String subDir = entry.getKey();
            List<Identifier> ids = entry.getValue();
            int totalVertices = ids.size() * 4 * format.getVertexSize();

            try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(totalVertices)) {
                BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, format);

                for (Identifier fullId : ids) {
                    String spritePath = fullId.getPath()
                            .replace("textures/", "")
                            .replace(".png", "");
                    Identifier spriteId = Identifier.fromNamespaceAndPath(fullId.getNamespace(), spritePath);
                    TextureAtlasSprite sprite = atlas.getSprite(spriteId);
                    if (sprite == atlas.missingSprite()) {
                        ReadStar.LOGGER.warn("Sprite not found in atlas: {}", spriteId);
                        continue;
                    }
                    // 与 buildMoonPhases 一致的 UV 顺序
                    bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV1());
                    bufferBuilder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV1());
                    bufferBuilder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV0());
                    bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV0());
                }

                try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                    GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                            () -> "Moons/" + subDir + " buffer", 32, mesh.vertexBuffer());
                    result.put(subDir, buffer);
                }
            }

            ReadStar.LOGGER.info("Loaded celestial body group: {} ({} sprites)", subDir, ids.size());
        }

        return result;
    }

    private AbstractTexture getTexture(TextureManager textureManager, Identifier location) {
        return textureManager.getTexture(location);
    }

    private GpuBuffer buildSunriseFan() {
        int vertices = 18;
        int vtxSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();

        GpuBuffer var16;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(18 * vtxSize)) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
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

    private static GpuBuffer buildSunQuad(TextureAtlas atlas) {
        return buildCelestialQuad("Sun quad", atlas.getSprite(SUN_SPRITE));
    }

    private static GpuBuffer buildEndFlashQuad(TextureAtlas atlas) {
        return buildCelestialQuad("End flash quad", atlas.getSprite(END_FLASH_SPRITE));
    }

    private static GpuBuffer buildCelestialQuad(String name, TextureAtlasSprite sprite) {
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;

        GpuBuffer var6;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(4 * format.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, format);
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

    private static GpuBuffer buildMoonPhases(TextureAtlas atlas) {
        MoonPhase[] phases = MoonPhase.values();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;

        GpuBuffer var15;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder
                .exactlySized(phases.length * 4 * format.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, format);

            for (MoonPhase phase : phases) {
                TextureAtlasSprite sprite = atlas.getSprite(Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/moon/" + phase.getSerializedName()));
                bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV1());
                bufferBuilder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV1());
                bufferBuilder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV0());
                bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV0());
            }

            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                var15 = RenderSystem.getDevice().createBuffer(() -> "Moon phases", 32, mesh.vertexBuffer());
            }
        }

        return var15;
    }

    /**
     * 从 stars.json 加载真实星数据，构建带纹理坐标的顶点缓冲。
     * 每颗星是一个面向观察者的 billboard quad，使用图集中对应的颜色精灵。
     */
    private GpuBuffer buildStars(ResourceManager resourceManager) {
        Identifier starsDataPath = Identifier.fromNamespaceAndPath(ReadStar.MODID, "custom/stars/stars.json");

        JsonArray starsArray = new JsonArray();
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resourceOpt = resourceManager
                    .getResource(starsDataPath);
            if (resourceOpt.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(resourceOpt.get().open(),
                        StandardCharsets.UTF_8)) {
                    starsArray = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("Stars");
                }
                ReadStar.LOGGER.info("Loaded {} stars from {}", starsArray.size(), starsDataPath);
            } else {
                ReadStar.LOGGER.warn("Star data file not found: {}", starsDataPath);
            }
        } catch (Exception e) {
            ReadStar.LOGGER.error("Failed to load star data from {}: {}", starsDataPath, e.getMessage());
        }

        int starCount = starsArray.size();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        int vtxSize = format.getVertexSize();
        // 预计光晕星数量（Vmag < 2.0 才有光晕）
        int glowStarCount = 0;
        for (int i = 0; i < starCount; i++) {
            if (starsArray.get(i).getAsJsonObject().get("Vmag").getAsFloat() < 2.0f)
                glowStarCount++;
        }
        int totalQuads = starCount + glowStarCount; // 每星 1 核心 + 某些星额外 1 光晕
        int totalVertices = totalQuads * 4;

        // 如果没有星星数据，返回一个空的缓冲
        if (totalVertices == 0) {
            this.starIndexCount = 0;
            try (ByteBufferBuilder buf = ByteBufferBuilder.exactlySized(1)) {
                BufferBuilder builder = new BufferBuilder(buf, VertexFormat.Mode.QUADS, format);
                try (MeshData mesh = builder.buildOrThrow()) {
                    return RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer (empty)", 32,
                            mesh.vertexBuffer());
                }
            }
        }

        var coreSize = Config.STAR_CORE_SIZE.get().floatValue();
        var glowSIze = Config.STAR_GLOW_SIZE.get().floatValue();

        try (ByteBufferBuilder buf = ByteBufferBuilder.exactlySized(vtxSize * totalVertices)) {
            BufferBuilder builder = new BufferBuilder(buf, VertexFormat.Mode.QUADS, format);
            for (int i = 0; i < starCount; i++) {
                try {
                    JsonObject star = starsArray.get(i).getAsJsonObject();
                    JsonArray pos = star.getAsJsonArray("position");
                    float px = pos.get(0).getAsFloat();
                    float py = pos.get(1).getAsFloat();
                    float pz = pos.get(2).getAsFloat();
                    float vmag = star.get("Vmag").getAsFloat();
                    int color = star.get("color").getAsInt();

                    // Billboard 变换
                    float starDist = 100.0F;
                    Vector3f center = new Vector3f(px, py, pz).normalize(starDist);
                    Vector3f dirToCenter = new Vector3f(center).negate();
                    Matrix3f rotation = new Matrix3f().rotateTowards(dirToCenter, new Vector3f(0.0F, 1.0F, 0.0F));

                    // 逐星亮度
                    int starAlpha = (int) (Mth.clamp(1.0f - vmag / 15.0f, 0.4f, 1.0f) * 255.0f);
                    int starColor = (int) (Mth.clamp(1.0f - vmag / 20.0f, 0.7f, 1.0f) * 255.0f);
                    float starSize = Mth.clamp(1.0f - vmag / 12.0f, 0.5f, 1.0f) * coreSize;

                    // ---- 核心 quad（所有星） ----
                    Identifier coreId = Identifier.fromNamespaceAndPath(ReadStar.MODID,
                            "environment/stars/color_" + color);
                    TextureAtlasSprite coreSprite = this.starsAtlas.getSprite(coreId);
                    builder.addVertex(new Vector3f(starSize, -starSize, 0.0F).mul(rotation).add(center))
                            .setUv(coreSprite.getU0(), coreSprite.getV0())
                            .setColor(starColor, starColor, starColor, starAlpha);
                    builder.addVertex(new Vector3f(starSize, starSize, 0.0F).mul(rotation).add(center))
                            .setUv(coreSprite.getU1(), coreSprite.getV0())
                            .setColor(starColor, starColor, starColor, starAlpha);
                    builder.addVertex(new Vector3f(-starSize, starSize, 0.0F).mul(rotation).add(center))
                            .setUv(coreSprite.getU1(), coreSprite.getV1())
                            .setColor(starColor, starColor, starColor, starAlpha);
                    builder.addVertex(new Vector3f(-starSize, -starSize, 0.0F).mul(rotation).add(center))
                            .setUv(coreSprite.getU0(), coreSprite.getV1())
                            .setColor(starColor, starColor, starColor, starAlpha);

                    // ---- 光晕 quad（仅 Vmag < 2.0 的亮星，按亮度分三级） ----
                    if (vmag < 2.0f) {
                        String glowLevel;
                        if (vmag < 0.5f)
                            glowLevel = "glow_high";
                        else if (vmag < 1.5f)
                            glowLevel = "glow_med";
                        else
                            glowLevel = "glow_low";

                        String glowPath = "environment/stars/" + glowLevel + "_" + color;
                        Identifier glowId = Identifier.fromNamespaceAndPath(ReadStar.MODID, glowPath);
                        TextureAtlasSprite glowSprite = this.starsAtlas.getSprite(glowId);
                        builder.addVertex(new Vector3f(glowSIze, -glowSIze, 0.0F).mul(rotation).add(center))
                                .setUv(glowSprite.getU0(), glowSprite.getV0()).setColor(255, 255, 255, starAlpha);
                        builder.addVertex(new Vector3f(glowSIze, glowSIze, 0.0F).mul(rotation).add(center))
                                .setUv(glowSprite.getU1(), glowSprite.getV0()).setColor(255, 255, 255, starAlpha);
                        builder.addVertex(new Vector3f(-glowSIze, glowSIze, 0.0F).mul(rotation).add(center))
                                .setUv(glowSprite.getU1(), glowSprite.getV1()).setColor(255, 255, 255, starAlpha);
                        builder.addVertex(new Vector3f(-glowSIze, -glowSIze, 0.0F).mul(rotation).add(center))
                                .setUv(glowSprite.getU0(), glowSprite.getV1()).setColor(255, 255, 255, starAlpha);
                    }

                } catch (Exception e) {
                    ReadStar.LOGGER.warn("Failed to build star vertex at index {}: {}", i, e.getMessage());
                }
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                this.starIndexCount = mesh.drawState().indexCount();
                ReadStar.LOGGER.info("Built star vertex buffer with {} indices ({} stars, {} with glow)",
                        this.starIndexCount, starCount, glowStarCount);
                return RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", 32, mesh.vertexBuffer());
            }
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

    private static GpuBuffer buildEndSky() {
        GpuBuffer var10;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder
                .exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS,
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

    public void renderSkyDisc(int skyColor) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), ARGB.vector4fFromARGB32(skyColor), new Vector3f(),
                        new Matrix4f());
        GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky disc", colorTexture, OptionalInt.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, this.topSkyBuffer);
            renderPass.draw(0, 10);
        }
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
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(0.0F, 0.0F, 0.0F, 1.0F), new Vector3f(), new Matrix4f());
        GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky dark", colorTexture, OptionalInt.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, this.bottomSkyBuffer);
            renderPass.draw(0, 10);
        }

        modelViewStack.popMatrix();
    }

    public void renderSunMoonAndStars(PoseStack poseStack, float rainBrightness, float starBrightness) {
        CelestialBodyManager manager = CelestialBodyManager.getInstance();
        poseStack.pushPose();

        // ===== 整体天球框架旋转（先确定世界坐标 → 再整体旋转） =====
        // Y = currentRotationVector (观测者天顶方向) → 标准 Y(0,1,0) 映射至此
        // Z = rotationAxis (天球极轴)              → 标准 Z(0,0,1) 映射至此
        // X = Y × Z
        boolean hasFrame = false;
        Quaternionf frameQuat = null;
        Vector3f observerPos = null;

        if (Observer != null) {
            observerPos = Observer.position;
            Vector3f yAxis = new Vector3f(Observer.currentRotationVector).normalize();
            Vector3f zAxis = new Vector3f(Observer.getRotationAxis()).normalize();
            if (yAxis.lengthSquared() > 0.001f && zAxis.lengthSquared() > 0.001f) {
                Vector3f xAxis = new Vector3f(yAxis).cross(zAxis).normalize();
                // 构建标准正交基矩阵 [X | Y | Z]，映射 LOCAL→WORLD
                Matrix3f basis = new Matrix3f();
                basis.m00(xAxis.x); basis.m10(xAxis.y); basis.m20(xAxis.z);
                basis.m01(yAxis.x); basis.m11(yAxis.y); basis.m21(yAxis.z);
                basis.m02(zAxis.x); basis.m12(zAxis.y); basis.m22(zAxis.z);
                frameQuat = new Quaternionf().setFromNormalized(basis);
                poseStack.mulPose(frameQuat);
                hasFrame = true;
            }
        }

        // ===== 在天球框架内各自指向世界坐标方向 =====
        if (hasFrame && observerPos != null) {
            // ---- SUN ----
            CelestialBody earth = manager.getCelestialBody("earth");
            if (earth != null && earth.hostStar != null) {
                Vector3f toWorld = new Vector3f(earth.hostStar.position).sub(observerPos);
                if (toWorld.lengthSquared() > 0.0001f) {
                    toWorld.normalize();
                    float size = manager.getApparentSize(observerPos, earth.hostStar);
                    poseStack.pushPose();
                    poseStack.mulPose(new Quaternionf().rotateTo(new Vector3f(0, 1, 0), toWorld));
                    this.renderSun(size, rainBrightness, poseStack);
                    poseStack.popPose();
                }
            }

            // ---- MOON ----
            CelestialBody moonBody = findMoonBody(manager);
            if (moonBody != null) {
                Vector3f toWorld = new Vector3f(moonBody.position).sub(observerPos);
                if (toWorld.lengthSquared() > 0.0001f) {
                    toWorld.normalize();
                    MoonPhase phase = computeMoonPhase(observerPos, moonBody);
                    float size = manager.getApparentSize(observerPos, moonBody);
                    poseStack.pushPose();
                    poseStack.mulPose(new Quaternionf().rotateTo(new Vector3f(0, 1, 0), toWorld));
                    this.renderMoon(phase, size, rainBrightness, poseStack);
                    poseStack.popPose();
                }
            }

            // ---- OTHER CELESTIAL BODIES（遍历所有天体，渲染有纹理解析的） ----
            // 对 CelestialBodyManager 中每个天体：
            //   1. 排除观测者自身、宿主恒星（太阳）和主月亮（已在上面单独渲染）
            //   2. 排除无 hostStar 的天体（不发光，月相计算无意义）
            //   3. 通过 body.name 在 otherMoonsBuffers 中查找对应的月相缓冲
            //   4. 找到则用 renderBody() 渲染，与 renderMoon() 相同的管线并复用 computeMoonPhase
            for (CelestialBody body : manager.getCelestialBodyTreeMap()) {
                // 排除已单独渲染的对象
                if (body == Observer) continue;
                if (body == Observer.hostStar) continue;
                if (moonBody != null && body == moonBody) continue;
                if (body.hostStar == null) continue;

                Vector3f toWorld = new Vector3f(body.position).sub(observerPos);
                if (toWorld.lengthSquared() > 0.0001f) {
                    toWorld.normalize();
                    MoonPhase phase = computeMoonPhase(observerPos, body);
                    float size = manager.getApparentSize(observerPos, body);
                    GpuBuffer buffer = this.otherMoonsBuffers.get(body.name);
                    if (buffer != null) {
                        poseStack.pushPose();
                        poseStack.mulPose(new Quaternionf().rotateTo(new Vector3f(0, 1, 0), toWorld));
                        renderBody(body.name, buffer, phase, size, rainBrightness, poseStack);
                        poseStack.popPose();
                    }
                }
            }
        }

        // ===== STARS (世界坐标已固定，被 frameQuat 整体旋转) =====
        if (starBrightness > 0.0F) {
            this.renderStars(starBrightness, poseStack);
        }

        poseStack.popPose();
    }

    /** 查找名为 "moon" 的天体，用于原版月相渲染 */
    private static CelestialBody findMoonBody(CelestialBodyManager manager) {
        if (!manager.hasCelestialBody("moon"))
            return null;
        return manager.getCelestialBody("moon");
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
            BufferBuilder builder = new BufferBuilder(buf, VertexFormat.Mode.QUADS, format);

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
                    GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
                    GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
                    GpuBuffer indexBuffer = this.quadIndices.getBuffer(totalIndices);
                    GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                        .writeTransform(modelViewStack, new Vector4f(0.6f, 0.6f, 0.01f, starBrightness * 0.7f), new Vector3f(), new Matrix4f());

                    try (RenderPass renderPass = RenderSystem.getDevice()
                            .createCommandEncoder()
                            .createRenderPass(() -> "Meteors", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
                        renderPass.setPipeline(renderPipeline);
                        RenderSystem.bindDefaultUniforms(renderPass);
                        renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                        renderPass.setVertexBuffer(0, buffer);
                        renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
                        renderPass.drawIndexed(0, 0, totalIndices, 1);
                    }

                    modelViewStack.popMatrix();
                }
            }
        }
    }

    private void renderSun(float size, float rainBrightness, PoseStack poseStack) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(size, -1.0F, size);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(),
                        new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky sun", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(),
                    this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.sunBuffer);
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }

        modelViewStack.popMatrix();
    }

    /**
     * 渲染原版月亮（固定使用 this.moonBuffer，即内置 8 月相纹理）。
     * 与 renderBody() 功能相同但 buffer 硬编码，彼此独立互不替代。
     */
    private void renderMoon(MoonPhase moonPhase, float size, float rainBrightness, PoseStack poseStack) {
        int baseVertex = moonPhase.index() * 4;
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(size, -1.0F, size);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(),
                        new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky moon", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(),
                    this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.moonBuffer);
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(baseVertex, 0, 6, 1);
        }

        modelViewStack.popMatrix();
    }

    /**
     * 通用天体渲染方法，与 renderMoon() 逻辑完全一致，区别仅在于使用外部传入的
     * GpuBuffer 而非固定的 this.moonBuffer。
     * 通过 phase.index() * 4 定位到对应月相的 quad。
     *
     * @param name          天体的调试名称（仅用于 RenderPass 命名）
     * @param buffer        该天体的月相 GPU 缓冲（来自 otherMoonsBuffers）
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
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(),
                        new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky " + name, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(),
                    this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, buffer);
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(baseVertex, 0, 6, 1);
        }

        modelViewStack.popMatrix();
    }

    /**
     * 渲染星星：使用 CELESTIAL 管线 + 星星图集纹理，
     * 每颗星是一个 billboard quad，带有该星特有的颜色贴图。
     */
    private void renderStars(float starBrightness, PoseStack poseStack) {
        if (this.starIndexCount <= 0)
            return;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(this.starIndexCount);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack,
                        new Vector4f(starBrightness, starBrightness, starBrightness, starBrightness), new Vector3f(),
                        new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Stars", colorTexture, OptionalInt.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(ReadStarClient.STAR_TEXTURED_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.starsAtlas.getTextureView(), this.starsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.starBuffer);
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(0, 0, this.starIndexCount, 1);
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
                    .writeTransform(modelViewStack, ARGB.vector4fFromARGB32(sunriseAndSunsetColor), new Vector3f(),
                            new Matrix4f());
            GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
            GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Sunrise sunset", color, OptionalInt.empty(), depth,
                            OptionalDouble.empty())) {
                renderPass.setPipeline(RenderPipelines.SUNRISE_SUNSET);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.setVertexBuffer(0, this.sunriseBuffer);
                renderPass.draw(0, 18);
            }

            modelViewStack.popMatrix();
            poseStack.popPose();
        }
    }

    public void renderEndSky() {
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = autoIndices.getBuffer(36);
        GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(),
                        new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End sky", colorTexture, OptionalInt.empty(), depthTexture,
                        OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.END_SKY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.endSkyTexture.getTextureView(), this.endSkyTexture.getSampler());
            renderPass.setVertexBuffer(0, this.endSkyBuffer);
            renderPass.setIndexBuffer(indexBuffer, autoIndices.type());
            renderPass.drawIndexed(0, 0, 36, 1);
        }
    }

    public void renderEndFlash(PoseStack poseStack, float intensity, float xAngle, float yAngle) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yAngle));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - xAngle));
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(60.0F, 1.0F, 60.0F);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(intensity, intensity, intensity, intensity),
                        new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End flash", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(),
                    this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.endFlashBuffer);
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }

        modelViewStack.popMatrix();
    }

    @Override
    public void close() {
        this.sunBuffer.close();
        this.moonBuffer.close();
        this.starBuffer.close();
        this.topSkyBuffer.close();
        this.bottomSkyBuffer.close();
        this.endSkyBuffer.close();
        this.sunriseBuffer.close();
        this.endFlashBuffer.close();
        this.otherMoonsBuffers.values().forEach(GpuBuffer::close);
    }
}
