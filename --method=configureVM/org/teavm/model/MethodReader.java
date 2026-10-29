package org.teavm.model;

import java.util.List;

public interface MethodReader extends MemberReader {
   ValueType getResultType();

   GenericTypeParameter[] getTypeParameters();

   GenericValueType getGenericResultType();

   int parameterCount();

   ValueType[] getSignature();

   ValueType parameterType(int var1);

   GenericValueType genericParameterType(int var1);

   ValueType[] getParameterTypes();

   GenericValueType[] getGenericParameterTypes();

   AnnotationContainerReader parameterAnnotation(int var1);

   AnnotationContainerReader[] getParameterAnnotations();

   MethodDescriptor getDescriptor();

   MethodReference getReference();

   ProgramReader getProgram();

   AnnotationValue getAnnotationDefault();

   List<? extends String> getThrownTypes();
}
