package org.teavm.model.text;

import java.util.List;
import java.util.Objects;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.IncomingReader;
import org.teavm.model.InliningInfo;
import org.teavm.model.InstructionIterator;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.TextLocation;
import org.teavm.model.TryCatchBlockReader;
import org.teavm.model.VariableReader;

public class ListingBuilder {
   public String buildListing(ProgramReader program, String prefix) {
      StringBuilder sb = new StringBuilder();
      StringBuilder insnSb = new StringBuilder();
      InstructionStringifier stringifier = new InstructionStringifier(insnSb, program);

      for (int i = 0; i < program.variableCount(); i++) {
         VariableReader var = program.variableAt(i);
         if (var != null && var.getDebugName() != null) {
            sb.append(prefix).append("var @").append(stringifier.getVariableLabel(i));
            sb.append(" as ").append(var.getDebugName());
            sb.append(" // " + var.getIndex());
            sb.append('\n');
         }
      }

      for (int i = 0; i < program.basicBlockCount(); i++) {
         BasicBlockReader block = program.basicBlockAt(i);
         sb.append(prefix).append("$").append(i).append("\n");
         if (block != null) {
            if (block.getExceptionVariable() != null) {
               sb.append("    @").append(stringifier.getVariableLabel(block.getExceptionVariable().getIndex())).append(" := exception\n");
            }

            for (PhiReader phi : block.readPhis()) {
               sb.append(prefix).append("    ");
               sb.append("@").append(stringifier.getVariableLabel(phi.getReceiver().getIndex())).append(" := phi ");
               List<? extends IncomingReader> incomings = phi.readIncomings();

               for (int j = 0; j < incomings.size(); j++) {
                  if (j > 0) {
                     sb.append(", ");
                  }

                  IncomingReader incoming = incomings.get(j);
                  sb.append("@")
                     .append(stringifier.getVariableLabel(incoming.getValue().getIndex()))
                     .append(" from ")
                     .append("$")
                     .append(incoming.getSource().getIndex());
               }

               sb.append("\n");
            }

            TextLocation location = null;

            for (InstructionIterator iterator = block.iterateInstructions(); iterator.hasNext(); sb.append(prefix).append("    ").append(insnSb).append("\n")) {
               iterator.next();
               insnSb.setLength(0);
               iterator.read(stringifier);
               if (!Objects.equals(location, stringifier.getLocation())) {
                  location = stringifier.getLocation();
                  sb.append(prefix).append("  at ");
                  if (location != null && !location.isEmpty()) {
                     sb.append("'");
                     InstructionStringifier.escapeStringLiteral(location.getFileName(), sb);
                     sb.append("' " + location.getLine());
                  } else {
                     sb.append("unknown location");
                  }

                  if (location != null) {
                     for (InliningInfo inliningInfo = location.getInlining(); inliningInfo != null; inliningInfo = inliningInfo.getParent()) {
                        sb.append(" at ");
                        InstructionStringifier.escapeIdentifierIfNeeded(sb, inliningInfo.getMethod().toString());
                        if (inliningInfo.getFileName() != null) {
                           sb.append(" '");
                           InstructionStringifier.escapeStringLiteral(inliningInfo.getFileName(), sb);
                           sb.append("' " + inliningInfo.getLine());
                        }
                     }
                  }

                  sb.append('\n');
               }
            }

            for (TryCatchBlockReader tryCatch : block.readTryCatchBlocks()) {
               sb.append(prefix).append("    catch ");
               if (tryCatch.getExceptionType() != null) {
                  InstructionStringifier.escapeStringLiteral(tryCatch.getExceptionType(), sb);
               }

               sb.append(" goto $").append(tryCatch.getHandler().getIndex());
               sb.append("\n");
            }
         }
      }

      return sb.toString();
   }
}
