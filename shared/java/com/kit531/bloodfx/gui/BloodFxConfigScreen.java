package com.kit531.bloodfx.gui;

import com.kit531.bloodfx.Compat;
import com.kit531.bloodfx.HitType;
import com.kit531.bloodfx.client.BloodSounds;
import com.kit531.bloodfx.config.BloodFxConfig;
import com.kit531.bloodfx.update.Updater;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * The settings screen opened from Mod Menu. Five tabs: General, then one per hit type.
 * Built from plain vanilla widgets so it does not depend on any config library.
 */
public class BloodFxConfigScreen extends Screen {
    private static final String[] TAB_NAMES = {"General", "Normal", "Knockback", "Crit", "Sweep"};
    private static final HitType[] TAB_TYPES = {null, HitType.NORMAL, HitType.KNOCKBACK, HitType.CRIT, HitType.SWEEP};

    private final Screen parent;
    private int tab = 0;
    private Button updateButton;
    private StringWidget updateStatus;

    public BloodFxConfigScreen(Screen parent) {
        super(Component.literal("Blood FX"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BloodFxConfig cfg = BloodFxConfig.get();
        int cx = this.width / 2;

        addRenderableWidget(label(cx, 8, this.title));

        // Tabs
        int tabWidth = 62;
        int gap = 2;
        int totalWidth = TAB_NAMES.length * tabWidth + (TAB_NAMES.length - 1) * gap;
        int startX = cx - totalWidth / 2;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            Button button = Button.builder(Component.literal(TAB_NAMES[i]), b -> {
                tab = index;
                this.rebuildWidgets();
            }).bounds(startX + i * (tabWidth + gap), 24, tabWidth, 20).build();
            button.active = i != tab;
            addRenderableWidget(button);
        }

        if (tab == 0) {
            buildGeneral(cfg, cx);
        } else {
            buildType(cfg, TAB_TYPES[tab], cx);
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    /** A text line centred on {@code cx}. Sized to the text so it does not depend on widget alignment. */
    private StringWidget label(int cx, int y, Component text) {
        int w = this.font.width(text);
        return new StringWidget(cx - w / 2, y, w, 10, text, this.font);
    }

    // ---- General tab -------------------------------------------------------------------

    private void buildGeneral(BloodFxConfig cfg, int cx) {
        int left = cx - 155;
        int right = cx + 5;
        int w = 150;
        int y = 56;
        int step = 24;

        addToggle(left, y, w, "Mod", () -> cfg.enabled, v -> cfg.enabled = v);
        addToggle(left, y + step, w, "Blood particles", () -> cfg.particlesEnabled, v -> cfg.particlesEnabled = v);
        addToggle(left, y + step * 2, w, "Sounds", () -> cfg.soundsEnabled, v -> cfg.soundsEnabled = v);
        addToggle(left, y + step * 3, w, "Hit colour tint", () -> cfg.tintEnabled, v -> cfg.tintEnabled = v);

        addRenderableWidget(new ValueSlider(right, y + step, w, 20, 0, 300, cfg.particleAmount,
                v -> Component.literal("Particle amount: " + Math.round(v) + "%"),
                v -> {
                    cfg.particleAmount = (int) Math.round(v);
                    BloodFxConfig.markDirty();
                }));
        addRenderableWidget(new ValueSlider(right, y + step * 2, w, 20, 0, 100, cfg.masterVolume * 100.0,
                v -> Component.literal("Master volume: " + Math.round(v) + "%"),
                v -> {
                    cfg.masterVolume = (float) (v / 100.0);
                    BloodFxConfig.markDirty();
                }));
        addRenderableWidget(new ValueSlider(right, y + step * 3, w, 20, 0.5, 5.0, cfg.tintDurationMultiplier,
                v -> Component.literal(String.format("Tint duration: x%.1f (%d ticks)", v, Math.round(10 * v))),
                v -> {
                    cfg.tintDurationMultiplier = (float) (Math.round(v * 10.0) / 10.0);
                    BloodFxConfig.markDirty();
                }));
        addRenderableWidget(new ValueSlider(right, y + step * 4, w, 20, 10, 300, cfg.tintBrightness * 100.0,
                v -> Component.literal("Tint brightness: " + Math.round(v) + "%"),
                v -> {
                    cfg.tintBrightness = (float) (v / 100.0);
                    BloodFxConfig.markDirty();
                }));

        // Remote updates
        addRenderableWidget(Button.builder(updateModeText(cfg.updateMode), b -> {
            cfg.updateMode = (cfg.updateMode + 1) % 3;
            b.setMessage(updateModeText(cfg.updateMode));
        }).bounds(left, y + step * 4, w, 20).build());

        updateButton = Button.builder(updateButtonText(), b -> {
            if (Updater.state() == Updater.State.AVAILABLE) {
                Updater.installAsync();
            } else {
                Updater.checkAsync(true);
            }
            b.setMessage(updateButtonText());
        }).bounds(cx - 100, y + step * 5, 200, 20).build();
        addRenderableWidget(updateButton);

        updateStatus = new StringWidget(cx - 150, y + step * 5 + 24, 300, 10,
                Component.literal(Updater.statusText()), this.font);
        addRenderableWidget(updateStatus);
    }

    /** Keeps the update button and status line in step with the background update thread. */
    public void tick() {
        if (updateButton != null) {
            updateButton.setMessage(updateButtonText());
        }
        if (updateStatus != null) {
            updateStatus.setMessage(Component.literal(Updater.statusText()));
        }
    }

    private static Component updateModeText(int mode) {
        return Component.literal("Updates: " + switch (mode) {
            case 0 -> "OFF";
            case 1 -> "NOTIFY";
            default -> "AUTO";
        });
    }

    private static Component updateButtonText() {
        return Component.literal(switch (Updater.state()) {
            case CHECKING -> "Checking...";
            case DOWNLOADING -> "Downloading...";
            case AVAILABLE -> "Install the update";
            case READY -> "Restart the game to update";
            default -> "Check for updates";
        });
    }

    private void addToggle(int x, int y, int w, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(toggleText(label, getter.getAsBoolean()), b -> {
            boolean now = !getter.getAsBoolean();
            setter.accept(now);
            BloodFxConfig.markDirty();
            b.setMessage(toggleText(label, now));
        }).bounds(x, y, w, 20).build());
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    // ---- Hit type tabs -----------------------------------------------------------------

    private void buildType(BloodFxConfig cfg, HitType type, int cx) {
        BloodFxConfig.TypeSettings settings = cfg.of(type);

        addRenderableWidget(label(cx, 54, Component.literal("Tint colour (hex, e.g. #FF7A00)")));

        EditBox colorBox = new EditBox(this.font, cx - 100, 66, 200, 20, Component.literal("Colour"));
        colorBox.setMaxLength(7);
        colorBox.setValue(settings.color);
        colorBox.setResponder(text -> {
            if (text.matches("#?[0-9a-fA-F]{6}")) {
                settings.color = text.startsWith("#") ? text : "#" + text;
                BloodFxConfig.markDirty();
            }
        });
        addRenderableWidget(colorBox);

        if (type == HitType.NORMAL) {
            addRenderableWidget(label(cx, 98, Component.literal("Normal damage has no extra sound.")));
            return;
        }

        Button soundButton = Button.builder(soundText(settings.sound), b -> {
            settings.sound = (settings.sound + 1) % (BloodSounds.VARIANTS + 1);
            BloodFxConfig.markDirty();
            b.setMessage(soundText(settings.sound));
            BloodSounds.preview(type, settings.sound, settings.volume * cfg.masterVolume);
        }).bounds(cx - 100, 96, 150, 20).build();
        addRenderableWidget(soundButton);

        addRenderableWidget(Button.builder(Component.literal("Play"), b ->
                BloodSounds.preview(type, settings.sound, settings.volume * cfg.masterVolume))
                .bounds(cx + 54, 96, 46, 20).build());

        addRenderableWidget(new ValueSlider(cx - 100, 124, 200, 20, 0, 150, settings.volume * 100.0,
                v -> Component.literal("Volume: " + Math.round(v) + "%"),
                v -> {
                    settings.volume = (float) (v / 100.0);
                    BloodFxConfig.markDirty();
                }));
    }

    private static Component soundText(int variant) {
        return Component.literal(variant == 0 ? "Sound: Original" : "Sound: Variant " + variant);
    }

    @Override
    public void onClose() {
        BloodFxConfig.save();
        if (this.minecraft != null) {
            Compat.setScreen(this.minecraft, this.parent);
        }
    }

    /** A slider that maps 0..1 onto [min, max] and reports the real value. */
    private static final class ValueSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final DoubleFunction<Component> label;
        private final DoubleConsumer onChange;

        ValueSlider(int x, int y, int width, int height, double min, double max, double current,
                    DoubleFunction<Component> label, DoubleConsumer onChange) {
            super(x, y, width, height, Component.empty(), (current - min) / (max - min));
            this.min = min;
            this.max = max;
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        private double real() {
            return min + this.value * (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(real()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }
}
