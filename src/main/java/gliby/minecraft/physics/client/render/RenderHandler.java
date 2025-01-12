package gliby.minecraft.physics.client.render;

import gliby.minecraft.gman.client.render.ItemRendererManager;
import gliby.minecraft.physics.Physics;
import gliby.minecraft.physics.client.render.entity.RenderPhysicsBlock;
import gliby.minecraft.physics.client.render.entity.RenderToolGunBeam;
import gliby.minecraft.physics.client.render.items.RenderItemPhysicsGun;
import gliby.minecraft.physics.client.render.items.RenderItemToolGun;
import gliby.minecraft.physics.client.render.lighting.AtomicStrykerLight;
import gliby.minecraft.physics.client.render.lighting.IDynamicLightHandler;
import gliby.minecraft.physics.client.render.lighting.NullLight;
import gliby.minecraft.physics.client.render.lighting.SELLight;
import gliby.minecraft.physics.common.entity.EntityPhysicsBlock;
import gliby.minecraft.physics.common.entity.EntityToolGunBeam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class RenderHandler {

    private static Map<String, Integer> physicsGunColors = new HashMap<String, Integer>();
    private static IDynamicLightHandler lightHandler;
    private Physics physics;

    static {
        physicsGunColors.put("a6a677aa-2589-4988-b686-59afd7c170f2", 0xFF87FF77);
        physicsGunColors.put("c4924b85-b249-4264-9d93-b54b1137d629", 0xFFF81A1A);
        physicsGunColors.put("1d7df249-ed29-49ca-a448-209264346386", 0xFFED7C12); // Reed
        physicsGunColors.put("04372b9e-4e31-4a69-9660-4ac1cc2dbdb4", 0xFFD22828); // ZeAmateis
    }

    public RenderHandler(Physics physics, boolean dynamicLightsPresent) {
        this.physics = physics;
    }

    /**
     * @return the lightHandler
     */
    public static IDynamicLightHandler getLightHandler() {
        return lightHandler;
    }

    /**
     * @return the physicsGunColors
     */
    public Map<String, Integer> getPhysicsGunColors() {
        return physicsGunColors;
    }

    /**
     * Called on Forge's init event, found inside @Mod interface.
     *
     * @param event
     */
    public void preInit(FMLPreInitializationEvent event) {
//        MinecraftForge.EVENT_BUS.register(new RenderAdditionalPlayer());
        Minecraft mc = Minecraft.getMinecraft();

        itemRendererManager = new ItemRendererManager();
        MinecraftForge.EVENT_BUS.register(itemRendererManager);

        ItemRendererManager itemRenderManager = getItemRendererManager();
        // Create fake-model resource, doesn't need any file to function.
        // Create actual RawItemRenderer instance, simply a class that
        // "extends RawItemRenderer".
        // Register and bind to item!
        itemRenderManager.registerItemRenderer(physics.getGameManager().itemPhysicsGun,
                new RenderItemPhysicsGun(this, new ModelResourceLocation(Physics.ID, "physicsgun")));
        itemRenderManager.registerItemRenderer(physics.getGameManager().itemToolgun,
                new RenderItemToolGun(new ModelResourceLocation(Physics.ID, "toolgun")));

    }

    public void init(FMLInitializationEvent event) {
        if (Loader.isModLoaded("dynamiclights")) {
            lightHandler = new AtomicStrykerLight();
            Physics.getLogger().info("DynamicLights by AtomicStryker has been found, enabling dynamic light creation!");
        } else if (Loader.isModLoaded("sel")) {
            lightHandler = new SELLight();
            Physics.getLogger().info("SEL by lakmoore has been found, enabling smooth dynamic light creation!");
        } else {
            lightHandler = new NullLight();
        }

        Minecraft mc = Minecraft.getMinecraft();
        RenderingRegistry.registerEntityRenderingHandler(EntityPhysicsBlock.class,
                new RenderPhysicsBlock(this, mc.getRenderManager()));
        // RenderingRegistry.registerEntityRenderingHandler(EntityPhysicsRagdoll.class,
        // new RenderPhysicsRagdoll(mc.getRenderManager()));
//        RenderingRegistry.registerEntityRenderingHandler(EntityPhysicsModelPart.class,
//                new RenderPhysicsModelPart(this, mc.getRenderManager()));
        RenderingRegistry.registerEntityRenderingHandler(EntityToolGunBeam.class, new RenderToolGunBeam(this, mc.getRenderManager()));
    }

    @SideOnly(Side.CLIENT)
    ItemRendererManager itemRendererManager;

    @SideOnly(Side.CLIENT)
    public ItemRendererManager getItemRendererManager() {
        return itemRendererManager;
    }


}
