import test from 'node:test';
import assert from 'node:assert/strict';
import { translateShader } from '../../port/runtime/glsl.mjs';

test('GLSL AST translation preserves identifiers and expands macros', () => {
  const source = `#version 120
    #define SAMPLE texture2D
    // texture2D should remain in this comment
    varying vec2 uv; uniform sampler2D image; float texture2DCount=1.;
    void main(){gl_FragColor=SAMPLE(image,uv)*texture2DCount;}`;
  const result = translateShader(source,'fragment');
  assert.ok(result.source.startsWith('#version 300 es'));
  assert.ok(result.source.includes('texture2D should remain'));
  assert.ok(result.source.includes('texture2DCount'));
  assert.ok(result.source.includes('texture(image,uv)'));
  assert.ok(result.source.includes('in vec2 uv'));
  assert.ok(result.source.includes('out vec4 pzFragColor'));
});

test('legacy matrices and inputs become explicit shader interfaces', () => {
  const result = translateShader('#version 120\nvoid main(){gl_Position=ftransform();gl_FrontColor=gl_Color;gl_TexCoord[0]=gl_MultiTexCoord0;}','vertex');
  assert.ok(result.source.includes('uniform mat4 pzModelViewProjectionMatrix'));
  assert.ok(result.source.includes('out vec4 pzTexCoord[8]'));
  assert.ok(result.source.includes('in vec4 pzColor'));
});

test('unsupported GLSL and malformed syntax fail explicitly', () => {
  assert.throws(()=>translateShader('void main(){gl_FragData[0]=vec4(1.);}','fragment'),/gl_FragData/);
  assert.throws(()=>translateShader('#version 450\nvoid main(){}','vertex'),/450/);
  assert.throws(()=>translateShader('void main( {','vertex'));
});

test('desktop uniform initializers move to an explicit host initialization list', () => {
  const result = translateShader('uniform float gain=0.5; uniform vec4 tint=vec4(1.,0.,0.,1.); void main(){gl_FragColor=tint*gain;}','fragment');
  assert.deepEqual(result.uniformDefaults,[{name:'gain',type:'float',value:0.5},{name:'tint',type:'vec4',value:[1,0,0,1]}]);
  assert.ok(!result.source.includes('gain=0.5'));
});

test('scalar integer promotion respects variable scopes and array indices', () => {
  const result = translateShader('uniform float x; void main(){float y=x*2-1; int data[2]; int i=1; {int x=3;data[i]=x*2;} gl_FragColor=vec4(y);}','fragment');
  assert.ok(result.source.includes('x*float(2)-float(1)'));
  assert.ok(result.source.includes('int data[2]'));
  assert.ok(result.source.includes('data[i]=x*2'));
});

test('a fragment helper unit without main() translates instead of throwing', () => {
  // Round 50: zombie.core.opengl.ShaderUnit::compile turns every #include into its own
  // ShaderUnit, so media/shaders/puddles_common.frag.glsl is handed to glShaderSource as a
  // fragment-stage unit that only defines mainImage()/puddlesMain() and has no main().
  // Throwing there killed RenderThread::invokeOnRenderContext (diag148.log); the helper must be
  // translated verbatim and report the alpha-test gap honestly.
  const helper = `#version 120
    varying vec4 vertColour;
    void mainImage(out vec4 c, in vec2 uv){c = vertColour*texture2D(sampler,uv);}
    void puddlesMain(){gl_FragColor = vec4(1.0);}`;
  const result = translateShader(helper, 'fragment');
  assert.ok(result.source.startsWith('#version 300 es'));
  assert.ok(result.source.includes('void puddlesMain'));
  assert.ok(result.source.includes('pzFragColor'), 'gl_FragColor is still renamed in a helper unit');
  assert.equal(result.alphaTestEmulated, false, 'nothing to attach the discard to');
  assert.equal(result.hasMain, false, 'gl.mjs marks this unit as a library for the per-stage merge');
  assert.ok(result.globalInits.length === 0);
  // A unit that DOES have main() keeps the injected discard.
  const withMain = translateShader('#version 120\nvoid main(){gl_FragColor=vec4(1.0);}', 'fragment');
  assert.equal(withMain.alphaTestEmulated, true);
  assert.equal(withMain.hasMain, true);
  assert.ok(withMain.source.includes('pzAlphaTestDiscard()'));
});

test('non-constant global initializers move into main() (GLSL ES 3.00 forbids them)', () => {
  // GLSL 1.20 allows `vec2 p = Uniform[0].y;` at global scope; ES 3.00 only allows constant
  // expressions there, so the initializer becomes an assignment at the top of main().
  const src = '#version 120\nuniform vec4 Uniform[2];\nvec2 p = Uniform[0].xy;\nvoid main(){gl_FragColor=vec4(p,0.0,1.0);}';
  const result = translateShader(src, 'fragment');
  assert.deepEqual(result.globalInits, [], 'a unit with main() hoists them itself');
  assert.ok(result.source.indexOf('p = Uniform[0].xy') < result.source.indexOf('pzFragColor=vec4(p'),
    'the assignment must run before main() uses p');
  assert.ok(/vec2 p\s*;/.test(result.source), 'the global keeps its declaration without the initializer');
  // Library units carry them so the merged source can place them in the merged main().
  const lib = translateShader('#version 120\nuniform vec4 Uniform[2];\nvec2 p = Uniform[0].xy;\nvoid use(){gl_FragColor=vec4(p,0.0,1.0);}', 'fragment');
  assert.equal(lib.hasMain, false);
  assert.deepEqual(lib.globalInits, ['p = Uniform[0].xy;']);
});

// Round 79: SmartTexture's shader has `uniform float maskPaddingRadius = 1.0 / 64.0;`.
test('constant arithmetic in uniform initializers is folded', () => {
  const result = translateShader('#version 120\nuniform float r = 1.0 / 64.0;\nuniform vec2 v = vec2(1.0, 2.0) * (2.0 - 1.0);\nvoid main(){gl_FragColor=vec4(r,v,1.0);}', 'fragment');
  assert.deepEqual(result.uniformDefaults.find(u => u.name === 'r').value, 1 / 64);
  assert.deepEqual(result.uniformDefaults.find(u => u.name === 'v').value, [1, 2]);
});
