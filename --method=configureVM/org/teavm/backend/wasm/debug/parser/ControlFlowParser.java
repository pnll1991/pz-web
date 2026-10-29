package org.teavm.backend.wasm.debug.parser;

import java.util.ArrayList;
import java.util.List;
import org.teavm.backend.wasm.debug.info.ControlFlowInfo;
import org.teavm.backend.wasm.debug.info.FunctionControlFlow;
import org.teavm.backend.wasm.debug.info.FunctionControlFlowBuilder;
import org.teavm.backend.wasm.parser.AddressListener;
import org.teavm.backend.wasm.parser.BranchOpcode;
import org.teavm.backend.wasm.parser.CodeListener;
import org.teavm.backend.wasm.parser.CodeSectionListener;
import org.teavm.backend.wasm.parser.Opcode;
import org.teavm.backend.wasm.parser.WasmHollowBlockType;
import org.teavm.hppc.IntArrayList;

public class ControlFlowParser implements CodeSectionListener, CodeListener, AddressListener {
   private int previousAddress;
   private int address;
   private int startAddress;
   private List<ControlFlowParser.Branch> branches = new ArrayList<>();
   private List<FunctionControlFlow> ranges = new ArrayList<>();
   private List<ControlFlowParser.Branch> pendingBranches = new ArrayList<>();
   private List<ControlFlowParser.Block> blocks = new ArrayList<>();

   public ControlFlowInfo build() {
      return new ControlFlowInfo(this.ranges.toArray(new FunctionControlFlow[0]));
   }

   @Override
   public void address(int address) {
      this.previousAddress = this.address;
      this.address = address;
      this.flush();
   }

   @Override
   public boolean functionStart(int index, int size) {
      this.startAddress = this.address;
      return true;
   }

   @Override
   public CodeListener code() {
      return this;
   }

   @Override
   public int startBlock(boolean loop, WasmHollowBlockType type) {
      return this.startBlock(loop);
   }

   @Override
   public int startConditionalBlock(WasmHollowBlockType type) {
      return this.startBlock(false);
   }

   private int startBlock(boolean loop) {
      int token = this.blocks.size();
      ControlFlowParser.Branch branch = !loop ? this.newPendingBranch(false) : null;
      ControlFlowParser.Block block = new ControlFlowParser.Block(branch, this.address);
      block.loop = loop;
      this.blocks.add(block);
      if (branch != null) {
         block.pendingBranches.add(branch);
      }

      return token;
   }

   @Override
   public void startElseSection(int token) {
      ControlFlowParser.Block block = this.blocks.get(this.blocks.size() - 1);
      ControlFlowParser.Branch lastBranch = this.branches.get(this.branches.size() - 1);
      if (lastBranch.address != this.previousAddress) {
         lastBranch = new ControlFlowParser.Branch(this.previousAddress, false);
         this.branches.add(lastBranch);
      }

      block.pendingBranches.add(lastBranch);
      block.branch.targets.add(this.address);
   }

   @Override
   public void endBlock(int token, boolean loop) {
      ControlFlowParser.Block block = this.blocks.remove(this.blocks.size() - 1);
      this.pendingBranches.addAll(block.pendingBranches);
      if (loop) {
         ControlFlowParser.Branch branch = this.newBranch(false);
         branch.targets.add(block.address);
      }
   }

   @Override
   public void call(int functionIndex) {
      this.call();
   }

   @Override
   public void indirectCall(int typeIndex, int tableIndex) {
      this.call();
   }

   private void call() {
      this.newPendingBranch(true);
   }

   @Override
   public void opcode(Opcode opcode) {
      switch (opcode) {
         case RETURN:
         case UNREACHABLE:
            this.newBranch(false);
      }
   }

   @Override
   public void branch(BranchOpcode opcode, int depth, int target) {
      ControlFlowParser.Branch branch = this.newBranch(false);
      if (opcode == BranchOpcode.BR_IF) {
         this.pendingBranches.add(branch);
      }

      branch.jumpTo(this.blocks.get(target));
   }

   @Override
   public void tableBranch(int[] depths, int[] targets, int defaultDepth, int defaultTarget) {
      ControlFlowParser.Branch branch = this.newPendingBranch(false);

      for (int target : targets) {
         branch.jumpTo(this.blocks.get(target));
      }

      branch.jumpTo(this.blocks.get(defaultDepth));
   }

   private ControlFlowParser.Branch newPendingBranch(boolean isCall) {
      ControlFlowParser.Branch branch = this.newBranch(isCall);
      this.pendingBranches.add(branch);
      return branch;
   }

   private ControlFlowParser.Branch newBranch(boolean isCall) {
      ControlFlowParser.Branch branch = new ControlFlowParser.Branch(this.address, isCall);
      this.branches.add(branch);
      return branch;
   }

   private void flush() {
      for (ControlFlowParser.Branch branch : this.pendingBranches) {
         branch.targets.add(this.address);
      }

      this.pendingBranches.clear();
   }

   @Override
   public void functionEnd() {
      FunctionControlFlowBuilder cfb = new FunctionControlFlowBuilder(this.startAddress, this.address);

      for (ControlFlowParser.Branch branch : this.branches) {
         if (branch.isCall) {
            cfb.addCall(branch.address, branch.targets.toArray());
         } else {
            cfb.addBranch(branch.address, branch.targets.toArray());
         }
      }

      this.ranges.add(cfb.build());
      this.branches.clear();
      this.pendingBranches.clear();
      this.blocks.clear();
   }

   private static class Block {
      ControlFlowParser.Branch branch;
      boolean loop;
      final int address;
      List<ControlFlowParser.Branch> pendingBranches = new ArrayList<>();

      Block(ControlFlowParser.Branch branch, int address) {
         this.branch = branch;
         this.address = address;
      }
   }

   private static class Branch {
      final int address;
      final IntArrayList targets = new IntArrayList();
      final boolean isCall;

      Branch(int address, boolean isCall) {
         this.address = address;
         this.isCall = isCall;
      }

      void jumpTo(ControlFlowParser.Block block) {
         if (block.loop) {
            this.targets.add(block.address);
         } else {
            block.pendingBranches.add(this);
         }
      }
   }
}
