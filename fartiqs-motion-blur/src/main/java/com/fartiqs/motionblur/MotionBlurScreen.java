package com.fartiqs.motionblur;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class MotionBlurScreen extends Screen {
    private final Screen parent;
    private ButtonWidget enabledBtn, adaptiveBtn, menusBtn, aspectToggleBtn, presetBtn;
    private SliderWidget strengthSlider, sensSlider;
    private AspectSlider aspectSlider;

    public MotionBlurScreen(Screen parent) {
        super(Text.literal("Fartiqs Motion Blur"));
        this.parent = parent;
    }

    private static Text onOff(String label, boolean v) {
        return Text.literal(label + ": " + (v ? "ON" : "OFF"));
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int w = 200, x = this.width / 2 - w / 2;
        int y = this.height / 2 - 120;
        int step = 24;

        // ---- Motion blur ----
        enabledBtn = addDrawableChild(ButtonWidget.builder(onOff("Motion Blur", c.enabled), b -> {
            c.enabled = !c.enabled; b.setMessage(onOff("Motion Blur", c.enabled)); Config.save();
        }).dimensions(x, y, w, 20).build());
        y += step;

        strengthSlider = addDrawableChild(new SliderWidget(x, y, w, 20, Text.empty(), c.strength / 0.95f) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.literal("Strength: " + Math.round(c.strength * 100) + "%"));
            }
            @Override protected void applyValue() {
                c.strength = (float) (this.value * 0.95); Config.save();
            }
        });
        y += step;

        adaptiveBtn = addDrawableChild(ButtonWidget.builder(onOff("Adaptive (blur when turning)", c.adaptive), b -> {
            c.adaptive = !c.adaptive; b.setMessage(onOff("Adaptive (blur when turning)", c.adaptive)); Config.save();
        }).dimensions(x, y, w, 20).build());
        y += step;

        sensSlider = addDrawableChild(new SliderWidget(x, y, w, 20, Text.empty(), c.adaptiveSensitivity) {
            { updateMessage(); }
            @Override protected void updateMessage() {
                setMessage(Text.literal("Adaptive Sensitivity: " + Math.round(c.adaptiveSensitivity * 100) + "%"));
            }
            @Override protected void applyValue() {
                c.adaptiveSensitivity = (float) this.value; Config.save();
            }
        });
        y += step;

        menusBtn = addDrawableChild(ButtonWidget.builder(onOff("Disable In Menus", c.disableInMenus), b -> {
            c.disableInMenus = !c.disableInMenus; b.setMessage(onOff("Disable In Menus", c.disableInMenus)); Config.save();
        }).dimensions(x, y, w, 20).build());
        y += step + 10;

        // ---- Aspect ratio ----
        aspectToggleBtn = addDrawableChild(ButtonWidget.builder(onOff("Custom Aspect Ratio", c.aspectEnabled), b -> {
            c.aspectEnabled = !c.aspectEnabled; b.setMessage(onOff("Custom Aspect Ratio", c.aspectEnabled)); Config.save();
        }).dimensions(x, y, w, 20).build());
        y += step;

        presetBtn = addDrawableChild(ButtonWidget.builder(presetText(c), b -> {
            c.aspectPreset = (c.aspectPreset + 1) % Config.ASPECT_NAMES.length;
            float v = Config.ASPECT_VALUES[c.aspectPreset];
            if (v > 0) {
                c.aspect = v;
                aspectSlider.setFromAspect(v);
            }
            b.setMessage(presetText(c));
            Config.save();
        }).dimensions(x, y, w, 20).build());
        y += step;

        aspectSlider = addDrawableChild(new AspectSlider(x, y, w, 20));
        y += step + 10;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset To Defaults"), b -> {
            c.reset(); Config.save(); this.clearAndInit();
        }).dimensions(x, y, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x + w / 2 + 2, y, w / 2 - 2, 20).build());
    }

    private class AspectSlider extends SliderWidget {
        AspectSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), sliderFromAspect(Config.get().aspect));
            updateMessage();
        }

        void setFromAspect(float a) {
            this.value = sliderFromAspect(a);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.format("Aspect Ratio: %.2f", Config.get().aspect)));
        }

        @Override
        protected void applyValue() {
            Config c = Config.get();
            c.aspect = 0.5f + (float) this.value * 3.5f; // 0.5 - 4.0
            c.aspectPreset = Config.ASPECT_NAMES.length - 1; // Custom
            if (presetBtn != null) presetBtn.setMessage(presetText(c));
            Config.save();
        }
    }

    private static Text presetText(Config c) {
        return Text.literal("Preset: " + Config.ASPECT_NAMES[c.aspectPreset]);
    }

    private static double sliderFromAspect(float a) {
        return Math.max(0, Math.min(1, (a - 0.5) / 3.5));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(this.textRenderer, this.title, this.width / 2 - this.textRenderer.getWidth(this.title) / 2, this.height / 2 - 140, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        Config.save();
        this.client.setScreen(parent);
    }
}
