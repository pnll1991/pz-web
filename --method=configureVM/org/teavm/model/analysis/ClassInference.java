package org.teavm.model.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.common.GraphUtils;
import org.teavm.common.IntegerArray;
import org.teavm.dependency.DependencyInfo;
import org.teavm.dependency.FieldDependencyInfo;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.dependency.ValueDependencyInfo;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;
import org.teavm.hppc.IntStack;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.hppc.cursors.IntCursor;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.TryCatchBlock;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AbstractInstructionVisitor;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

public class ClassInference {
   private DependencyInfo dependencyInfo;
   private ClassHierarchy hierarchy;
   private SubclassListProvider subclassListProvider;
   private Graph assignmentGraph;
   private Graph arrayDataAssignmentGraph;
   private Graph cloneGraph;
   private Graph arrayGraph;
   private Graph itemGraph;
   private Graph graph;
   private ClassInference.ValueCast[] casts;
   private int[] exceptions;
   private ClassInference.VirtualCallSite[] virtualCallSites;
   private int overflowLimit;
   private int[] propagationPath;
   private int[] nodeMapping;
   private IntHashSet[] types;
   private boolean[] overflowTypes;
   private ObjectIntMap<ValueType> typeMap = new ObjectIntHashMap();
   private List<ValueType> typeList = new ArrayList<>();
   private boolean changed = true;
   private boolean[] nodeChanged;
   private boolean[] formerNodeChanged;
   private static final int MAX_DEGREE = 3;
   private static final byte FRESH = 0;
   private static final byte VISITING = 1;
   private static final byte VISITED = 2;

   public ClassInference(DependencyInfo dependencyInfo, ClassHierarchy hierarchy, Iterable<? extends String> classNames, int overflowLimit) {
      this.dependencyInfo = dependencyInfo;
      this.hierarchy = hierarchy;
      this.overflowLimit = overflowLimit;
      this.subclassListProvider = new SubclassListProvider(dependencyInfo.getClassSource(), classNames, overflowLimit);
   }

   public void infer(Program program, MethodReference methodReference) {
      this.types = new IntHashSet[program.variableCount() << 3];
      this.overflowTypes = new boolean[program.variableCount() << 3];
      this.nodeChanged = new boolean[this.types.length];
      this.formerNodeChanged = new boolean[this.nodeChanged.length];
      this.nodeMapping = new int[this.types.length];
      int i = 0;

      while (i < this.types.length) {
         this.nodeMapping[i] = i++;
      }

      MethodDependencyInfo thisMethodDep = this.dependencyInfo.getMethod(methodReference);
      this.buildPreliminaryGraphs(program, thisMethodDep);

      for (int ix = 0; ix <= methodReference.parameterCount(); ix++) {
         ValueDependencyInfo paramDep = thisMethodDep.getVariable(ix);
         if (paramDep != null) {
            for (int degree = 0; degree <= 3; degree++) {
               this.addTypesFrom(ix, degree, paramDep);
               if (!paramDep.hasArrayType()) {
                  break;
               }

               paramDep = paramDep.getArrayItem();
            }
         }
      }

      this.buildPropagationGraph();
      this.collapseSCCs();
      this.buildPropagationPath();
      this.propagate(program);
      this.assignmentGraph = null;
      this.graph = null;
      this.cloneGraph = null;
      this.arrayGraph = null;
      this.itemGraph = null;
      this.casts = null;
      this.exceptions = null;
      this.virtualCallSites = null;
      this.propagationPath = null;
      this.nodeChanged = null;
   }

   public boolean isOverflow(int variableIndex) {
      return this.overflowTypes[this.nodeMapping[packNodeAndDegree(variableIndex, 0)]];
   }

   public List<? extends MethodReference> getMethodImplementations(MethodDescriptor descriptor) {
      return this.subclassListProvider.getMethods(descriptor);
   }

   public ValueType[] typesOf(int variableIndex) {
      IntHashSet typeSet = this.types[this.nodeMapping[packNodeAndDegree(variableIndex, 0)]];
      if (typeSet == null) {
         return new ValueType[0];
      }

      int[] typeIndexes = typeSet.toArray();
      ValueType[] types = new ValueType[typeIndexes.length];

      for (int i = 0; i < typeIndexes.length; i++) {
         types[i] = this.typeList.get(typeIndexes[i]);
      }

      return types;
   }

   private void buildPreliminaryGraphs(Program program, MethodDependencyInfo thisMethodDep) {
      ClassInference.GraphBuildingVisitor visitor = new ClassInference.GraphBuildingVisitor(program.variableCount(), this.dependencyInfo);
      visitor.thisMethodDep = thisMethodDep;

      for (BasicBlock block : program.getBasicBlocks()) {
         visitor.currentBlock = block;

         for (Phi phi : block.getPhis()) {
            visitor.visit(phi);
         }

         for (Instruction insn : block) {
            insn.acceptVisitor(visitor);
         }

         if (block.getExceptionVariable() != null) {
            this.getNodeTypes(packNodeAndDegree(block.getExceptionVariable().getIndex(), 0));
         }
      }

      this.assignmentGraph = visitor.assignmentGraphBuilder.build();
      this.cloneGraph = visitor.cloneGraphBuilder.build();
      this.arrayGraph = visitor.arrayGraphBuilder.build();
      this.itemGraph = visitor.itemGraphBuilder.build();
      this.casts = visitor.casts.toArray(new ClassInference.ValueCast[0]);
      this.exceptions = visitor.exceptions.getAll();
      this.virtualCallSites = visitor.virtualCallSites.toArray(new ClassInference.VirtualCallSite[0]);
      GraphBuilder arrayAssignmentGraphBuilder = new GraphBuilder(this.assignmentGraph.size());

      for (int i = 0; i < this.assignmentGraph.size(); i++) {
         for (int j : this.assignmentGraph.outgoingEdges(i)) {
            arrayAssignmentGraphBuilder.addEdge(i, j);
         }
      }

      for (ClassInference.ValueCast cast : this.casts) {
         arrayAssignmentGraphBuilder.addEdge(cast.fromVariable, cast.toVariable);
      }

      this.arrayDataAssignmentGraph = arrayAssignmentGraphBuilder.build();
   }

   private void buildPropagationGraph() {
      IntStack stack = new IntStack();

      for (int i = 0; i < this.types.length; i++) {
         if (this.types[i] != null || this.overflowTypes[i]) {
            stack.push(i);
         }
      }

      boolean[] visited = new boolean[this.types.length];
      GraphBuilder graphBuilder = new GraphBuilder(this.types.length);

      while (!stack.isEmpty()) {
         int entry = stack.pop();
         if (!visited[entry]) {
            visited[entry] = true;
            int degree = extractDegree(entry);
            int variable = extractNode(entry);
            IntSet nextEntries = new IntHashSet();

            for (int successor : this.assignmentGraph.outgoingEdges(variable)) {
               nextEntries.add(packNodeAndDegree(successor, degree));
            }

            for (int successor : this.cloneGraph.outgoingEdges(variable)) {
               nextEntries.add(packNodeAndDegree(successor, degree));
            }

            if (degree > 0) {
               for (int predecessor : this.arrayDataAssignmentGraph.incomingEdges(variable)) {
                  int predecessorEntry = packNodeAndDegree(predecessor, degree);
                  graphBuilder.addEdge(predecessorEntry, entry);
                  graphBuilder.addEdge(entry, predecessorEntry);
                  if (!visited[predecessorEntry]) {
                     stack.push(predecessorEntry);
                  }
               }

               for (int successor : this.itemGraph.outgoingEdges(variable)) {
                  nextEntries.add(packNodeAndDegree(successor, degree - 1));
               }

               for (int predecessor : this.arrayGraph.incomingEdges(variable)) {
                  int predecessorEntry = packNodeAndDegree(predecessor, degree - 1);
                  graphBuilder.addEdge(predecessorEntry, entry);
                  if (!visited[predecessorEntry]) {
                     stack.push(predecessorEntry);
                  }
               }

               for (int successor : this.arrayDataAssignmentGraph.outgoingEdges(variable)) {
                  int successorEntry = packNodeAndDegree(successor, degree);
                  graphBuilder.addEdge(successorEntry, entry);
                  graphBuilder.addEdge(entry, successorEntry);
                  if (!visited[successorEntry]) {
                     stack.push(successorEntry);
                  }
               }
            }

            if (degree <= 3) {
               for (int successor : this.arrayGraph.outgoingEdges(variable)) {
                  nextEntries.add(packNodeAndDegree(successor, degree + 1));
               }

               for (int predecessor : this.itemGraph.incomingEdges(variable)) {
                  int predecessorEntry = packNodeAndDegree(predecessor, degree + 1);
                  graphBuilder.addEdge(predecessorEntry, entry);
                  if (!visited[predecessorEntry]) {
                     stack.push(predecessorEntry);
                  }
               }
            }

            for (IntCursor next : nextEntries) {
               graphBuilder.addEdge(entry, next.value);
               if (!visited[next.value]) {
                  stack.push(next.value);
               }
            }
         }
      }

      this.graph = graphBuilder.build();
   }

   private void collapseSCCs() {
      int[][] sccs = GraphUtils.findStronglyConnectedComponents(this.graph);
      if (sccs.length != 0) {
         for (int[] scc : sccs) {
            for (int i = 1; i < scc.length; i++) {
               this.nodeMapping[scc[i]] = scc[0];
            }
         }

         boolean[] nodeChangedBackup = (boolean[])this.nodeChanged.clone();
         IntHashSet[] typesBackup = (IntHashSet[])this.types.clone();
         boolean[] overflowTypesBackup = (boolean[])this.overflowTypes.clone();
         Arrays.fill(this.nodeChanged, false);
         Arrays.fill(this.types, null);
         Arrays.fill(this.overflowTypes, false);
         GraphBuilder graphBuilder = new GraphBuilder(this.graph.size());

         for (int i = 0; i < this.graph.size(); i++) {
            for (int j : this.graph.outgoingEdges(i)) {
               int from = this.nodeMapping[i];
               int to = this.nodeMapping[j];
               if (from != to) {
                  graphBuilder.addEdge(from, to);
               }
            }

            int node = this.nodeMapping[i];
            if (overflowTypesBackup[i]) {
               this.overflowTypes[node] = true;
               this.types[node] = null;
            } else if (typesBackup[i] != null && !this.overflowTypes[i]) {
               IntHashSet nodeTypes = this.getNodeTypes(node);
               if (nodeTypes.addAll(typesBackup[i]) > 0 && nodeTypes.size() > this.overflowLimit) {
                  this.types[node] = null;
                  this.overflowTypes[node] = true;
               }
            }

            if (nodeChangedBackup[i]) {
               this.nodeChanged[node] = true;
            }
         }

         this.graph = graphBuilder.build();
      }
   }

   private void buildPropagationPath() {
      byte[] state = new byte[this.types.length];
      int[] path = new int[this.types.length];
      int pathSize = 0;
      IntStack stack = new IntStack();

      for (int i = 0; i < this.graph.size(); i++) {
         if (this.graph.incomingEdgesCount(i) == 0 && (this.overflowTypes[i] || this.types[i] != null)) {
            stack.push(i);
         }
      }

      while (!stack.isEmpty()) {
         int node = stack.pop();
         if (state[node] == 0) {
            state[node] = 1;
            stack.push(node);

            for (int successor : this.graph.outgoingEdges(node)) {
               if (state[successor] == 0) {
                  stack.push(successor);
               }
            }
         } else if (state[node] == 1) {
            path[pathSize++] = node;
            state[node] = 2;
         }
      }

      this.propagationPath = Arrays.copyOf(path, pathSize);
   }

   private void propagate(Program program) {
      this.changed = false;

      boolean outerChanged;
      do {
         System.arraycopy(this.nodeChanged, 0, this.formerNodeChanged, 0, this.nodeChanged.length);
         Arrays.fill(this.nodeChanged, false);
         this.propagateAlongDAG();
         outerChanged = this.changed;

         do {
            this.changed = false;
            this.propagateAlongCasts();
            this.propagateAlongVirtualCalls(program);
            this.propagateAlongExceptions(program);
            if (this.changed) {
               outerChanged = true;
            }
         } while (this.changed);
      } while (outerChanged);
   }

   private void propagateAlongDAG() {
      for (int i = this.propagationPath.length - 1; i >= 0; i--) {
         int node = this.propagationPath[i];
         if (!this.overflowTypes[node]) {
            boolean predecessorsChanged = false;

            for (int predecessor : this.graph.incomingEdges(node)) {
               if (this.formerNodeChanged[predecessor] || this.nodeChanged[predecessor]) {
                  predecessorsChanged = true;
                  break;
               }
            }

            if (predecessorsChanged) {
               IntHashSet nodeTypes = this.getNodeTypes(node);

               for (int predecessor : this.graph.incomingEdges(node)) {
                  if (this.formerNodeChanged[predecessor] || this.nodeChanged[predecessor]) {
                     if (this.overflowTypes[predecessor]) {
                        if (!this.overflowTypes[node]) {
                           this.overflowTypes[node] = true;
                           this.types[node] = null;
                           this.changed = true;
                           this.nodeChanged[node] = true;
                           break;
                        }
                     } else if (this.types[predecessor] != null && nodeTypes.addAll(this.types[predecessor]) > 0) {
                        if (nodeTypes.size() > this.overflowLimit) {
                           this.types[node] = null;
                           this.overflowTypes[node] = true;
                           this.nodeChanged[node] = true;
                           this.changed = true;
                           break;
                        }

                        this.nodeChanged[node] = true;
                        this.changed = true;
                     }
                  }
               }
            }
         }
      }
   }

   private void propagateAlongCasts() {
      for (ClassInference.ValueCast cast : this.casts) {
         int toNode = this.nodeMapping[packNodeAndDegree(cast.toVariable, 0)];
         if (!this.overflowTypes[toNode]) {
            int fromNode = this.nodeMapping[packNodeAndDegree(cast.fromVariable, 0)];
            if (this.formerNodeChanged[fromNode] || this.nodeChanged[fromNode]) {
               IntHashSet targetTypes = this.getNodeTypes(toNode);
               if (!this.overflowTypes[fromNode]) {
                  for (IntCursor cursor : this.types[fromNode]) {
                     if (!targetTypes.contains(cursor.value)) {
                        ValueType type = this.typeList.get(cursor.value);
                        if (this.hierarchy.isSuperType(cast.targetType, type, false) && targetTypes.add(cursor.value)) {
                           this.changed = true;
                           this.nodeChanged[toNode] = true;
                           if (targetTypes.size() > this.overflowLimit) {
                              this.overflowTypes[toNode] = true;
                              this.types[toNode] = null;
                              break;
                           }
                        }
                     }
                  }
               } else {
                  int degree = 0;

                  ValueType targetType;
                  for (targetType = cast.targetType; targetType instanceof ValueType.Array; degree++) {
                     targetType = ((ValueType.Array)targetType).getItemType();
                  }

                  if (targetType instanceof ValueType.Object) {
                     String targetClassName = ((ValueType.Object)targetType).getClassName();
                     List<? extends String> subclasses = this.subclassListProvider.getSubclasses(targetClassName, degree > 0);
                     if (subclasses == null) {
                        this.types[toNode] = null;
                        this.overflowTypes[toNode] = true;
                        this.nodeChanged[toNode] = true;
                        this.changed = true;
                     } else {
                        for (String subclass : subclasses) {
                           ValueType valueType = ValueType.object(subclass);
                           if (degree > 0) {
                              valueType = ValueType.arrayOf(valueType);
                           }

                           int typeId = this.getTypeByValueType(valueType);
                           if (targetTypes.add(typeId)) {
                              this.changed = true;
                              this.nodeChanged[toNode] = true;
                              if (targetTypes.size() > this.overflowLimit) {
                                 this.overflowTypes[toNode] = true;
                                 this.types[toNode] = null;
                                 break;
                              }
                           }
                        }
                     }
                  } else {
                     int typeId = this.getTypeByValueType(targetType);
                     if (targetTypes.add(typeId)) {
                        this.changed = true;
                        this.nodeChanged[toNode] = true;
                        if (targetTypes.size() > this.overflowLimit) {
                           this.overflowTypes[toNode] = true;
                           this.types[toNode] = null;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void propagateAlongVirtualCalls(Program program) {
      ClassReaderSource classSource = this.dependencyInfo.getClassSource();

      for (ClassInference.VirtualCallSite callSite : this.virtualCallSites) {
         if (!callSite.receiverOverflow) {
            int instanceNode = this.nodeMapping[packNodeAndDegree(callSite.instance, 0)];
            if (this.formerNodeChanged[instanceNode] || this.nodeChanged[instanceNode]) {
               List<? extends String> receiverTypes;
               if (this.overflowTypes[instanceNode]) {
                  callSite.receiverOverflow = true;
                  List<? extends String> subclasses = this.subclassListProvider.getSubclasses(callSite.method.getClassName(), true);
                  if (subclasses == null) {
                     List<? extends MethodReference> implementations = this.subclassListProvider.getMethods(callSite.method.getDescriptor());
                     if (implementations != null) {
                        for (MethodReference methodReference : implementations) {
                           this.mountVirtualMethod(program, callSite, methodReference);
                        }
                     }
                     continue;
                  }

                  receiverTypes = subclasses;
               } else {
                  ArrayList<String> instanceNodeTypes = new ArrayList<>();

                  for (IntCursor type : this.types[instanceNode]) {
                     if (!callSite.knownClasses.contains(type.value)) {
                        ValueType valueType = this.typeList.get(type.value);
                        if (valueType instanceof ValueType.Object) {
                           instanceNodeTypes.add(((ValueType.Object)valueType).getClassName());
                        }
                     }
                  }

                  receiverTypes = instanceNodeTypes;
               }

               for (String className : receiverTypes) {
                  int typeId = this.getTypeByValueType(ValueType.object(className));
                  if (callSite.knownClasses.add(typeId)) {
                     MethodReference rawMethod = new MethodReference(className, callSite.method.getDescriptor());
                     MethodReader resolvedMethod = classSource.resolveImplementation(rawMethod);
                     if (resolvedMethod != null) {
                        MethodReference resolvedMethodRef = resolvedMethod.getReference();
                        this.mountVirtualMethod(program, callSite, resolvedMethodRef);
                     }
                  }
               }
            }
         }
      }
   }

   private void mountVirtualMethod(Program program, ClassInference.VirtualCallSite callSite, MethodReference methodReference) {
      if (callSite.resolvedMethods.add(methodReference)) {
         MethodDependencyInfo methodDep = this.dependencyInfo.getMethod(methodReference);
         if (methodDep != null) {
            if (callSite.receiver >= 0) {
               this.readValue(methodDep.getResult(), program.variableAt(callSite.receiver));
            }

            for (int i = 0; i < callSite.arguments.length; i++) {
               this.writeValue(methodDep.getVariable(i + 1), program.variableAt(callSite.arguments[i]));
            }

            for (ValueType thrownType : methodDep.getThrown().getTypes()) {
               if (thrownType instanceof ValueType.Object) {
                  String thrownTypeName = ((ValueType.Object)thrownType).getClassName();
                  this.propagateException(thrownTypeName, program.basicBlockAt(callSite.block));
               }
            }
         }
      }
   }

   private void propagateAlongExceptions(Program program) {
      for (int i = 0; i < this.exceptions.length; i += 2) {
         int variable = this.nodeMapping[packNodeAndDegree(this.exceptions[i], 0)];
         if (this.formerNodeChanged[variable] || this.nodeChanged[variable]) {
            BasicBlock block = program.basicBlockAt(this.exceptions[i + 1]);
            if (this.overflowTypes[variable]) {
               this.propagateOverflowException(block);
            } else {
               for (IntCursor type : this.types[variable]) {
                  ValueType valueType = this.typeList.get(type.value);
                  if (valueType instanceof ValueType.Object) {
                     String typeName = ((ValueType.Object)valueType).getClassName();
                     this.propagateException(typeName, block);
                  }
               }
            }
         }
      }
   }

   private void propagateException(String thrownTypeName, BasicBlock block) {
      for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
         String expectedType = tryCatch.getExceptionType();
         if (expectedType == null || this.hierarchy.isSuperType(expectedType, thrownTypeName, false)) {
            if (tryCatch.getHandler().getExceptionVariable() != null) {
               int exceptionNode = packNodeAndDegree(tryCatch.getHandler().getExceptionVariable().getIndex(), 0);
               exceptionNode = this.nodeMapping[exceptionNode];
               if (!this.overflowTypes[exceptionNode]) {
                  int thrownType = this.getTypeByValueType(ValueType.object(thrownTypeName));
                  IntHashSet nodeTypes = this.getNodeTypes(exceptionNode);
                  if (nodeTypes.add(thrownType)) {
                     this.nodeChanged[exceptionNode] = true;
                     this.changed = true;
                     if (nodeTypes.size() > this.overflowLimit) {
                        this.types[exceptionNode] = null;
                        this.overflowTypes[exceptionNode] = true;
                     }
                  }
               }
            }
            break;
         }
      }
   }

   private void propagateOverflowException(BasicBlock block) {
      for (TryCatchBlock tryCatch : block.getTryCatchBlocks()) {
         Variable exceptionVar = tryCatch.getHandler().getExceptionVariable();
         if (exceptionVar != null) {
            int exceptionNode = packNodeAndDegree(exceptionVar.getIndex(), 0);
            exceptionNode = this.nodeMapping[exceptionNode];
            if (!this.overflowTypes[exceptionNode]) {
               String expectedType = tryCatch.getExceptionType();
               List<? extends String> thrownTypes = this.subclassListProvider.getSubclasses(expectedType, false);
               if (thrownTypes != null) {
                  IntHashSet nodeTypes = this.getNodeTypes(exceptionNode);

                  for (String thrownTypeName : thrownTypes) {
                     int thrownType = this.getTypeByValueType(ValueType.object(thrownTypeName));
                     if (nodeTypes.add(thrownType)) {
                        this.nodeChanged[exceptionNode] = true;
                        this.changed = true;
                        if (nodeTypes.size() > this.overflowLimit) {
                           this.types[exceptionNode] = null;
                           this.overflowTypes[exceptionNode] = true;
                           break;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   IntHashSet getNodeTypes(int node) {
      IntHashSet result = this.types[node];
      if (result == null) {
         result = new IntHashSet();
         this.types[node] = result;
      }

      return result;
   }

   int getTypeByValueType(ValueType typeName) {
      int type = this.typeMap.getOrDefault(typeName, -1);
      if (type < 0) {
         type = this.typeList.size();
         this.typeMap.put(typeName, type);
         this.typeList.add(typeName);
      }

      return type;
   }

   void readValue(ValueDependencyInfo valueDep, Variable receiver) {
      int depth = 0;

      boolean hasArrayType;
      do {
         this.addTypesFrom(receiver.getIndex(), depth, valueDep);
         depth++;
         hasArrayType = valueDep.hasArrayType();
         valueDep = valueDep.getArrayItem();
      } while (hasArrayType && depth <= 3);
   }

   void writeValue(ValueDependencyInfo valueDep, Variable source) {
      int depth = 0;

      while (valueDep.hasArrayType() && depth < 3) {
         depth++;
         valueDep = valueDep.getArrayItem();
         this.addTypesFrom(source.getIndex(), depth, valueDep);
      }
   }

   boolean addType(int variable, int degree, ValueType typeName) {
      return this.addTypeImpl(packNodeAndDegree(variable, degree), this.getTypeByValueType(typeName));
   }

   boolean addTypeImpl(int node, int typeId) {
      if (this.overflowTypes[node]) {
         return true;
      }

      IntHashSet nodeTypes = this.getNodeTypes(node);
      if (nodeTypes.add(typeId)) {
         this.nodeChanged[node] = true;
         this.changed = true;
         if (nodeTypes.size() > this.overflowLimit) {
            this.types[node] = null;
            this.overflowTypes[node] = true;
            return true;
         }
      }

      return false;
   }

   void addTypesFrom(int variable, int degree, ValueDependencyInfo dep) {
      if (!this.overflowTypes[packNodeAndDegree(variable, degree)]) {
         if (dep.hasMoreTypesThan(this.overflowLimit)) {
            this.overflowType(variable, degree);
         } else {
            ValueType[] types = dep.getTypes();

            for (ValueType type : types) {
               if (this.addType(variable, degree, type)) {
                  break;
               }
            }
         }
      }
   }

   void overflowType(int variable, int degree) {
      int entry = this.nodeMapping[packNodeAndDegree(variable, degree)];
      if (!this.overflowTypes[entry]) {
         this.overflowTypes[entry] = true;
         this.nodeChanged[entry] = true;
         this.changed = true;
      }
   }

   static int extractNode(int nodeAndDegree) {
      return nodeAndDegree >>> 3;
   }

   static int extractDegree(int nodeAndDegree) {
      return nodeAndDegree & 7;
   }

   static int packNodeAndDegree(int node, int degree) {
      return node << 3 | degree;
   }

   class GraphBuildingVisitor extends AbstractInstructionVisitor {
      DependencyInfo dependencyInfo;
      GraphBuilder assignmentGraphBuilder;
      GraphBuilder cloneGraphBuilder;
      GraphBuilder arrayGraphBuilder;
      GraphBuilder itemGraphBuilder;
      MethodDependencyInfo thisMethodDep;
      List<ClassInference.ValueCast> casts = new ArrayList<>();
      IntegerArray exceptions = new IntegerArray(2);
      List<ClassInference.VirtualCallSite> virtualCallSites = new ArrayList<>();
      BasicBlock currentBlock;

      GraphBuildingVisitor(int variableCount, DependencyInfo dependencyInfo) {
         this.dependencyInfo = dependencyInfo;
         this.assignmentGraphBuilder = new GraphBuilder(variableCount);
         this.cloneGraphBuilder = new GraphBuilder(variableCount);
         this.arrayGraphBuilder = new GraphBuilder(variableCount);
         this.itemGraphBuilder = new GraphBuilder(variableCount);
      }

      public void visit(Phi phi) {
         for (Incoming incoming : phi.getIncomings()) {
            this.assignmentGraphBuilder.addEdge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
         }
      }

      @Override
      public void visit(ClassConstantInstruction insn) {
         ClassInference.this.addType(insn.getReceiver().getIndex(), 0, ValueType.object("java.lang.Class"));
      }

      @Override
      public void visit(StringConstantInstruction insn) {
         ClassInference.this.addType(insn.getReceiver().getIndex(), 0, ValueType.object("java.lang.String"));
      }

      @Override
      public void visit(AssignInstruction insn) {
         this.assignmentGraphBuilder.addEdge(insn.getAssignee().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(CastInstruction insn) {
         this.casts.add(new ClassInference.ValueCast(insn.getValue().getIndex(), insn.getReceiver().getIndex(), insn.getTargetType()));
         ClassInference.this.getNodeTypes(ClassInference.packNodeAndDegree(insn.getReceiver().getIndex(), 0));
      }

      @Override
      public void visit(RaiseInstruction insn) {
         this.exceptions.add(insn.getException().getIndex());
         this.exceptions.add(this.currentBlock.getIndex());
      }

      @Override
      public void visit(ConstructArrayInstruction insn) {
         ClassInference.this.addType(insn.getReceiver().getIndex(), 0, ValueType.arrayOf(insn.getItemType()));
      }

      @Override
      public void visit(ConstructInstruction insn) {
         ClassInference.this.addType(insn.getReceiver().getIndex(), 0, ValueType.object(insn.getType()));
      }

      @Override
      public void visit(ConstructMultiArrayInstruction insn) {
         ClassInference.this.addType(insn.getReceiver().getIndex(), 0, insn.getItemType());
      }

      @Override
      public void visit(GetFieldInstruction insn) {
         FieldDependencyInfo fieldDep = this.dependencyInfo.getField(insn.getField());
         ValueDependencyInfo valueDep = fieldDep.getValue();
         ClassInference.this.readValue(valueDep, insn.getReceiver());
      }

      @Override
      public void visit(PutFieldInstruction insn) {
         FieldDependencyInfo fieldDep = this.dependencyInfo.getField(insn.getField());
         ValueDependencyInfo valueDep = fieldDep.getValue();
         ClassInference.this.writeValue(valueDep, insn.getValue());
      }

      @Override
      public void visit(CloneArrayInstruction insn) {
         this.cloneGraphBuilder.addEdge(insn.getArray().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(UnwrapArrayInstruction insn) {
         this.assignmentGraphBuilder.addEdge(insn.getArray().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(GetElementInstruction insn) {
         this.itemGraphBuilder.addEdge(insn.getArray().getIndex(), insn.getReceiver().getIndex());
      }

      @Override
      public void visit(PutElementInstruction insn) {
         this.arrayGraphBuilder.addEdge(insn.getValue().getIndex(), insn.getArray().getIndex());
      }

      @Override
      public void visit(ExitInstruction insn) {
         if (insn.getValueToReturn() != null) {
            ValueDependencyInfo resultDependency = this.thisMethodDep.getResult();

            for (int resultDegree = 0; resultDependency.hasArrayType() && resultDegree <= 3; resultDegree++) {
               resultDependency = resultDependency.getArrayItem();
               ClassInference.this.addTypesFrom(insn.getValueToReturn().getIndex(), resultDegree, resultDependency);
            }
         }
      }

      @Override
      public void visit(InvokeInstruction insn) {
         if (insn.getType() == InvocationType.VIRTUAL) {
            int instance = insn.getInstance().getIndex();
            ClassInference.VirtualCallSite callSite = new ClassInference.VirtualCallSite();
            callSite.instance = instance;
            callSite.method = insn.getMethod();
            callSite.arguments = new int[insn.getArguments().size()];

            for (int i = 0; i < insn.getArguments().size(); i++) {
               callSite.arguments[i] = insn.getArguments().get(i).getIndex();

               for (int j = 0; j <= 3; j++) {
                  ClassInference.this.getNodeTypes(ClassInference.packNodeAndDegree(callSite.arguments[i], j));
               }
            }

            callSite.receiver = insn.getReceiver() != null ? insn.getReceiver().getIndex() : -1;
            callSite.block = this.currentBlock.getIndex();
            this.virtualCallSites.add(callSite);
            if (insn.getReceiver() != null) {
               for (int j = 0; j <= 3; j++) {
                  ClassInference.this.getNodeTypes(ClassInference.packNodeAndDegree(callSite.receiver, j));
               }
            }
         } else {
            MethodDependencyInfo methodDep = this.dependencyInfo.getMethod(insn.getMethod());
            if (methodDep != null) {
               if (insn.getReceiver() != null) {
                  ClassInference.this.readValue(methodDep.getResult(), insn.getReceiver());
               }

               for (int i = 0; i < insn.getArguments().size(); i++) {
                  ClassInference.this.writeValue(methodDep.getVariable(i + 1), insn.getArguments().get(i));
               }

               for (ValueType type : methodDep.getThrown().getTypes()) {
                  if (type instanceof ValueType.Object classType) {
                     ClassInference.this.propagateException(classType.getClassName(), this.currentBlock);
                  }
               }
            }
         }
      }
   }

   static final class ValueCast {
      final int fromVariable;
      final int toVariable;
      final ValueType targetType;

      ValueCast(int fromVariable, int toVariable, ValueType targetType) {
         this.fromVariable = fromVariable;
         this.toVariable = toVariable;
         this.targetType = targetType;
      }
   }

   static class VirtualCallSite {
      int instance;
      boolean receiverOverflow;
      IntSet knownClasses = new IntHashSet();
      Set<MethodReference> resolvedMethods = new HashSet<>();
      MethodReference method;
      int[] arguments;
      int receiver;
      int block;
   }
}
