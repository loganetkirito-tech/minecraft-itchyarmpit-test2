package fr.twmtw.pillars;

import com.mojang.serialization.Codec;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Column-by-column pillar generation inspired by BWG's PillarFeature geometry.
 * Independently written 1.18.2 implementation, not an exact source port.
 */
public class SeaPillarFeature extends Feature<NoneFeatureConfiguration> {
    public SeaPillarFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
    private static double hash(int x, int y, int z, int salt) {
        long n = x * 374761393L + y * 668265263L + z * 1442695041L + salt * 1274126177L;
        n = (n ^ (n >>> 13)) * 1274126177L;
        n ^= (n >>> 16);
        return ((n & 0x7fffffffL) / 1073741823.5) - 1;
    }
    // Smooth value noise, sampled at multiple spatial scales.
    private static double noise(double x, double y, double z, int salt) {
        int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
        double fx = smooth(x - ix), fy = smooth(y - iy), fz = smooth(z - iz);
        double a = mix(hash(ix,iy,iz,salt),hash(ix+1,iy,iz,salt),fx);
        double b = mix(hash(ix,iy,iz+1,salt),hash(ix+1,iy,iz+1,salt),fx);
        double c = mix(hash(ix,iy+1,iz,salt),hash(ix+1,iy+1,iz,salt),fx);
        double d = mix(hash(ix,iy+1,iz+1,salt),hash(ix+1,iy+1,iz+1,salt),fx);
        return mix(mix(a,b,fz),mix(c,d,fz),fy);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel world = ctx.level();
        Random random = ctx.random();
        BlockPos origin = ctx.origin();
        int cx=origin.getX(), cz=origin.getZ(), sea=world.getSeaLevel();
        int centerFloor=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,cx,cz)-1;
        if (centerFloor >= sea-4 || centerFloor <= world.getMinBuildHeight()+6) return false;
        if (world.getBlockState(new BlockPos(cx,centerFloor+1,cz)).getFluidState().isEmpty()) return false;

        int radius=8+random.nextInt(13);              // original TWMTW range 7..20
        int heightAbove=28+random.nextInt(62);        // deliberate ocean-depth correction
        int targetTop=Math.min(world.getMaxBuildHeight()-38,sea+heightAbove);
        int salt=random.nextInt(1000000);
        double freq=0.022+random.nextDouble()*0.045;
        double smallScale=0.18+random.nextDouble()*0.17;
        double phase=random.nextDouble()*6.283185307;
        int reach=radius+7, written=0;
        BlockPos.MutableBlockPos p=new BlockPos.MutableBlockPos();

        // Determine each (x,z) column separately. BWG similarly reads the
        // OCEAN_FLOOR_WG height independently for each candidate column.
        for(int dx=-reach;dx<=reach;dx++) for(int dz=-reach;dz<=reach;dz++) {
            int x=cx+dx,z=cz+dz;
            int floor=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
            double theta=Math.atan2(dz,dx);
            double d=Math.sqrt(dx*dx+dz*dz);
            double longWave=noise(x*0.055,0,z*0.055,salt+3);
            double outline=radius*(1+0.23*Math.sin(theta*3+phase)
                                    +0.12*Math.cos(theta*6-phase)
                                    +0.13*longWave);
            if (d>outline) continue;
            // Contours change by height through low-frequency 3D noise,
            // not by forcing a constant-radius cylinder or a circular cap.
            double topIrregularity=3.0*noise(x*0.075,0,z*0.075,salt+71);
            int top=Math.min(world.getMaxBuildHeight()-35,
                             targetTop+(int)Math.round(topIrregularity));
            for(int y=Math.max(world.getMinBuildHeight()+2,floor-4);y<=top;y++){
                double n=noise(x*freq,y*freq,z*freq,salt);
                double n2=noise(x*freq*2.2,y*freq*1.4,z*freq*2.2,salt+13);
                double radialAllowance=outline*(0.76+smallScale*(n+1)*0.5)+n2*2.4;
                if(d>radialAllowance) continue;
                p.set(x,y,z);
                BlockState old=world.getBlockState(p);
                if(!old.isAir() && old.getFluidState().isEmpty()
                    && !old.getMaterial().isReplaceable()) continue;
                boolean edge=d>radialAllowance-2.4;
                BlockState block=Blocks.STONE.defaultBlockState();
                if(edge && random.nextInt(13)==0) block=Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                else if(edge && random.nextInt(20)==0) block=Blocks.MOSS_BLOCK.defaultBlockState();
                world.setBlock(p,block,2);
                written++;
            }
            // Only the top surface of a completed column gets vegetation.
            p.set(x,top,z);
            BlockState b=world.getBlockState(p);
            if ((b.is(Blocks.STONE)||b.is(Blocks.MOSSY_COBBLESTONE)) &&
                 random.nextFloat()<0.58f) {
                world.setBlock(p,random.nextInt(4)==0
                    ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.MOSS_BLOCK.defaultBlockState(),2);
            }
        }
        if(written==0) return false;

        // A few trees per pillar, on verified high solid summits, never on water.
        int attempts=(radius>=14?5:3);
        if(random.nextInt(5)==0) attempts=0; // some bare pillars
        for(int i=0;i<attempts;i++){
            int dx=random.nextInt(radius*2-4)-(radius-2);
            int dz=random.nextInt(radius*2-4)-(radius-2);
            int x=cx+dx,z=cz+dz;
            int ground=world.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z)-1;
            if(ground<sea+16 || ground>world.getMaxBuildHeight()-36) continue;
            p.set(x,ground,z);
            BlockState below=world.getBlockState(p);
            if(!below.is(Blocks.MOSS_BLOCK) && !below.is(Blocks.GRASS_BLOCK)) continue;
            int treeH=17+random.nextInt(15);
            boolean clear=true;
            for(int y=ground+1;y<=ground+treeH+4;y++){
                p.set(x,y,z);
                if(!world.getBlockState(p).isAir()){clear=false;break;}
            }
            if(!clear) continue;
            for(int y=ground+1;y<=ground+treeH;y++){
                p.set(x,y,z);
                world.setBlock(p,Blocks.BIRCH_LOG.defaultBlockState(),2);
            }
            int crown=ground+treeH-1;
            for(int oy=-3;oy<=3;oy++){
                int r=(oy==-3||oy==3)?2:(oy==-2||oy==2?4:5);
                for(int ax=-r;ax<=r;ax++) for(int az=-r;az<=r;az++){
                    double dist=ax*ax+az*az;
                    if(dist>r*r+noise((x+ax)*0.4,crown+oy,(z+az)*0.4,salt+25)*5) continue;
                    p.set(x+ax,crown+oy,z+az);
                    if(world.getBlockState(p).isAir())
                        world.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState(),2);
                }
            }
        }
        return true;
    }
}
