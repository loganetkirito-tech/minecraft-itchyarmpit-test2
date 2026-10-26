package fr.twmtw.pillars;

import com.mojang.serialization.Codec;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Independently developed, Minecraft 1.18.2 sea columns.
 * V8 focuses on preserving the cylindrical silhouette while adding softer surface noise,
 * mossier surfaces, more varied tree scales and simple shrub cover.
 */
public class SeaPillarFeature extends Feature<NoneFeatureConfiguration> {
    public SeaPillarFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    private static final double TAU = Math.PI * 2.0;
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }

    private static final class Pillar {
        final int x, z, sea, floor, top;
        final double base, waveA, waveB, phaseA, phaseB, cyclesA, cyclesB, angle, faceting;
        final double noiseAngle1, noiseAngle2, noiseAmp, noiseFreq1, noiseFreq2, noisePhase1, noisePhase2;
        Pillar(int x, int z, int sea, int floor, int top, int radius, Random random) {
            this.x=x;this.z=z;this.sea=sea;this.floor=floor;this.top=top;
            base=radius;
            waveA=0.60+random.nextDouble()*Math.min(2.1, radius*0.17);
            waveB=0.20+random.nextDouble()*0.75;
            phaseA=random.nextDouble()*TAU;phaseB=random.nextDouble()*TAU;
            cyclesA=0.85+random.nextDouble()*0.65;cyclesB=1.4+random.nextDouble()*0.95;
            angle=random.nextDouble()*TAU;
            faceting=random.nextFloat()<0.12f ? 0.45+random.nextDouble()*0.2 : 0.0; // mostly round, some slightly geometric
            noiseAngle1=random.nextDouble()*TAU;
            noiseAngle2=random.nextDouble()*TAU;
            noiseAmp=0.38+random.nextDouble()*0.55;
            noiseFreq1=1.2+random.nextDouble()*1.3;
            noiseFreq2=2.0+random.nextDouble()*1.8;
            noisePhase1=random.nextDouble()*TAU;
            noisePhase2=random.nextDouble()*TAU;
        }
        double normalizedHeight(int y) {
            return clamp((y-floor)/(double)Math.max(1,top-floor),0,1);
        }
        double radiusAt(int y) {
            double t=normalizedHeight(y);
            double taper = (t > 0.82) ? (t - 0.82) / 0.18 : 0.0;
            double topSoftening = taper * (0.22 + base * 0.015);
            return Math.max(base*0.72,base
                + waveA*Math.sin(TAU*cyclesA*t+phaseA)
                + waveB*Math.sin(TAU*cyclesB*t+phaseB)
                - topSoftening);
        }
        double normalizedDistance(int dx,int dz) {
            double euclidean=Math.sqrt((double)dx*dx+(double)dz*dz);
            if(faceting==0.0) return euclidean;
            double a=Math.atan2(dz,dx)-angle;
            double octagon=(Math.max(Math.abs(Math.cos(a)),Math.abs(Math.sin(a)))+0.17)*euclidean;
            return euclidean*(1-faceting)+octagon*faceting;
        }
        double edgeNoise(int dx, int dz, int y) {
            double theta = Math.atan2(dz, dx);
            double t = normalizedHeight(y);
            return noiseAmp * (
                0.62*Math.sin(theta*noiseFreq1 + noiseAngle1 + t*3.1 + noisePhase1)
              + 0.38*Math.sin(theta*noiseFreq2 + noiseAngle2 + t*5.3 + noisePhase2)
            );
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

        // Slightly lower and less monumental than V7.
        int radius=5+random.nextInt(10); // 5..14
        int top=Math.min(world.getMaxBuildHeight()-38,sea+26+random.nextInt(53)); // +26..78
        Pillar primary=new Pillar(cx,cz,sea,floor,top,radius,random);

        // Rare paired formation with an above-water stone arch.
        boolean pair=random.nextInt(38)==0;
        Pillar secondary=null;
        if(pair){
            double angle=random.nextDouble()*TAU;
            int distance=radius+9+random.nextInt(5);
            int x2=cx+(int)Math.round(Math.cos(angle)*distance);
            int z2=cz+(int)Math.round(Math.sin(angle)*distance);
            int floor2=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x2,z2)-1;
            if(floor2<sea-5 && floor2>world.getMinBuildHeight()+4 &&
                    !world.getBlockState(new BlockPos(x2,floor2+1,z2)).getFluidState().isEmpty()) {
                secondary=new Pillar(x2,z2,sea,floor2,
                     Math.min(world.getMaxBuildHeight()-38,sea+24+random.nextInt(43)),
                     5+random.nextInt(6),random);
            }
        }

        boolean built=buildPillar(world,primary,random);
        if(secondary!=null){
            built |= buildPillar(world,secondary,random);
            int bridgeY=sea+10+random.nextInt(6);
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
        int reach=(int)Math.ceil(pillar.base+pillar.waveA+pillar.waveB+pillar.noiseAmp+3);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        boolean placed=false;
        for(int y=pillar.floor;y<=pillar.top;y++){
            double r=pillar.radiusAt(y);
            for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++){
                double d=pillar.normalizedDistance(dx,dz);
                double noisyR=r + pillar.edgeNoise(dx,dz,y);
                if(d>noisyR)continue;
                p.set(pillar.x+dx,y,pillar.z+dz);
                BlockState previous=world.getBlockState(p);
                if(!previous.isAir() && previous.getFluidState().isEmpty()
                    && !previous.getMaterial().isReplaceable())continue;
                double outerBand = noisyR - d;
                BlockState stone=Blocks.STONE.defaultBlockState();
                double t = pillar.normalizedHeight(y);
                if(outerBand < 1.3 && random.nextInt(10)==0){
                    stone = (t > 0.72 && random.nextBoolean()) ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                } else if (outerBand < 2.2 && t > 0.55 && random.nextInt(18)==0) {
                    stone = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                }
                world.setBlock(p,stone,2);placed=true;
            }
        }
        coatTop(world,pillar,random);
        decorateSides(world,pillar,random);
        decorateTop(world,pillar,random);
        return placed;
    }

    private void coatTop(WorldGenLevel world,Pillar pillar,Random random){
        int reach=(int)Math.ceil(pillar.base+pillar.waveA+pillar.waveB+pillar.noiseAmp+2);
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++){
            double noisyR=pillar.radiusAt(pillar.top)-0.6 + pillar.edgeNoise(dx,dz,pillar.top);
            if(pillar.normalizedDistance(dx,dz)>noisyR)continue;
            p.set(pillar.x+dx,pillar.top,pillar.z+dz);
            if(world.getBlockState(p).is(Blocks.STONE) || world.getBlockState(p).is(Blocks.MOSSY_COBBLESTONE)) {
                float roll=random.nextFloat();
                if(roll<0.46f) world.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
                else if(roll<0.76f) world.setBlock(p,Blocks.MOSS_BLOCK.defaultBlockState(),2);
            }
        }
    }

    private void decorateSides(WorldGenLevel world,Pillar pillar,Random random){
        int verticalStart = Math.max(pillar.floor + 6, pillar.sea - 3);
        int attempts = 14 + (int)Math.round(pillar.base * 1.8);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for(int i=0;i<attempts;i++){
            double ang = random.nextDouble()*TAU;
            int y = verticalStart + random.nextInt(Math.max(1, pillar.top - verticalStart - 3));
            double r = pillar.radiusAt(y) + pillar.edgeNoise((int)Math.round(Math.cos(ang)*pillar.base), (int)Math.round(Math.sin(ang)*pillar.base), y);
            int x = pillar.x + (int)Math.round(Math.cos(ang) * Math.max(2.0, r - 0.1));
            int z = pillar.z + (int)Math.round(Math.sin(ang) * Math.max(2.0, r - 0.1));
            p.set(x,y,z);
            BlockState here = world.getBlockState(p);
            if(!(here.is(Blocks.STONE) || here.is(Blocks.MOSSY_COBBLESTONE) || here.is(Blocks.MOSS_BLOCK))) continue;
            // Find an exposed face.
            Direction exposed = null;
            for(Direction dir : new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
                p.move(dir);
                boolean open = world.getBlockState(p).isAir() || !world.getBlockState(p).getFluidState().isEmpty();
                p.move(dir.getOpposite());
                if(open){ exposed = dir; break; }
            }
            if(exposed == null) continue;

            float patch = random.nextFloat();
            if(patch < 0.45f){
                // soft green moss patch across the exposed wall
                for(int oy=0; oy<1+random.nextInt(3); oy++){
                    for(int side=-1; side<=1; side++){
                        int px=x, pz=z;
                        if(exposed == Direction.NORTH || exposed == Direction.SOUTH) px += side;
                        else pz += side;
                        p.set(px, y+oy, pz);
                        if(world.getBlockState(p).is(Blocks.STONE) && random.nextFloat()<0.82f){
                            world.setBlock(p, random.nextBoolean()?Blocks.MOSSY_COBBLESTONE.defaultBlockState():Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                        }
                    }
                }
            }
            if(random.nextFloat() < 0.38f){
                // hanging vines on some exposed wall spots
                int vineLen = 2 + random.nextInt(8);
                BlockPos start = new BlockPos(x + exposed.getStepX(), y, z + exposed.getStepZ());
                if(world.getBlockState(start).isAir()){
                    for(int len=0; len<vineLen; len++){
                        BlockPos vp = start.below(len);
                        if(!world.getBlockState(vp).isAir()) break;
                        BlockState vine = Blocks.VINE.defaultBlockState();
                        if(exposed == Direction.NORTH) vine = vine.setValue(net.minecraft.world.level.block.VineBlock.SOUTH, true);
                        else if(exposed == Direction.SOUTH) vine = vine.setValue(net.minecraft.world.level.block.VineBlock.NORTH, true);
                        else if(exposed == Direction.WEST) vine = vine.setValue(net.minecraft.world.level.block.VineBlock.EAST, true);
                        else if(exposed == Direction.EAST) vine = vine.setValue(net.minecraft.world.level.block.VineBlock.WEST, true);
                        world.setBlock(vp, vine, 2);
                    }
                }
            }
        }
    }

    private void decorateTop(WorldGenLevel world,Pillar pillar,Random random){
        addShrubs(world, pillar, random);
        addTrees(world, pillar, random);
    }

    private void addTrees(WorldGenLevel world,Pillar pillar,Random random){
        int desired = pillar.base <= 6 ? random.nextInt(2)
                    : pillar.base <= 9 ? 1 + random.nextInt(2)
                    : pillar.base <= 12 ? 2 + random.nextInt(3)
                    : 3 + random.nextInt(3);
        if(random.nextInt(9)==0) desired = Math.max(0, desired-1);

        int successes=0,tries=desired*12;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();
        for(int attempt=0;attempt<tries && successes<desired;attempt++){
            int dx=random.nextInt((int)(pillar.base*2+3))-(int)(pillar.base+1);
            int dz=random.nextInt((int)(pillar.base*2+3))-(int)(pillar.base+1);
            if(pillar.normalizedDistance(dx,dz)>pillar.radiusAt(pillar.top)-2.6)continue;
            int x=pillar.x+dx,z=pillar.z+dz,y=pillar.top;
            p.set(x,y,z);
            if(!world.getBlockState(p).is(Blocks.GRASS_BLOCK) && !world.getBlockState(p).is(Blocks.MOSS_BLOCK))continue;
            int category=random.nextInt(100);
            int height = category < 28 ? 6 + random.nextInt(3)
                       : category < 68 ? 8 + random.nextInt(4)
                       : category < 90 ? 11 + random.nextInt(4)
                       : 15 + random.nextInt(5);
            boolean free=true;
            for(int t=1;t<=height+4;t++){
                p.set(x,y+t,z);
                if(!world.getBlockState(p).isAir()){free=false;break;}
            }
            if(!free)continue;
            for(int t=1;t<=height;t++){
                p.set(x,y+t,z);world.setBlock(p,Blocks.BIRCH_LOG.defaultBlockState(),2);
            }
            generateBirchCrown(world, x, y+height, z, random, height);
            successes++;
        }
    }

    private void generateBirchCrown(WorldGenLevel world, int x, int crownY, int z, Random random, int height){
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int crownHeight = height >= 14 ? 4 : (height >= 10 ? 3 : 2);
        int baseRadius = height >= 14 ? 3 : (height >= 10 ? 3 : 2);
        for(int oy = -crownHeight; oy <= crownHeight; oy++){
            int rr = baseRadius - (Math.abs(oy) >= crownHeight ? 1 : 0);
            if(oy == 0 && height >= 10) rr += 1;
            for(int ax=-rr-1; ax<=rr+1; ax++) for(int az=-rr-1; az<=rr+1; az++){
                double shape = (ax*ax)/(double)Math.max(1, rr*rr+1) + (az*az)/(double)Math.max(1, rr*rr+1);
                double threshold = 1.0 + (random.nextDouble()*0.34) - (Math.abs(oy)*0.08);
                if(shape > threshold) continue;
                // avoid a perfectly symmetric sphere
                if(Math.abs(ax)==rr+1 || Math.abs(az)==rr+1){
                    if(random.nextFloat() < 0.6f) continue;
                }
                p.set(x+ax, crownY+oy, z+az);
                if(world.getBlockState(p).isAir()) world.setBlock(p, Blocks.BIRCH_LEAVES.defaultBlockState(), 2);
            }
        }
        if(random.nextFloat() < 0.55f){
            // small side tuft
            int sx = x + (random.nextBoolean()?1:-1) * (1 + random.nextInt(2));
            int sz = z + (random.nextBoolean()?1:-1) * random.nextInt(2);
            for(int ax=-1; ax<=1; ax++) for(int az=-1; az<=1; az++){
                p.set(sx+ax, crownY-1+random.nextInt(2), sz+az);
                if(world.getBlockState(p).isAir() && random.nextFloat() < 0.8f) world.setBlock(p, Blocks.BIRCH_LEAVES.defaultBlockState(), 2);
            }
        }
    }

    private void addShrubs(WorldGenLevel world, Pillar pillar, Random random){
        int shrubAttempts = pillar.base <= 7 ? 2 + random.nextInt(3) : 4 + random.nextInt(6);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for(int i=0; i<shrubAttempts; i++){
            int dx=random.nextInt((int)(pillar.base*2+3))-(int)(pillar.base+1);
            int dz=random.nextInt((int)(pillar.base*2+3))-(int)(pillar.base+1);
            if(pillar.normalizedDistance(dx,dz)>pillar.radiusAt(pillar.top)-1.8) continue;
            int x=pillar.x+dx,z=pillar.z+dz,y=pillar.top;
            p.set(x,y,z);
            if(!world.getBlockState(p).is(Blocks.GRASS_BLOCK) && !world.getBlockState(p).is(Blocks.MOSS_BLOCK)) continue;

            // little floor plants
            if(random.nextFloat() < 0.45f){
                p.set(x, y+1, z);
                if(world.getBlockState(p).isAir()){
                    BlockState plant = random.nextBoolean() ? Blocks.FERN.defaultBlockState() : Blocks.GRASS.defaultBlockState();
                    world.setBlock(p, plant, 2);
                }
            }

            if(random.nextFloat() < 0.80f){
                int bushH = random.nextFloat() < 0.22f ? 2 : 1;
                for(int oy=0; oy<bushH; oy++){
                    int rr = oy==0 ? 1 : 0;
                    for(int ax=-rr; ax<=rr; ax++) for(int az=-rr; az<=rr; az++){
                        if(Math.abs(ax)+Math.abs(az) > 2) continue;
                        p.set(x+ax, y+1+oy, z+az);
                        if(world.getBlockState(p).isAir() && random.nextFloat() < (oy==0 ? 0.85f : 0.55f)){
                            world.setBlock(p, Blocks.AZALEA_LEAVES.defaultBlockState(), 2);
                        }
                    }
                }
                if(random.nextFloat() < 0.35f){
                    p.set(x, y+1, z);
                    if(world.getBlockState(p).isAir()) world.setBlock(p, Blocks.AZALEA.defaultBlockState(), 2);
                }
            }
        }
    }
}
