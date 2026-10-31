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
  if (node.type === 'group') return constant(node.expression);
  if (node.type === 'binary') {
    // Round 79: constant arithmetic in desktop uniform initializers (e.g. 1.0/255.0); scalars and
    // vectors combine component-wise like GLSL.
    const a = constant(node.left), b = constant(node.right), op = node.operator.literal;
    const f = { '+': (x, y) => x + y, '-': (x, y) => x - y, '*': (x, y) => x * y, '/': (x, y) => x / y }[op];
    if (f) {
      if (Array.isArray(a) && Array.isArray(b) && a.length === b.length) return a.map((x, i) => f(x, b[i]));
      if (Array.isArray(a) && !Array.isArray(b)) return a.map(x => f(x, b));
      if (!Array.isArray(a) && Array.isArray(b)) return b.map(y => f(a, y));
      if (!Array.isArray(a) && !Array.isArray(b)) return f(a, b);
    }
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

/**
 * Every built-in function name of GLSL ES 3.00 (spec §8). ES 3.00 §6.1 forbids redeclaring or
 * overloading any of them, while desktop GLSL 1.10/1.20 lets a shader add its own overloads
 * (PZ's util/math.glsl defines `float max(float,float)`, `clamp(vec3,float,float)`, ...).
 */
export const ES300_BUILTIN_FUNCTIONS = new Set([
  // 8.1 angle and trigonometry
  'radians','degrees','sin','cos','tan','asin','acos','atan','sinh','cosh','tanh','asinh','acosh','atanh',
  // 8.2 exponential
  'pow','exp','log','exp2','log2','sqrt','inversesqrt',
  // 8.3 common
  'abs','sign','floor','trunc','round','roundEven','ceil','fract','mod','modf','min','max','clamp','mix',
  'step','smoothstep','isnan','isinf','floatBitsToInt','floatBitsToUint','intBitsToFloat','uintBitsToFloat',
  // 8.4 floating-point pack and unpack
  'packSnorm2x16','unpackSnorm2x16','packUnorm2x16','unpackUnorm2x16','packHalf2x16','unpackHalf2x16',
  // 8.5 geometric
  'length','distance','dot','cross','normalize','faceforward','reflect','refract',
  // 8.6 matrix
  'matrixCompMult','outerProduct','transpose','determinant','inverse',
  // 8.7 vector relational
  'lessThan','lessThanEqual','greaterThan','greaterThanEqual','equal','notEqual','any','all','not',
  // 8.8 texture lookup
  'textureSize','texture','textureProj','textureLod','textureOffset','texelFetch','texelFetchOffset',
  'textureProjOffset','textureLodOffset','textureProjLod','textureProjLodOffset','textureGrad',
  'textureGradOffset','textureProjGrad','textureProjGradOffset',
  // 8.9 fragment processing
  'dFdx','dFdy','fwidth'
]);
/** Prefix for user functions that carry a built-in name; no desktop identifier may already use it. */
export const USER_BUILTIN_PREFIX = 'pz_user_';

const vectorSize = type => /^[ibu]?vec([234])$/.exec(type ?? '')?.[1] * 1 || (['float','int','uint','bool'].includes(type) ? 1 : 0);
const scalarOf = type => type?.startsWith('i') ? 'int' : type?.startsWith('u') ? 'uint' : type?.startsWith('b') ? 'bool' : 'float';
const vectorOf = (scalar, size) => size === 1 ? scalar : `${{float:'',int:'i',uint:'u',bool:'b'}[scalar]}vec${size}`;
function matrixShape(type) {
  const m = /^mat([234])(?:x([234]))?$/.exec(type ?? '');
  return m ? { columns: Number(m[1]), rows: Number(m[2] ?? m[1]) } : null;
}
const matrixOf = (columns, rows) => columns === rows ? `mat${columns}` : `mat${columns}x${rows}`;
function typeName(specifier, quantifier) {
  const inner = specifier?.specifier;
  const base = inner?.token ?? inner?.identifier ?? inner?.typeName?.identifier;
  if (!base) return undefined;
  return (quantifier?.length || specifier?.quantifier?.length) ? `${base}[]` : base;
}
const callName = node => node.identifier?.type === 'identifier' ? node.identifier.identifier : undefined;
const callArgs = node => (node.args ?? []).filter(n => n.type !== 'literal' || n.literal !== ',');

/** Desktop 1.20 implicit conversions in calls: int/uint (and their vectors) to float vectors. */
function convertible(from, to) {
  if (from === to) return true;
  const size = vectorSize(from);
  return size > 0 && size === vectorSize(to) && scalarOf(to) === 'float' && ['int','uint'].includes(scalarOf(from));
}

const SAME_AS_FIRST = new Set(['radians','degrees','sin','cos','tan','asin','acos','atan','sinh','cosh','tanh',
  'asinh','acosh','atanh','pow','exp','log','exp2','log2','sqrt','inversesqrt','abs','sign','floor','trunc','round',
  'roundEven','ceil','fract','mod','modf','min','max','clamp','mix','normalize','faceforward','reflect','refract',
  'dFdx','dFdy','fwidth','matrixCompMult','inverse','not']);
const TEXTURE_LOOKUPS = new Set(['texture','textureProj','textureLod','textureOffset','texelFetch','texelFetchOffset',
  'textureProjOffset','textureLodOffset','textureProjLod','textureProjLodOffset','textureGrad','textureGradOffset',
  'textureProjGrad','textureProjGradOffset']);
const LEGACY_LOOKUPS = new Set(['texture2D','texture2DProj','texture2DLod','textureCube','textureCubeLod','texture3D',
  'texture2DLodEXT','texture2DGradARB']);
/** Return type of a GLSL ES 3.00 built-in for the given argument types, or undefined if unknown. */
function builtinReturnType(name, types) {
  const [a, b] = types, last = types.at(-1);
  if (SAME_AS_FIRST.has(name)) return a;
  if (name === 'step' || name === 'smoothstep') return last;
  if (['length','distance','dot','determinant'].includes(name)) return 'float';
  if (name === 'cross') return 'vec3';
  if (name === 'any' || name === 'all') return 'bool';
  if (['lessThan','lessThanEqual','greaterThan','greaterThanEqual','equal','notEqual','isnan','isinf'].includes(name))
    return vectorSize(a) ? vectorOf('bool', vectorSize(a)) : undefined;
  if (name === 'floatBitsToInt') return vectorSize(a) ? vectorOf('int', vectorSize(a)) : undefined;
  if (name === 'floatBitsToUint') return vectorSize(a) ? vectorOf('uint', vectorSize(a)) : undefined;
  if (name === 'intBitsToFloat' || name === 'uintBitsToFloat') return vectorSize(a) ? vectorOf('float', vectorSize(a)) : undefined;
  if (name.startsWith('pack')) return 'uint';
  if (name.startsWith('unpack')) return 'vec2';
  if (name === 'outerProduct') return vectorSize(a) && vectorSize(b) ? matrixOf(vectorSize(b), vectorSize(a)) : undefined;
  if (name === 'transpose') { const m = matrixShape(a); return m ? matrixOf(m.rows, m.columns) : undefined; }
  if (LEGACY_LOOKUPS.has(name)) return 'vec4';
  if (TEXTURE_LOOKUPS.has(name)) {
    if (!a?.includes('sampler')) return undefined;
    return a.includes('Shadow') ? 'float' : a.startsWith('isampler') ? 'ivec4' : a.startsWith('usampler') ? 'uvec4' : 'vec4';
  }
  if (name === 'textureSize') {
    if (!a?.includes('sampler')) return undefined;
    return /3D|Array/.test(a) && !/Cube/.test(a) ? 'ivec3' : 'ivec2';
  }
  return undefined;
}

/**
 * GLSL ES 3.00 rejects user functions named like a built-in (spec §6.1); desktop 1.10/1.20 accepts
 * them as extra overloads next to the built-ins. Each such user function (prototype or definition)
 * is renamed to USER_BUILTIN_PREFIX + name, and exactly the calls desktop overload resolution sends
 * to it are renamed too: a user overload is visible from its first declaration onwards (textual
 * order, as in a desktop compile). Resolution follows the desktop version of the source:
 *  - 1.10/1.20: user overloads sit next to the built-ins; a call goes to the user overload whose
 *    parameter types match the argument types exactly, otherwise it keeps the built-in. When a call's
 *    argument types cannot be inferred, or only an implicit int->float conversion would select a user
 *    overload (the built-in being an equal candidate), the translation fails instead of guessing.
 *  - 1.30+ (spec §6.1/§4.2.2 scoping): a visible user declaration hides every built-in of that name,
 *    so a call goes to the exact user match, else to the single user overload reachable by implicit
 *    int->float conversion. A call no user overload can take keeps the built-in: desktop would reject
 *    it if the declaration were visible there, so it only became visible through the per-stage merge
 *    of separately compiled units (gl.mjs buildStages).
 */
function renameBuiltinOverloads(ast, version) {
  const hidesBuiltins = version >= 130;
  const isFunctionNode = node => node.type === 'function' ||
    (node.type === 'declaration_statement' && node.declaration?.type === 'function_prototype');
  const prototypeOf = node => node.type === 'function' ? node.prototype : node.declaration;
  const top = ast.program.filter(isFunctionNode);
  if (!top.some(node => ES300_BUILTIN_FUNCTIONS.has(prototypeOf(node).header.name.identifier))) return [];
  walk(ast.program, node => {
    if (node.type === 'identifier' && node.identifier.startsWith(USER_BUILTIN_PREFIX))
      throw new Error(`Shader identifier collides with the built-in overload prefix: ${node.identifier}`);
  });
  // Variable types through the parser's scopes (references are resolved by scope, not by name).
  const declared = new Map(), references = new Map(), structs = new Map();
  walk(ast.program, node => {
    if (node.type === 'declarator_list') for (const entry of node.declarations ?? [])
      declared.set(entry.identifier, typeName(node.specified_type.specifier, entry.quantifier));
    if (node.type === 'parameter_declaration' && node.identifier) {
      // Parser bindings point at the parameter_declaration node itself, at the identifier for variables.
      const type = typeName(node.specifier, node.quantifier);
      declared.set(node, type); declared.set(node.identifier, type);
    }
    if (node.type === 'struct' && node.typeName) {
      const members = new Map();
      for (const member of node.declarations ?? []) for (const entry of member.declaration?.declarations ?? [])
        members.set(entry.identifier.identifier, typeName(member.declaration.specified_type.specifier, entry.quantifier));
      structs.set(node.typeName.identifier, members);
    }
  });
  for (const scope of ast.scopes) for (const binding of Object.values(scope.bindings)) {
    const type = declared.get(binding.declaration);
    if (type) for (const reference of binding.references) references.set(reference, type);
  }
  // User functions by name, in source order, for return types of calls to them.
  const userReturn = new Map();
  for (const node of top) {
    const header = prototypeOf(node).header, name = header.name.identifier;
    const params = (prototypeOf(node).parameters ?? []).map(p => typeName(p.specifier, p.quantifier)).filter(t => t !== 'void');
    const list = userReturn.get(name) ?? [];
    if (!list.some(o => o.params.join() === params.join())) list.push({ params, returnType: typeName(header.returnType.specifier) });
    userReturn.set(name, list);
  }
  const visible = new Map(); // builtin name -> [{params, returnType}] declared so far
  const resolved = new Map(); // call node -> overload or null (built-in)
  function select(type, selector) {
    if (!type) return undefined;
    if (selector.type === 'quantifier') {
      if (type.endsWith('[]')) return type.slice(0, -2);
      const m = matrixShape(type);
      if (m) return vectorOf('float', m.rows);
      return vectorSize(type) > 1 ? scalarOf(type) : undefined;
    }
    if (selector.type === 'field_selection') {
      const field = selector.selection.identifier;
      if (structs.has(type)) return structs.get(type).get(field);
      const size = vectorSize(type);
      if (size > 1 && /^[xyzwrgbastpq]{1,4}$/.test(field)) return vectorOf(scalarOf(type), field.length);
      return undefined;
    }
    if (selector.type === 'literal' && ['++','--'].includes(selector.literal)) return type;
    return undefined;
  }
  function typeOf(node) {
    if (!node) return undefined;
    switch (node.type) {
      case 'float_constant': return 'float';
      case 'int_constant': return /[uU]$/.test(node.token) ? 'uint' : 'int';
      case 'bool_constant': return 'bool';
      case 'identifier': return references.get(node);
      case 'group': return typeOf(node.expression);
      case 'unary': return node.operator.literal === '!' ? 'bool' : typeOf(node.expression);
      case 'assignment': return typeOf(node.left);
      case 'ternary': return typeOf(node.left) ?? typeOf(node.right);
      case 'sequence': return typeOf(node.right ?? node.expressions?.at(-1));
      case 'postfix': {
        let type = typeOf(node.expression), chain = node.postfix;
        while (chain) {
          if (chain.type === 'postfix') { type = select(type, chain.expression); chain = chain.postfix; }
          else { type = select(type, chain); chain = null; }
        }
        return type;
      }
      case 'binary': {
        const op = node.operator.literal;
        if (['==','!=','<','<=','>','>=','&&','||','^^'].includes(op)) return 'bool';
        const left = typeOf(node.left), right = typeOf(node.right);
        if (!left || !right) return undefined;
        if (['<<','>>'].includes(op)) return left;
        if (left === right) return left;
        const ml = matrixShape(left), mr = matrixShape(right);
        if (op === '*' && ml && mr) return matrixOf(mr.columns, ml.rows);
        if (op === '*' && ml && vectorSize(right) > 1) return vectorOf('float', ml.rows);
        if (op === '*' && mr && vectorSize(left) > 1) return vectorOf('float', mr.columns);
        if (vectorSize(left) === 1) return right;
        if (vectorSize(right) === 1) return left;
        return undefined;
      }
      case 'function_call': {
        if (node.identifier.type === 'type_specifier') return typeName(node.identifier);
        if (node.identifier.type === 'postfix') return 'int'; // array.length()
        const name = callName(node);
        if (ES300_BUILTIN_FUNCTIONS.has(name) && visible.has(name)) {
          const overload = resolve(node);
          if (overload) return overload.returnType;
        }
        const types = callArgs(node).map(typeOf);
        if (userReturn.has(name) && !ES300_BUILTIN_FUNCTIONS.has(name)) {
          const list = userReturn.get(name);
          const match = list.find(o => o.params.length === types.length && o.params.every((p, i) => p === types[i]));
          if (match) return match.returnType;
          const returns = new Set(list.map(o => o.returnType));
          return returns.size === 1 ? [...returns][0] : undefined;
        }
        return builtinReturnType(name, types);
      }
      default: return undefined;
    }
  }
  function resolve(node) {
    if (resolved.has(node)) return resolved.get(node);
    const name = callName(node), args = callArgs(node);
    const candidates = (visible.get(name) ?? []).filter(o => o.params.length === args.length);
    let result = null;
    if (candidates.length > 0) {
      const types = args.map(typeOf);
      const signature = `${name}(${types.map(t => t ?? '?').join(', ')})`;
      const overloads = (visible.get(name) ?? []).map(o => `${name}(${o.params.join(', ')})`).join(', ');
      const exact = candidates.filter(o => o.params.every((p, i) => p === types[i]));
      const reachable = candidates.filter(o => o.params.every((p, i) => !types[i] || convertible(types[i], p)));
      if (hidesBuiltins) {
        // No reachable user overload: the desktop unit holding this call cannot have seen the user
        // declaration (it would not compile), only the per-stage merge made it visible: built-in.
        if (exact.length === 1) result = exact[0];
        else if (types.every(Boolean) && reachable.length === 1) result = reachable[0];
        else if (reachable.length > 0) throw new Error(`Cannot resolve ${signature} among the user overloads ` +
          `${overloads} that hide the built-in ${name} in GLSL ${version}`);
      } else if (types.every(Boolean)) {
        result = exact[0] ?? null;
        if (!result && reachable.length > 0)
          throw new Error(`Call ${signature} matches the user overload ${overloads} only by implicit conversion; ` +
            'desktop overload resolution against the built-in is ambiguous here');
      } else if (reachable.length > 0) {
        throw new Error(`Cannot infer the argument types of ${signature} to choose between the built-in and the ` +
          `user overload ${overloads}`);
      }
    }
    resolved.set(node, result);
    return result;
  }
  const renamed = new Set();
  for (const node of ast.program) {
    if (isFunctionNode(node)) {
      const proto = prototypeOf(node), name = proto.header.name.identifier;
      if (ES300_BUILTIN_FUNCTIONS.has(name)) {
        const params = (proto.parameters ?? []).map(p => typeName(p.specifier, p.quantifier)).filter(t => t !== 'void');
        if (params.some(p => !p)) throw new Error(`Cannot read the parameter types of user function ${name}`);
        const list = visible.get(name) ?? [];
        const existing = list.find(o => o.params.join() === params.join());
        if (!existing) list.push({ params, returnType: typeName(proto.header.returnType.specifier) });
        visible.set(name, list);
        proto.header.name.identifier = USER_BUILTIN_PREFIX + name;
        renamed.add(name);
      }
    }
    const calls = [];
    walk(node, child => { if (child.type === 'function_call' && visible.has(callName(child))) calls.push(child); });
    for (const call of calls) resolve(call);
    for (const call of calls) if (resolved.get(call)) call.identifier.identifier = USER_BUILTIN_PREFIX + callName(call);
  }
  return [...renamed].sort();
}

/**
 * WebGL2 has no glAlphaFunc, so the desktop alpha test is reproduced inside the shader path this
 * backend generates: the injected call reads the fragment's own output colour and `discard`s exactly
 * where desktop GL would reject the fragment (before blending). The comparison arrives as int/float
 * uniforms that port/runtime/gl.mjs sets from the recorded glAlphaFunc state whenever a program with
 * this injection becomes current; -1 means "GL_ALPHA_TEST disabled", which is the desktop default.
 * All nodes come from parsing real GLSL, never from string surgery.
 */
const ALPHA_TEST_FUNCTION = `
void pzAlphaTestDiscard() {
    if (pzAlphaTestFunc >= 0) {
        float pzAlphaValue = pzFragColor.a;
        bool pzAlphaPass = false;
        if (pzAlphaTestFunc == 512) pzAlphaPass = false;
        else if (pzAlphaTestFunc == 513) pzAlphaPass = pzAlphaValue < pzAlphaTestRef;
        else if (pzAlphaTestFunc == 514) pzAlphaPass = pzAlphaValue == pzAlphaTestRef;
        else if (pzAlphaTestFunc == 515) pzAlphaPass = pzAlphaValue <= pzAlphaTestRef;
        else if (pzAlphaTestFunc == 516) pzAlphaPass = pzAlphaValue > pzAlphaTestRef;
        else if (pzAlphaTestFunc == 517) pzAlphaPass = pzAlphaValue != pzAlphaTestRef;
        else if (pzAlphaTestFunc == 518) pzAlphaPass = pzAlphaValue >= pzAlphaTestRef;
        else pzAlphaPass = true;
        if (!pzAlphaPass) discard;
    }
}
`;

/** Parse, preprocess, transform AST nodes and generate GLSL ES. Never edit by regex. */
export function translateShader(source, stage, { defines = {} } = {}) {
  if (!['vertex','fragment'].includes(stage)) throw new Error(`Unknown shader stage: ${stage}`);
  const prepared = preprocess(source, { defines, preserveComments:true, stopOnError:true,
    preserve:{version:()=>true,extension:()=>true} });
  const ast = parse(prepared, { stage, quiet:true });
  promoteIntegers(ast);
  // Desktop version of this source (no directive means 1.10); it decides how user overloads of
  // built-in names resolve, see renameBuiltinOverloads.
  const versionNode = ast.program.find(node => node.type === 'preprocessor' && /^\s*#\s*version\b/.test(node.line));
  const builtinOverloads = renameBuiltinOverloads(ast, Number(versionNode?.line.trim().split(/\s+/)[1] ?? 110));
  const declarations = new Set();
  const replacements = new Set();
  const uniformDefaults = [];
  let injectedAlphaTest = false;
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
  // Fragment stage: the generated pzFragColor is the fragment colour the alpha test must judge.
  const usesFragColor = replacements.has('gl_FragColor');
  let alphaTestHeader = '';
  // A unit without main() is a desktop "library" shader object (e.g. puddles_common.frag.glsl) that
  // desktop GL links next to the unit holding main(). gl.mjs merges such units per stage at link time,
  // and the alpha test is injected there, into the merged source that owns main().
  const hasMain = ast.program.some(node => node.type === 'function' &&
    node.prototype?.header?.name?.identifier === 'main' && Array.isArray(node.body?.statements));
  // GLSL 1.10/1.20 allows non-constant initializers on plain globals (e.g. `float x = Uniform[0].y;`),
  // GLSL ES 3.00 does not. Desktop semantics are "evaluated before main() runs", so such initializers
  // become assignments at the top of main(), in declaration order. A library unit (no main) carries
  // them in globalInits and gl.mjs places them in the merged main().
  const globalInits = [];
  for (const node of ast.program) {
    if (node.type !== 'declaration_statement' && node.type !== 'declarator_list') continue;
    const list = node.type === 'declaration_statement' ? node.declaration : node;
    if (list?.type !== 'declarator_list') continue;
    const qualifiers = list.specified_type?.qualifiers ?? [];
    if (qualifiers.length > 0) continue; // const/uniform/in/out keep their own rules
    for (const declaration of list.declarations ?? []) {
      if (!declaration.initializer || declaration.quantifier) continue;
      let isConstant = true;
      try { constant(declaration.initializer); } catch { isConstant = false; }
      if (isConstant) continue;
      globalInits.push(`${declaration.identifier.identifier} = ${generate(declaration.initializer).trim()};`);
      delete declaration.initializer; delete declaration.equal;
    }
  }
  if (globalInits.length > 0 && hasMain) {
    const statements = parse(`#version 300 es\nvoid pzGlobalInit(){${globalInits.join('\n')}}`, {quiet:true, stage})
      .program.find(node => node.type === 'function')?.body?.statements ?? [];
    if (statements.length !== globalInits.length) throw new Error('Global initializer hoist failed to parse');
    const main = ast.program.find(node => node.type === 'function' && node.prototype?.header?.name?.identifier === 'main');
    main.body.statements.unshift(...statements);
  }
  if (stage === 'fragment' && usesFragColor && hasMain) {
    const call = parse('#version 300 es\nvoid pzAlphaTestMain(){pzAlphaTestDiscard();}', {quiet:true, stage})
        .program.find(node => node.type === 'function')?.body?.statements ?? [];
    if (call.length !== 1) throw new Error('Alpha-test call statement failed to parse');
    for (const node of ast.program) {
        if (node.type !== 'function') continue;
        if (node.prototype?.header?.name?.identifier !== 'main') continue;
        if (!Array.isArray(node.body?.statements))
            throw new Error('Fragment main() has no statement list to extend');
        node.body.statements.push(structuredClone(call[0]));
        injectedAlphaTest = true;
    }
    if (!injectedAlphaTest) throw new Error('Fragment shader has no main() to attach the alpha test to');
    alphaTestHeader = 'uniform int pzAlphaTestFunc;\nuniform float pzAlphaTestRef;\n' + ALPHA_TEST_FUNCTION + '\n';
  }
  const header = parse('#version 300 es\nprecision highp float;\nprecision highp int;\n' + [...declarations].join('\n') + '\n' + alphaTestHeader, {quiet:true,stage});
  ast.program.unshift(...header.program);
  const output = generate(ast);
  // Parse the generated output as well; WebGL compile/link remains authoritative.
  parse(output, {quiet:true,stage});
  return {source:output,replacements:[...replacements].sort(),declarations:[...declarations],uniformDefaults,alphaTestEmulated:injectedAlphaTest,hasMain,globalInits:hasMain?[]:globalInits,builtinOverloads};
}
