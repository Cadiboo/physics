package gliby.minecraft.physics.common.physics.engine.nativebullet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody.btRigidBodyConstructionInfo;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import com.badlogic.gdx.utils.Disposable;
import com.bulletphysicsx.linearmath.Transform;
import gliby.minecraft.physics.Physics;
import gliby.minecraft.physics.common.physics.IPhysicsWorldConfiguration;
import gliby.minecraft.physics.common.physics.PhysicsOverworld;
import gliby.minecraft.physics.common.physics.PhysicsWorld;
import gliby.minecraft.physics.common.physics.engine.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */

// FIXME NativePhysicsWorld: Stop using Vector/Matrix/Transform conversions. Use
// IVector and
// IQuaternion, IMatrix, replace with custom vector stuff or MC Vec3.
public class NativePhysicsWorld extends PhysicsWorld {

    static Vector3f staticVector = new Vector3f();
    static Vector3 tempVec = new Vector3();
    static Quaternion tempQuat = new Quaternion();
    // TODO NativePhysicsWorld: replace this hack.
    private static Transform temp = new Transform();
    private static Quat4f rotationTemp = new Quat4f();

    static {
        Bullet.init();
    }

    protected List<ICollisionObject> collisionObjects;
    private btDiscreteDynamicsWorld dynamicsWorld;
    private List<IConstraint> constraints;
    private List<IRigidBody> rigidBodies;
    private PhysicsOverworld physicsOverworld;
    private Physics physics;
    private btDbvtBroadphase broadphase;
    private btCollisionConfiguration collisionConfiguration;
    private btCollisionDispatcher collisionDispatcher;
    private btVoxelShape voxelShape;
    private btCollisionObject voxelBody;
    private NativeVoxelProvider voxelProvider;
    private btSequentialImpulseConstraintSolver sequentialSolver;
    /**
     * Tracks disposable objects that fall under miscellaneous.
     */

    private List<Disposable> disposables;

    public NativePhysicsWorld(Physics physics, PhysicsOverworld physicsOverworld,
                              IPhysicsWorldConfiguration physicsConfig) {
        super(physicsConfig);
        this.physics = physics;
        this.physicsOverworld = physicsOverworld;
    }

    static Matrix4 fromTransformToMatrix4(Transform transform) {
        return toMatrix4(transform.getMatrix(tempMatrix));
    }

    public static Matrix4 toMatrix4(Matrix4f matrix4f) {
        temp.set(matrix4f);
        Quat4f rot = temp.getRotation(rotationTemp);
        return new Matrix4().set(temp.origin.x, temp.origin.y, temp.origin.z, rot.x, rot.y, rot.z, rot.w, 1, 1, 1);
    }

    static Vector3 toVector3(Vector3f vector3f) {
        return new Vector3(vector3f.x, vector3f.y, vector3f.z);
    }

    static Vector3f toVector3f(Vector3 vector3) {
        return new Vector3f(vector3.x, vector3.y, vector3.z);
    }

    static Vector3f toStaticVector3f(Vector3 vector3) {
        staticVector.set(vector3.x, vector3.y, vector3.z);
        return staticVector;
    }

    static Matrix4f tempMatrix = new Matrix4f();

    // FIXME NativePhysicsWorld: Potential Memory Leak.
    static Matrix4f toMatrix4f(Matrix4 matrix4) {
        Vector3 position = matrix4.getTranslation(tempVec);
        Quaternion rotation = matrix4.getRotation(tempQuat);
        rotationTemp.set(rotation.x, rotation.y, rotation.z, rotation.w);
        temp.setRotation(rotationTemp);
        temp.origin.set(position.x, position.y, position.z);
        return temp.getMatrix(tempMatrix);
    }

//    @Override
//    public void run() {
//        getDelta();
//        lastFPS = getTime();
//        while (running) {
//            synchronized (this) {
//                try {
//                    wait(1000 / getPhysicsConfiguration().getTicksPerSecond());
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (getPhysicsConfiguration().shouldSimulate(getPhysicsConfiguration().getWorld(), this))
//                update();
//            updateFPS();
//        }
//    }

    @Override
    public void create() {
        collisionObjects = new CopyOnWriteArrayList<ICollisionObject>();
        rigidBodies = new CopyOnWriteArrayList<IRigidBody>();
        constraints = new CopyOnWriteArrayList<IConstraint>();
        disposables = new ArrayList<Disposable>();

        broadphase = new btDbvtBroadphase();
        collisionConfiguration = new btDefaultCollisionConfiguration();
        collisionDispatcher = new btCollisionDispatcher(collisionConfiguration);

        dynamicsWorld = new btDiscreteDynamicsWorld(collisionDispatcher, broadphase,
                sequentialSolver = new btSequentialImpulseConstraintSolver(), collisionConfiguration);
        dynamicsWorld.setGravity(toVector3(getPhysicsConfiguration().getRegularGravity()));

        voxelShape = new btVoxelShape(
                voxelProvider = new NativeVoxelProvider(getPhysicsConfiguration().getWorld(), this, physics),
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
        if (dynamicsWorld != null) {
            final float delta = getDelta();
            final int maxSubStep = Math.max(1, Math.round(delta / 7));
            dynamicsWorld.stepSimulation(1, maxSubStep);
            super.update();
        }
    }

    @Override
    public IRigidBody createRigidBody(Entity owner, Transform transform, float mass, ICollisionShape shape) {
        Vector3 localInertia = new Vector3();
        if (mass != 0) {
            shape.calculateLocalInertia(mass, localInertia);
        }
        btDefaultMotionState motionState = new btDefaultMotionState(fromTransformToMatrix4(transform));
        btRigidBodyConstructionInfo constructionInfo = new btRigidBodyConstructionInfo(mass, motionState,
                (btCollisionShape) shape.getCollisionShape(), localInertia);
        NativeRigidBody rigidBody = new NativeRigidBody(this, new btRigidBody(constructionInfo), owner);

        return rigidBody;
    }

    @Override
    public IRigidBody createInertialessRigidbody(Entity owner, Transform transform, float mass,
                                                 ICollisionShape shape) {
        btDefaultMotionState motionState = new btDefaultMotionState(fromTransformToMatrix4(transform));
        btRigidBodyConstructionInfo constructionInfo = new btRigidBodyConstructionInfo(mass, motionState,
                (btCollisionShape) shape.getCollisionShape());
        NativeRigidBody rigidBody = new NativeRigidBody(this, new btRigidBody(constructionInfo), owner);
        return rigidBody;
    }

    @Override
    public ICollisionShape createBoxShape(Vector3f extents) {
        NativeCollisionShape shape = new NativeCollisionShape(this, new btBoxShape(toVector3(extents)));
        return shape;
    }

    @Override
    public IRayResult createClosestRayResultCallback(Vector3f rayFromWorld, Vector3f rayToWorld) {
        ClosestRayResultCallback nativeCallback;
        NativeClosestRayResultCallback callback = new NativeClosestRayResultCallback(
                nativeCallback = new ClosestRayResultCallback(toVector3(rayFromWorld), toVector3(rayToWorld)));
        disposables.add(nativeCallback);
        return callback;
    }

    @Override
    public void addRigidBody(final IRigidBody body) {
        dynamicsWorld.addRigidBody((btRigidBody) body.getBody());
        rigidBodies.add(body);

    }

    // float stepsPerSecond;

    @Override
    public void addRigidBody(final IRigidBody body, final short collisionFilterGroup, final short collisionFilterMask) {

        dynamicsWorld.addRigidBody((btRigidBody) body.getBody(), collisionFilterGroup, collisionFilterMask);
        rigidBodies.add(body);

    }

    @Override
    public void addConstraint(final IConstraint p2p) {

        dynamicsWorld.addConstraint((btTypedConstraint) p2p.getConstraint());
        constraints.add(p2p);

    }

    // TODO NativePhysicsWorld: Dispose of object on remove.
    @Override
    public void removeRigidBody(final IRigidBody body) {
        rigidBodies.remove(body);

        btRigidBody nativeBody;
        dynamicsWorld.removeRigidBody(nativeBody = (btRigidBody) body.getBody());
        nativeBody.dispose();

    }

    @Override
    public void awakenArea(Vector3f min, Vector3f max) {
        final AxisAlignedBB bb = AxisAlignedBB.fromBounds(min.x, min.y, min.z, max.x, max.y, max.z);
        for (int i = 0; i < rigidBodies.size(); i++) {
            IRigidBody body = rigidBodies.get(i);
            Vector3f vec3 = body.getCenterOfMassPosition();
            Vec3 centerOfMass = new Vec3(vec3.x, vec3.y, vec3.z);
            if (bb.isVecInside(centerOfMass)) {
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
        dynamicsWorld.rayTest(toVector3(rayFromWorld), toVector3(rayToWorld),
                (RayResultCallback) resultCallback.getRayResultCallback());


    }

    @Override
    public void clearRayTest(final IRayResult resultCallback) {
        RayResultCallback rayCallback = (RayResultCallback) resultCallback.getRayResultCallback();
        if (rayCallback != null && !rayCallback.isDisposed())
            rayCallback.dispose();
    }


    @Override
    public void removeCollisionObject(final ICollisionObject collisionObject) {

        btCollisionObject nativeCollsionObject;
        dynamicsWorld.removeCollisionObject(
                nativeCollsionObject = (btCollisionObject) collisionObject.getCollisionObject());
        collisionObjects.remove(collisionObject);
        nativeCollsionObject.dispose();


    }

    @Override
    public void setGravity(final Vector3f newGravity) {

        dynamicsWorld.setGravity(toVector3(newGravity));

    }

    @Override
    public void addCollisionObject(final ICollisionObject object) {

        dynamicsWorld.addCollisionObject((btCollisionObject) object.getCollisionObject());
        collisionObjects.add(object);

    }

    @Override
    public void addCollisionObject(final ICollisionObject object, final short collisionFilterGroup,
                                   final short collisionFilterMask) {

        dynamicsWorld.addCollisionObject((btCollisionObject) object.getCollisionObject(), collisionFilterGroup,
                collisionFilterMask);
        collisionObjects.add(object);

    }

    @Override
    public List<IRigidBody> getRigidBodies() {
        return rigidBodies;
    }


    @Override
    public IGhostObject createPairCachingGhostObject() {
        btPairCachingGhostObject nativePair;
        NativePairCachingGhostObject pairCache = new NativePairCachingGhostObject(this,
                nativePair = new btPairCachingGhostObject());
        disposables.add(nativePair);
        return pairCache;
    }

    @Override
    public IRigidBody upcastRigidBody(Object collisionObject) {
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
        NativePoint2PointConstraint p2p = new NativePoint2PointConstraint(this,
                nativeConstraint = new btPoint2PointConstraint((btRigidBody) rigidBody.getBody(),
                        toVector3(relativePivot)));
        return p2p;
    }

    @Override
    public void removeConstraint(final IConstraint constraint) {

        btTypedConstraint nativeConstraint = (btTypedConstraint) constraint.getConstraint();
        dynamicsWorld.removeConstraint((btTypedConstraint) constraint.getConstraint());
        constraints.remove(constraint);
        nativeConstraint.dispose();

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
    public List<IConstraint> getConstraints() {
        return constraints;
    }

    @Override
    public IConstraintGeneric6Dof createGeneric6DofConstraint(IRigidBody rbA, IRigidBody rbB, Transform frameInA,
                                                              Transform frameInB, boolean useLinearReferenceFrameA) {
        NativeConstraintGeneric6Dof constraint = new NativeConstraintGeneric6Dof(this,
                new btGeneric6DofConstraint((btRigidBody) rbA.getBody(), (btRigidBody) rbB.getBody(),
                        fromTransformToMatrix4(frameInA), fromTransformToMatrix4(frameInB),
                        useLinearReferenceFrameA));
        return constraint;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + this.rigidBodies.size() + " rigid bodies" + "]";
    }

    // TODO NativePhysicsWorld: Add rope support

    @Override
    public void addRope(IRope object) {
        // TODO NativePhysicsWorld: rope feature
    }

    @Override
    public List<IRope> getRopes() {
        // TODO NativePhysicsWorld: rope feature
        return null;
    }

    @Override
    public void removeRope(IRope rope) {
        // TODO NativePhysicsWorld: rope feature

    }

    @Override
    public IRope createRope(Vector3f startPos, Vector3f endPos, int detail) {
        // TODO NativePhysicsWorld: rope feature
        return null;
    }

    @Override
    public ICollisionShape createSphereShape(float radius) {
        btSphereShape nativeSphere;
        NativeCollisionShape shape = new NativeCollisionShape(this, nativeSphere = new btSphereShape(radius));
        disposables.add(nativeSphere);
        return shape;
    }

    // TODO NativePhysicsWorld: Add slider constraint
    @Override
    public IConstraintSlider createSliderConstraint(IRigidBody rbA, IRigidBody rbB, Transform frameInA,
                                                    Transform frameInB, boolean useLinearReferenceFrameA) {
        return null;
    }


    public ICollisionShape buildCollisionShape(List<AxisAlignedBB> bbs, Vector3f offset) {
        btCompoundShape compoundShape = new btCompoundShape();
        for (AxisAlignedBB bb : bbs) {
            AxisAlignedBB relativeBB = AxisAlignedBB.fromBounds((bb.minX - offset.x) * 0.5f,
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
            compoundShape.addChildShape(fromTransformToMatrix4(transform), new btBoxShape(toVector3(extents)));
        }
        NativeCollisionShape collisionShape = new NativeCollisionShape(this, compoundShape);
        return collisionShape;

    }

    // TODO NativePhysicsWorld: Make dispose actually dispose something.
    @Override
    public void dispose() {

        dynamicsWorld.removeCollisionObject(voxelBody);
        voxelShape.dispose();
        voxelBody.dispose();
        voxelProvider.dispose();

        for (int i = 0; i < constraints.size(); i++) {
            // Get constraint
            IConstraint constraint = constraints.get(i);
            // Remove reference from list

            // Get native reference
            btTypedConstraint constraintRef = (btTypedConstraint) constraint.getConstraint();
            // Remove from world.
            dynamicsWorld.removeConstraint(constraintRef);

            // Dispose of native reference
            constraintRef.dispose();
        }

        constraints.clear();

        for (int i = 0; i < collisionObjects.size(); i++) {
            ICollisionObject object = collisionObjects.get(i);
            btCollisionObject objectRef = (btCollisionObject) object.getCollisionObject();
            dynamicsWorld.removeCollisionObject(objectRef);
            objectRef.dispose();
        }

        collisionObjects.clear();

        for (int i = 0; i < rigidBodies.size(); i++) {
            // Get rigidBody
            IRigidBody rigidBody = rigidBodies.get(i);
            // Remove reference from list
            removeRigidBody(rigidBody);
        }

        rigidBodies.clear();

        broadphase.dispose();
        collisionDispatcher.dispose();
        collisionConfiguration.dispose();
        sequentialSolver.dispose();
        dynamicsWorld.dispose();
        dynamicsWorld = null;

        for (int i = 0; i < disposables.size(); i++) {
            Disposable disposable = disposables.get(i);
            if (disposable != null)
                disposable.dispose();
        }
    }
}