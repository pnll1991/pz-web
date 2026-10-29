package org.teavm.backend.wasm.disasm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.backend.wasm.parser.NameIndirectMapListener;
import org.teavm.backend.wasm.parser.NameMapListener;
import org.teavm.backend.wasm.parser.NameSectionListener;

public class NameAccumulatingSectionListener implements NameSectionListener {
   private List<String> functions = new ArrayList<>();
   private List<List<String>> locals = new ArrayList<>();
   private List<String> types = new ArrayList<>();
   private List<String> globals = new ArrayList<>();
   private List<List<String>> fields = new ArrayList<>();

   @Override
   public NameMapListener functions() {
      return this.listener(this.functions);
   }

   @Override
   public NameIndirectMapListener locals() {
      return this.indirectListener(this.locals);
   }

   @Override
   public NameMapListener types() {
      return this.listener(this.types);
   }

   @Override
   public NameMapListener globals() {
      return this.listener(this.globals);
   }

   @Override
   public NameIndirectMapListener fields() {
      return this.indirectListener(this.fields);
   }

   public NameProvider buildProvider() {
      return new NameProvider() {
         String[] functionArray = NameAccumulatingSectionListener.this.buildMap(NameAccumulatingSectionListener.this.functions);
         String[][] localArray = NameAccumulatingSectionListener.this.buildIndirectMap(NameAccumulatingSectionListener.this.locals);
         String[] typeArray = NameAccumulatingSectionListener.this.buildMap(NameAccumulatingSectionListener.this.types);
         String[] globalArray = NameAccumulatingSectionListener.this.buildMap(NameAccumulatingSectionListener.this.globals);
         String[][] fieldArray = NameAccumulatingSectionListener.this.buildIndirectMap(NameAccumulatingSectionListener.this.fields);

         @Override
         public String function(int index) {
            return this.fetch(this.functionArray, index);
         }

         @Override
         public String local(int functionIndex, int index) {
            return this.fetch(this.localArray, functionIndex, index);
         }

         @Override
         public String type(int index) {
            return this.fetch(this.typeArray, index);
         }

         @Override
         public String global(int index) {
            return this.fetch(this.globalArray, index);
         }

         @Override
         public String field(int typeIndex, int index) {
            return this.fetch(this.fieldArray, typeIndex, index);
         }

         private String fetch(String[] array, int index) {
            return array != null && index < array.length ? array[index] : null;
         }

         private String fetch(String[][] array, int mapIndex, int index) {
            return array != null && mapIndex < array.length ? this.fetch(array[mapIndex], index) : null;
         }
      };
   }

   private String[] buildMap(List<String> list) {
      return list == null ? null : list.toArray(new String[0]);
   }

   private String[][] buildIndirectMap(List<List<String>> list) {
      String[][] result = new String[list.size()][];

      for (int i = 0; i < list.size(); i++) {
         result[i] = this.buildMap(list.get(i));
      }

      return result;
   }

   private NameMapListener listener(List<String> target) {
      return (index, name) -> {
         if (index >= target.size()) {
            target.addAll(Collections.nCopies(index - target.size() + 1, null));
         }

         target.set(index, name);
      };
   }

   private NameIndirectMapListener indirectListener(List<List<String>> target) {
      return index -> {
         if (index >= target.size()) {
            target.addAll(Collections.nCopies(index - target.size() + 1, null));
         }

         ArrayList<String> list = new ArrayList<>();
         target.set(index, list);
         return this.listener(list);
      };
   }
}
