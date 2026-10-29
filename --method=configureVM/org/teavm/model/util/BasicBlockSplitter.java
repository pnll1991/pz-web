package org.teavm.model.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.teavm.hppc.ByteArrayList;
import org.teavm.hppc.ByteIndexedContainer;
import org.teavm.hppc.IntArrayList;
import org.teavm.hppc.IntIndexedContainer;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.Variable;
import org.teavm.model.optimization.RedundantJumpElimination;

public class BasicBlockSplitter {
   private Program program;
   private int[] mappings;
   private IntIndexedContainer previousPtr;
   private IntIndexedContainer firstPtr;
   private ByteIndexedContainer isLastInSequence;
   private BasicBlock[] variableDefinedAt;

   public BasicBlockSplitter(Program program) {
      this.program = program;
   }

   private void initIfNecessary() {
      if (this.mappings == null) {
         this.mappings = new int[this.program.basicBlockCount()];
         this.previousPtr = new IntArrayList(this.program.basicBlockCount() * 2);
         this.firstPtr = new IntArrayList(this.program.basicBlockCount() * 2);
         this.isLastInSequence = new ByteArrayList(this.program.basicBlockCount() * 2);

         for (int i = 0; i < this.mappings.length; i++) {
            this.mappings[i] = i;
            this.previousPtr.add(i);
            this.firstPtr.add(i);
            this.isLastInSequence.add((byte)1);
         }

         this.variableDefinedAt = ProgramUtils.getVariableDefinitionPlaces(this.program);
      }
   }

   public BasicBlock split(BasicBlock block, Instruction afterInstruction) {
      this.initIfNecessary();
      if (afterInstruction != null && afterInstruction.getBasicBlock() != block) {
         throw new IllegalArgumentException();
      }

      if (this.isLastInSequence.get(block.getIndex()) == 0) {
         throw new IllegalArgumentException();
      }

      BasicBlock splitBlock = this.program.createBasicBlock();

      while (this.previousPtr.size() < splitBlock.getIndex()) {
         this.previousPtr.add(this.previousPtr.size());
         this.firstPtr.add(this.firstPtr.size());
         this.isLastInSequence.add((byte)1);
      }

      this.isLastInSequence.set(block.getIndex(), (byte)0);
      this.previousPtr.add(block.getIndex());
      this.firstPtr.add(this.firstPtr.get(block.getIndex()));
      this.mappings[this.firstPtr.get(block.getIndex())] = splitBlock.getIndex();
      this.isLastInSequence.add((byte)1);
      splitBlock.getTryCatchBlocks().addAll(ProgramUtils.copyTryCatches(block, this.program));
      if (afterInstruction != null) {
         while (afterInstruction.getNext() != null) {
            Instruction nextInstruction = afterInstruction.getNext();
            nextInstruction.delete();
            splitBlock.add(nextInstruction);
         }
      } else {
         while (block.getFirstInstruction() != null) {
            Instruction instruction = block.getFirstInstruction();
            instruction.delete();
            splitBlock.add(instruction);
         }
      }

      return splitBlock;
   }

   public void fixProgram() {
      if (this.mappings != null) {
         for (BasicBlock block : this.program.getBasicBlocks()) {
            Map<BasicBlock, List<Incoming>> incomingsBySource = new LinkedHashMap<>();

            for (Phi phi : block.getPhis()) {
               for (Incoming incoming : phi.getIncomings()) {
                  if (incoming.getSource().getIndex() < this.mappings.length
                     && this.mappings[incoming.getSource().getIndex()] != incoming.getSource().getIndex()) {
                     incomingsBySource.computeIfAbsent(incoming.getSource(), b -> new ArrayList<>()).add(incoming);
                  }
               }
            }

            for (BasicBlock source : incomingsBySource.keySet()) {
               boolean isExceptionHandler = source.getTryCatchBlocks().stream().anyMatch(tryCatch -> tryCatch.getHandler() == block);
               if (isExceptionHandler) {
                  this.fixIncomingsInExceptionHandler(source, incomingsBySource.get(source));
               } else {
                  BasicBlock newSource = this.program.basicBlockAt(this.mappings[source.getIndex()]);

                  for (Incoming incoming : incomingsBySource.get(source)) {
                     incoming.setSource(newSource);
                  }
               }
            }
         }

         RedundantJumpElimination.optimize(this.program);
      }
   }

   private void fixIncomingsInExceptionHandler(BasicBlock source, List<Incoming> incomings) {
      List<BasicBlock> sourceParts = this.buildBasicBlocksSequence(source);
      assert sourceParts.get(0) == source;
      Map<Variable, List<Incoming>> incomingsByValue = this.groupIncomingsByValue(incomings);
      Map<Phi, Variable> lastDefinedValues = new HashMap<>();

      for (Incoming incoming : incomings) {
         if (this.variableDefinedAt[incoming.getValue().getIndex()] != source) {
            lastDefinedValues.put(incoming.getPhi(), incoming.getValue());
         }
      }

      DefinitionExtractor defExtractor = new DefinitionExtractor();

      for (BasicBlock block : sourceParts) {
         if (block != source) {
            for (Entry<Phi, Variable> lastDefinedEntry : lastDefinedValues.entrySet()) {
               Incoming incomingCopy = new Incoming();
               incomingCopy.setSource(block);
               incomingCopy.setValue(lastDefinedEntry.getValue());
               lastDefinedEntry.getKey().getIncomings().add(incomingCopy);
            }
         }

         for (Variable definedVar : ProgramUtils.getVariablesDefinedInBlock(block, defExtractor)) {
            List<Incoming> incomingsOfDefinedVar = incomingsByValue.get(definedVar);
            if (incomingsOfDefinedVar != null) {
               for (Incoming incoming : incomingsOfDefinedVar) {
                  incoming.setSource(block);
                  lastDefinedValues.put(incoming.getPhi(), definedVar);
               }
            }
         }
      }
   }

   private List<BasicBlock> buildBasicBlocksSequence(BasicBlock first) {
      List<BasicBlock> result = new ArrayList<>(2);

      BasicBlock block;
      for (block = this.program.basicBlockAt(this.mappings[first.getIndex()]);
         this.previousPtr.get(block.getIndex()) != block.getIndex();
         block = this.program.basicBlockAt(this.previousPtr.get(block.getIndex()))
      ) {
         result.add(block);
      }

      result.add(block);
      Collections.reverse(result);
      return result;
   }

   private Map<Variable, List<Incoming>> groupIncomingsByValue(List<Incoming> incomings) {
      Map<Variable, List<Incoming>> incoingsByValue = new HashMap<>();

      for (Incoming incoming : incomings) {
         incoingsByValue.computeIfAbsent(incoming.getValue(), i -> new ArrayList<>()).add(incoming);
      }

      return incoingsByValue;
   }
}
