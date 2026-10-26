import { parse, generate } from '@shaderfrog/glsl-parser/index.js';
import preprocess from '@shaderfrog/glsl-parser/preprocessor/index.js';

const functions = new Map([
  ['texture2D','texture'], ['textureCube','texture'], ['texture3D','texture'],
  ['texture2DProj','textureProj'], ['texture2DLod','textureLod'], ['textureCubeLod','textureLod'],
  ['texture2DGradARB','textureGrad'], ['texture2DLodEXT','textureLod']
]);

function walk(value, visitor) {
  if (!value || typeof value !== 'object') return;
  if (value.type) visitor(value);
  for (const [key, child] of Object.entries(value)) if (key !== 'scopes' && key !== 'location') {
    if (Array.isArray(child)) for (const node of child) walk(node, visitor);
    else walk(child, visitor);
  }
}

function constant(node) {
  if (node.type === 'float_constant' || node.type === 'int_constant') return Number(node.token.replace(/[uUfF]$/, ''));
  if (node.type === 'bool_constant') return node.token === 'true' ? 1 : 0;
  if (node.type === 'unary') {
    const value = constant(node.expression);
    if (node.operator.literal === '-') return -value;
    if (node.operator.literal === '+') return value;
  }
  if (node.type === 'function_call') {
    const type = node.identifier.specifier?.token ?? node.identifier.identifier;
    const args = node.args.filter(n=>n.type !== 'literal' || n.literal !== ',').map(constant).flat();
    if (['float','int','bool'].includes(type) && args.length === 1) return args[0];
    const count = new Map([['vec2',2],['vec3',3],['vec4',4],['ivec2',2],['ivec3',3],['ivec4',4],['bvec2',2],['bvec3',3],['bvec4',4]]).get(type);
    if (count && args.length === 1) return Array(count).fill(args[0]);
    if (count && args.length === count) return args;
    if (['mat2','mat3','mat4'].includes(type)) {
      const size = Number(type.at(-1));
      if (args.length === 1) return Array.from({length:size*size},(_,i)=>i%(size+1)===0?args[0]:0);
      if (args.length === size*size) return args;
    }
  }
  throw new Error(`Unsupported uniform initializer AST: ${node.type}`);
}

// Desktop GLSL allows scalar int-to-float promotion in arithmetic. ES requires
// it to be explicit. Resolve identifier nodes through parser scopes, not names.
function promoteIntegers(ast) {
  const declared = new Map(), references = new Map();
  walk(ast.program, node => {
    if (node.type === 'declarator_list') for (const entry of node.declarations)
      declared.set(entry.identifier,node.specified_type.specifier.specifier.token);
    if (node.type === 'parameter_declaration' && node.identifier)
      declared.set(node.identifier,node.specifier?.specifier?.token ?? node.specifier?.token);
  });
  for (const scope of ast.scopes) for (const binding of Object.values(scope.bindings)) {
    const type = declared.get(binding.declaration);
    if (type) for (const reference of binding.references) references.set(reference,type);
  }
  const floatType = type => ['float','vec2','vec3','vec4','mat2','mat3','mat4'].includes(type);
  const scalarInt = type => type === 'int' || type === 'uint';
  function typeOf(node) {
    if (!node) return undefined;
    if (node.type === 'float_constant') return 'float';
    if (node.type === 'int_constant') return 'int';
    if (node.type === 'identifier') return references.get(node);
    if (node.type === 'group') return typeOf(node.expression);
    if (node.type === 'unary') return typeOf(node.expression);
    if (node.type === 'postfix') {
      const type = typeOf(node.expression), field = node.postfix?.selection?.identifier;
      if (field && floatType(type)) return field.length === 1 ? 'float' : `vec${field.length}`;
      return type;
    }
    if (node.type === 'binary') {
      if (['==','!=','<','<=','>','>=','&&','||','^^'].includes(node.operator.literal)) return 'bool';
      const left = typeOf(node.left), right = typeOf(node.right);
      return floatType(left) ? left : floatType(right) ? right : left === right ? left : undefined;
    }
    if (node.type === 'function_call') {
      const name = node.identifier.specifier?.token ?? node.identifier.identifier;
      if (floatType(name) || scalarInt(name)) return name;
      if (['texture','texture2D','textureCube','texture3D'].includes(name)) return 'vec4';
      if (['dot','length','distance'].includes(name)) return 'float';
      if (['sin','cos','tan','abs','sqrt','floor','ceil','fract','mod','min','max','clamp','mix','pow','normalize'].includes(name)) return typeOf(node.args[0]);
      const overloads = ast.scopes[0]?.functions?.[name];
      if (overloads) {
        const types = new Set(Object.values(overloads).map(x=>x.returnType));
        if (types.size === 1) return [...types][0];
      }
    }
  }
  const cast = expression => ({type:'function_call',identifier:{type:'type_specifier',specifier:{type:'keyword',token:'float'},quantifier:null},
    lp:{type:'literal',literal:'('},args:[expression],rp:{type:'literal',literal:')'}});
  walk(ast.program, node => {
    if (node.type === 'binary' && ['+','-','*','/','<','>','<=','>=','==','!='].includes(node.operator.literal)) {
      const left = typeOf(node.left), right = typeOf(node.right);
      if (floatType(left) && scalarInt(right)) node.right = cast(node.right);
      if (floatType(right) && scalarInt(left)) node.left = cast(node.left);
    }
    if (node.type === 'declarator_list') for (const entry of node.declarations) {
      if (node.specified_type.specifier.specifier.token === 'float' && scalarInt(typeOf(entry.initializer))) entry.initializer = cast(entry.initializer);
    }
  });
}

/** Parse, preprocess, transform AST nodes and generate GLSL ES. Never edit by regex. */
export function translateShader(source, stage, { defines = {} } = {}) {
  if (!['vertex','fragment'].includes(stage)) throw new Error(`Unknown shader stage: ${stage}`);
  const prepared = preprocess(source, { defines, preserveComments:true, stopOnError:true,
    preserve:{version:()=>true,extension:()=>true} });
  const ast = parse(prepared, { stage, quiet:true });
  promoteIntegers(ast);
  const declarations = new Set();
  const replacements = new Set();
  const uniformDefaults = [];
  const direction = stage === 'vertex' ? 'out' : 'in';
  const builtins = new Map([
    ['gl_ModelViewProjectionMatrix',['pzModelViewProjectionMatrix','uniform mat4 pzModelViewProjectionMatrix;']],
    ['gl_ProjectionMatrix',['pzProjectionMatrix','uniform mat4 pzProjectionMatrix;']],
    ['gl_ModelViewMatrix',['pzModelViewMatrix','uniform mat4 pzModelViewMatrix;']],
    ['gl_NormalMatrix',['pzNormalMatrix','uniform mat3 pzNormalMatrix;']],
    ['gl_TexCoord',['pzTexCoord',`${direction} vec4 pzTexCoord[8];`]],
    ['gl_FrontColor',['pzFrontColor','out vec4 pzFrontColor;']],
    ['gl_Color', stage === 'vertex' ? ['pzColor','in vec4 pzColor;'] : ['pzFrontColor','in vec4 pzFrontColor;']],
    ['gl_Vertex',['pzVertex','in vec4 pzVertex;']],
    ['gl_Normal',['pzNormal','in vec3 pzNormal;']],
    ['gl_FragColor',['pzFragColor','layout(location=0) out vec4 pzFragColor;']]
  ]);
  for (let i = 0; i < 8; i++) builtins.set(`gl_MultiTexCoord${i}`, [`pzMultiTexCoord${i}`,`in vec4 pzMultiTexCoord${i};`]);
  const allowed = new Set(['gl_Position','gl_PointSize','gl_FragCoord','gl_FrontFacing','gl_PointCoord',
    'gl_FragDepth','gl_VertexID','gl_InstanceID','gl_DepthRange']);
  ast.program = ast.program.filter(node => {
    if (node.type !== 'preprocessor') return true;
    const words = node.line.trim().split(/\s+/);
    if (words[0] === '#version') {
      if (!['100','120','130','140','150','300','330'].includes(words[1])) throw new Error(`Unsupported desktop GLSL version: ${words[1]}`);
      node.line = ''; return true; // Keep whitespace/comments attached to the directive.
    }
    if (words[0] === '#extension') {
      const name = words[1];
      if (['GL_ARB_shader_texture_lod','GL_EXT_shader_texture_lod','GL_OES_standard_derivatives'].includes(name)) { node.line = ''; return true; }
      throw new Error(`Unsupported GLSL extension: ${name}`);
    }
    throw new Error(`Unresolved shader directive: ${words[0]}`);
  });
  const userFunctions = new Set(Object.entries(ast.scopes[0]?.functions ?? {})
    .filter(([,overloads]) => Object.values(overloads).some(x=>x.declaration)).map(([name])=>name));
  walk(ast.program, node => {
    if (node.type === 'declarator_list' && node.specified_type.qualifiers?.some(q=>q.token==='uniform')) {
      for (const declaration of node.declarations) if (declaration.initializer) {
        if (declaration.quantifier) throw new Error('Uniform array initializers are not supported yet');
        uniformDefaults.push({name:declaration.identifier.identifier, type:node.specified_type.specifier.specifier.token,
          value:constant(declaration.initializer)});
        delete declaration.initializer; delete declaration.equal;
      }
    }
    if (node.type === 'fully_specified_type') for (const qualifier of node.qualifiers ?? []) {
      if (qualifier.token === 'attribute') {
        if (stage !== 'vertex') throw new Error('attribute is only valid in a vertex shader');
        qualifier.token = 'in'; replacements.add('attribute');
      }
      if (qualifier.token === 'varying') { qualifier.token = direction; replacements.add('varying'); }
    }
    if (node.type !== 'identifier') return;
    const name = node.identifier;
    if (name.startsWith('pz') && ['pzVertex','pzColor','pzFrontColor','pzFragColor','pzTexCoord',
      'pzModelViewProjectionMatrix','pzProjectionMatrix','pzModelViewMatrix','pzNormalMatrix','pzFtransform'].includes(name))
      throw new Error(`Shader identifier collides with compatibility name: ${name}`);
    if (functions.has(name) && !userFunctions.has(name)) { node.identifier = functions.get(name); replacements.add(name); }
    if (name === 'ftransform' && !userFunctions.has(name)) {
      if (stage !== 'vertex') throw new Error('ftransform is only valid in a vertex shader');
      node.identifier = 'pzFtransform';
      declarations.add('in vec4 pzVertex;'); declarations.add('uniform mat4 pzModelViewProjectionMatrix;');
      declarations.add('vec4 pzFtransform(){return pzModelViewProjectionMatrix*pzVertex;}');
    }
    if (builtins.has(name)) {
      const [replacement, declaration] = builtins.get(name);
      if (stage === 'fragment' && (name === 'gl_Vertex' || name.startsWith('gl_MultiTexCoord') || name === 'gl_FrontColor' || name === 'gl_Normal'))
        throw new Error(`Vertex builtin in fragment shader: ${name}`);
      node.identifier = replacement; declarations.add(declaration); replacements.add(name);
    } else if (name.startsWith('gl_') && !allowed.has(name)) throw new Error(`Unsupported GLSL builtin: ${name}`);
  });
  const header = parse('#version 300 es\nprecision highp float;\nprecision highp int;\n' + [...declarations].join('\n') + '\n', {quiet:true,stage});
  ast.program.unshift(...header.program);
  const output = generate(ast);
  // Parse the generated output as well; WebGL compile/link remains authoritative.
  parse(output, {quiet:true,stage});
  return {source:output,replacements:[...replacements].sort(),declarations:[...declarations],uniformDefaults};
}
