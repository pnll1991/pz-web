package org.teavm.backend.c.generate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.teavm.hppc.ObjectIntHashMap;
import org.teavm.hppc.ObjectIntMap;
import org.teavm.model.ValueType;
import org.teavm.model.lowlevel.CallSiteDescriptor;
import org.teavm.model.lowlevel.CallSiteLocation;
import org.teavm.model.lowlevel.ExceptionHandlerDescriptor;

public class CallSiteGenerator {
   private GenerationContext context;
   private CodeWriter writer;
   private IncludeManager includes;
   private ObjectIntMap<CallSiteGenerator.LocationList> locationMap = new ObjectIntHashMap();
   private List<CallSiteGenerator.LocationList> locations = new ArrayList<>();
   private List<CallSiteGenerator.HandlerContainer> exceptionHandlers = new ArrayList<>();
   private ObjectIntMap<CallSiteGenerator.MethodLocation> methodLocationMap = new ObjectIntHashMap();
   private List<CallSiteGenerator.MethodLocation> methodLocations = new ArrayList<>();
   private String callSitesName;
   private boolean isStatic;

   public CallSiteGenerator(GenerationContext context, CodeWriter writer, IncludeManager includes, String callSitesName) {
      this.context = context;
      this.writer = writer;
      this.includes = includes;
      this.callSitesName = callSitesName;
   }

   public void setStatic(boolean isStatic) {
      this.isStatic = isStatic;
   }

   public void generate(List<? extends CallSiteDescriptor> callSites) {
      CodeWriter writerForMethodLocations = this.writer.fragment();
      CodeWriter writerForLocations = this.writer.fragment();
      this.generateCallSites(callSites);
      CodeWriter oldWriter = this.writer;
      this.writer = writerForLocations;
      this.generateLocations();
      this.writer = writerForMethodLocations;
      this.generateMethodLocations();
      this.generateHandlers();
      this.writer = oldWriter;
   }

   private void generateCallSites(List<? extends CallSiteDescriptor> callSites) {
      this.includes.includePath("strings.h");
      if (this.isStatic) {
         this.writer.print("static ");
      }

      this.writer.print("TeaVM_CallSite " + this.callSitesName + "[" + callSites.size() + "] = {").indent();
      boolean first = true;

      for (CallSiteDescriptor callSite : callSites) {
         if (!first) {
            this.writer.print(", ");
         }

         first = false;
         int locationIndex = -1;
         if (!this.context.isObfuscated()) {
            CallSiteLocation[] locations = callSite.getLocations();
            if (locations != null && locations.length > 0) {
               CallSiteGenerator.LocationList prevList = null;

               for (int i = locations.length - 1; i >= 0; i--) {
                  CallSiteGenerator.LocationList list = new CallSiteGenerator.LocationList(locations[i], prevList);
                  locationIndex = this.locationMap.getOrDefault(list, -1);
                  if (locationIndex < 0) {
                     locationIndex = this.locations.size();
                     this.locationMap.put(list, locationIndex);
                     this.locations.add(list);
                  } else {
                     list = this.locations.get(locationIndex);
                  }

                  prevList = list;
               }
            }
         }

         String firstHandlerExpr = !callSite.getHandlers().isEmpty()
            ? "exceptionHandlers_" + this.callSitesName + " + " + this.exceptionHandlers.size()
            : "NULL";
         this.writer.println().print("{ ");
         this.writer.print(".firstHandler = ").print(firstHandlerExpr).print(", ");
         this.writer.print(".location = ").print(locationIndex >= 0 ? "callSiteLocations_" + this.callSitesName + " + " + locationIndex : "NULL");
         this.writer.print(" }");

         for (int i = 0; i < callSite.getHandlers().size(); i++) {
            if (i > 0) {
               this.exceptionHandlers.get(this.exceptionHandlers.size() - 1).nextIndex = this.exceptionHandlers.size();
            }

            this.exceptionHandlers.add(new CallSiteGenerator.HandlerContainer(callSite.getHandlers().get(i)));
         }
      }

      this.writer.println().outdent().println("};");
   }

   private void generateLocations() {
      if (!this.locations.isEmpty()) {
         this.writer.print("static TeaVM_CallSiteLocation callSiteLocations_" + this.callSitesName + "[" + this.locations.size() + "] = {").indent();
         boolean first = true;

         for (CallSiteGenerator.LocationList locationList : this.locations) {
            if (!first) {
               this.writer.print(",");
            }

            first = false;
            CallSiteLocation location = locationList.location;
            this.writer.println().print("{ ");
            CallSiteGenerator.MethodLocation methodLocation = new CallSiteGenerator.MethodLocation(
               location.getFileName(), location.getClassName(), location.getMethodName()
            );
            int index = this.methodLocationMap.getOrDefault(methodLocation, -1);
            if (index < 0) {
               index = this.methodLocations.size();
               this.methodLocationMap.put(methodLocation, index);
               this.methodLocations.add(methodLocation);
            }

            this.writer.print(".method = ").print("methodLocations_" + this.callSitesName + " + " + index).print(", ");
            this.writer.print(".lineNumber = ").print(String.valueOf(location.getLineNumber()));
            if (locationList.next != null) {
               int nextIndex = this.locationMap.get(locationList.next);
               this.writer.print(", .next = callSiteLocations_" + this.callSitesName + " + " + nextIndex);
            }

            this.writer.print(" }");
         }

         this.writer.println().outdent().println("};");
      }
   }

   private void generateMethodLocations() {
      if (!this.methodLocations.isEmpty()) {
         this.writer.print("static TeaVM_MethodLocation methodLocations_" + this.callSitesName + "[" + this.methodLocations.size() + "] = {").indent();
         boolean first = true;

         for (CallSiteGenerator.MethodLocation location : this.methodLocations) {
            if (!first) {
               this.writer.print(",");
            }

            first = false;
            this.writer.println().print("{ ");
            String fileName = location.file != null && !location.file.isEmpty() ? location.file : null;
            this.writer.print(".fileName = ").print(this.getStringExpr(fileName)).print(", ");
            this.writer.print(".className = ").print(this.getStringExpr(location.className)).print(", ");
            this.writer.print(".methodName = ").print(this.getStringExpr(location.methodName));
            this.writer.print(" }");
         }

         this.writer.println().outdent().println("};");
      }
   }

   private void generateHandlers() {
      if (!this.exceptionHandlers.isEmpty()) {
         String name = "exceptionHandlers_" + this.callSitesName;
         this.writer.print("static TeaVM_ExceptionHandler " + name + "[" + this.exceptionHandlers.size() + "] = {").indent();
         boolean first = true;

         for (CallSiteGenerator.HandlerContainer handler : this.exceptionHandlers) {
            if (!first) {
               this.writer.print(",");
            }

            first = false;
            this.writer.println().print("{ ");
            if (handler.descriptor.getClassName() != null) {
               this.includes.includeClass(handler.descriptor.getClassName());
            }

            String classExpr = handler.descriptor.getClassName() != null
               ? "(TeaVM_Class*) &" + this.context.getNames().forClassInstance(ValueType.object(handler.descriptor.getClassName()))
               : "NULL";
            this.writer.print(".id = ").print(String.valueOf(handler.descriptor.getId())).print(", ");
            this.writer.print(".exceptionClass = ").print(classExpr).print(", ");
            this.writer.print(".next = ").print(handler.nextIndex < 0 ? "NULL" : name + "+" + handler.nextIndex);
            this.writer.print(" }");
         }

         this.writer.println().outdent().println("};");
      }
   }

   private String getStringExpr(String s) {
      return s != null ? "TEAVM_GET_STRING_ADDRESS(" + this.context.getStringPool().getStringIndex(s) + ")" : "NULL";
   }

   static class HandlerContainer {
      ExceptionHandlerDescriptor descriptor;
      int nextIndex = -1;

      HandlerContainer(ExceptionHandlerDescriptor descriptor) {
         this.descriptor = descriptor;
      }
   }

   static final class LocationList {
      final CallSiteLocation location;
      final CallSiteGenerator.LocationList next;

      LocationList(CallSiteLocation location, CallSiteGenerator.LocationList next) {
         this.location = location;
         this.next = next;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof CallSiteGenerator.LocationList that) ? false : this.location.equals(that.location) && Objects.equals(this.next, that.next);
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.location, this.next);
      }
   }

   static final class MethodLocation {
      final String file;
      final String className;
      final String methodName;

      MethodLocation(String file, String className, String methodName) {
         this.file = file;
         this.className = className;
         this.methodName = methodName;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof CallSiteGenerator.MethodLocation that)
               ? false
               : Objects.equals(this.file, that.file) && Objects.equals(this.className, that.className) && Objects.equals(this.methodName, that.methodName);
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.file, this.className, this.methodName);
      }
   }
}
