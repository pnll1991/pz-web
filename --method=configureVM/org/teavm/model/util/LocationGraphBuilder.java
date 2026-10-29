package org.teavm.model.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import org.teavm.ast.ControlFlowEntry;
import org.teavm.common.Graph;
import org.teavm.model.BasicBlock;
import org.teavm.model.Instruction;
import org.teavm.model.Program;
import org.teavm.model.TextLocation;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.JumpInstruction;

class LocationGraphBuilder {
   private Map<TextLocation, Set<TextLocation>> graphBuilder;
   private List<Set<TextLocation>> startLocations;
   private List<LocationGraphBuilder.AdditionalConnection> additionalConnections;

   public ControlFlowEntry[] build(Program program) {
      this.graphBuilder = new HashMap<>();
      Graph graph = ProgramUtils.buildControlFlowGraph(program);
      this.dfs(graph, program);
      return this.assemble();
   }

   private void dfs(Graph graph, Program program) {
      this.startLocations = new ArrayList<>(Collections.nCopies(graph.size(), null));
      this.additionalConnections = new ArrayList<>();
      Deque<LocationGraphBuilder.Step> stack = new ArrayDeque<>();

      for (int i = 0; i < graph.size(); i++) {
         if (graph.incomingEdgesCount(i) == 0) {
            stack.push(new LocationGraphBuilder.Step(null, new HashSet<>(), i));
         }
      }

      boolean[] visited = new boolean[graph.size()];
      TextLocation[] blockLocations = new TextLocation[graph.size()];

      while (!stack.isEmpty()) {
         LocationGraphBuilder.Step step = stack.pop();
         if (!visited[step.block]) {
            visited[step.block] = true;
            this.startLocations.set(step.block, step.startLocations);
            BasicBlock block = program.basicBlockAt(step.block);
            TextLocation location = step.location;
            boolean started = false;

            for (Instruction insn : block) {
               if (!(insn instanceof JumpInstruction) && !(insn instanceof EmptyInstruction) && insn.getLocation() != null) {
                  if (!started) {
                     step.startLocations.add(insn.getLocation());
                  }

                  started = true;
                  if (blockLocations[step.block] == null) {
                     blockLocations[step.block] = insn.getLocation();
                  }

                  if (location != null && !location.isEmpty() && !Objects.equals(location, insn.getLocation())) {
                     this.addEdge(location, insn.getLocation());
                  }

                  location = insn.getLocation();
               }
            }

            if (graph.outgoingEdgesCount(step.block) == 0) {
               if (location != null && !location.isEmpty()) {
                  this.addEdge(location, new TextLocation(null, -1));
               }
            } else {
               for (int next : graph.outgoingEdges(step.block)) {
                  stack.push(new LocationGraphBuilder.Step(location, started ? new HashSet<>() : step.startLocations, next));
               }
            }
         } else if (step.location != null && !step.location.isEmpty()) {
            this.additionalConnections.add(new LocationGraphBuilder.AdditionalConnection(step.location, this.startLocations.get(step.block)));
         }
      }
   }

   private ControlFlowEntry[] assemble() {
      for (LocationGraphBuilder.AdditionalConnection additionalConn : this.additionalConnections) {
         for (TextLocation succ : additionalConn.successors) {
            this.addEdge(additionalConn.location, succ);
         }
      }

      ControlFlowEntry[] locationGraph = new ControlFlowEntry[this.graphBuilder.size()];
      int index = 0;

      for (Entry<TextLocation, Set<TextLocation>> entry : this.graphBuilder.entrySet()) {
         TextLocation[] successors = entry.getValue().toArray(new TextLocation[0]);

         for (int i = 0; i < successors.length; i++) {
            if (successors[i] != null && successors[i].getLine() < 0) {
               successors[i] = null;
            }
         }

         locationGraph[index++] = new ControlFlowEntry(entry.getKey(), successors);
      }

      return locationGraph;
   }

   private void addEdge(TextLocation source, TextLocation dest) {
      Set<TextLocation> successors = this.graphBuilder.get(source);
      if (successors == null) {
         successors = new HashSet<>();
         this.graphBuilder.put(source, successors);
      }

      successors.add(dest);
   }

   static class AdditionalConnection {
      TextLocation location;
      Set<TextLocation> successors;

      AdditionalConnection(TextLocation location, Set<TextLocation> successors) {
         this.location = location;
         this.successors = successors;
      }
   }

   static class Step {
      TextLocation location;
      Set<TextLocation> startLocations;
      int block;

      Step(TextLocation location, Set<TextLocation> startLocations, int block) {
         this.location = location;
         this.startLocations = startLocations;
         this.block = block;
      }
   }
}
