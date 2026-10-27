package com.fartiqs.motionblur;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Motion blur by frame accumulation, done with plain OpenGL so it does not depend on
 * Minecraft's internal render classes. Every GL state it touches is restored afterwards.
 */
public class MotionBlurRenderer {
    private static final String VERT = "#version 150\n"
            + "out vec2 uv;\n"
            + "void main() {\n"
            + "  vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n"
            + "  uv = p;\n"
            + "  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
            + "}\n";
    private static final String FRAG = "#version 150\n"
            + "in vec2 uv;\n"
            + "out vec4 o;\n"
            + "uniform sampler2D tex;\n"
            + "uniform float alpha;\n"
            + "void main() { o = vec4(texture(tex, uv).rgb, alpha); }\n";

    private static int program = 0, vao = 0, uAlpha = -1;
    private static int histTex = 0, histFbo = 0, histW = 0, histH = 0;
    private static boolean failed = false;
    private static float lastYaw, lastPitch;
    private static boolean hasLastRot;

    public static void apply(MinecraftClient mc) {
        Config cfg = Config.get();
        if (failed || mc.world == null) {
            hasLastRot = false;
            return;
        }
        if (!cfg.enabled) {
            hasLastRot = false;
            return;
        }

        boolean blend = true;
        if (cfg.disableInMenus && mc.currentScreen != null && !(mc.currentScreen instanceof MotionBlurScreen)) {
            blend = false;
        }

        float alpha = MathHelper.clamp(cfg.strength, 0f, 0.95f);
        if (cfg.adaptive) {
            float yaw = mc.gameRenderer.getCamera().getYaw();
            float pitch = mc.gameRenderer.getCamera().getPitch();
            if (hasLastRot) {
                float movement = Math.abs(MathHelper.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch);
                float full = MathHelper.lerp(cfg.adaptiveSensitivity, 12f, 1.5f);
                alpha *= MathHelper.clamp(movement / full, 0f, 1f);
            }
            lastYaw = yaw;
            lastPitch = pitch;
            hasLastRot = true;
        }

        try {
            run(alpha, blend);
        } catch (Throwable t) {
            failed = true;
            System.err.println("[Fartiqs Motion Blur] Disabled after a rendering error:");
            t.printStackTrace();
        }
    }

    private static void run(float alpha, boolean blend) {
        // ---- save GL state ----
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int bSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int bDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int bSrcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int bDstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        try {
            int mainFbo = prevDraw;
            if (mainFbo == 0) return; // not rendering into an offscreen target, skip

            // size of the target we are about to blur
            int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type != GL11.GL_TEXTURE) return;
            int mainTex = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainTex);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0) return;

            if (program == 0) init();

            boolean fresh = false;
            if (histTex == 0 || histW != w || histH != h) {
                createHistory(w, h);
                fresh = true;
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            // 1) blend the previous frame over the current one
            if (!fresh && blend && alpha > 0.001f) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
                GL11.glViewport(0, 0, w, h);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                GL11.glColorMask(true, true, true, true);
                GL20.glUseProgram(program);
                GL20.glUniform1f(uAlpha, alpha);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, histTex);
                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            }

            // 2) store the result as the next frame's "previous"
            GL11.glDisable(GL11.GL_BLEND);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, histFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            // ---- restore GL state ----
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL13.glActiveTexture(prevActive);
            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVao);
            GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
            GL14.glBlendFuncSeparate(bSrcRgb, bDstRgb, bSrcA, bDstA);
            setEnabled(GL11.GL_BLEND, wasBlend);
            setEnabled(GL11.GL_DEPTH_TEST, wasDepth);
            setEnabled(GL11.GL_CULL_FACE, wasCull);
            setEnabled(GL11.GL_SCISSOR_TEST, wasScissor);
        }
    }

    private static void setEnabled(int cap, boolean on) {
        if (on) GL11.glEnable(cap); else GL11.glDisable(cap);
    }

    private static void init() {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Motion blur shader link failed: " + GL20.glGetProgramInfoLog(p));
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "tex"), 0);
        uAlpha = GL20.glGetUniformLocation(p, "alpha");
        vao = GL30.glGenVertexArrays();
        program = p;
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Motion blur shader compile failed: " + GL20.glGetShaderInfoLog(s));
        }
        return s;
    }

    private static void createHistory(int w, int h) {
        if (histFbo != 0) GL30.glDeleteFramebuffers(histFbo);
        if (histTex != 0) GL11.glDeleteTextures(histTex);
        histTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, histTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12ClampToEdge());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12ClampToEdge());
        histFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, histFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, histTex, 0);
        histW = w;
        histH = h;
    }

    private static int GL12ClampToEdge() {
        return org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
    }
}
