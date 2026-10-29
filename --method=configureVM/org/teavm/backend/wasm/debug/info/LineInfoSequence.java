package org.teavm.backend.wasm.debug.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LineInfoSequence {
   private int startAddress;
   private int endAddress;
   private MethodInfo method;
   private LineInfoCommand[] commands;
   private List<LineInfoCommand> commandList;

   public LineInfoSequence(int startAddress, int endAddress, MethodInfo method, LineInfoCommand[] commands) {
      this.startAddress = startAddress;
      this.endAddress = endAddress;
      this.method = method;
      this.commands = (LineInfoCommand[])commands.clone();
      this.commandList = Collections.unmodifiableList(Arrays.asList(this.commands));
   }

   public int startAddress() {
      return this.startAddress;
   }

   public int endAddress() {
      return this.endAddress;
   }

   public MethodInfo method() {
      return this.method;
   }

   public List<? extends LineInfoCommand> commands() {
      return this.commandList;
   }

   public LineInfoUnpackedSequence unpack() {
      LineInfoCommandExecutor commandExecutor = new LineInfoCommandExecutor();
      ArrayList<InstructionLocation> locations = new ArrayList<>();

      for (LineInfoCommand command : this.commands) {
         command.acceptVisitor(commandExecutor);
         InstructionLocation location = commandExecutor.createLocation();
         if (location != null) {
            locations.add(location);
         }
      }

      return new LineInfoUnpackedSequence(this.startAddress, this.endAddress, this.method, locations);
   }
}
