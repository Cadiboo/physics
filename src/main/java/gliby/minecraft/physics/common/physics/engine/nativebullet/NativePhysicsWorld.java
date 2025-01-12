package gliby.minecraft.physics.common.physics.engine.nativebullet;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.BulletBase;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody.btRigidBodyConstructionInfo;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.bulletphysicsx.linearmath.Transform;
import gliby.minecraft.physics.Physics;
import gliby.minecraft.physics.client.render.VecUtility;
import gliby.minecraft.physics.common.physics.IPhysicsWorldConfiguration;
import gliby.minecraft.physics.common.physics.PhysicsOverworld;
import gliby.minecraft.physics.common.physics.PhysicsWorld;
import gliby.minecraft.physics.common.physics.engine.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */

// IVector and
// IQuaternion, IMatrix, replace with custom vector stuff or MC Vec3.
public class NativePhysicsWorld extends PhysicsWorld {

    static {
        Bullet.init();
    }

    private btDiscreteDynamicsWorld dynamicsWorld;
    private List<IRigidBody> rigidBodies;

    private btDbvtBroadphase broadphase;
    private btCollisionConfiguration collisionConfiguration;
    private btCollisionDispatcher collisionDispatcher;
    private btVoxelShape voxelShape;
    private btCollisionObject voxelBody;
    private btVoxelInfo voxelInfo;
    private NativeVoxelProvider voxelProvider;

    private btSequentialImpulseConstraintSolver sequentialSolver;

    public NativePhysicsWorld(Physics physics, PhysicsOverworld physicsOverworld,
                              IPhysicsWorldConfiguration physicsConfig) {
        super(physicsConfig);
    }


    @Override
    public void create() {
        rigidBodies = new ArrayList<IRigidBody>();

        broadphase = new btDbvtBroadphase();
        collisionConfiguration = new btDefaultCollisionConfiguration();
        collisionDispatcher = new btCollisionDispatcher(collisionConfiguration);

        dynamicsWorld = new btDiscreteDynamicsWorld(collisionDispatcher, broadphase,
                sequentialSolver = new btSequentialImpulseConstraintSolver(), collisionConfiguration);
        dynamicsWorld.setGravity(VecUtility.toVector3(getPhysicsConfiguration().getRegularGravity()));

        voxelShape = new btVoxelShape(
                voxelProvider = new NativeVoxelProvider(voxelInfo = new btVoxelInfo(), getPhysicsConfiguration().getWorld(), this),
                new Vector3(-Integer.MAX_VALUE, -Integer.MAX_VALUE, -Integer.MAX_VALUE),
                new Vector3(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        voxelBody = new btCollisionObject();
        voxelBody.setCollisionShape(voxelShape);
        voxelBody.setCollisionFlags(btCollisionObject.CollisionFlags.CF_STATIC_OBJECT | voxelBody.getCollisionFlags());
        dynamicsWorld.addCollisionObject(voxelBody);
        super.create();
    }


    @Override
    protected void update() {
        if (dynamicsWorld != null && !dynamicsWorld.isDisposed() && voxelBody != null && !voxelBody.isDisposed()) {
            dynamicsWorld.stepSimulation(1, getMaxSubstep());
            super.update();
        }
    }

    @Override
    public IRigidBody createRigidBody(Entity owner, Transform transform, float mass, ICollisionShape shape) {
        Vector3 localInertia = new Vector3();
        if (mass != 0) {
            shape.calculateLocalInertia(mass, localInertia);
        }
        btDefaultMotionState motionState = new btDefaultMotionState(VecUtility.toMatrix4(transform));
        btRigidBodyConstructionInfo constructionInfo = new btRigidBodyConstructionInfo(mass, motionState,
                (btCollisionShape) shape.getCollisionShape(), localInertia);

        btRigidBody body = new btRigidBody(constructionInfo);

        return new NativeRigidBody(this, body, owner);
    }

    @Override
    public IRigidBody createInertialessRigidbody(Entity owner, Transform transform, float mass,
                                                 ICollisionShape shape) {
        btDefaultMotionState motionState = new btDefaultMotionState(VecUtility.toMatrix4(transform));
        btRigidBodyConstructionInfo constructionInfo = new btRigidBodyConstructionInfo(mass, motionState,
                (btCollisionShape) shape.getCollisionShape());

        btRigidBody body = new btRigidBody(constructionInfo);

        return new NativeRigidBody(this, body, owner);
    }

    @Override
    public ICollisionShape createBoxShape(Vector3f extents) {
        btBoxShape nativeBox = new btBoxShape(VecUtility.toVector3(extents));
        return new NativeCollisionShape(this, nativeBox);
    }

    @Override
    public IRayResult createClosestRayResultCallback(Vector3f rayFromWorld, Vector3f rayToWorld) {
        ClosestRayResultCallback nativeCallback;
        return new NativeClosestRayResultCallback(
                nativeCallback = new ClosestRayResultCallback(VecUtility.toVector3(rayFromWorld), VecUtility.toVector3(rayToWorld)));
    }

    @Override
    public void addRigidBody(final IRigidBody body) {
        btRigidBody nativeBody = (btRigidBody) body.getBody();
        dynamicsWorld.addRigidBody(nativeBody);
        rigidBodies.add(body);
    }

    // float stepsPerSecond;

    @Override
    public void addRigidBody(final IRigidBody body, final short collisionFilterGroup, final short collisionFilterMask) {

        btRigidBody nativeBody = (btRigidBody) body.getBody();
        dynamicsWorld.addRigidBody(nativeBody, collisionFilterGroup, collisionFilterMask);
        rigidBodies.add(body);

    }

    @Override
    public void addConstraint(final IConstraint p2p) {
        dynamicsWorld.addConstraint((btTypedConstraint) p2p.getConstraint());
    }

    @Override
    public void removeRigidBody(final IRigidBody body) {
        rigidBodies.remove(body);
        btRigidBody nativeBody;
        dynamicsWorld.removeRigidBody(nativeBody = (btRigidBody) body.getBody());
    }

    @Override
    public void awakenArea(Vector3f min, Vector3f max) {
        final AxisAlignedBB bb = new AxisAlignedBB(min.x, min.y, min.z, max.x, max.y, max.z);
        for (int i = 0; i < rigidBodies.size(); i++) {
            IRigidBody body = rigidBodies.get(i);
            Vector3f vec3 = body.getCenterOfMassPosition();
            Vec3d centerOfMass = new Vec3d(vec3.x, vec3.y, vec3.z);
            if (bb.contains(centerOfMass)) {
                body.activate();
            }
        }

    }

    @Override
    public void rayTest(final Vector3f rayFromWorld, final Vector3f rayToWorld, final IRayResult resultCallback) {
        /*
         * physicsTasks.add(new Runnable() {
         *
         * @Override public void run() { } });
         * dynamicsWorld.rayTest(toVector3(rayFromWorld), toVector3(rayToWorld),
         * (RayResultCallback) resultCallback.getRayResultCallback());
         */
        dynamicsWorld.rayTest(VecUtility.toVector3(rayFromWorld), VecUtility.toVector3(rayToWorld),
                (RayResultCallback) resultCallback.getRayResultCallback());


    }

    @Override
    public void clearRayTest(final IRayResult resultCallback) {
        RayResultCallback rayCallback = (RayResultCallback) resultCallback.getRayResultCallback();
    }


    @Override
    public void removeCollisionObject(final ICollisionObject collisionObject) {

        btCollisionObject nativeCollsionObject;
        dynamicsWorld.removeCollisionObject(
                nativeCollsionObject = (btCollisionObject) collisionObject.getCollisionObject());
    }

    @Override
    public void setGravity(final Vector3f newGravity) {

        dynamicsWorld.setGravity(VecUtility.toVector3(newGravity));

    }

    @Override
    public void addCollisionObject(final ICollisionObject object) {
        btCollisionObject collisionObject = (btCollisionObject) object.getCollisionObject();
        dynamicsWorld.addCollisionObject(collisionObject);
    }

    @Override
    public void addCollisionObject(final ICollisionObject object, final short collisionFilterGroup,
                                   final short collisionFilterMask) {

        btCollisionObject collisionObject = (btCollisionObject) object.getCollisionObject();

        dynamicsWorld.addCollisionObject(collisionObject, collisionFilterGroup,
                collisionFilterMask);
    }

    @Override
    public List<IRigidBody> getRigidBodies() {
        return rigidBodies;
    }


    @Override
    public IGhostObject createPairCachingGhostObject() {
        btPairCachingGhostObject nativePair;
        return new NativePairCachingGhostObject(this,
                nativePair = new btPairCachingGhostObject());
    }

    @Override
    public IRigidBody upCastRigidBody(Object collisionObject) {
        for (int i = 0; i < rigidBodies.size(); i++) {
            IRigidBody body = rigidBodies.get(i);
            if (body.getBody() == collisionObject)
                return body;

        }
        return null;
    }

    @Override
    public IConstraintPoint2Point createPoint2PointConstraint(IRigidBody rigidBody, Vector3f relativePivot) {
        btPoint2PointConstraint nativeConstraint;
        return new NativePoint2PointConstraint(this,
                nativeConstraint = new btPoint2PointConstraint((btRigidBody) rigidBody.getBody(),
                        VecUtility.toVector3(relativePivot)));
    }

    @Override
    public void removeConstraint(final IConstraint constraint) {

        btTypedConstraint nativeConstraint = (btTypedConstraint) constraint.getConstraint();
        dynamicsWorld.removeConstraint((btTypedConstraint) constraint.getConstraint());
        if (!nativeConstraint.isDisposed()) {
            nativeConstraint.dispose();
        }
    }

    @Override
    public ICollisionShape readBlockCollisionShape(String json) {
        return null;
    }

    @Override
    public String writeBlockCollisionShape(ICollisionShape shape) {
        return null;
    }

    @Override
    public IConstraintGeneric6Dof createGeneric6DofConstraint(IRigidBody rbA, IRigidBody rbB, Transform frameInA,
                                                              Transform frameInB, boolean useLinearReferenceFrameA) {

        btGeneric6DofConstraint nativeConstraint = new btGeneric6DofConstraint((btRigidBody) rbA.getBody(), (btRigidBody) rbB.getBody(),
                VecUtility.toMatrix4(frameInA), VecUtility.toMatrix4(frameInB),
                useLinearReferenceFrameA);

        return new NativeConstraintGeneric6Dof(this, nativeConstraint);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + this.rigidBodies.size() + " rigid bodies" + "]";
    }


    @Override
    public void addRope(IRope object) {
    }

    @Override
    public List<IRope> getRopes() {
        return null;
    }

    @Override
    public void removeRope(IRope rope) {
    }

    @Override
    public IRope createRope(Vector3f startPos, Vector3f endPos, int detail) {
        return null;
    }

    // Blasphemy
    @Override
    public ICollisionShape createSphereShape(float radius) {
        btSphereShape nativeSphere;
        return new NativeCollisionShape(this, nativeSphere = new btSphereShape(radius));
    }

    @Override
    public boolean isValid() {
        return !dynamicsWorld.isDisposed();
    }

    // TODO (0.8.0) NativePhysicsWorld: Add slider constraint
    @Override
    public IConstraintSlider createSliderConstraint(IRigidBody rbA, IRigidBody rbB, Transform frameInA,
                                                    Transform frameInB, boolean useLinearReferenceFrameA) {
        return null;
    }


    public ICollisionShape buildCollisionShape(List<AxisAlignedBB> bbs, Vector3f offset) {
        btCompoundShape compoundShape = new btCompoundShape();
        for (AxisAlignedBB bb : bbs) {
            AxisAlignedBB relativeBB = new AxisAlignedBB((bb.minX - offset.x) * 0.5f,
                    (bb.minY - offset.y) * 0.5f, (bb.minZ - offset.z) * 0.5f, (bb.maxX - offset.x) * 0.5f,
                    (bb.maxY - offset.y) * 0.5f, (bb.maxZ - offset.z) * 0.5f);
            Vector3f extents = new Vector3f((float) relativeBB.maxX - (float) relativeBB.minX,
                    (float) relativeBB.maxY - (float) relativeBB.minY,
                    (float) relativeBB.maxZ - (float) relativeBB.minZ);
            Transform transform = new Transform();
            transform.setIdentity();
            transform.origin.set((float) relativeBB.minX + (float) relativeBB.maxX - 0.5f,
                    (float) relativeBB.minY + (float) relativeBB.maxY - 0.5f,
                    (float) relativeBB.minZ + (float) relativeBB.maxZ - 0.5f);
            compoundShape.addChildShape(VecUtility.toMatrix4(transform), new btBoxShape(VecUtility.toVector3(extents)));
        }
        return new NativeCollisionShape(this, compoundShape);

    }

    public void safeDispose(BulletBase base) {
        if (base != null && !base.isDisposed()) base.dispose();
    }


    // TODO (0.6.0) FIXME: after disposal renewal doesn't work for voxel block provider.
    @Override
    public void dispose() {
        rigidBodies.clear();
        safeDispose(dynamicsWorld);
        safeDispose(broadphase);
        safeDispose(sequentialSolver);
        safeDispose(collisionConfiguration);
        safeDispose(collisionDispatcher);
        safeDispose(voxelInfo);
        safeDispose(voxelBody);
        safeDispose(voxelShape);
        safeDispose(voxelProvider);

        dynamicsWorld = null;
        broadphase = null;
        sequentialSolver = null;
        collisionConfiguration = null;
        collisionDispatcher = null;
        voxelProvider = null;

        voxelShape = null;
        voxelBody = null;
        voxelInfo = null;
        super.dispose();


    }
}