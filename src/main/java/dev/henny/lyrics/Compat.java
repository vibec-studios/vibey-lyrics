package dev.henny.lyrics;

import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.9
import net.minecraft.network.chat.FontDescription;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else
/*import net.minecraft.resources.ResourceLocation;*/
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Every call whose shape changed between Minecraft 1.21.1 and 26.3 lives here (apart from the drawing code in
 * client/ui/Gfx, client/ui/BaseScreen and render/WorldSink), so the rest of the mod reads the same on every version.
 */
public final class Compat {
    private Compat() {}

    /** Light value for "fully lit", LightTexture.FULL_BRIGHT (gone as a constant in 26.x). */
    public static final int FULL_BRIGHT = 0xF000F0;

    //? if >=1.21.11 {
    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    //?} else {
    /*public static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    *///?}

    public static void openScreen(Screen screen) {
        //? if >=26.2 {
        /*Minecraft.getInstance().setScreenAndShow(screen);
        *///?} else
        Minecraft.getInstance().setScreen(screen);
    }

    /** Opens a folder in the system file manager. */
    public static void openFolder(java.nio.file.Path path) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(path);
        *///?}
        //? if >=1.21.11 <26.3 {
        net.minecraft.util.Util.getPlatform().openPath(path);
        //?}
        //? if <1.21.11
        /*net.minecraft.Util.getPlatform().openPath(path);*/
    }

    /** The screen that is open, or null. */
    public static Screen screen() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.screen();
        *///?} else
        return Minecraft.getInstance().screen;
    }

    /** F1: everything but the world hidden. */
    public static boolean hudHidden() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.hud.isHidden();
        *///?} else
        return Minecraft.getInstance().options.hideGui;
    }

    public static void rotate(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionf q) {
        //? if >=26.3 {
        /*pose.mulPose(new org.joml.Matrix4f().rotation(q));
        *///?} else
        pose.mulPose(q);
    }

    public static Camera camera() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainCamera();
        *///?} else
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static Vec3 cameraPos(Camera camera) {
        //? if >=1.21.6 {
        return camera.position();
        //?} else
        /*return camera.getPosition();*/
    }

    public static float cameraYaw(Camera camera) {
        //? if >=1.21.11 {
        return camera.yRot();
        //?} else
        /*return camera.getYRot();*/
    }

    public static float cameraPitch(Camera camera) {
        //? if >=1.21.11 {
        return camera.xRot();
        //?} else
        /*return camera.getXRot();*/
    }

    /** Time of day in ticks (0 = sunrise, 6000 = noon), counted up without wrapping. */
    public static long dayTime(net.minecraft.world.level.Level level) {
        //? if >=26.1 {
        /*return level.getOverworldClockTime();
        *///?} else
        return level.getDayTime();
    }

    /** Style with a font: "default", "uniform" (vanilla) or a namespaced id like "vibeylyrics:bebas". */
    public static Style font(Style style, String font) {
        if (font == null || "default".equals(font)) return style;
        int colon = font.indexOf(':');
        String ns = colon < 0 ? "minecraft" : font.substring(0, colon);
        String path = colon < 0 ? font : font.substring(colon + 1);
        //? if >=1.21.9 {
        return style.withFont(new FontDescription.Resource(id(ns, path)));
        //?} else
        /*return style.withFont(id(ns, path));*/
    }

    // -------------------------------------------------------------------------------------------- key mappings

    //? if >=1.21.9 {
    private static KeyMapping.Category category;
    //?}

    public static KeyMapping key(String name, int keyCode) {
        //? if >=1.21.9 {
        if (category == null) category = KeyMapping.Category.register(id(VibeyLyrics.MOD_ID, "main"));
        KeyMapping mapping = new KeyMapping(name, keyCode, category);
        //?} else
        /*KeyMapping mapping = new KeyMapping(name, keyCode, "key.categories.vibeylyrics");*/
        //? if >=26.1 {
        /*return KeyMappingHelper.registerKeyMapping(mapping);
        *///?} else
        return KeyBindingHelper.registerKeyBinding(mapping);
    }
}
