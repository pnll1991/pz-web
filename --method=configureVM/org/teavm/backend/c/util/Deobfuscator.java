package org.teavm.backend.c.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.common.json.JsonAllErrorVisitor;
import org.teavm.common.json.JsonArrayVisitor;
import org.teavm.common.json.JsonErrorReporter;
import org.teavm.common.json.JsonParser;
import org.teavm.common.json.JsonPropertyVisitor;
import org.teavm.common.json.JsonVisitingConsumer;
import org.teavm.common.json.JsonVisitor;

public class Deobfuscator {
   private Map<Integer, Deobfuscator.CallSite> callSites = new HashMap<>();

   public Deobfuscator(Reader reader) throws IOException {
      Deobfuscator.CallSiteVisitor visitor = new Deobfuscator.CallSiteVisitor();
      new JsonParser(new JsonVisitingConsumer(new JsonArrayVisitor(visitor))).parse(reader);
      visitor.flush();
   }

   public Deobfuscator.Location[] getLocations(int callSiteId) {
      Deobfuscator.CallSite callSite = this.callSites.get(callSiteId);
      if (callSite == null) {
         return null;
      }

      Deobfuscator.Location[] result = new Deobfuscator.Location[callSite.locations.length];

      for (int i = 0; i < result.length; i++) {
         result[i] = callSite.locations[i].clone();
      }

      return result;
   }

   public String deobfuscate(Reader text) throws IOException {
      StringBuilder result = new StringBuilder();
      String expectedPrefix = "\tat Obfuscated.obfuscated(Obfuscated.java:";
      int expectedPrefixPos = 0;
      boolean isInNumber = false;
      boolean expectingLineEnd = false;
      StringBuilder sb = new StringBuilder();

      while (true) {
         int c = text.read();
         if (expectedPrefixPos >= 0) {
            if (expectedPrefix.charAt(expectedPrefixPos) == c) {
               if (++expectedPrefixPos == expectedPrefix.length()) {
                  expectedPrefixPos = -1;
                  isInNumber = true;
               }
               continue;
            }

            result.append(expectedPrefix, 0, expectedPrefixPos);
            expectedPrefixPos = -1;
         } else if (isInNumber) {
            if (c >= 48 && c <= 57) {
               sb.append((char)c);
               continue;
            }

            if (c == 41) {
               isInNumber = false;
               expectingLineEnd = true;
               continue;
            }

            isInNumber = false;
            result.append(expectedPrefix).append(sb);
         }

         if (c == 13 || c == 10 || c < 0) {
            if (expectingLineEnd) {
               int callSiteId = Integer.parseInt(sb.toString());
               Deobfuscator.Location[] locations = this.getLocations(callSiteId);
               if (locations == null) {
                  result.append(expectedPrefix).append(sb).append(')');
               } else {
                  boolean first = true;

                  for (Deobfuscator.Location location : locations) {
                     if (!first) {
                        result.append('\n');
                     }

                     first = false;
                     result.append("    ");
                     result.append(location.className).append('.').append(location.methodName).append('(');
                     if (location.fileName != null & location.line >= 0) {
                        result.append(location.fileName).append(':').append(location.line);
                     } else {
                        result.append("Unknown location");
                     }

                     result.append(")");
                  }
               }

               sb.setLength(0);
            }

            expectingLineEnd = false;
            expectedPrefixPos = 0;
         }

         if (c < 0) {
            return result.toString();
         }

         result.append((char)c);
      }
   }

   public static void main(String[] args) throws IOException {
      if (args.length != 2) {
         System.err.println("Two arguments expected (JSON table file, stack trace file)");
         System.exit(1);
      }

      Deobfuscator deobfuscator;
      try (
         InputStream input = new BufferedInputStream(new FileInputStream(args[0]));
         Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
      ) {
         deobfuscator = new Deobfuscator(reader);
      }

      String result;
      try (
         BufferedInputStream var16 = new BufferedInputStream(new FileInputStream(args[1]));
         Reader reader = new InputStreamReader(var16, StandardCharsets.UTF_8);
      ) {
         result = deobfuscator.deobfuscate(reader);
      }

      System.out.println(result);
   }

   static class CallSite {
      int id;
      Deobfuscator.Location[] locations;
   }

   class CallSiteVisitor extends JsonAllErrorVisitor {
      Deobfuscator.CallSite callSite;
      List<Deobfuscator.Location> locations = new ArrayList<>();
      Deobfuscator.Location location;
      JsonPropertyVisitor propertyVisitor = new JsonPropertyVisitor(false);
      JsonPropertyVisitor locationPropertyVisitor = new JsonPropertyVisitor(false);
      JsonAllErrorVisitor locationVisitor = new JsonAllErrorVisitor() {
         @Override
         public JsonVisitor object(JsonErrorReporter reporter) {
            CallSiteVisitor.this.location = new Deobfuscator.Location();
            CallSiteVisitor.this.locations.add(CallSiteVisitor.this.location);
            return CallSiteVisitor.this.locationPropertyVisitor;
         }
      };

      CallSiteVisitor() {
         this.propertyVisitor.addProperty("id", new JsonAllErrorVisitor() {
            @Override
            public void intValue(JsonErrorReporter reporter, long value) {
               CallSiteVisitor.this.callSite.id = (int)value;
            }
         });
         this.propertyVisitor.addProperty("locations", new JsonArrayVisitor(this.locationVisitor));
         this.locationPropertyVisitor.addProperty("file", new JsonAllErrorVisitor() {
            @Override
            public void stringValue(JsonErrorReporter reporter, String value) {
               CallSiteVisitor.this.location.fileName = value;
            }

            @Override
            public void nullValue(JsonErrorReporter reporter) {
               CallSiteVisitor.this.location.fileName = null;
            }
         });
         this.locationPropertyVisitor.addProperty("class", new JsonAllErrorVisitor() {
            @Override
            public void stringValue(JsonErrorReporter reporter, String value) {
               CallSiteVisitor.this.location.className = value;
            }

            @Override
            public void nullValue(JsonErrorReporter reporter) {
               CallSiteVisitor.this.location.className = null;
            }
         });
         this.locationPropertyVisitor.addProperty("method", new JsonAllErrorVisitor() {
            @Override
            public void stringValue(JsonErrorReporter reporter, String value) {
               CallSiteVisitor.this.location.methodName = value;
            }

            @Override
            public void nullValue(JsonErrorReporter reporter) {
               CallSiteVisitor.this.location.methodName = null;
            }
         });
         this.locationPropertyVisitor.addProperty("line", new JsonAllErrorVisitor() {
            @Override
            public void intValue(JsonErrorReporter reporter, long value) {
               CallSiteVisitor.this.location.line = (int)value;
            }
         });
      }

      @Override
      public JsonVisitor object(JsonErrorReporter reporter) {
         this.flush();
         this.callSite = new Deobfuscator.CallSite();
         return this.propertyVisitor;
      }

      void flush() {
         if (this.callSite != null) {
            Deobfuscator.this.callSites.put(this.callSite.id, this.callSite);
            this.callSite.locations = this.locations.toArray(new Deobfuscator.Location[0]);
            this.locations.clear();
         }
      }
   }

   public static class Location implements Cloneable {
      public String className;
      public String methodName;
      public String fileName;
      public int line = -1;

      protected Deobfuscator.Location clone() {
         try {
            return (Deobfuscator.Location)super.clone();
         } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
         }
      }
   }
}
