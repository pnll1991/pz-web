package org.teavm.backend.wasm.transformation;

import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.model.WasmCompositeType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmFunctionType;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.instruction.WasmCall;
import org.teavm.backend.wasm.model.instruction.WasmCast;
import org.teavm.backend.wasm.model.instruction.WasmExternConversion;
import org.teavm.backend.wasm.model.instruction.WasmExternConversionType;
import org.teavm.backend.wasm.model.instruction.WasmGetLocal;
import org.teavm.backend.wasm.model.instruction.WasmInstructionList;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.runtime.Fiber;

class CoroutineFunctions {
   private BaseWasmFunctionRepository functions;
   private WasmFunction pushIntCache;
   private WasmFunction pushLongCache;
   private WasmFunction pushFloatCache;
   private WasmFunction pushDoubleCache;
   private WasmFunction pushObjectCache;
   private WasmFunction pushFunctionCache;
   private WasmFunction pushExceptionCache;
   private WasmFunction popIntCache;
   private WasmFunction popLongCache;
   private WasmFunction popFloatCache;
   private WasmFunction popDoubleCache;
   private WasmFunction popObjectCache;
   private WasmFunction popFunctionCache;
   private WasmFunction popExceptionCache;
   private WasmFunction isResumingCache;
   private WasmFunction isSuspendingCache;
   private WasmFunction currentFiberCache;

   CoroutineFunctions(BaseWasmFunctionRepository functions) {
      this.functions = functions;
   }

   WasmFunction pushInt() {
      if (this.pushIntCache == null) {
         this.pushIntCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "reversePush", int.class, Fiber.class, void.class));
      }

      return this.pushIntCache;
   }

   WasmFunction pushLong() {
      if (this.pushLongCache == null) {
         this.pushLongCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "reversePush", long.class, Fiber.class, void.class));
      }

      return this.pushLongCache;
   }

   WasmFunction pushFloat() {
      if (this.pushFloatCache == null) {
         this.pushFloatCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "reversePush", float.class, Fiber.class, void.class));
      }

      return this.pushFloatCache;
   }

   WasmFunction pushDouble() {
      if (this.pushDoubleCache == null) {
         this.pushDoubleCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "reversePush", double.class, Fiber.class, void.class));
      }

      return this.pushDoubleCache;
   }

   WasmFunction pushPlatformObject() {
      if (this.pushObjectCache == null) {
         this.pushObjectCache = this.functions
            .forStaticMethod(new MethodReference(Fiber.class, "reversePush", Fiber.PlatformObject.class, Fiber.class, void.class));
      }

      return this.pushObjectCache;
   }

   WasmFunction pushPlatformFunction() {
      if (this.pushFunctionCache == null) {
         this.pushFunctionCache = this.functions
            .forStaticMethod(new MethodReference(Fiber.class, "reversePush", Fiber.PlatformFunction.class, Fiber.class, void.class));
      }

      return this.pushFunctionCache;
   }

   WasmFunction pushPlatformException() {
      if (this.pushExceptionCache == null) {
         this.pushExceptionCache = this.functions
            .forStaticMethod(new MethodReference(Fiber.class, "reversePush", Fiber.PlatformException.class, Fiber.class, void.class));
      }

      return this.pushExceptionCache;
   }

   WasmFunction popInt() {
      if (this.popIntCache == null) {
         this.popIntCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popInt", int.class));
      }

      return this.popIntCache;
   }

   WasmFunction popLong() {
      if (this.popLongCache == null) {
         this.popLongCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popLong", long.class));
      }

      return this.popLongCache;
   }

   WasmFunction popFloat() {
      if (this.popFloatCache == null) {
         this.popFloatCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popFloat", float.class));
      }

      return this.popFloatCache;
   }

   WasmFunction popDouble() {
      if (this.popDoubleCache == null) {
         this.popDoubleCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popDouble", double.class));
      }

      return this.popDoubleCache;
   }

   WasmFunction popPlatformObject() {
      if (this.popObjectCache == null) {
         this.popObjectCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popPlatformObject", Fiber.PlatformObject.class));
      }

      return this.popObjectCache;
   }

   WasmFunction popPlatformFunction() {
      if (this.popFunctionCache == null) {
         this.popFunctionCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popPlatformFunction", Fiber.PlatformFunction.class));
      }

      return this.popFunctionCache;
   }

   WasmFunction popPlatformException() {
      if (this.popExceptionCache == null) {
         this.popExceptionCache = this.functions.forInstanceMethod(new MethodReference(Fiber.class, "popPlatformException", Fiber.PlatformException.class));
      }

      return this.popExceptionCache;
   }

   WasmFunction isResuming() {
      if (this.isResumingCache == null) {
         this.isResumingCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "isPresentAndResuming", Fiber.class, boolean.class));
      }

      return this.isResumingCache;
   }

   WasmFunction isSuspending() {
      if (this.isSuspendingCache == null) {
         this.isSuspendingCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "isPresentAndSuspending", Fiber.class, boolean.class));
      }

      return this.isSuspendingCache;
   }

   WasmFunction currentFiber() {
      if (this.currentFiberCache == null) {
         this.currentFiberCache = this.functions.forStaticMethod(new MethodReference(Fiber.class, "current", Fiber.class));
      }

      return this.currentFiberCache;
   }

   void restoreValue(WasmType type, WasmInstructionList target, TextLocation location) {
      if (type instanceof WasmType.Number) {
         switch (((WasmType.Number)type).number) {
            case INT32:
               target.add(new WasmCall(this.popInt()), location);
               break;
            case INT64:
               target.add(new WasmCall(this.popLong()), location);
               break;
            case FLOAT32:
               target.add(new WasmCall(this.popFloat()), location);
               break;
            case FLOAT64:
               target.add(new WasmCall(this.popDouble()), location);
         }
      } else {
         if (!(type instanceof WasmType.Reference refType)) {
            throw new IllegalArgumentException();
         }

         if (type instanceof WasmType.SpecialReference) {
            switch (((WasmType.SpecialReference)type).kind) {
               case EXTERN:
                  target.add(new WasmCall(this.popPlatformObject()), location);
                  target.add(new WasmExternConversion(WasmExternConversionType.ANY_TO_EXTERN), location);
                  return;
               case FUNC:
                  target.add(new WasmCall(this.popPlatformFunction()), location);
                  break;
               case EXN:
                  target.add(new WasmCall(this.popPlatformException()), location);
                  break;
               default:
                  target.add(new WasmCall(this.popPlatformObject()), location);
            }
         } else if (type instanceof WasmType.CompositeReference) {
            WasmCompositeType composite = ((WasmType.CompositeReference)type).composite;
            if (composite instanceof WasmFunctionType) {
               target.add(new WasmCall(this.popPlatformFunction()), location);
            } else {
               target.add(new WasmCall(this.popPlatformObject()), location);
            }
         }

         target.add(new WasmCast(refType), location);
      }
   }

   void saveValue(WasmType type, WasmInstructionList list, WasmLocal fiberLocal, TextLocation location) {
      if (type instanceof WasmType.Number) {
         list.add(new WasmGetLocal(fiberLocal), location);
         switch (((WasmType.Number)type).number) {
            case INT32:
               list.add(new WasmCall(this.pushInt()), location);
               break;
            case INT64:
               list.add(new WasmCall(this.pushLong()), location);
               break;
            case FLOAT32:
               list.add(new WasmCall(this.pushFloat()), location);
               break;
            case FLOAT64:
               list.add(new WasmCall(this.pushDouble()), location);
         }
      } else {
         if (type instanceof WasmType.SpecialReference) {
            switch (((WasmType.SpecialReference)type).kind) {
               case EXTERN:
                  list.add(new WasmExternConversion(WasmExternConversionType.EXTERN_TO_ANY), location);
                  break;
               case FUNC:
                  list.add(new WasmGetLocal(fiberLocal), location);
                  list.add(new WasmCall(this.pushPlatformFunction()), location);
                  return;
               case EXN:
                  list.add(new WasmGetLocal(fiberLocal), location);
                  list.add(new WasmCall(this.pushPlatformException()), location);
                  return;
            }
         } else if (type instanceof WasmType.CompositeReference) {
            WasmCompositeType composite = ((WasmType.CompositeReference)type).composite;
            if (composite instanceof WasmFunctionType) {
               list.add(new WasmGetLocal(fiberLocal), location);
               list.add(new WasmCall(this.pushPlatformFunction()), location);
               return;
            }
         }

         list.add(new WasmGetLocal(fiberLocal), location);
         list.add(new WasmCall(this.pushPlatformObject()), location);
      }
   }
}
