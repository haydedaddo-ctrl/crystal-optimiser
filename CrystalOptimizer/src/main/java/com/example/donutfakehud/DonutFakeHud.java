package com.example.donutfakehud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class DonutFakeHud implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/donut-fake-hud.json");
    }
    private static long fakeBalance = 999_999_999L;
    private static String hudLabel = "Balance";
    private static boolean enabled = true;
    private static boolean opaqueBackground = true;
    private static int hudX = 10;
    private static int hudY = 10;
    private static String lastFakePay = "No fake payments yet";
    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        load();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donut_fake_hud.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.donut_fake_hud"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new SettingsScreen());
            }
        });
        HudRenderCallback.EVENT.register((DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!enabled || client.player == null || client.options.hudHidden) return;
            String line = hudLabel + ": $" + money(fakeBalance);
            int width = client.textRenderer.getWidth(line);
            int x = Math.max(0, Math.min(hudX, client.getWindow().getScaledWidth() - width - 8));
            int y = Math.max(0, Math.min(hudY, client.getWindow().getScaledHeight() - 20));
            // Opaque mode can visually cover the server's balance when positioned over it.
            if (opaqueBackground) context.fill(x - 4, y - 3, x + width + 4, y + 13, 0xFF101010);
            context.drawTextWithShadow(client.textRenderer, line, x, y, 0xFFFFD34E);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("fakebal")
                .executes(ctx -> { reply("Your local fake balance is $" + money(fakeBalance)); return 1; })
                .then(literal("set").then(argument("amount", LongArgumentType.longArg(0))
                    .executes(ctx -> { fakeBalance = LongArgumentType.getLong(ctx, "amount"); save(); reply("Fake balance set to $" + money(fakeBalance)); return 1; })))
                .then(literal("add").then(argument("amount", LongArgumentType.longArg(0))
                    .executes(ctx -> { fakeBalance = safeAdd(fakeBalance, LongArgumentType.getLong(ctx, "amount")); save(); reply("Added fake money. Balance: $" + money(fakeBalance)); return 1; })))
                .then(literal("remove").then(argument("amount", LongArgumentType.longArg(0))
                    .executes(ctx -> { fakeBalance = Math.max(0, fakeBalance - LongArgumentType.getLong(ctx, "amount")); save(); reply("Removed fake money. Balance: $" + money(fakeBalance)); return 1; })))
                .then(literal("toggle").executes(ctx -> { enabled = !enabled; save(); reply("Fake HUD " + (enabled ? "enabled" : "disabled; server display is visible again")); return 1; }))
            );
            dispatcher.register(literal("fakepay")
                .then(argument("player", StringArgumentType.word())
                .then(argument("amount", LongArgumentType.longArg(0))
                .executes(ctx -> {
                    String player = StringArgumentType.getString(ctx, "player");
                    long amount = LongArgumentType.getLong(ctx, "amount");
                    if (amount > fakeBalance) { reply("Fake payment failed: not enough fake money."); return 0; }
                    fakeBalance -= amount;
                    lastFakePay = "Fake paid $" + money(amount) + " to " + player;
                    save();
                    reply(lastFakePay + " (LOCAL ONLY — no server payment was sent)");
                    return 1;
                }))));
            dispatcher.register(literal("fakepaylast").executes(ctx -> { reply(lastFakePay); return 1; }));
        });
    }

    private static class SettingsScreen extends Screen {
        private TextFieldWidget balanceField, labelField, xField, yField;
        protected SettingsScreen() { super(Text.literal("Donut Fake HUD Settings")); }

        @Override
        protected void init() {
            int cx = width / 2;
            int left = cx - 100;
            balanceField = new TextFieldWidget(textRenderer, left, 48, 200, 20, Text.literal("Fake balance"));
            balanceField.setText(Long.toString(fakeBalance));
            balanceField.setMaxLength(18);
            addDrawableChild(balanceField);
            labelField = new TextFieldWidget(textRenderer, left, 88, 200, 20, Text.literal("HUD label"));
            labelField.setText(hudLabel);
            labelField.setMaxLength(24);
            addDrawableChild(labelField);
            xField = new TextFieldWidget(textRenderer, left, 128, 94, 20, Text.literal("X"));
            xField.setText(Integer.toString(hudX));
            xField.setMaxLength(5);
            addDrawableChild(xField);
            yField = new TextFieldWidget(textRenderer, left + 106, 128, 94, 20, Text.literal("Y"));
            yField.setText(Integer.toString(hudY));
            yField.setMaxLength(5);
            addDrawableChild(yField);
            addDrawableChild(ButtonWidget.builder(Text.literal("HUD: " + (enabled ? "ON" : "OFF")), b -> {
                enabled = !enabled; b.setMessage(Text.literal("HUD: " + (enabled ? "ON" : "OFF")));
            }).dimensions(left, 160, 98, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Background: " + (opaqueBackground ? "ON" : "OFF")), b -> {
                opaqueBackground = !opaqueBackground; b.setMessage(Text.literal("Background: " + (opaqueBackground ? "ON" : "OFF")));
            }).dimensions(left + 102, 160, 98, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Save settings"), b -> saveAndClose())
                    .dimensions(left, 190, 200, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                    .dimensions(left, 216, 200, 20).build());
        }

        private void saveAndClose() {
            try {
                fakeBalance = Math.max(0, Long.parseLong(balanceField.getText().trim()));
                hudLabel = labelField.getText().isBlank() ? "Balance" : labelField.getText().trim();
                hudX = Math.max(0, Math.min(10000, Integer.parseInt(xField.getText().trim())));
                hudY = Math.max(0, Math.min(10000, Integer.parseInt(yField.getText().trim())));
                save();
                close();
            } catch (NumberFormatException e) {
                if (client != null && client.player != null) client.player.sendMessage(Text.literal("Enter valid numbers for balance, X and Y."), false);
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "Fake balance ($)", width / 2 - 100, 36, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "Text label", width / 2 - 100, 76, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "HUD X", width / 2 - 100, 116, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, "HUD Y", width / 2 + 6, 116, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer, "Right Shift opens this menu | OFF reveals the server's normal display", width / 2, height - 18, 0xFFCCCCCC);
        }
        @Override public boolean shouldPause() { return false; }
    }

    private static long safeAdd(long a, long b) {
        try { return Math.addExact(a, b); } catch (ArithmeticException e) { return Long.MAX_VALUE; }
    }
    private static String money(long value) { return String.format(Locale.US, "%,d", value); }
    private static void reply(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal("[Fake HUD] " + message), false);
    }
    private static void load() {
        try {
            Path path = configPath();
            if (Files.exists(path)) {
                Settings settings = GSON.fromJson(Files.readString(path), Settings.class);
                if (settings != null) {
                    fakeBalance = Math.max(0, settings.fakeBalance);
                    if (settings.lastFakePay != null) lastFakePay = settings.lastFakePay;
                    if (settings.hudLabel != null) hudLabel = settings.hudLabel;
                    enabled = settings.enabled;
                    opaqueBackground = settings.opaqueBackground;
                    hudX = Math.max(0, settings.hudX);
                    hudY = Math.max(0, settings.hudY);
                }
            }
        } catch (Exception ignored) { }
    }
    private static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new Settings(fakeBalance, lastFakePay, hudLabel, enabled, opaqueBackground, hudX, hudY)));
        } catch (IOException e) { reply("Could not save fake balance config."); }
    }
    private record Settings(long fakeBalance, String lastFakePay, String hudLabel, boolean enabled,
                            boolean opaqueBackground, int hudX, int hudY) { }
}
