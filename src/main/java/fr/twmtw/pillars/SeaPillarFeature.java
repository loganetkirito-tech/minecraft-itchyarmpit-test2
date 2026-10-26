package fr.twmtw.pillars;

import com.mojang.serialization.Codec;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Independently developed, Minecraft 1.18.2 sea columns.
 * Primarily upright cylinders with smoothly undulating radius, occasional low arches.
 * Nothing here overrides the global Tectonic density functions.
 */
public class SeaPillarFeature extends Feature<NoneFeatureConfiguration> {
    public SeaPillarFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    private static final double TAU = Math.PI * 2.0;
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }

    private static final class Pillar {
        final int x, z, sea, floor, top;
        final double base, waveA, waveB, phaseA, phaseB, cyclesA, cyclesB, angle, faceting;
        Pillar(int x, int z, int sea, int floor, int top, int radius, Random random) {
            this.x=x;this.z=z;this.sea=sea;this.floor=floor;this.top=top;
            base=radius;
            waveA=0.65+random.nextDouble()*Math.min(2.5, radius*0.19);
            waveB=0.25+random.nextDouble()*0.9;
            phaseA=random.nextDouble()*TAU;phaseB=random.nextDouble()*TAU;
            cyclesA=0.9+random.nextDouble()*0.7;cyclesB=1.6+random.nextDouble()*1.1;
            angle=random.nextDouble()*TAU;
            faceting=random.nextFloat()<0.10f ? 0.65 : 0.0; // minority angular variants
        }
        double radiusAt(int y) {
            double t=clamp((y-floor)/(double)Math.max(1,top-floor),0,1);
            return Math.max(base*0.69,base
                + waveA*Math.sin(TAU*cyclesA*t+phaseA)
                + waveB*Math.sin(TAU*cyclesB*t+phaseB));
        }
        double normalizedDistance(int dx,int dz) {
            double euclidean=Math.sqrt((double)dx*dx+(double)dz*dz);
            if(faceting==0.0) return euclidean;
            double a=Math.atan2(dz,dx)-angle;
            double octagon=(Math.max(Math.abs(Math.cos(a)),Math.abs(Math.sin(a)))+0.17)*euclidean;
            return euclidean*(1-faceting)+octagon*faceting;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel world=context.level();
        Random random=context.random();
        BlockPos origin=context.origin();
        int sea=world.getSeaLevel();
        int cx=origin.getX(),cz=origin.getZ();
        int floor=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,cx,cz)-1;
        if(floor>=sea-5 || floor<=world.getMinBuildHeight()+4) return false;
        if(world.getBlockState(new BlockPos(cx,floor+1,cz)).getFluidState().isEmpty()) return false;

        // Ordinary pillars: 5..16 radius, 35..100 blocks above water.
        int radius=5+random.nextInt(12);
        int top=Math.min(world.getMaxBuildHeight()-38,sea+35+random.nextInt(66));
        Pillar primary=new Pillar(cx,cz,sea,floor,top,radius,random);

        // All formation geometry stays circular and largely vertical, no mushroom caps.
        // At most one carefully controlled second column; optional rock arch above sea.
        boolean pair=random.nextInt(34)==0; // rare per placed pillar
        Pillar secondary=null;
        if(pair){
            double angle=random.nextDouble()*TAU;
            int distance=radius+9+random.nextInt(6);
            int x2=cx+(int)Math.round(Math.cos(angle)*distance);
            int z2=cz+(int)Math.round(Math.sin(angle)*distance);
            int floor2=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x2,z2)-1;
            if(floor2<sea-5 && floor2>world.getMinBuildHeight()+4 &&
                    !world.getBlockState(new BlockPos(x2,floor2+1,z2)).getFluidState().isEmpty()) {
                secondary=new Pillar(x2,z2,sea,floor2,
                     Math.min(world.getMaxBuildHeight()-38,sea+34+random.nextInt(51)),
                     5+random.nextInt(7),random);
            }
        }

        boolean built=buildPillar(world,primary,random);
        if(secondary!=null){
            built |= buildPillar(world,secondary,random);
            // Bridge spans 10..16 blocks above water; hole underneath remains open.
            int bridgeY=sea+11+random.nextInt(6);
            int dx=secondary.x-primary.x, dz=secondary.z-primary.z;
            int steps=Math.max(Math.abs(dx),Math.abs(dz));
            for(int i=0;i<=steps;i++){
                double t=i/(double)Math.max(1,steps);
                int x=(int)Math.round(primary.x+dx*t),z=(int)Math.round(primary.z+dz*t);
                for(int ox=-2;ox<=2;ox++)for(int oz=-2;oz<=2;oz++)for(int oy=0;oy<4;oy++){
                    if(ox*ox+oz*oz>5)continue;
                    BlockPos p=new BlockPos(x+ox,bridgeY+oy,z+oz);
                    if(world.getBlockState(p).isAir() || !world.getBlockState(p).getFluidState().isEmpty())
                        world.setBlock(p,Blocks.STONE.defaultBlockState(),2);
                }
            }
        }
        return built;
    }

    private boolean buildPillar(WorldGenLevel world,Pillar pillar,Random random){
        int reach=(int)Math.ceil(pillar.base+pillar.waveA+pillar.waveB+2);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        boolean placed=false;
        // Horizontal cross-sections, with a radius depending ONLY on elevation.
        for(int y=pillar.floor;y<=pillar.top;y++){
            double r=pillar.radiusAt(y);
            for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++){
                if(pillar.normalizedDistance(dx,dz)>r)continue;
                p.set(pillar.x+dx,y,pillar.z+dz);
                BlockState previous=world.getBlockState(p);
                if(!previous.isAir() && previous.getFluidState().isEmpty()
                    && !previous.getMaterial().isReplaceable())continue;
                double d=pillar.normalizedDistance(dx,dz);
                BlockState stone=Blocks.STONE.defaultBlockState();
                if(d>=r-1.6 && random.nextInt(12)==0)stone=Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                world.setBlock(p,stone,2);placed=true;
            }
        }
        // Surface is almost flat and follows the same cylinder radius, not a projecting disc.
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++){
            if(pillar.normalizedDistance(dx,dz)>pillar.radiusAt(pillar.top)-1)continue;
            p.set(pillar.x+dx,pillar.top,pillar.z+dz);
            if(world.getBlockState(p).is(Blocks.STONE) && random.nextFloat()<0.72f)
                world.setBlock(p,random.nextFloat()<0.35f?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.MOSS_BLOCK.defaultBlockState(),2);
        }
        decorateTop(world,pillar,random);
        return placed;
    }

    private void decorateTop(WorldGenLevel world,Pillar pillar,Random random){
        int desired=pillar.base<=7?(random.nextInt(3)==0?1:0)
                  :pillar.base<=11?1+random.nextInt(3):2+random.nextInt(4);
        if(random.nextInt(7)==0)desired=0;
        int successes=0,tries=desired*10;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int attempt=0;attempt<tries && successes<desired;attempt++){
            int dx=random.nextInt((int)(pillar.base*2+1))-(int)pillar.base;
            int dz=random.nextInt((int)(pillar.base*2+1))-(int)pillar.base;
            if(pillar.normalizedDistance(dx,dz)>pillar.radiusAt(pillar.top)-3.5)continue;
            int x=pillar.x+dx,z=pillar.z+dz,y=pillar.top;
            p.set(x,y,z);
            if(!world.getBlockState(p).is(Blocks.GRASS_BLOCK) && !world.getBlockState(p).is(Blocks.MOSS_BLOCK))continue;
            int height=15+random.nextInt(14);
            boolean free=true;
            for(int t=1;t<=height+4;t++){
                p.set(x,y+t,z);
                if(!world.getBlockState(p).isAir()){free=false;break;}
            }
            if(!free)continue;
            for(int t=1;t<=height;t++){
                p.set(x,y+t,z);world.setBlock(p,Blocks.BIRCH_LOG.defaultBlockState(),2);
            }
            int crown=y+height;
            for(int oy=-3;oy<=3;oy++){
                int rr=(Math.abs(oy)==3)?2:((Math.abs(oy)==2)?3:4);
                for(int ax=-rr;ax<=rr;ax++)for(int az=-rr;az<=rr;az++){
                    if(ax*ax+az*az>rr*rr+random.nextInt(5)-2)continue;
                    p.set(x+ax,crown+oy,z+az);
                    if(world.getBlockState(p).isAir())
                        world.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState(),2);
                }
            }
            successes++;
        }
    }
}
