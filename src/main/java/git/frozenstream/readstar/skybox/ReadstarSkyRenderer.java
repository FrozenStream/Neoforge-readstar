package git.frozenstream.readstar.skybox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import git.frozenstream.readstar.ReadStar;
import git.frozenstream.readstar.ReadStarClient;
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
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class ReadstarSkyRenderer implements AutoCloseable {

    private static final Identifier SUN_SPRITE = Identifier.withDefaultNamespace("sun");
    private static final Identifier END_FLASH_SPRITE = Identifier.withDefaultNamespace("end_flash");
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
    private final GpuBuffer sunriseBuffer;
    private final GpuBuffer endFlashBuffer;
    private final RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
    private final AbstractTexture endSkyTexture;
    private int starIndexCount;

    public ReadstarSkyRenderer(TextureManager textureManager, AtlasManager atlasManager, ResourceManager resourceManager) {
        this.celestialsAtlas = atlasManager.getAtlasOrThrow(AtlasIds.CELESTIALS);
        this.starsAtlas = atlasManager.getAtlasOrThrow(ReadStarClient.STAR_ATLAS_INFO);
        this.starBuffer = this.buildStars(resourceManager);
        this.endSkyBuffer = buildEndSky();
        this.endSkyTexture = this.getTexture(textureManager, END_SKY_LOCATION);
        this.endFlashBuffer = buildEndFlashQuad(this.celestialsAtlas);
        this.sunBuffer = buildSunQuad(this.celestialsAtlas);
        this.moonBuffer = buildMoonPhases(this.celestialsAtlas);
        this.sunriseBuffer = this.buildSunriseFan();

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
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(phases.length * 4 * format.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, format);

            for (MoonPhase phase : phases) {
                TextureAtlasSprite sprite = atlas.getSprite(Identifier.withDefaultNamespace("moon/" + phase.getSerializedName()));
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
            Optional<net.minecraft.server.packs.resources.Resource> resourceOpt = resourceManager.getResource(starsDataPath);
            if (resourceOpt.isPresent()) {
                try (InputStreamReader reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
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
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        int vtxSize = format.getVertexSize();
        int totalVertices = starCount * 4;

        // 如果没有星星数据，返回一个空的缓冲
        if (totalVertices == 0) {
            this.starIndexCount = 0;
            try (ByteBufferBuilder buf = ByteBufferBuilder.exactlySized(1)) {
                BufferBuilder builder = new BufferBuilder(buf, VertexFormat.Mode.QUADS, format);
                try (MeshData mesh = builder.buildOrThrow()) {
                    return RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer (empty)", 32, mesh.vertexBuffer());
                }
            }
        }

        try (ByteBufferBuilder buf = ByteBufferBuilder.exactlySized(vtxSize * totalVertices)) {
            BufferBuilder builder = new BufferBuilder(buf, VertexFormat.Mode.QUADS, format);

            for (int i = 0; i < starCount; i++) {
                try {
                    JsonObject star = starsArray.get(i).getAsJsonObject();
                    JsonArray pos = star.getAsJsonArray("position");
                    float px = pos.get(0).getAsFloat();
                    float py = pos.get(2).getAsFloat();
                    float pz = pos.get(1).getAsFloat();
                    float vmag = star.get("Vmag").getAsFloat();
                    int color = star.get("color").getAsInt();
                    float size = 1f;

                    Identifier spriteId = Identifier.fromNamespaceAndPath(ReadStar.MODID, "environment/stars/color_" + color);
                    TextureAtlasSprite sprite = this.starsAtlas.getSprite(spriteId);

                    // Billboard quad：始终面向观察者
                    float starDist = 100.0F;
                    Vector3f center = new Vector3f(px, py, pz).normalize(starDist);
                    Vector3f dirToCenter = new Vector3f(center).negate();
                    Matrix3f rotation = new Matrix3f().rotateTowards(dirToCenter, new Vector3f(0.0F, 1.0F, 0.0F));

                    builder.addVertex(new Vector3f(size, -size, 0.0F).mul(rotation).add(center))
                        .setUv(sprite.getU0(), sprite.getV0());
                    builder.addVertex(new Vector3f(size, size, 0.0F).mul(rotation).add(center))
                        .setUv(sprite.getU1(), sprite.getV0());
                    builder.addVertex(new Vector3f(-size, size, 0.0F).mul(rotation).add(center))
                        .setUv(sprite.getU1(), sprite.getV1());
                    builder.addVertex(new Vector3f(-size, -size, 0.0F).mul(rotation).add(center))
                        .setUv(sprite.getU0(), sprite.getV1());

                } catch (Exception e) {
                    ReadStar.LOGGER.warn("Failed to build star vertex at index {}: {}", i, e.getMessage());
                }
            }

            try (MeshData mesh = builder.buildOrThrow()) {
                this.starIndexCount = mesh.drawState().indexCount();
                ReadStar.LOGGER.info("Built star vertex buffer with {} indices", this.starIndexCount);
                return RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", 32, mesh.vertexBuffer());
            }
        }
    }

    private void buildSkyDisc(VertexConsumer builder, float yy) {
        float x = Math.signum(yy) * 512.0F;
        builder.addVertex(0.0F, yy, 0.0F);

        for (int i = -180; i <= 180; i += 45) {
            builder.addVertex(x * Mth.cos(i * (float) (Math.PI / 180.0)), yy, 512.0F * Mth.sin(i * (float) (Math.PI / 180.0)));
        }
    }

    private static GpuBuffer buildEndSky() {
        GpuBuffer var10;
        try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

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
                var10 = RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", 40, meshData.vertexBuffer());
            }
        }

        return var10;
    }

    public void renderSkyDisc(int skyColor) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), ARGB.vector4fFromARGB32(skyColor), new Vector3f(), new Matrix4f());
        GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky disc", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
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
                state.sunAngle = attributeProbe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * (float) (Math.PI / 180.0);
                state.moonAngle = attributeProbe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTicks) * (float) (Math.PI / 180.0);
                state.starAngle = attributeProbe.getValue(EnvironmentAttributes.STAR_ANGLE, partialTicks) * (float) (Math.PI / 180.0);
                state.rainBrightness = 1.0F - level.getRainLevel(partialTicks);
                state.starBrightness = attributeProbe.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partialTicks);
                state.sunriseAndSunsetColor = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, partialTicks);
                state.moonPhase = attributeProbe.getValue(EnvironmentAttributes.MOON_PHASE, partialTicks);
                state.skyColor = attributeProbe.getValue(EnvironmentAttributes.SKY_COLOR, partialTicks);
                state.shouldRenderDarkDisc = this.shouldRenderDarkDisc(partialTicks, level);
            }
        }
    }

    private boolean shouldRenderDarkDisc(float deltaPartialTick, ClientLevel level) {
        return Minecraft.getInstance().player.getEyePosition(deltaPartialTick).y - level.getLevelData().getHorizonHeight(level) < 0.0;
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
                .createRenderPass(() -> "Sky dark", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, this.bottomSkyBuffer);
            renderPass.draw(0, 10);
        }

        modelViewStack.popMatrix();
    }

    public void renderSunMoonAndStars(
            PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness
    ) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotation(sunAngle));
        this.renderSun(rainBrightness, poseStack);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotation(moonAngle));
        this.renderMoon(moonPhase, rainBrightness, poseStack);
        poseStack.popPose();
        if (starBrightness > 0.0F) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotation(starAngle));
            this.renderStars(1, poseStack);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private void renderSun(float rainBrightness, PoseStack poseStack) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(30.0F, 1.0F, 30.0F);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky sun", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.sunBuffer);
            renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
            renderPass.drawIndexed(0, 0, 6, 1);
        }

        modelViewStack.popMatrix();
    }

    private void renderMoon(MoonPhase moonPhase, float rainBrightness, PoseStack poseStack) {
        int baseVertex = moonPhase.index() * 4;
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(0.0F, 100.0F, 0.0F);
        modelViewStack.scale(20.0F, 1.0F, 20.0F);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Sky moon", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
            renderPass.setVertexBuffer(0, this.moonBuffer);
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
        if (this.starIndexCount <= 0) return;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(this.starIndexCount);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(modelViewStack, new Vector4f(starBrightness, starBrightness, starBrightness, starBrightness), new Vector3f(), new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Stars", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
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
                    .writeTransform(modelViewStack, ARGB.vector4fFromARGB32(sunriseAndSunsetColor), new Vector3f(), new Matrix4f());
            GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
            GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Sunrise sunset", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
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
                .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End sky", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
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
                .writeTransform(modelViewStack, new Vector4f(intensity, intensity, intensity, intensity), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "End flash", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            renderPass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
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
    }
}
