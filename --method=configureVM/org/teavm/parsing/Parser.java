package org.teavm.parsing;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.teavm.asm.Type;
import org.teavm.asm.commons.JSRInlinerAdapter;
import org.teavm.asm.tree.AnnotationNode;
import org.teavm.asm.tree.ClassNode;
import org.teavm.asm.tree.FieldNode;
import org.teavm.asm.tree.InnerClassNode;
import org.teavm.asm.tree.MethodNode;
import org.teavm.common.Graph;
import org.teavm.common.GraphUtils;
import org.teavm.hppc.IntIntHashMap;
import org.teavm.hppc.IntIntMap;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationContainer;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.GenericTypeParameter;
import org.teavm.model.GenericValueType;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.Phi;
import org.teavm.model.PrimitiveType;
import org.teavm.model.Program;
import org.teavm.model.ReferenceCache;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.optimization.UnreachableBasicBlockEliminator;
import org.teavm.model.util.DefinitionExtractor;
import org.teavm.model.util.PhiUpdater;
import org.teavm.model.util.ProgramNodeSplittingBackend;
import org.teavm.model.util.ProgramUtils;

public class Parser {
   private static final int DECL_CLASS = 0;
   private static final int DECL_METHOD = 1;
   private static final int DECL_FIELD = 2;
   private ReferenceCache referenceCache;

   public Parser(ReferenceCache referenceCache) {
      this.referenceCache = referenceCache;
   }

   public MethodHolder parseMethod(MethodNode node, String fileName) {
      MethodNode nodeWithoutJsr = new MethodNode(589824, node.access, node.name, node.desc, node.signature, node.exceptions.toArray(new String[0]));
      JSRInlinerAdapter adapter = new JSRInlinerAdapter(
         nodeWithoutJsr, node.access, node.name, node.desc, node.signature, node.exceptions.toArray(new String[0])
      );
      node.accept(adapter);
      node = nodeWithoutJsr;
      ValueType[] signature = MethodDescriptor.parseSignature(node.desc);
      MethodHolder method = new MethodHolder(this.referenceCache.getCached(new MethodDescriptor(node.name, signature)));
      this.parseModifiers(node.access, method, 1);
      this.parseAnnotations(method.getAnnotations(), node.visibleAnnotations, node.invisibleAnnotations);
      if (node.instructions.size() > 0) {
         ProgramParser programParser = new ProgramParser(this.referenceCache);
         programParser.setFileName(fileName);
         Program program = programParser.parse(node);
         UnreachableBasicBlockEliminator optimizer = new UnreachableBasicBlockEliminator();
         optimizer.removeUnreachableInstructions(program);
         optimizer.optimize(program);
         Graph cfg = ProgramUtils.buildControlFlowGraph(program);
         if (GraphUtils.isIrreducible(cfg)) {
            ProgramNodeSplittingBackend be = new ProgramNodeSplittingBackend(program);
            int[] weights = new int[program.basicBlockCount()];

            for (int i = 0; i < weights.length; i++) {
               int count = 0;

               for (Instruction insn = program.basicBlockAt(i).getFirstInstruction(); insn != null; insn = insn.getNext()) {
                  count++;
               }

               weights[i] = count;
            }

            GraphUtils.splitIrreducibleGraph(cfg, weights, be);
         }

         PhiUpdater phiUpdater = new PhiUpdater();
         Variable[] argumentMapping = this.applySignature(program, method.getParameterTypes());
         phiUpdater.updatePhis(program, argumentMapping);
         method.setProgram(program);
         applyDebugNames(program, phiUpdater, programParser, argumentMapping);
         applyDebugNames(program, phiUpdater, programParser, this.applySignature(program, method.getDescriptor().getParameterTypes()));

         while (program.variableCount() <= method.parameterCount()) {
            program.createVariable();
         }
      }

      if (node.annotationDefault != null) {
         method.setAnnotationDefault(this.parseAnnotationValue(node.annotationDefault));
      }

      for (int i = 0; i < method.parameterCount(); i++) {
         this.parseAnnotations(
            method.parameterAnnotation(i),
            node.visibleParameterAnnotations != null ? node.visibleParameterAnnotations[i] : null,
            node.invisibleParameterAnnotations != null ? node.invisibleParameterAnnotations[i] : null
         );
      }

      if (node.exceptions != null && !node.exceptions.isEmpty()) {
         ArrayList<String> thrownTypes = new ArrayList<>();

         for (String type : node.exceptions) {
            thrownTypes.add(type.replace('/', '.'));
         }

         method.setThrownTypes(thrownTypes);
      }

      if (node.signature != null) {
         this.parseMethodGenericSignature(node.signature, method);
      }

      return method;
   }

   private void parseMethodGenericSignature(String signature, MethodHolder method) {
      GenericValueType.ParsePosition position = new GenericValueType.ParsePosition();
      List<GenericTypeParameter> typeParameters = new ArrayList<>();
      String elementName = "method '" + method.getDescriptor() + "'";
      if (signature.charAt(position.index) == '<') {
         this.parseTypeParameters(signature, typeParameters, position, elementName);
      }

      List<GenericValueType> parameters = new ArrayList<>();
      if (signature.charAt(position.index) != '(') {
         throw this.couldNotParseSignature(elementName, signature);
      }

      position.index++;

      while (signature.charAt(position.index) != ')') {
         GenericValueType parameter = GenericValueType.parse(signature, position);
         if (parameter == null) {
            throw this.couldNotParseSignature(elementName, signature);
         }

         parameters.add(parameter);
      }

      position.index++;
      GenericValueType returnType = GenericValueType.parse(signature, position);
      if (returnType == null) {
         throw this.couldNotParseSignature(elementName, signature);
      }

      if (position.index < signature.length() && signature.charAt(position.index) != '^') {
         throw this.couldNotParseSignature(elementName, signature);
      }

      method.setTypeParameters(typeParameters.toArray(new GenericTypeParameter[0]));
      method.setGenericSignature(returnType, parameters.toArray(new GenericValueType[0]));
   }

   private static void applyDebugNames(Program program, PhiUpdater phiUpdater, ProgramParser parser, Variable[] argumentMapping) {
      if (program.basicBlockCount() != 0) {
         IntIntMap[] blockEntryVariableMappings = getBlockEntryVariableMappings(program, phiUpdater, argumentMapping);
         DefinitionExtractor defExtractor = new DefinitionExtractor();
         Map<Integer, String> debugNames = new HashMap<>();

         for (int i = 0; i < program.basicBlockCount(); i++) {
            BasicBlock block = program.basicBlockAt(i);
            IntIntMap varMap = blockEntryVariableMappings[i];

            for (Instruction insn : block) {
               insn.acceptVisitor(defExtractor);
               Map<Integer, String> newDebugNames = parser.getDebugNames(insn);
               if (newDebugNames != null) {
                  debugNames = newDebugNames;
               }

               for (Variable definedVar : defExtractor.getDefinedVariables()) {
                  int sourceVar = phiUpdater.getSourceVariable(definedVar.getIndex());
                  if (sourceVar >= 0) {
                     varMap.put(sourceVar, definedVar.getIndex());
                  }
               }

               for (Entry<Integer, String> debugName : debugNames.entrySet()) {
                  int receiver = varMap.getOrDefault(debugName.getKey(), -1);
                  if (receiver >= 0) {
                     Variable receiverVar = program.variableAt(receiver);
                     receiverVar.setDebugName(debugName.getValue());
                     receiverVar.setLabel(debugName.getValue());
                  }
               }
            }
         }
      }
   }

   private static IntIntMap[] getBlockEntryVariableMappings(Program program, PhiUpdater phiUpdater, Variable[] argumentMapping) {
      IntIntMap[] result = new IntIntMap[program.basicBlockCount()];
      DefinitionExtractor defExtractor = new DefinitionExtractor();
      Graph cfg = ProgramUtils.buildControlFlowGraph(program);
      Graph dom = GraphUtils.buildDominatorGraph(GraphUtils.buildDominatorTree(cfg), cfg.size());

      class Step {
         int node;
         IntIntMap varMap;

         Step(int node, IntIntMap varMap) {
            this.node = node;
            this.varMap = varMap;
         }
      }

      Step[] stack = new Step[program.basicBlockCount()];
      int top = 0;
      IntIntHashMap entryVarMap = new IntIntHashMap();

      for (int i = 0; i < argumentMapping.length; i++) {
         Variable arg = argumentMapping[i];
         if (arg != null) {
            entryVarMap.put(i, arg.getIndex());
         }
      }

      stack[top++] = new Step(0, entryVarMap);

      while (top > 0) {
         Step step = stack[--top];
         int node = step.node;
         IntIntMap varMap = new IntIntHashMap(step.varMap);
         BasicBlock block = program.basicBlockAt(node);

         for (Phi phi : block.getPhis()) {
            int receiver = phi.getReceiver().getIndex();
            int sourceVar = phiUpdater.getSourceVariable(receiver);
            if (sourceVar >= 0) {
               varMap.put(sourceVar, receiver);
            }
         }

         result[node] = new IntIntHashMap(varMap);

         for (Instruction insn : block) {
            insn.acceptVisitor(defExtractor);

            for (Variable definedVar : defExtractor.getDefinedVariables()) {
               int sourceVar = phiUpdater.getSourceVariable(definedVar.getIndex());
               if (sourceVar >= 0) {
                  varMap.put(sourceVar, definedVar.getIndex());
               }
            }
         }

         for (int successor : dom.outgoingEdges(node)) {
            stack[top++] = new Step(successor, new IntIntHashMap(varMap));
         }
      }

      return result;
   }

   private Variable[] applySignature(Program program, ValueType[] arguments) {
      if (program.variableCount() == 0) {
         return new Variable[0];
      }

      Variable[] variableMap = new Variable[program.variableCount()];
      int index = 0;
      variableMap[index] = program.variableAt(index);
      index++;

      for (int i = 0; i < arguments.length; i++) {
         variableMap[index] = program.variableAt(i + 1);
         index++;
         ValueType arg = arguments[i];
         if (arg instanceof ValueType.Primitive) {
            PrimitiveType kind = ((ValueType.Primitive)arg).getKind();
            if (kind == PrimitiveType.LONG || kind == PrimitiveType.DOUBLE) {
               variableMap[index] = variableMap[index - 1];
               index++;
            }
         }
      }

      return Arrays.copyOf(variableMap, index);
   }

   public ClassHolder parseClass(ClassNode node) {
      ClassHolder cls = new ClassHolder(this.referenceCache.getCached(node.name.replace('/', '.')));
      this.parseModifiers(node.access, cls, 0);
      if (node.superName != null) {
         cls.setParent(this.referenceCache.getCached(node.superName.replace('/', '.')));
      }

      if (cls.getName().equals("java.lang.Object")) {
         cls.setParent(null);
      }

      if (node.interfaces != null) {
         for (String iface : node.interfaces) {
            cls.getInterfaces().add(this.referenceCache.getCached(iface.replace('/', '.')));
         }
      }

      if (node.signature != null) {
         this.parseSignature(cls, node.signature);
      }

      for (FieldNode fieldNode : node.fields) {
         FieldHolder field = this.parseField(fieldNode);
         cls.addField(field);
         field.updateReference(this.referenceCache);
      }

      String fullFileName = this.referenceCache.getCached(node.name.substring(0, node.name.lastIndexOf(47) + 1) + node.sourceFile);

      for (MethodNode methodNode : node.methods) {
         MethodHolder method = this.parseMethod(methodNode, fullFileName);
         cls.addMethod(method);
         method.updateReference(this.referenceCache);
      }

      if (node.outerClass != null) {
         cls.setOwnerName(node.outerClass.replace('/', '.'));
      }

      if (node.innerClasses != null && !node.innerClasses.isEmpty()) {
         for (InnerClassNode innerClassNode : node.innerClasses) {
            if (node.name.equals(innerClassNode.name)) {
               if (innerClassNode.outerName != null) {
                  cls.setDeclaringClassName(innerClassNode.outerName.replace('/', '.'));
                  cls.setOwnerName(cls.getDeclaringClassName());
               }

               cls.setSimpleName(innerClassNode.innerName);
            }

            if (node.name.equals(innerClassNode.outerName)) {
               cls.getInnerClasses().add(this.referenceCache.getCached(innerClassNode.name.replace('/', '.')));
            }
         }
      }

      this.parseAnnotations(cls.getAnnotations(), node.visibleAnnotations, node.invisibleAnnotations);
      return cls;
   }

   private void parseSignature(ClassHolder cls, String signature) {
      GenericValueType.ParsePosition position = new GenericValueType.ParsePosition();
      List<GenericTypeParameter> typeParameters = new ArrayList<>();
      String elementName = "class '" + cls.getName() + "'";
      if (signature.charAt(position.index) == '<') {
         this.parseTypeParameters(signature, typeParameters, position, elementName);
      }

      cls.setGenericParameters(typeParameters.toArray(new GenericTypeParameter[0]));
      GenericValueType.Object supertype = GenericValueType.parseObject(signature, position);
      if (supertype == null) {
         throw this.couldNotParseSignature(elementName, signature);
      }

      cls.setGenericParent(supertype);
      List<GenericValueType.Object> interfaces = new ArrayList<>();

      while (position.index < signature.length()) {
         GenericValueType.Object itf = GenericValueType.parseObject(signature, position);
         if (itf == null) {
            throw this.couldNotParseSignature(elementName, signature);
         }

         interfaces.add(itf);
      }

      cls.getGenericInterfaces().addAll(interfaces);
   }

   private void parseTypeParameters(String signature, List<GenericTypeParameter> typeParameters, GenericValueType.ParsePosition position, String elementName) {
      position.index++;

      label44:
      while (position.index < signature.length()) {
         int next = signature.indexOf(58, position.index);
         if (next < 0 || next == position.index) {
            throw this.couldNotParseSignature(elementName, signature);
         }

         String name = signature.substring(position.index, next);
         position.index = next;
         List<GenericValueType.Reference> bounds = new ArrayList<>();

         while (position.index < signature.length()) {
            char c = signature.charAt(position.index);
            if (c != ':') {
               typeParameters.add(new GenericTypeParameter(name, bounds.get(0), bounds.subList(1, bounds.size()).toArray(new GenericValueType.Reference[0])));
               if (signature.charAt(position.index) == '>') {
                  position.index++;
                  return;
               }
               continue label44;
            }

            position.index++;
            if (bounds.isEmpty() && signature.charAt(position.index) == ':') {
               bounds.add(null);
            } else {
               GenericValueType.Reference bound = GenericValueType.parseReference(signature, position);
               if (bound == null) {
                  throw this.couldNotParseSignature(elementName, signature);
               }

               bounds.add(bound);
            }
         }

         throw this.couldNotParseSignature(elementName, signature);
      }

      throw this.couldNotParseSignature(elementName, signature);
   }

   private IllegalArgumentException couldNotParseSignature(String forElement, String signature) {
      return new IllegalArgumentException("Could not parse class signature '" + signature + "' for " + forElement);
   }

   public FieldHolder parseField(FieldNode node) {
      FieldHolder field = new FieldHolder(this.referenceCache.getCached(node.name));
      field.setType(this.referenceCache.getCached(ValueType.parse(node.desc)));
      field.setInitialValue(node.value);
      this.parseModifiers(node.access, field, 2);
      this.parseAnnotations(field.getAnnotations(), node.visibleAnnotations, node.invisibleAnnotations);
      if (node.signature != null) {
         GenericValueType.ParsePosition position = new GenericValueType.ParsePosition();
         GenericValueType type = GenericValueType.parse(node.signature, position);
         if (type == null || position.index < node.signature.length()) {
            throw this.couldNotParseSignature("field '" + field.getReference() + "'", node.signature);
         }

         field.setGenericType(type);
      }

      return field;
   }

   public void parseModifiers(int access, ElementHolder member, int type) {
      if ((access & 2) != 0) {
         member.setLevel(AccessLevel.PRIVATE);
      } else if ((access & 4) != 0) {
         member.setLevel(AccessLevel.PROTECTED);
      } else if ((access & 1) != 0) {
         member.setLevel(AccessLevel.PUBLIC);
      }

      if ((access & 1024) != 0) {
         member.getModifiers().add(ElementModifier.ABSTRACT);
      }

      if ((access & 8192) != 0) {
         member.getModifiers().add(ElementModifier.ANNOTATION);
      }

      if ((access & 64) != 0 && type == 1) {
         member.getModifiers().add(ElementModifier.BRIDGE);
      }

      if ((access & 131072) != 0) {
         member.getModifiers().add(ElementModifier.DEPRECATED);
      }

      if ((access & 16384) != 0) {
         member.getModifiers().add(ElementModifier.ENUM);
      }

      if ((access & 16) != 0) {
         member.getModifiers().add(ElementModifier.FINAL);
      }

      if ((access & 512) != 0) {
         member.getModifiers().add(ElementModifier.INTERFACE);
      }

      if ((access & 256) != 0) {
         member.getModifiers().add(ElementModifier.NATIVE);
      }

      if ((access & 8) != 0) {
         member.getModifiers().add(ElementModifier.STATIC);
      }

      if ((access & 2048) != 0) {
         member.getModifiers().add(ElementModifier.STRICT);
      }

      if ((access & 32) != 0 && type == 1) {
         member.getModifiers().add(ElementModifier.SYNCHRONIZED);
      }

      if ((access & 4096) != 0) {
         member.getModifiers().add(ElementModifier.SYNTHETIC);
      }

      if ((access & 128) != 0 && type == 2) {
         member.getModifiers().add(ElementModifier.TRANSIENT);
      }

      if ((access & 128) != 0 && type == 1) {
         member.getModifiers().add(ElementModifier.VARARGS);
      }

      if ((access & 64) != 0 && type == 2) {
         member.getModifiers().add(ElementModifier.VOLATILE);
      }

      if ((access & 65536) != 0) {
         member.getModifiers().add(ElementModifier.RECORD);
      }
   }

   private void parseAnnotations(AnnotationContainer annotations, List<AnnotationNode> visibleAnnotations, List<AnnotationNode> invisibleAnnotations) {
      List<Object> annotNodes = new ArrayList<>();
      if (visibleAnnotations != null) {
         annotNodes.addAll(visibleAnnotations);
      }

      if (invisibleAnnotations != null) {
         annotNodes.addAll(invisibleAnnotations);
      }

      for (Object obj : annotNodes) {
         AnnotationNode annotNode = (AnnotationNode)obj;
         String desc = annotNode.desc;
         if (desc.startsWith("L") && desc.endsWith(";")) {
            desc = desc.substring(1, desc.length() - 1);
         }

         desc = desc.replace('/', '.');
         if (annotations.get(desc) == null) {
            AnnotationHolder annot = new AnnotationHolder(this.referenceCache.getCached(desc));
            this.parseAnnotationValues(annot, annotNode.values);
            annotations.add(annot);
         }
      }
   }

   private void parseAnnotationValues(AnnotationHolder annot, List<Object> values) {
      if (values != null) {
         for (int i = 0; i < values.size(); i += 2) {
            String key = (String)values.get(i);
            Object value = values.get(i + 1);
            annot.getValues().put(key, this.parseAnnotationValue(value));
         }
      }
   }

   private AnnotationValue parseAnnotationValue(Object value) {
      if (value instanceof String[] enumInfo) {
         ValueType.Object object = (ValueType.Object)ValueType.parse(enumInfo[0]);
         return new AnnotationValue(this.referenceCache.getCached(new FieldReference(object.getClassName(), enumInfo[1])));
      } else if (value instanceof Type cls) {
         return new AnnotationValue(this.referenceCache.getCached(ValueType.parse(cls.getDescriptor())));
      } else if (value instanceof List<?> originalList) {
         List<AnnotationValue> resultList = new ArrayList<>();

         for (Object item : originalList) {
            resultList.add(this.parseAnnotationValue(item));
         }

         return new AnnotationValue(resultList);
      } else if (value instanceof AnnotationNode annotNode) {
         ValueType.Object object = (ValueType.Object)this.referenceCache.getCached(ValueType.parse(annotNode.desc));
         AnnotationHolder annotation = new AnnotationHolder(object.getClassName());
         this.parseAnnotationValues(annotation, annotNode.values);
         return new AnnotationValue(annotation);
      } else {
         if (value instanceof String) {
            return new AnnotationValue((String)value);
         }

         if (value instanceof Boolean) {
            return new AnnotationValue((Boolean)value);
         }

         if (value instanceof Character) {
            return new AnnotationValue((Character)value);
         }

         if (value instanceof Byte) {
            return new AnnotationValue((Byte)value);
         }

         if (value instanceof Short) {
            return new AnnotationValue((Short)value);
         }

         if (value instanceof Integer) {
            return new AnnotationValue((Integer)value);
         }

         if (value instanceof Long) {
            return new AnnotationValue((Long)value);
         }

         if (value instanceof Float) {
            return new AnnotationValue((Float)value);
         }

         if (value instanceof Double) {
            return new AnnotationValue((Double)value);
         }

         if (!value.getClass().isArray()) {
            throw new AssertionError();
         }

         List<AnnotationValue> resultList = new ArrayList<>();
         int size = Array.getLength(value);

         for (int i = 0; i < size; i++) {
            Object item = Array.get(value, i);
            resultList.add(this.parseAnnotationValue(item));
         }

         return new AnnotationValue(resultList);
      }
   }
}
