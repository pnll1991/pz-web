package org.teavm.backend.c.generate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;

public class ClassGenerationContext {
   private GenerationContext context;
   private IncludeManager includes;
   private String currentClassName;
   private CodeWriter methodIdWriter;
   private CodeWriter importWriter;
   private CodeWriter initWriter;
   private Set<MethodReference> importedMethods = new HashSet<>();
   private Map<MethodDescriptor, String> virtualMethodIds = new HashMap<>();

   public ClassGenerationContext(GenerationContext context, IncludeManager includes, CodeWriter prologueWriter, CodeWriter initWriter, String currentClassName) {
      this.context = context;
      this.includes = includes;
      this.importWriter = prologueWriter.fragment();
      this.methodIdWriter = prologueWriter.fragment();
      this.initWriter = initWriter != null ? initWriter.fragment() : null;
      this.currentClassName = currentClassName;
   }

   public GenerationContext getContext() {
      return this.context;
   }

   public void importMethod(MethodReference method, boolean isStatic) {
      if (this.context.isIncremental()) {
         if (this.importedMethods.add(method)) {
            this.importWriter.print("extern ");
            CodeGenerator.generateMethodSignature(this.importWriter, this.context.getNames(), method, isStatic, false);
            this.importWriter.println(";");
         }
      } else {
         this.includes.includeClass(method.getClassName());
      }
   }

   public String getVirtualMethodId(MethodDescriptor methodDesc) {
      return this.virtualMethodIds.computeIfAbsent(methodDesc, m -> {
         String name = "methodId_" + this.context.getNames().forClass(this.currentClassName) + "_" + this.context.getNames().forVirtualMethod(m);
         this.methodIdWriter.println("static int32_t " + name + ";");
         this.initWriter.print(name + " = teavm_vc_getMethodId(u");
         StringPoolGenerator.generateSimpleStringLiteral(this.initWriter, methodDesc.toString());
         this.initWriter.println(");");
         return name;
      });
   }
}
