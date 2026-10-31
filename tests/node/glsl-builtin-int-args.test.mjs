import test from 'node:test';
import assert from 'node:assert/strict';
import { translateShader, USER_BUILTIN_PREFIX, RENAMED_IDENTIFIER_PREFIX, webglIdentifier, desktopIdentifier }
  from '../../port/runtime/glsl.mjs';

// Desktop GLSL 1.20+ converts int/ivec to float/vec implicitly (calls, operators, initializers);
// GLSL ES 3.00 does not. All shaders here are synthetic fixtures written for this test.
const compact = source => source.replace(/\s+/g, '');
const frag = body => compact(translateShader(`#version 330
uniform float f; uniform vec3 v3; uniform vec3 w3; uniform int ia; uniform int ib; uniform ivec2 ip;
out vec4 o;
void main() { ${body} }`, 'fragment').source);

test('float-only built-ins convert int literal arguments', () => {
  assert.ok(frag('o = vec4(pow(f, 2));').includes('pow(f,2.0)'));
  assert.ok(frag('o = vec4(mix(v3, w3, 1), 1.0);').includes('mix(v3,w3,1.0)'));
  assert.ok(frag('o = vec4(smoothstep(0, 1, f));').includes('smoothstep(0.0,1.0,f)'));
  // A non-literal int argument is wrapped in the float constructor of its size.
  assert.ok(frag('o = vec4(pow(f, ia));').includes('pow(f,float(ia))'));
  assert.ok(frag('o = vec4(pow(f, 1 - f));').includes('pow(f,1.0-f)'));
});

test('int-capable built-ins convert only when a float argument picks the float overload', () => {
  assert.ok(frag('o = vec4(clamp(v3, 0, 1), 1.0);').includes('clamp(v3,0.0,1.0)'));
  assert.ok(frag('o = vec4(clamp(f, -1, 1));').includes('clamp(f,-1.0,1.0)'));
  const allInt = frag('int m = max(ia, ib); o = vec4(float(m));');
  assert.ok(allInt.includes('max(ia,ib)'));
  assert.ok(frag('o = vec4(float(clamp(ia, 0, 1)));').includes('clamp(ia,0,1)'));
});

test('an int bias of a texture lookup becomes float', () => {
  const out = compact(translateShader(`#version 120
uniform sampler2D s; varying vec2 uv;
void main() { gl_FragColor = texture2D(s, uv, 0); }`, 'fragment').source);
  assert.ok(out.includes('texture(s,uv,0.0)'));
});

test('user functions with float parameters called with int arguments', () => {
  const plain = compact(translateShader(`#version 120
float scale(float a, float b) { return a * b; }
uniform float u;
void main() { gl_FragColor = vec4(scale(u, 0)); }`, 'fragment').source);
  assert.ok(plain.includes('scale(u,0.0)'));
  const renamed = compact(translateShader(`#version 330
float max(float a, float b) { return a > b ? a : b; }
uniform float u; out vec4 o;
void main() { o = vec4(max(u, 0)); }`, 'fragment').source);
  assert.ok(renamed.includes(`${USER_BUILTIN_PREFIX}max(u,0.0)`));
  // An exact match is left alone.
  assert.ok(compact(translateShader(`#version 120
float g(int a) { return float(a); }
void main() { gl_FragColor = vec4(g(1)); }`, 'fragment').source).includes('g(1)'));
});

test('a call to a user function types as its return type, not its name', () => {
  // half() returns float, so `1 - half(u)` promotes the 1 and pow sees two floats.
  const out = compact(translateShader(`#version 120
uniform float u;
float half1(float x) { return x * 0.5; }
void main() { gl_FragColor = vec4(pow(1 - half1(u), 2)); }`, 'fragment').source);
  assert.ok(out.includes('pow(1.0-half1(u),2.0)'));
});

test('int vectors combined with float operands convert to float vectors', () => {
  const out = frag('vec2 step2 = vec2(0.5); vec2 q = (ip + ivec2(1, 2)) * step2; o = vec4(q, 0.0, 1.0);');
  assert.ok(out.includes('vec2((ip+ivec2(1,2)))*step2'), out);
  assert.ok(frag('vec3 a = 2 * v3; o = vec4(a, 1.0);').includes('2.0*v3'));
  assert.ok(frag('vec2 a = ip; o = vec4(a, 0.0, 1.0);').includes('vec2a=vec2(ip)'));
  assert.ok(frag('vec3 a = v3; a *= 2; o = vec4(a, 1.0);').includes('a*=2.0'));
  // Pure int arithmetic stays int.
  assert.ok(frag('ivec2 a = ip * 2; o = vec4(float(a.x));').includes('ivec2a=ip*2'));
});

test('variables named like ES 3.00 built-ins or reserved words are renamed consistently', () => {
  const result = translateShader(`#version 120
uniform sampler2D texture;
uniform float filter = 0.5;
varying vec2 uv;
vec4 outline(sampler2D texture, vec2 p) { return texture2D(texture, p); }
void main() { float input = filter; gl_FragColor = outline(texture, uv) * texture2D(texture, uv) * input; }`, 'fragment');
  const out = compact(result.source);
  assert.deepEqual(result.renamedIdentifiers, ['filter', 'input', 'texture']);
  assert.ok(out.includes(`uniformsampler2D${RENAMED_IDENTIFIER_PREFIX}texture;`));
  assert.ok(out.includes(`vec4outline(sampler2D${RENAMED_IDENTIFIER_PREFIX}texture,vec2p){returntexture(${RENAMED_IDENTIFIER_PREFIX}texture,p);}`));
  assert.ok(out.includes(`outline(${RENAMED_IDENTIFIER_PREFIX}texture,uv)*texture(${RENAMED_IDENTIFIER_PREFIX}texture,uv)*${RENAMED_IDENTIFIER_PREFIX}input`));
  assert.ok(!/[^_]texture[;,)]/.test(out.replaceAll(`${RENAMED_IDENTIFIER_PREFIX}texture`, '')));
  // Uniform defaults carry the WebGL name; the bridge maps desktop names both ways.
  assert.deepEqual(result.uniformDefaults.map(u => u.name), [`${RENAMED_IDENTIFIER_PREFIX}filter`]);
  assert.equal(webglIdentifier('texture'), `${RENAMED_IDENTIFIER_PREFIX}texture`);
  assert.equal(webglIdentifier('texture[0]'), `${RENAMED_IDENTIFIER_PREFIX}texture[0]`);
  assert.equal(webglIdentifier('DIFFUSE'), 'DIFFUSE');
  assert.equal(desktopIdentifier(`${RENAMED_IDENTIFIER_PREFIX}texture`), 'texture');
  assert.equal(desktopIdentifier('DIFFUSE'), 'DIFFUSE');
  assert.throws(() => translateShader(`#version 120
float ${RENAMED_IDENTIFIER_PREFIX}x = 1.0; void main() { gl_FragColor = vec4(${RENAMED_IDENTIFIER_PREFIX}x); }`, 'fragment'), /prefix/);
});

test('a varying named texture is renamed identically in both stages', () => {
  const vs = compact(translateShader('#version 120\nvarying vec2 texture;\nvoid main(){ texture = gl_Vertex.xy; gl_Position = gl_Vertex; }', 'vertex').source);
  const fs = compact(translateShader('#version 120\nvarying vec2 texture;\nvoid main(){ gl_FragColor = vec4(texture, 0.0, 1.0); }', 'fragment').source);
  assert.ok(vs.includes(`outvec2${RENAMED_IDENTIFIER_PREFIX}texture;`));
  assert.ok(fs.includes(`invec2${RENAMED_IDENTIFIER_PREFIX}texture;`));
});

test('later declarators of one declaration are typed and renamed too', () => {
  const out = compact(translateShader(`#version 120
uniform float k;
void main() { vec3 r, u; float a, filter; u = vec3(k); filter = 2; r = reflect(u, vec3(0.0, 0.0, 1.0)); a = pow(r.x, 2) + filter; gl_FragColor = vec4(a); }`, 'fragment').source);
  assert.ok(out.includes(`${RENAMED_IDENTIFIER_PREFIX}filter=2.0;`), out);
  assert.ok(out.includes('pow(r.x,2.0)'), out);
});

test('prototype parameters named like reserved words are renamed as well', () => {
  const out = compact(translateShader(`#version 120
vec3 limit(vec3 a, float min, float input);
void main() { gl_FragColor = vec4(limit(vec3(1.0), 0.0, 1.0), 1.0); }
vec3 limit(vec3 a, float lo, float hi) { return a; }`, 'fragment').source);
  assert.ok(out.includes(`vec3limit(vec3a,float${RENAMED_IDENTIFIER_PREFIX}min,float${RENAMED_IDENTIFIER_PREFIX}input);`), out);
});
