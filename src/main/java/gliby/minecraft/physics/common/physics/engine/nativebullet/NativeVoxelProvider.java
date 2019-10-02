package gliby.minecraft.physics.common.physics.engine.nativebullet;

import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btVoxelContentProvider;
import com.badlogic.gdx.physics.bullet.collision.btVoxelInfo;
import gliby.minecraft.physics.Physics;
import gliby.minecraft.physics.common.physics.PhysicsWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class NativeVoxelProvider extends btVoxelContentProvider {

    private World world;
    private Physics physics;
    private PhysicsWorld physicsWorld;
    private btVoxelInfo info;

    NativeVoxelProvider(btVoxelInfo info, final World world, PhysicsWorld physicsWorld, Physics physics) {
        this.world = world;
        this.physicsWorld = physicsWorld;
        this.physics = physics;
        this.info = info;
    }

    @Override
    public btVoxelInfo getVoxel(int x, int y, int z) {
        if (!world.playerEntities.isEmpty()) {
            final BlockPos blockPosition = new BlockPos(x, y, z);
            final IBlockState state = world.getBlockState(blockPosition);
            info.setTracable(false);
            info.setBlocking(state.getBlock().getMaterial(state).isSolid());
            info.setCollisionShape((btCollisionShape) physicsWorld.getBlockCache()
                    .getShape(world, blockPosition, state).getCollisionShape());
            info.setFriction((1 - state.getBlock().slipperiness) * 5);
        }
        return info;

    }

}
