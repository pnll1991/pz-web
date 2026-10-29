package org.teavm.backend.javascript.codegen;

import org.teavm.backend.javascript.templating.SourceFragment;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class RememberedSource implements SourceFragment {
   public static final int FILTER_TEXT = 1;
   public static final int FILTER_REF = 2;
   public static final int FILTER_DEBUG = 4;
   public static final int FILTER_STATS = 8;
   public static final int FILTER_ALL = 15;
   private byte[] commands;
   private String chars;
   private int[] intArgs;
   private String[] strings;
   private FieldReference[] fields;
   private MethodDescriptor[] methodDescriptors;
   private MethodReference[] methods;

   RememberedSource(
      byte[] commands, String chars, int[] intArgs, String[] strings, FieldReference[] fields, MethodDescriptor[] methodDescriptors, MethodReference[] methods
   ) {
      this.commands = commands;
      this.chars = chars;
      this.intArgs = intArgs;
      this.strings = strings;
      this.fields = fields;
      this.methodDescriptors = methodDescriptors;
      this.methods = methods;
   }

   public void replay(SourceWriterSink sink, int filter) {
      int commandIndex = 0;
      int charIndex = 0;
      int intArgIndex = 0;
      byte[] commands = this.commands;
      int[] intArgs = this.intArgs;
      String chars = this.chars;

      while (commandIndex < commands.length) {
         byte command = commands[commandIndex++];
         if ((command & 128) != 0) {
            int count = 1 + (command & 127);
            if ((filter & 1) != 0) {
               sink.append(chars, charIndex, charIndex + count);
            }

            charIndex += count;
         } else {
            switch (command) {
               case 0:
                  if ((filter & 2) != 0) {
                     sink.appendClass(this.strings[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 1:
                  if ((filter & 2) != 0) {
                     sink.appendField(this.fields[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 2:
                  if ((filter & 2) != 0) {
                     sink.appendStaticField(this.fields[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 3:
                  if ((filter & 2) != 0) {
                     sink.appendVirtualMethod(this.methodDescriptors[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 4:
                  if ((filter & 2) != 0) {
                     sink.appendMethod(this.methods[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 5:
                  if ((filter & 2) != 0) {
                     sink.appendFunction(this.strings[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 6:
                  if ((filter & 2) != 0) {
                     sink.appendGlobal(this.strings[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 7:
                  if ((filter & 2) != 0) {
                     sink.appendInit(this.methods[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 8:
                  if ((filter & 2) != 0) {
                     sink.appendClassInit(this.strings[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 9:
                  if ((filter & 1) != 0) {
                     sink.newLine();
                  }
                  break;
               case 10:
                  if ((filter & 1) != 0) {
                     sink.ws();
                  }
                  break;
               case 11:
                  if ((filter & 1) != 0) {
                     sink.tokenBoundary();
                  }
                  break;
               case 12:
                  if ((filter & 1) != 0) {
                     sink.softNewLine();
                  }
                  break;
               case 13:
                  if ((filter & 1) != 0) {
                     sink.indent();
                  }
                  break;
               case 14:
                  if ((filter & 1) != 0) {
                     sink.outdent();
                  }
                  break;
               case 15:
                  if ((filter & 4) != 0) {
                     int fileIndex = intArgs[intArgIndex];
                     String file = fileIndex >= 0 ? this.strings[fileIndex] : null;
                     sink.emitLocation(file, intArgs[intArgIndex + 1]);
                  }

                  intArgIndex += 2;
                  break;
               case 16:
                  if ((filter & 4) != 0) {
                     sink.enterLocation();
                  }
                  break;
               case 17:
                  if ((filter & 4) != 0) {
                     sink.exitLocation();
                  }
                  break;
               case 18:
                  if ((filter & 4) != 0) {
                     sink.emitStatementStart();
                  }
                  break;
               case 19:
                  if ((filter & 4) != 0) {
                     int methodIndex = intArgs[intArgIndex];
                     sink.emitMethod(methodIndex >= 0 ? this.methodDescriptors[methodIndex] : null);
                  }

                  intArgIndex++;
                  break;
               case 20:
                  if ((filter & 4) != 0) {
                     int classIndex = intArgs[intArgIndex];
                     sink.emitClass(classIndex >= 0 ? this.strings[classIndex] : null);
                  }

                  intArgIndex++;
                  break;
               case 21:
                  if ((filter & 1) != 0) {
                     sink.sameLineWs();
                  }
                  break;
               case 22:
                  if ((filter & 8) != 0) {
                     sink.markClassStart(this.strings[intArgs[intArgIndex]]);
                  }

                  intArgIndex++;
                  break;
               case 23:
                  if ((filter & 8) != 0) {
                     sink.markClassEnd();
                  }
                  break;
               case 24:
                  if ((filter & 8) != 0) {
                     sink.markSectionStart(intArgs[intArgIndex]);
                  }

                  intArgIndex++;
                  break;
               case 25:
                  if ((filter & 8) != 0) {
                     sink.markSectionEnd();
                  }
                  break;
               case 26:
                  int count = intArgs[intArgIndex++];
                  if ((filter & 4) == 0) {
                     intArgIndex += count + 1;
                     break;
                  }

                  String[] names = new String[count];

                  for (int i = 0; i < count; i++) {
                     names[i] = this.strings[intArgs[intArgIndex++]];
                  }

                  String jsName = this.strings[intArgs[intArgIndex++]];
                  sink.emitVariables(names, jsName);
                  break;
               case 27:
                  if ((filter & 1) != 0) {
                     sink.startFunctionDeclaration();
                  }
                  break;
               case 28:
                  if ((filter & 1) != 0) {
                     sink.startVariableDeclaration();
                  }
                  break;
               case 29:
                  if ((filter & 1) != 0) {
                     sink.endDeclaration();
                  }
                  break;
               case 30:
                  if ((filter & 1) != 0) {
                     sink.declareVariable();
                  }
            }
         }
      }
   }

   @Override
   public void write(SourceWriter writer, int precedence) {
      this.replay(writer, 15);
   }
}
