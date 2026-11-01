package dev.tomer.modhub;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/** Draws colored 3D glow ON THE BLOCK instead of a screen-space waypoint.
 * Dedicated no-depth-test pipeline renders through stone and terrain.
 * Compiled in Mojang-named workspace then remapped by Fabric Loom to intermediary.
 */
public final class WorldGlowRenderer {
    private WorldGlowRenderer() { }
    private static final RenderPipeline GLOW = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("tomermodhub","pipeline/through_wall_glow"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );
    private static final float[][] RGB = {
        {1.00f,0.78f,0.12f},  // normal chest gold
        {0.72f,0.36f,1.00f},  // ender chest purple
        {0.08f,0.96f,0.91f},  // shulker cyan
        {1.00f,0.30f,0.10f}   // torch orange red
    };
    private static final Vector4f COLOR_MODULATOR=new Vector4f(1f,1f,1f,1f);
    private static final ByteBufferBuilder ALLOCATOR=new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static MappableRingBuffer vertexBuffer;
    private static boolean registered;

    public static void register() {
        if(registered)return;
        registered=true;
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldGlowRenderer::render);
    }
    private static void render(WorldRenderContext context) {
        if(!GlowBridge.enabled)return;
        List<GlowBridge.GlowPoint> points=GlowBridge.points;
        if(points.isEmpty())return;
        PoseStack matrices=context.matrices();
        if(matrices==null)return;
        Vec3 camera=context.worldState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x,-camera.y,-camera.z);
        BufferBuilder builder=new BufferBuilder(ALLOCATOR,GLOW.getVertexFormatMode(),GLOW.getVertexFormat());
        int drawn=0;
        for(GlowBridge.GlowPoint p:points) {
            if(drawn++>=110)break;
            float[] c=RGB[Math.max(0,Math.min(3,p.type()))];
            // Larger soft outer halo + colored transparent block highlight.
            ShapeRenderer.addChainedFilledBoxVertices(matrices,builder,
                p.x()-.075f,p.y()-.075f,p.z()-.075f,
                p.x()+1.075f,p.y()+1.075f,p.z()+1.075f,
                c[0],c[1],c[2],.20f);
            ShapeRenderer.addChainedFilledBoxVertices(matrices,builder,
                p.x()+.015f,p.y()+.015f,p.z()+.015f,
                p.x()+.985f,p.y()+.985f,p.z()+.985f,
                c[0],c[1],c[2],.46f);
        }
        matrices.popPose();
        MeshData mesh=builder.buildOrThrow();
        MeshData.DrawState draw=mesh.drawState();
        VertexFormat format=draw.format();
        GpuBuffer vertices=upload(draw,format,mesh);
        draw(Minecraft.getInstance(),GLOW,mesh,draw,vertices,format);
        vertexBuffer.rotate();
    }
    private static GpuBuffer upload(MeshData.DrawState draw,VertexFormat format,MeshData mesh) {
        int required=draw.vertexCount()*format.getVertexSize();
        if(vertexBuffer==null||vertexBuffer.size()<required){
            if(vertexBuffer!=null)vertexBuffer.close();
            vertexBuffer=new MappableRingBuffer(()->"Tomer Mod Hub glow ESP",GpuBuffer.USAGE_VERTEX|GpuBuffer.USAGE_MAP_WRITE,required);
        }
        CommandEncoder encoder=RenderSystem.getDevice().createCommandEncoder();
        try(GpuBuffer.MappedView mapped=encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0,mesh.vertexBuffer().remaining()),false,true)){
            MemoryUtil.memCopy(mesh.vertexBuffer(),mapped.data());
        }
        return vertexBuffer.currentBuffer();
    }
    private static void draw(Minecraft client,RenderPipeline pipeline,MeshData mesh,MeshData.DrawState params,GpuBuffer vertices,VertexFormat format){
        GpuBuffer indices;
        VertexFormat.IndexType indexType;
        if(pipeline.getVertexFormatMode()==VertexFormat.Mode.QUADS){
            mesh.sortQuads(ALLOCATOR,RenderSystem.getProjectionType().vertexSorting());
            indices=pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
            indexType=mesh.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer seq=RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices=seq.getBuffer(params.indexCount());
            indexType=seq.type();
        }
        GpuBufferSlice transform=RenderSystem.getDynamicUniforms().writeTransform(
            RenderSystem.getModelViewMatrix(),COLOR_MODULATOR,new Vector3f(),RenderSystem.getTextureMatrix(),1f);
        try(RenderPass pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                ()->"Tomer Mod Hub block glow",client.getMainRenderTarget().getColorTextureView(),OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(),OptionalDouble.empty())){
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",transform);
            pass.setVertexBuffer(0,vertices);
            pass.setIndexBuffer(indices,indexType);
            pass.drawIndexed(0,0,params.indexCount(),1);
        }
        mesh.close();
    }
}

