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

const isFunctionNode = node => node.type === 'function' ||
  (node.type === 'declaration_statement' && node.declaration?.type === 'function_prototype');
const prototypeOf = node => node.type === 'function' ? node.prototype : node.declaration;
const parameterTypes = proto => (proto.parameters ?? []).map(p => typeName(p.specifier, p.quantifier)).filter(t => t !== 'void');
/** Name of the function a call invokes (built-in or user), undefined for constructors and .length(). */
function calleeName(node, structs) {
  if (node.identifier?.type === 'identifier') return node.identifier.identifier;
  const inner = node.identifier?.type === 'type_specifier' ? node.identifier.specifier : undefined;
  if (inner?.type === 'type_name' && !node.identifier.quantifier?.length && !structs.has(inner.identifier)) return inner.identifier;
  return undefined;
}

/**
 * Static types of expressions, from the parser's scopes (identifier references are resolved by
 * scope, not by name). User functions are read from the top-level declarations under their
 * current names, so a fresh instance after renameBuiltinOverloads sees the pz_user_* overloads.
 * `callType(node)` may answer a call first (undefined falls through to the generic rules).
 */
function createTypeInference(ast, { callType } = {}) {
  const declared = new Map(), references = new Map(), structs = new Map();
  walk(ast.program, node => {
    if (node.type === 'declarator_list') for (const entry of node.declarations ?? []) {
      // Bindings point at the identifier, or at the whole declaration for later declarators (`vec3 r,u;`).
      const type = typeName(node.specified_type.specifier, entry.quantifier);
      declared.set(entry.identifier, type); declared.set(entry, type);
    }
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
  // User functions by (current) name, in source order: [{params, returnType}].
  const userFunctions = new Map();
  for (const node of ast.program.filter(isFunctionNode)) {
    const proto = prototypeOf(node), name = proto.header.name.identifier, params = parameterTypes(proto);
    const list = userFunctions.get(name) ?? [];
    if (!list.some(o => o.params.join() === params.join())) list.push({ params, returnType: typeName(proto.header.returnType.specifier) });
    userFunctions.set(name, list);
  }
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
  function userCallType(name, node) {
    const list = userFunctions.get(name), types = callArgs(node).map(typeOf);
    const match = list.find(o => o.params.length === types.length && o.params.every((p, i) => p === types[i]));
    if (match) return match.returnType;
    const returns = new Set(list.map(o => o.returnType));
    return returns.size === 1 ? [...returns][0] : undefined;
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
        // Desktop implicit int->float: an int side combined with a float side yields the float type.
        if (vectorSize(left) === vectorSize(right) && isFloating(left) !== isFloating(right))
          return isFloating(left) ? left : right;
        if (vectorSize(left) === 1) return vectorSize(right) > 1 && isFloating(left) && !isFloating(right) ? vectorOf('float', vectorSize(right)) : right;
        if (vectorSize(right) === 1) return vectorSize(left) > 1 && isFloating(right) && !isFloating(left) ? vectorOf('float', vectorSize(left)) : left;
        return undefined;
      }
      case 'function_call': {
        if (node.identifier.type === 'postfix') return 'int'; // array.length()
        const name = calleeName(node, structs);
        if (name === undefined) return typeName(node.identifier); // constructor
        const answered = callType?.(node);
        if (answered !== undefined) return answered;
        if (userFunctions.has(name) && !ES300_BUILTIN_FUNCTIONS.has(name)) return userCallType(name, node);
        if (node.identifier.type !== 'identifier') return undefined; // undeclared user function
        return builtinReturnType(name, callArgs(node).map(typeOf));
      }
      default: return undefined;
    }
  }
  return { typeOf, userFunctions, structs };
}

/** float, vecN and matrices (desktop conversions target these). */
const isFloating = type => !!type && (matrixShape(type) !== null || (vectorSize(type) > 0 && scalarOf(type) === 'float'));
/** int, uint and their vectors (the sources of desktop implicit conversions). */
const isInteger = type => vectorSize(type) > 0 && ['int','uint'].includes(scalarOf(type));
function intLiteralValue(token) {
  const t = token.replace(/[uU]$/, '');
  if (/^0[xX]/.test(t)) return parseInt(t.slice(2), 16);
  if (/^0[0-7]+$/.test(t)) return parseInt(t, 8);
  return Number(t);
}
/**
 * The explicit form of a desktop implicit int->float conversion of `expression` (static type
 * `type`): an int literal becomes the equal float literal (also under unary +/-), anything else is
 * wrapped in the float constructor of the same size (float(x), vec2(v), ...).
 */
function toFloating(expression, type) {
  if (expression.type === 'int_constant') {
    const value = intLiteralValue(expression.token);
    const text = String(value);
    return { ...expression, type: 'float_constant', token: /[.eE]/.test(text) ? text : `${text}.0` };
  }
  if (expression.type === 'unary' && ['-','+'].includes(expression.operator.literal) && expression.expression.type === 'int_constant')
    return { ...expression, expression: toFloating(expression.expression, type) };
  const whitespace = expression.whitespace;
  return { type: 'function_call',
    identifier: { type: 'type_specifier', specifier: { type: 'keyword', token: vectorOf('float', vectorSize(type)) }, quantifier: null },
    lp: { type: 'literal', literal: '(' }, args: [expression], rp: { type: 'literal', literal: ')', whitespace } };
}

/**
 * Desktop GLSL 1.20+ converts int/uint (scalars and vectors) to the float type of the same size
 * implicitly (spec 1.20 §4.1.10); GLSL ES 3.00 has no implicit conversions. Made explicit here for
 * binary arithmetic/comparison operands (the int side of an int/float pair), initializers and
 * assignments of float-typed variables. Calls are handled by convertCallArguments.
 */
function promoteIntegers(ast) {
  const { typeOf } = createTypeInference(ast);
  const sameShape = (from, to) => vectorSize(from) === vectorSize(to) && !matrixShape(to);
  walk(ast.program, node => {
    if (node.type === 'binary' && ['+','-','*','/','<','>','<=','>=','==','!='].includes(node.operator.literal)) {
      const left = typeOf(node.left), right = typeOf(node.right);
      if (isFloating(left) && isInteger(right)) node.right = toFloating(node.right, right);
      else if (isFloating(right) && isInteger(left)) node.left = toFloating(node.left, left);
    }
    if (node.type === 'assignment' && ['=','+=','-=','*=','/='].includes(node.operator.literal)) {
      const left = typeOf(node.left), right = typeOf(node.right);
      if (isFloating(left) && isInteger(right) && (sameShape(right, left) || (node.operator.literal !== '=' && vectorSize(right) === 1)))
        node.right = toFloating(node.right, right);
    }
    if (node.type === 'declarator_list') {
      for (const entry of node.declarations ?? []) {
        const target = typeName(node.specified_type.specifier, entry.quantifier), source = typeOf(entry.initializer);
        if (entry.initializer && isFloating(target) && isInteger(source) && sameShape(source, target))
          entry.initializer = toFloating(entry.initializer, source);
      }
    }
  });
}

/** ES 3.00 built-ins whose every parameter is float-typed (genType/float): no int overloads exist. */
const FLOAT_ONLY_BUILTINS = new Set(['radians','degrees','sin','cos','tan','asin','acos','atan','sinh','cosh','tanh',
  'asinh','acosh','atanh','pow','exp','log','exp2','log2','sqrt','inversesqrt','floor','trunc','round','roundEven',
  'ceil','fract','mod','step','smoothstep','mix','length','distance','dot','cross','normalize','faceforward',
  'reflect','refract','dFdx','dFdy','fwidth']);
/** ES 3.00 built-ins with genIType/genUType overloads next to the float ones. */
const INT_CAPABLE_BUILTINS = new Set(['abs','sign','min','max','clamp']);
/** Lookups whose argument 2 is the float bias/lod (desktop names: this pass runs before texture2D -> texture). */
const FLOAT_BIAS_LOOKUPS = new Set(['texture','textureProj','textureLod','texture2D','texture2DProj','texture2DLod',
  'textureCube','textureCubeLod','texture3D','texture2DLodEXT']);

/**
 * Desktop 1.20+ overload resolution converts int/uint arguments implicitly to float parameters
 * (pow(x, 2), clamp(v, 0, 1), smoothstep(0, 1, x), user f(float) called as f(0)); ES 3.00 finds
 * no matching overload. Each such argument gets the explicit conversion of the overload desktop
 * picks:
 *  - float-only built-ins: every int argument (desktop has no int overload either);
 *  - abs/sign/min/max/clamp: only when some argument is float-typed; an all-int call keeps the
 *    ES 3.00 int overload (the one desktop 1.30+ picks too);
 *  - texture/textureProj/textureLod (and the desktop texture2D... names): an int bias/lod;
 *  - user functions (including renamed pz_user_* overloads): when no overload matches exactly and
 *    exactly one is reachable by int->float conversions. Unknown argument types are left alone.
 */
function convertCallArguments(ast) {
  const { typeOf, userFunctions, structs } = createTypeInference(ast);
  const affected = [];
  walk(ast.program, node => {
    if (node.type !== 'function_call') return;
    const name = calleeName(node, structs);
    if (name === undefined) return;
    const args = callArgs(node), types = args.map(typeOf);
    let targets; // index -> float type to convert to
    if (userFunctions.has(name)) {
      if (!types.every(Boolean)) return;
      const candidates = userFunctions.get(name).filter(o => o.params.length === types.length);
      if (candidates.some(o => o.params.every((p, i) => p === types[i]))) return;
      const reachable = candidates.filter(o => o.params.every((p, i) => convertible(types[i], p)));
      if (reachable.length !== 1) return;
      targets = types.map((t, i) => t !== reachable[0].params[i] ? reachable[0].params[i] : undefined);
    } else if (FLOAT_ONLY_BUILTINS.has(name) || (INT_CAPABLE_BUILTINS.has(name) && types.some(isFloating))) {
      if (name === 'mix' && types.some(t => scalarOf(t) === 'bool' && vectorSize(t) > 0)) return;
      targets = types.map(t => isInteger(t) ? vectorOf('float', vectorSize(t)) : undefined);
    } else if (FLOAT_BIAS_LOOKUPS.has(name) && args.length === 3 && types[2] && isInteger(types[2])) {
      targets = [undefined, undefined, 'float'];
    } else return;
    if (!targets.some(Boolean)) return;
    // Rebuild the argument list keeping the comma literals in place.
    let index = 0;
    node.args = node.args.map(arg => {
      if (arg.type === 'literal' && arg.literal === ',') return arg;
      const i = index++;
      return targets[i] ? toFloating(arg, types[i]) : arg;
    });
    affected.push(name);
  });
  return [...new Set(affected)].sort();
}

/** GLSL ES 3.00 reserved words (spec §3.8) the parser accepts as identifiers, plus built-in names. */
const ES300_RESERVED_IDENTIFIERS = new Set(['input','output','filter','common','partition','active','superp',
  'resource','asm','class','union','enum','typedef','template','this','goto','inline','noinline','public',
  'static','extern','external','interface','long','short','half','fixed','unsigned','hvec2','hvec3','hvec4',
  'fvec2','fvec3','fvec4','sampler3DRect','sizeof','cast','namespace','using']);
/** Prefix for user variables/parameters whose name GLSL ES 3.00 reserves or uses for a built-in. */
export const RENAMED_IDENTIFIER_PREFIX = 'pz_id_';
const renamedIdentifier = name => ES300_BUILTIN_FUNCTIONS.has(name) || ES300_RESERVED_IDENTIFIERS.has(name);
/**
 * WebGL2 name of a desktop uniform/attribute/varying name (`texture` -> `pz_id_texture`), applied to
 * the base identifier of `name[0]`/`name.field`. Deterministic, so gl.mjs maps names without
 * per-program state.
 */
export function webglIdentifier(name) {
  const m = /^([A-Za-z_]\w*)(.*)$/s.exec(name);
  return m && renamedIdentifier(m[1]) ? RENAMED_IDENTIFIER_PREFIX + m[1] + m[2] : name;
}
/** Desktop name for a WebGL2 introspection name (inverse of webglIdentifier). */
export function desktopIdentifier(name) {
  if (!name.startsWith(RENAMED_IDENTIFIER_PREFIX)) return name;
  const rest = name.slice(RENAMED_IDENTIFIER_PREFIX.length), base = /^[A-Za-z_]\w*/.exec(rest)?.[0];
  return base && renamedIdentifier(base) ? rest : name;
}
/**
 * Desktop 1.10/1.20 lets a variable/uniform/parameter be named `texture` (the lookup there is
 * texture2D) or like another ES 3.00 built-in/reserved word; once texture2D becomes texture the
 * name shadows the function ("'texture' : function name expected"). Every such binding and all its
 * references are renamed to RENAMED_IDENTIFIER_PREFIX + name, through the parser's scopes.
 */
function renameReservedIdentifiers(ast) {
  walk(ast.program, node => {
    if (node.type === 'identifier' && node.identifier.startsWith(RENAMED_IDENTIFIER_PREFIX))
      throw new Error(`Shader identifier collides with the renamed-identifier prefix: ${node.identifier}`);
  });
  const renamed = new Set();
  for (const scope of ast.scopes) for (const [name, binding] of Object.entries(scope.bindings)) {
    if (!renamedIdentifier(name)) continue;
    for (const reference of new Set([binding.declaration, ...binding.references])) {
      const target = ['parameter_declaration','declaration'].includes(reference?.type) ? reference.identifier : reference;
      if (target?.type === 'identifier' && target.identifier === name) target.identifier = RENAMED_IDENTIFIER_PREFIX + name;
    }
    renamed.add(name);
  }
  // Parameters of prototypes have no scope binding; rename them by the same deterministic rule.
  walk(ast.program, node => {
    const id = node.type === 'parameter_declaration' ? node.identifier : undefined;
    if (id?.type === 'identifier' && renamedIdentifier(id.identifier)) {
      renamed.add(id.identifier); id.identifier = RENAMED_IDENTIFIER_PREFIX + id.identifier;
    }
  });
  return [...renamed].sort();
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
  const top = ast.program.filter(isFunctionNode);
  if (!top.some(node => ES300_BUILTIN_FUNCTIONS.has(prototypeOf(node).header.name.identifier))) return [];
  walk(ast.program, node => {
    if (node.type === 'identifier' && node.identifier.startsWith(USER_BUILTIN_PREFIX))
      throw new Error(`Shader identifier collides with the built-in overload prefix: ${node.identifier}`);
  });
  const visible = new Map(); // builtin name -> [{params, returnType}] declared so far
  const resolved = new Map(); // call node -> overload or null (built-in)
  // Calls to a built-in name with visible user overloads take the resolved overload's return type.
  const { typeOf } = createTypeInference(ast, { callType(node) {
    const name = callName(node);
    if (ES300_BUILTIN_FUNCTIONS.has(name) && visible.has(name)) return resolve(node)?.returnType;
  } });
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
  const renamedIdentifiers = renameReservedIdentifiers(ast);
  promoteIntegers(ast);
  // Desktop version of this source (no directive means 1.10); it decides how user overloads of
  // built-in names resolve, see renameBuiltinOverloads.
  const versionNode = ast.program.find(node => node.type === 'preprocessor' && /^\s*#\s*version\b/.test(node.line));
  const builtinOverloads = renameBuiltinOverloads(ast, Number(versionNode?.line.trim().split(/\s+/)[1] ?? 110));
  // After the renames, so calls to pz_user_* overloads resolve against their own parameters.
  const convertedCalls = convertCallArguments(ast);
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
  return {source:output,replacements:[...replacements].sort(),declarations:[...declarations],uniformDefaults,alphaTestEmulated:injectedAlphaTest,hasMain,globalInits:hasMain?[]:globalInits,builtinOverloads,renamedIdentifiers,convertedCalls};
}
