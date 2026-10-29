package org.teavm.dependency;

import java.util.Collection;
import org.teavm.cache.IncrementalDependencyRegistration;
import org.teavm.callgraph.CallGraph;
import org.teavm.common.ServiceRepository;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.extension.ExtensionEnvironmentImpl;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.parsing.resource.ResourceProvider;

public class DependencyAgent implements DependencyInfo, ServiceRepository {
   private DependencyAnalyzer analyzer;
   private String entryPoint;

   DependencyAgent(DependencyAnalyzer analyzer) {
      this.analyzer = analyzer;
   }

   public String getEntryPoint() {
      return this.entryPoint;
   }

   void setEntryPoint(String entryPoint) {
      this.entryPoint = entryPoint;
   }

   public DependencyNode createNode() {
      return this.analyzer.createNode();
   }

   public DependencyType getType(ValueType valueType) {
      return this.analyzer.getType(valueType);
   }

   public String generateClassName() {
      return this.analyzer.generateClassName();
   }

   public String submitClassFile(byte[] data) {
      return this.analyzer.submitClassFile(data);
   }

   public void submitClass(ClassHolder cls) {
      this.analyzer.submitClass(cls);
   }

   public void submitMethod(MethodReference method, Program program) {
      this.analyzer.submitMethod(method, program);
   }

   public MethodDependency linkMethod(MethodReference methodRef) {
      return this.analyzer.linkMethod(methodRef);
   }

   public MethodDependency linkMethod(String className, MethodDescriptor descriptor) {
      return this.analyzer.linkMethod(className, descriptor);
   }

   public ClassDependency linkClass(String className) {
      return this.analyzer.linkClass(className);
   }

   public FieldDependency linkField(FieldReference fieldRef) {
      return this.analyzer.linkField(fieldRef);
   }

   public Diagnostics getDiagnostics() {
      return this.analyzer.getDiagnostics();
   }

   @Override
   public <T> T getService(Class<T> type) {
      return this.analyzer.getService(type);
   }

   @Override
   public ClassReaderSource getClassSource() {
      return this.analyzer.agentClassSource;
   }

   public ResourceProvider getResourceProvider() {
      return this.analyzer.getResourceProvider();
   }

   public ClassReaderSource getUnprocessedClassSource() {
      return this.analyzer.getUnprocessedClassSource();
   }

   @Override
   public ClassLoader getClassLoader() {
      return this.analyzer.getClassLoader();
   }

   public ClassHierarchy getClassHierarchy() {
      return this.analyzer.getClassHierarchy();
   }

   @Override
   public Collection<MethodReference> getReachableMethods() {
      return this.analyzer.getReachableMethods();
   }

   @Override
   public Collection<FieldReference> getReachableFields() {
      return this.analyzer.getReachableFields();
   }

   @Override
   public Collection<String> getReachableClasses() {
      return this.analyzer.getReachableClasses();
   }

   @Override
   public FieldDependencyInfo getField(FieldReference fieldRef) {
      return this.analyzer.getField(fieldRef);
   }

   @Override
   public MethodDependencyInfo getMethod(MethodReference methodRef) {
      return this.analyzer.getMethod(methodRef);
   }

   @Override
   public MethodDependencyInfo getMethodImplementation(MethodReference methodRef) {
      return this.analyzer.getMethodImplementation(methodRef);
   }

   @Override
   public ClassDependencyInfo getClass(String className) {
      return this.analyzer.getClass(className);
   }

   @Override
   public CallGraph getCallGraph() {
      return this.analyzer.getCallGraph();
   }

   public IncrementalDependencyRegistration getIncrementalCache() {
      return this.analyzer.incrementalCache;
   }

   @Override
   public boolean isPrecise() {
      return this.analyzer.isPrecise();
   }

   public ExtensionEnvironmentImpl extensionEnvironment() {
      return this.analyzer.extensionEnvironment();
   }

   void cleanup() {
      this.analyzer = null;
   }
}
