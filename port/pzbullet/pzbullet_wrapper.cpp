// Own wasm32 Bullet wrapper for the personal browser port.
// It exposes only the semantics this project can honestly declare; every other
// PZBullet entry point stays unimplemented on the Java side and throws with its JNI name.
#include "LinearMath/btVector3.h"
#include "LinearMath/btQuaternion.h"
#include "LinearMath/btTransform.h"
#include "LinearMath/btAlignedObjectArray.h"
#include "BulletCollision/CollisionDispatch/btCollisionWorld.h"
#include "BulletCollision/CollisionDispatch/btCollisionDispatcher.h"
#include "BulletCollision/CollisionDispatch/btDefaultCollisionConfiguration.h"
#include "BulletCollision/BroadphaseCollision/btDbvtBroadphase.h"
#include "BulletCollision/CollisionShapes/btBoxShape.h"
#include "BulletCollision/CollisionShapes/btSphereShape.h"
#include "BulletDynamics/Dynamics/btDiscreteDynamicsWorld.h"
#include "BulletDynamics/Dynamics/btRigidBody.h"
#include "LinearMath/btDefaultMotionState.h"
#include "BulletDynamics/ConstraintSolver/btSequentialImpulseConstraintSolver.h"

#include <cstdint>
#include <cstdio>
#include <cstring>

namespace {

struct PortWorld {
  btDefaultCollisionConfiguration configuration;
  btCollisionDispatcher dispatcher;
  btDbvtBroadphase broadphase;
  btSequentialImpulseConstraintSolver solver;
  btDiscreteDynamicsWorld world;
  btAlignedObjectArray<btRigidBody *> bodies;
  btAlignedObjectArray<btCollisionShape *> shapes;
  explicit PortWorld(const btVector3 &gravity)
      : dispatcher(&configuration), world(&dispatcher, &broadphase, &solver, &configuration) {
    world.setGravity(gravity);
  }
};

PortWorld *g_world = nullptr;
bool g_logging_initialised = false;

// Stable scratch buffers so callers never own wasm memory.
char g_version[64] = {0};
double g_transform[3] = {0, 0, 0};

}  // namespace

extern "C" {

// Real library identity, taken from the pinned upstream source macro. Never invented.
const char *pzbullet_version() {
  std::snprintf(g_version, sizeof(g_version), "Bullet %d (upstream bullet3-2.83, wasm32 port build)", BT_BULLET_VERSION);
  return g_version;
}

void pzbullet_init_logging() { g_logging_initialised = true; }

int pzbullet_logging_initialised() { return g_logging_initialised ? 1 : 0; }

int pzbullet_create_world(double gravity_x, double gravity_y, double gravity_z) {
  if (g_world) return 0;
  g_world = new PortWorld(btVector3(static_cast<btScalar>(gravity_x), static_cast<btScalar>(gravity_y),
                                    static_cast<btScalar>(gravity_z)));
  return 1;
}

int pzbullet_body_count() { return g_world ? g_world->bodies.size() : -1; }

int pzbullet_add_box(double half_x, double half_y, double half_z, double mass,
                     double px, double py, double pz, double rx, double ry, double rz, double rw) {
  if (!g_world) return -1;
  btBoxShape *shape = new btBoxShape(btVector3(static_cast<btScalar>(half_x), static_cast<btScalar>(half_y),
                                               static_cast<btScalar>(half_z)));
  g_world->shapes.push_back(shape);
  btDefaultMotionState *motion = new btDefaultMotionState(btTransform(
      btQuaternion(static_cast<btScalar>(rx), static_cast<btScalar>(ry), static_cast<btScalar>(rz),
                   static_cast<btScalar>(rw)),
      btVector3(static_cast<btScalar>(px), static_cast<btScalar>(py), static_cast<btScalar>(pz))));
  btRigidBody::btRigidBodyConstructionInfo info(static_cast<btScalar>(mass), motion, shape, btVector3(btScalar(0), btScalar(0), btScalar(0)));
  btRigidBody *body = new btRigidBody(info);
  g_world->bodies.push_back(body);
  g_world->world.addRigidBody(body);
  return g_world->bodies.size() - 1;
}

int pzbullet_add_sphere(double radius, double mass, double px, double py, double pz) {
  if (!g_world) return -1;
  btSphereShape *shape = new btSphereShape(static_cast<btScalar>(radius));
  g_world->shapes.push_back(shape);
  btDefaultMotionState *motion = new btDefaultMotionState(btTransform(btQuaternion(0, 0, 0, 1),
      btVector3(static_cast<btScalar>(px), static_cast<btScalar>(py), static_cast<btScalar>(pz))));
  btRigidBody::btRigidBodyConstructionInfo info(static_cast<btScalar>(mass), motion, shape, btVector3(btScalar(0), btScalar(0), btScalar(0)));
  btRigidBody *body = new btRigidBody(info);
  g_world->bodies.push_back(body);
  g_world->world.addRigidBody(body);
  return g_world->bodies.size() - 1;
}

int pzbullet_step_simulation(double time_step, int max_sub_steps, double fixed_time_step) {
  if (!g_world) return -1;
  return g_world->world.stepSimulation(static_cast<btScalar>(time_step), max_sub_steps, static_cast<btScalar>(fixed_time_step));
}

// Returns a pointer to three doubles (x, y, z of the body origin) valid until the next
// call, or nullptr when the body is unknown. Callers copy; they never free.
const double *pzbullet_get_transform(int body_index) {
  if (!g_world || body_index < 0 || body_index >= g_world->bodies.size()) return nullptr;
  const btVector3 &origin = g_world->bodies[body_index]->getWorldTransform().getOrigin();
  g_transform[0] = origin.getX(); g_transform[1] = origin.getY(); g_transform[2] = origin.getZ();
  return g_transform;
}

void pzbullet_remove_all_bodies() {
  if (!g_world) return;
  for (int i = 0; i < g_world->bodies.size(); ++i) g_world->world.removeRigidBody(g_world->bodies[i]);
  for (int i = 0; i < g_world->bodies.size(); ++i) delete g_world->bodies[i];
  for (int i = 0; i < g_world->shapes.size(); ++i) delete g_world->shapes[i];
  g_world->bodies.resize(0);
  g_world->shapes.resize(0);
}

void pzbullet_dispose_world() {
  if (!g_world) return;
  pzbullet_remove_all_bodies();
  delete g_world;
  g_world = nullptr;
}

}  // extern "C"
