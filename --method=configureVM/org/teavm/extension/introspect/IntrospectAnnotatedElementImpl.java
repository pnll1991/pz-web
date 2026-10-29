package org.teavm.extension.introspect;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.teavm.model.AnnotationContainerReader;

public abstract class IntrospectAnnotatedElementImpl implements IntrospectAnnotatedElement {
   protected final Introspection introspection;
   private Map<IntrospectClass<?>, IntrospectAnnotation<?>> annotationsByType;
   private Map<? extends IntrospectClass<?>, ? extends List<? extends IntrospectAnnotation<?>>> annotationListsByType;
   private List<IntrospectAnnotation<?>> allAnnotations;

   IntrospectAnnotatedElementImpl(Introspection introspection) {
      this.introspection = introspection;
   }

   public <A extends Annotation> IntrospectAnnotation<A> annotation(IntrospectClass<A> type) {
      if (this.annotationsByType == null) {
         this.annotationsByType = this.allAnnotations().stream().collect(Collectors.toMap(IntrospectAnnotation::type, a -> a));
      }

      return (IntrospectAnnotation<A>)this.annotationsByType.get(type);
   }

   public <A extends Annotation> IntrospectAnnotation<A> annotation(Class<A> type) {
      return this.annotation(this.introspection.findClass(type));
   }

   public <A extends Annotation> List<? extends IntrospectAnnotation<A>> annotations(IntrospectClass<A> type) {
      if (this.annotationListsByType == null) {
         HashMap<IntrospectClass<?>, List<IntrospectAnnotation<?>>> result = new HashMap<>();

         for (IntrospectAnnotation<?> annotation : this.allAnnotations()) {
            result.computeIfAbsent(annotation.type(), k -> new ArrayList<>()).add(annotation);
         }

         HashMap<IntrospectClass<?>, List<IntrospectAnnotation<?>>> unmodifiableResult = new HashMap<>();

         for (Entry<IntrospectClass<?>, List<IntrospectAnnotation<?>>> entry : result.entrySet()) {
            unmodifiableResult.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
         }

         this.annotationListsByType = unmodifiableResult;
      }

      List<? extends IntrospectAnnotation<A>> list = (List<? extends IntrospectAnnotation<A>>)this.annotationListsByType.get(type);
      return list != null ? list : Collections.emptyList();
   }

   public <A extends Annotation> List<? extends IntrospectAnnotation<A>> annotations(Class<A> type) {
      return this.annotations(this.introspection.findClass(type));
   }

   public List<? extends IntrospectAnnotation<?>> allAnnotations() {
      if (this.allAnnotations == null) {
         AnnotationContainerReader underlyingAnnotations = this.annotationContainer();
         if (underlyingAnnotations == null) {
            this.allAnnotations = Collections.emptyList();
         } else {
            this.allAnnotations = StreamSupport.stream(this.annotationContainer().all().spliterator(), false)
               .map(annotation -> new IntrospectAnnotationImpl(this.introspection, annotation))
               .collect(Collectors.toUnmodifiableList());
         }
      }

      return this.allAnnotations;
   }

   protected abstract AnnotationContainerReader annotationContainer();
}
