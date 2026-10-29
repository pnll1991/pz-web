package org.teavm.backend.wasm.debug.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.common.CollectionUtil;
import org.teavm.hppc.IntArrayDeque;
import org.teavm.hppc.IntHashSet;
import org.teavm.hppc.IntSet;

public class StepLocationsFinder {
   public final DebugInfo debugInfo;
   private LineInfoSequence lines;
   private FunctionControlFlow graph;
   private List<StepLocationsFinder.Point> points;
   private IntArrayDeque queue = new IntArrayDeque();
   private String currentFileName;
   private int currentLine;
   private boolean enterMethod;
   private IntSet breakpointAddresses = new IntHashSet();
   private IntSet callAddresses = new IntHashSet();
   private IntSet visited = new IntHashSet();

   public StepLocationsFinder(DebugInfo debugInfo) {
      this.debugInfo = debugInfo;
   }

   public boolean step(String fileName, int line, int address, boolean enterMethod) {
      address -= this.debugInfo.offset();
      this.updatePoints(address);
      if (this.points == null) {
         return false;
      }

      int index = CollectionUtil.binarySearch(this.points, address, p -> p.address);
      if (index < 0) {
         index = -index - 2;
      }

      if (index < 0) {
         return false;
      }

      this.enterMethod = enterMethod;
      this.currentFileName = fileName;
      this.currentLine = line;
      this.queue.addLast(index);
      this.callAddresses.clear();
      this.breakpointAddresses.clear();

      while (!this.queue.isEmpty()) {
         this.processTask();
      }

      this.visited.clear();
      this.currentFileName = null;
      return true;
   }

   public int[] getBreakpointAddresses() {
      return this.breakpointAddresses.toArray();
   }

   public int[] getCallAddresses() {
      return this.callAddresses.toArray();
   }

   private void updatePoints(int address) {
      LineInfoSequence lines = this.debugInfo.lines().find(address);
      FunctionControlFlow graph = this.debugInfo.controlFlow().find(address);
      if (lines != null && graph != null) {
         if (lines != this.lines || graph != this.graph) {
            this.lines = lines;
            this.graph = graph;
            this.points = null;
         }

         if (this.points == null) {
            this.points = this.createPoints();
         }
      } else {
         this.lines = null;
         this.graph = null;
         this.points = null;
      }
   }

   private List<StepLocationsFinder.Point> createPoints() {
      ArrayList<StepLocationsFinder.Point> list = new ArrayList<>();
      int indexInLines = 0;
      FunctionControlFlowIterator graphIter = this.graph.iterator(0);
      LineInfoCommandExecutor commandExecutor = new LineInfoCommandExecutor();

      while (indexInLines < this.lines.commands().size() && graphIter.hasNext()) {
         boolean nextInGraph;
         if (graphIter == null) {
            nextInGraph = false;
         } else if (indexInLines >= this.lines.commands().size()) {
            nextInGraph = true;
         } else {
            nextInGraph = this.lines.commands().get(indexInLines).address() >= graphIter.address();
         }

         if (nextInGraph) {
            StepLocationsFinder.Point point = new StepLocationsFinder.Point(graphIter.address());
            list.add(point);
            point.isCall = graphIter.isCall();
            point.next = graphIter.targets();
            graphIter.next();
         } else {
            LineInfoCommand cmd = this.lines.commands().get(indexInLines++);
            cmd.acceptVisitor(commandExecutor);
            InstructionLocation location = commandExecutor.createLocation();
            if (location != null && location.location() != null) {
               StepLocationsFinder.Point point;
               if (!list.isEmpty() && list.get(list.size() - 1).address == cmd.address()) {
                  point = list.get(list.size() - 1);
               } else {
                  point = new StepLocationsFinder.Point(cmd.address());
                  list.add(point);
               }

               point.location = location.location();
            }
         }
      }

      for (StepLocationsFinder.Point point : list) {
         int[] next = point.next;
         if (next != null) {
            int j = 0;

            for (int i = 0; i < next.length; i++) {
               int foundIndex = CollectionUtil.binarySearch(list, next[i], p -> p.address);
               if (foundIndex < 0) {
                  foundIndex = -foundIndex - 1;
               }

               if (foundIndex < list.size()) {
                  next[j++] = foundIndex;
               }
            }

            if (j != next.length) {
               if (j == 0) {
                  point.next = null;
               } else {
                  point.next = Arrays.copyOf(next, j);
               }
            }
         }
      }

      list.trimToSize();
      return list;
   }

   private void processTask() {
      int index = this.queue.removeFirst();
      if (this.visited.add(index)) {
         StepLocationsFinder.Point point = this.points.get(index);
         if (point.location != null && !this.isCurrent(point.location)) {
            this.breakpointAddresses.add(point.address + this.debugInfo.offset());
         } else {
            if (this.enterMethod && point.isCall) {
               this.breakpointAddresses.add(point.address + this.debugInfo.offset());
               this.callAddresses.add(point.address + this.debugInfo.offset());
            }

            if (point.next != null) {
               for (int nextIndex : point.next) {
                  this.queue.addLast(nextIndex);
               }
            } else if (index < this.points.size() - 1) {
               this.queue.addLast(index + 1);
            }
         }
      }
   }

   private boolean isCurrent(Location loc) {
      return loc.line() == this.currentLine && loc.file().fullName().equals(this.currentFileName);
   }

   private static class Point {
      int address;
      int[] next;
      boolean isCall;
      Location location;

      Point(int address) {
         this.address = address;
      }
   }
}
