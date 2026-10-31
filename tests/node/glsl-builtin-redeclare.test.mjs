import test from 'node:test';
import assert from 'node:assert/strict';
import { parse } from '@shaderfrog/glsl-parser/index.js';
import { translateShader, ES300_BUILTIN_FUNCTIONS, USER_BUILTIN_PREFIX } from '../../port/runtime/glsl.mjs';

// GLSL ES 3.00 §6.1 forbids redeclaring/overloading built-ins; desktop 1.10/1.20 allows it.
// All shaders here are synthetic fixtures written for this test.

/** Names of every function prototype/definition in a translated source. */
function declaredFunctions(source) {
  const ast = parse(source, { quiet: true, stage: 'fragment' });
  return ast.program.filter(n => n.type === 'function' || n.declaration?.type === 'function_prototype')
    .map(n => (n.prototype ?? n.declaration).header.name.identifier);
}
const assertNoBuiltinDeclared = source =>
  assert.deepEqual(declaredFunctions(source).filter(n => ES300_BUILTIN_FUNCTIONS.has(n)), []);
const compact = source => source.replace(/\s+/g, '');

test('the built-in list covers every GLSL ES 3.00 function family', () => {
  for (const name of ['sin','atanh','inversesqrt','roundEven','modf','clamp','smoothstep','floatBitsToUint',
    'packHalf2x16','unpackSnorm2x16','faceforward','refract','outerProduct','inverse','lessThanEqual','not',
    'textureSize','texelFetchOffset','textureProjGradOffset','dFdx','fwidth'])
    assert.ok(ES300_BUILTIN_FUNCTIONS.has(name), name);
  assert.ok(!ES300_BUILTIN_FUNCTIONS.has('lerp'), 'lerp is not a GLSL built-in');
  assert.ok(!ES300_BUILTIN_FUNCTIONS.has('texture2D'), 'texture2D does not exist in ES 3.00');
});

test('a user max(float,float) is renamed and only its calls follow it', () => {
  const src = `#version 120
    uniform float x; uniform vec2 a; uniform vec2 b;
    float max(float p, float q) { return p > q ? p : q; }
    vec3 clamp01(vec3 v) { return clamp(v, 0.0, 1.0); }
    void main() {
      float s = max(1.0, x);
      vec2 m = max(a, b);
      gl_FragColor = vec4(clamp01(vec3(s, m)), 1.0);
    }`;
  const result = translateShader(src, 'fragment');
  assertNoBuiltinDeclared(result.source);
  assert.deepEqual(result.builtinOverloads, ['max']);
  const out = compact(result.source);
  assert.ok(out.includes(`float${USER_BUILTIN_PREFIX}max(floatp,floatq)`), 'definition renamed');
  assert.ok(out.includes(`s=${USER_BUILTIN_PREFIX}max(1.0,x)`), 'float call goes to the user overload');
  assert.ok(out.includes('m=max(a,b)'), 'vec2 call keeps the built-in');
  assert.ok(out.includes('vec3clamp01(vec3v)'), 'non-built-in names are untouched');
  assert.ok(out.includes('returnclamp(v,0.0,1.0)'), 'built-in clamp untouched when no user clamp exists');
});

test('prototype and definition get the same name; earlier calls keep the built-in', () => {
  const src = `#version 120
    uniform float u;
    float early() { return min(u, 2.0); }
    float min(float a, float b);
    float late() { return min(u, 2.0); }
    float min(float a, float b) { if (a < b) return a; return b; }
    void main() { gl_FragColor = vec4(early(), late(), min(vec2(u), vec2(1.0))); }`;
  const result = translateShader(src, 'fragment');
  assertNoBuiltinDeclared(result.source);
  const names = declaredFunctions(result.source);
  assert.equal(names.filter(n => n === `${USER_BUILTIN_PREFIX}min`).length, 2, 'prototype and definition');
  const out = compact(result.source);
  assert.ok(out.includes('floatearly(){returnmin(u,2.0);}'), 'no user min visible yet: built-in');
  assert.ok(out.includes(`floatlate(){return${USER_BUILTIN_PREFIX}min(u,2.0);}`), 'prototype visible: user');
  assert.ok(out.includes('min(vec2(u),vec2(1.0))') && !out.includes(`${USER_BUILTIN_PREFIX}min(vec2`));
});

test('a prototype-only unit (desktop header) renames its declaration and calls the same way', () => {
  // A unit that only sees the prototype must call the same renamed symbol the defining unit exports,
  // so the per-stage merge (and any separate compile) links.
  const header = translateShader(`#version 120
    uniform float u;
    float max(float a, float b);
    vec3 clamp(vec3 a, float lo, float hi);
    void main() { gl_FragColor = vec4(clamp(vec3(u), 0.0, max(u, 0.5)), 1.0); }`, 'fragment');
  assertNoBuiltinDeclared(header.source);
  const out = compact(header.source);
  assert.ok(out.includes(`vec3${USER_BUILTIN_PREFIX}clamp(vec3a,floatlo,floathi);`));
  assert.ok(out.includes(`${USER_BUILTIN_PREFIX}clamp(vec3(u),0.0,${USER_BUILTIN_PREFIX}max(u,0.5))`));
});

test('nested calls resolve by the inferred type of the inner call', () => {
  const src = `#version 120
    uniform vec3 c; uniform float k;
    float max(float a, float b) { return a > b ? a : b; }
    vec3 clamp(vec3 v, float lo, float hi) {
      vec3 r;
      r.x = max(lo, v.x) > hi ? hi : max(lo, v.x);
      r.y = max(lo, v.y); r.z = max(lo, v.z);
      return r;
    }
    void main() {
      float a = max(max(c.x, k), max(c, c).x);
      vec3 b = clamp(max(c, vec3(k)), 0.0, max(k, 1.0));
      vec4 d = clamp(vec4(c, k), 0.0, 1.0);
      gl_FragColor = vec4(b * a, d.w);
    }`;
  const result = translateShader(src, 'fragment');
  assertNoBuiltinDeclared(result.source);
  assert.deepEqual(result.builtinOverloads, ['clamp', 'max']);
  const out = compact(result.source);
  const P = USER_BUILTIN_PREFIX;
  assert.ok(out.includes(`r.x=${P}max(lo,v.x)>hi?hi:${P}max(lo,v.x)`), 'parameters and swizzles typed');
  assert.ok(out.includes(`a=${P}max(${P}max(c.x,k),max(c,c).x)`), 'outer user, inner float user, vec3 built-in');
  assert.ok(out.includes(`b=${P}clamp(max(c,vec3(k)),0.0,${P}max(k,1.0))`), 'vec3 built-in max feeds user clamp');
  assert.ok(out.includes('d=clamp(vec4(c,k),0.0,1.0)'), 'vec4 has no user overload: built-in');
});

test('undecidable calls fail instead of guessing (desktop 1.20)', () => {
  // Argument type unknown (an undeclared identifier cannot be typed).
  assert.throws(() => translateShader(`#version 120
    uniform float u;
    float max(float a, float b) { return a > b ? a : b; }
    void main() { gl_FragColor = vec4(max(u, gl_FragCoord.x * undefinedThing)); }`, 'fragment'),
  /Cannot infer the argument types of max\(float, \?\)/);
  // Only an int->float conversion reaches the user overload; the built-in competes equally.
  assert.throws(() => translateShader(`#version 120
    uniform float u;
    float max(float a, float b) { return a > b ? a : b; }
    void main() { gl_FragColor = vec4(max(u, 0)); }`, 'fragment'), /implicit conversion/);
});

test('GLSL 1.30+ user declarations hide the built-in, so conversions reach the user overload', () => {
  const result = translateShader(`#version 330
    uniform float u; uniform vec3 v;
    out vec4 o;
    float max(float a, float b) { return a > b ? a : b; }
    vec3 clamp(vec3 a, float lo, float hi) { return vec3(a.x < lo ? lo : a.x > hi ? hi : a.x, a.y, a.z); }
    void main() { o = vec4(clamp(v, 0, 1), max(u, 0)); }`, 'fragment');
  assertNoBuiltinDeclared(result.source);
  const out = compact(result.source);
  // Round 81: the int arguments get the explicit conversion ES 3.00 needs (convertCallArguments).
  assert.ok(out.includes(`${USER_BUILTIN_PREFIX}clamp(v,0.0,1.0)`));
  assert.ok(out.includes(`${USER_BUILTIN_PREFIX}max(u,0.0)`));
});

test('identifiers already using the reserved prefix are rejected', () => {
  assert.throws(() => translateShader(`#version 120
    float max(float a, float b) { return a; }
    float ${USER_BUILTIN_PREFIX}max = 1.0;
    void main() { gl_FragColor = vec4(${USER_BUILTIN_PREFIX}max); }`, 'fragment'), /prefix/);
});

test('shaders without built-in-named functions are unchanged by the pass', () => {
  const result = translateShader('#version 120\nuniform float u;\nvoid main(){gl_FragColor=vec4(max(u,0.5));}', 'fragment');
  assert.deepEqual(result.builtinOverloads, []);
  assert.ok(compact(result.source).includes('max(u,0.5)'));
});
