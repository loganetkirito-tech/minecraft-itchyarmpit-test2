package fr.twmtw.pillars;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(TWMTWPillars.MODID)
public class TWMTWPillars {
    public static final String MODID = "twmtw_pillars";
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, MODID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> SEA_PILLAR = FEATURES.register("sea_pillar", () -> new SeaPillarFeature(NoneFeatureConfiguration.CODEC));

    public TWMTWPillars() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        FEATURES.register(bus);
    }
}
