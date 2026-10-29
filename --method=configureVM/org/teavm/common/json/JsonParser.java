package org.teavm.common.json;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class JsonParser {
   private JsonConsumer consumer;
   private int lastChar;
   private int lineNumber;
   private int columnNumber;
   private boolean cr;
   private JsonErrorReporter errorReporter = new JsonErrorReporter() {
      @Override
      public void error(String message) {
         JsonParser.this.error(message);
      }
   };

   public JsonParser(JsonConsumer consumer) {
      this.consumer = consumer;
   }

   public void parse(Reader reader) throws IOException {
      this.lineNumber = 0;
      this.columnNumber = 0;
      this.lastChar = reader.read();
      this.skipWhitespaces(reader);
      if (this.lastChar == -1) {
         this.error("Unexpected end of file");
      }

      this.parseValue(reader);
      this.skipWhitespaces(reader);
      if (this.lastChar != -1) {
         this.error("Unexpected characters after end of JSON string");
      }
   }

   private void parseValue(Reader reader) throws IOException {
      switch (this.lastChar) {
         case 34:
            this.parseString(reader);
            break;
         case 45:
         case 48:
         case 49:
         case 50:
         case 51:
         case 52:
         case 53:
         case 54:
         case 55:
         case 56:
         case 57:
            this.parseNumber(reader);
            break;
         case 91:
            this.parseArray(reader);
            break;
         case 102:
            this.parseFalse(reader);
            break;
         case 110:
            this.parseNull(reader);
            break;
         case 116:
            this.parseTrue(reader);
            break;
         case 123:
            this.parseObject(reader);
            break;
         default:
            this.error("Unexpected character");
      }
   }

   private void parseObject(Reader reader) throws IOException {
      Set<String> usedPropertyNames = new HashSet<>();
      this.consumer.enterObject(this.errorReporter);
      this.nextChar(reader);
      this.skipWhitespaces(reader);
      if (this.lastChar == 125) {
         this.nextChar(reader);
         this.consumer.exitObject(this.errorReporter);
      } else {
         this.parseProperty(reader, usedPropertyNames);

         while (this.lastChar != 125) {
            if (this.lastChar != 44) {
               this.error("Either property delimiter (',') or end of object ('}') expected");
            }

            this.nextChar(reader);
            this.skipWhitespaces(reader);
            this.parseProperty(reader, usedPropertyNames);
         }

         this.nextChar(reader);
         this.consumer.exitObject(this.errorReporter);
      }
   }

   private void parseProperty(Reader reader, Set<String> usedPropertyNames) throws IOException {
      this.skipWhitespaces(reader);
      if (this.lastChar != 34) {
         this.error("Object key (string literal) expected");
      }

      String name = this.parseStringLiteral(reader);
      if (!usedPropertyNames.add(name)) {
         this.error("Duplicate object property: " + name);
      }

      this.consumer.enterProperty(this.errorReporter, name);
      this.skipWhitespaces(reader);
      if (this.lastChar != 58) {
         this.error("':' character expected after property name");
      }

      this.nextChar(reader);
      this.skipWhitespaces(reader);
      this.parseValue(reader);
      this.consumer.exitProperty(this.errorReporter, name);
      this.skipWhitespaces(reader);
   }

   private void parseArray(Reader reader) throws IOException {
      this.consumer.enterArray(this.errorReporter);
      this.nextChar(reader);
      this.skipWhitespaces(reader);
      if (this.lastChar == 93) {
         this.nextChar(reader);
         this.consumer.exitArray(this.errorReporter);
      } else {
         this.parseValue(reader);
         this.skipWhitespaces(reader);

         while (this.lastChar != 93) {
            if (this.lastChar != 44) {
               this.error("Either array item delimiter (',') or end of array (']') expected");
            }

            this.nextChar(reader);
            this.skipWhitespaces(reader);
            this.parseValue(reader);
            this.skipWhitespaces(reader);
         }

         this.nextChar(reader);
         this.consumer.exitArray(this.errorReporter);
      }
   }

   private void parseString(Reader reader) throws IOException {
      this.consumer.stringValue(this.errorReporter, this.parseStringLiteral(reader));
   }

   private String parseStringLiteral(Reader reader) throws IOException {
      this.nextChar(reader);
      StringBuilder sb = new StringBuilder();

      while (this.lastChar != 34) {
         if (this.lastChar == -1) {
            this.error("Unexpected end of input inside string literal");
         } else if (this.lastChar < 32) {
            this.error("Unexpected control character inside string literal");
         }

         if (this.lastChar == 92) {
            this.nextChar(reader);
            switch (this.lastChar) {
               case 34:
               case 47:
               case 92:
                  sb.append((char)this.lastChar);
                  this.nextChar(reader);
                  break;
               case 98:
                  sb.append('\b');
                  this.nextChar(reader);
                  break;
               case 102:
                  sb.append('\f');
                  this.nextChar(reader);
                  break;
               case 110:
                  sb.append('\n');
                  this.nextChar(reader);
                  break;
               case 114:
                  sb.append('\r');
                  this.nextChar(reader);
                  break;
               case 116:
                  sb.append('\t');
                  this.nextChar(reader);
                  break;
               case 117:
                  this.nextChar(reader);
                  int code = this.getHexDigit(reader) << 12 | this.getHexDigit(reader) << 8 | this.getHexDigit(reader) << 4 | this.getHexDigit(reader);
                  sb.append((char)code);
                  break;
               default:
                  this.error("Wrong escape sequence");
            }
         } else {
            sb.append((char)this.lastChar);
            this.nextChar(reader);
         }
      }

      this.nextChar(reader);
      return sb.toString();
   }

   private int getHexDigit(Reader reader) throws IOException {
      int value;
      if (this.lastChar >= 48 && this.lastChar <= 57) {
         value = this.lastChar - 48;
      } else if (this.lastChar >= 65 && this.lastChar <= 70) {
         value = this.lastChar - 65 + 10;
      } else if (this.lastChar >= 97 && this.lastChar <= 102) {
         value = this.lastChar - 97 + 10;
      } else {
         this.error("Wrong escape sequence");
         value = 0;
      }

      this.nextChar(reader);
      return value;
   }

   private void parseNumber(Reader reader) throws IOException {
      boolean isFloatingPoint = false;
      StringBuilder sb = new StringBuilder();
      if (this.lastChar == 45) {
         this.acceptChar(sb, reader);
      }

      if (this.lastChar == 48) {
         this.acceptChar(sb, reader);
      } else {
         if (!isDigit(this.lastChar)) {
            if (this.lastChar != 101 && this.lastChar != 69 && this.lastChar != 46) {
               this.error("Wrong number, digits must follow '-' sign");
            } else {
               this.error("Wrong number, at least one digit expected in integer part");
            }
         }

         this.acceptChar(sb, reader);

         while (isDigit(this.lastChar)) {
            this.acceptChar(sb, reader);
         }
      }

      if (this.lastChar == 46) {
         isFloatingPoint = true;
         this.acceptChar(sb, reader);
         if (!isDigit(this.lastChar)) {
            this.error("Wrong number, at least one digit must be in fraction part");
         }

         this.acceptChar(sb, reader);

         while (isDigit(this.lastChar)) {
            this.acceptChar(sb, reader);
         }
      }

      if (this.lastChar == 101 || this.lastChar == 69) {
         isFloatingPoint = true;
         this.acceptChar(sb, reader);
         if (this.lastChar == 43 || this.lastChar == 45) {
            this.acceptChar(sb, reader);
         }

         if (!isDigit(this.lastChar)) {
            this.error("Wrong number, at least one digit must be in exponent");
         }

         this.acceptChar(sb, reader);

         while (isDigit(this.lastChar)) {
            this.acceptChar(sb, reader);
         }
      }

      this.expectEndOfToken("Wrong number");
      if (isFloatingPoint) {
         this.tryParseDouble(sb);
      } else {
         long value;
         try {
            value = Long.parseLong(sb.toString());
         } catch (NumberFormatException e) {
            this.tryParseDouble(sb);
            return;
         }

         this.consumer.intValue(this.errorReporter, value);
      }
   }

   private void tryParseDouble(StringBuilder sb) {
      double value;
      try {
         value = Double.parseDouble(sb.toString());
      } catch (NumberFormatException e) {
         this.error("Wrong number");
         value = 0.0;
      }

      this.consumer.floatValue(this.errorReporter, value);
   }

   private void acceptChar(StringBuilder sb, Reader reader) throws IOException {
      sb.append((char)this.lastChar);
      this.nextChar(reader);
   }

   private void parseNull(Reader reader) throws IOException {
      this.expectIdentifier(reader, "null");
      this.consumer.nullValue(this.errorReporter);
   }

   private void parseTrue(Reader reader) throws IOException {
      this.expectIdentifier(reader, "true");
      this.consumer.booleanValue(this.errorReporter, true);
   }

   private void parseFalse(Reader reader) throws IOException {
      this.expectIdentifier(reader, "false");
      this.consumer.booleanValue(this.errorReporter, false);
   }

   private void expectIdentifier(Reader reader, String identifier) throws IOException {
      for (int i = 0; i < identifier.length(); i++) {
         if (this.lastChar != identifier.charAt(i)) {
            this.error("Unexpected identifier");
         }

         this.nextChar(reader);
      }

      this.expectEndOfToken("Wrong identifier");
   }

   private void expectEndOfToken(String errorMessage) {
      switch (this.lastChar) {
         default:
            if (!isWhitespace(this.lastChar)) {
               this.error(errorMessage);
            }
         case 44:
         case 58:
         case 91:
         case 93:
         case 123:
         case 125:
      }
   }

   private void skipWhitespaces(Reader reader) throws IOException {
      while (isWhitespace(this.lastChar)) {
         this.nextChar(reader);
      }
   }

   private void nextChar(Reader reader) throws IOException {
      boolean wasCr = this.cr;
      if (this.cr) {
         this.lineNumber++;
         this.columnNumber = 0;
         this.cr = false;
      }

      switch (this.lastChar) {
         case 10:
            if (!wasCr) {
               this.lineNumber++;
               this.columnNumber = 0;
            }
            break;
         case 13:
            this.cr = true;
            break;
         default:
            this.columnNumber++;
      }

      this.lastChar = reader.read();
   }

   void error(String error) {
      throw new JsonSyntaxException(this.lineNumber, this.columnNumber, error);
   }

   private static boolean isWhitespace(int c) {
      switch (c) {
         case 9:
         case 10:
         case 13:
         case 32:
            return true;
         default:
            return false;
      }
   }

   private static boolean isDigit(int c) {
      return c >= 48 && c <= 57;
   }

   public static JsonParser ofValue(Consumer<JsonValue> consumer) {
      return new JsonParser(new JsonVisitingConsumer(JsonValueParserVisitor.create(consumer)));
   }
}
