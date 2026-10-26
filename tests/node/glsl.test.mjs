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
