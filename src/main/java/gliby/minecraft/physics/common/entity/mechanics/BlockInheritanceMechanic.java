package gliby.minecraft.physics.common.entity.mechanics;

import gliby.minecraft.physics.common.entity.EnumRigidBodyProperty;
import gliby.minecraft.physics.common.entity.IEntityPhysics;
import gliby.minecraft.physics.common.physics.PhysicsWorld;
import gliby.minecraft.physics.common.physics.engine.IRigidBody;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;

import javax.vecmath.Vector3f;
import java.util.List;

/**
 * Block Inheritance is a mechanic that aims to replicate the original blocks principles.
 * For example the Cactus Block deals damage when you collide with it,
 * let's try to inherit that mechanic from the game and apply to our Physics Blocks.
 */
public class BlockInheritanceMechanic extends RigidBodyMechanic {

    @SuppressWarnings("unchecked")
    @Override
    public void update(IRigidBody rigidBody, PhysicsWorld physicsWorld, Entity entity, Side side) {
        IBlockState blockState;
        if (side.isServer()) {
            if ((blockState = (IBlockState) rigidBody.getProperties().get(EnumRigidBodyProperty.BLOCKSTATE.getName())) != null) {
                Vector3f bbMin = new Vector3f(), bbMax = new Vector3f();
                rigidBody.getAabb(bbMin, bbMax);
                AxisAlignedBB bb = new AxisAlignedBB(bbMin.x, bbMin.y, bbMin.z, bbMax.x, bbMax.y, bbMax.z)
                        .offset(0.5f, 0.5f, 0.5f);
                List<Entity> entitesWithin = rigidBody.getOwner().getEntityWorld().getEntitiesWithinAABB(Entity.class,
                        bb, IEntityPhysics.NOT_PHYSICS_OBJECT);
                for (int i = 0; i < entitesWithin.size(); i++) {
                    Entity collidedEntity = entitesWithin.get(i);
                    Block block = blockState.getBlock();
                    // collidedEntity.attackEntityFrom(DamageSource.cactus, 1F);
                    BlockPos.PooledMutableBlockPos pos = BlockPos.PooledMutableBlockPos.retain(rigidBody.getOwner().posX, rigidBody.getOwner().posY, rigidBody.getOwner().posZ);
                    block.onEntityCollidedWithBlock(rigidBody.getOwner().getEntityWorld(), pos, blockState,
                            collidedEntity);
                    pos.release();
//                    block.onEntityCollidedWithBlock(rigidBody.getOwner().getEntityWorld(), pos, collidedEntity);
                }
            }
        }
    }

}
