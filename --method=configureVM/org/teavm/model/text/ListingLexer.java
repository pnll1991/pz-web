package org.teavm.model.text;

import java.io.IOException;
import java.io.Reader;

class ListingLexer {
   private Reader reader;
   private int c;
   private ListingToken token;
   private Object tokenValue;
   private int index = -1;
   private int tokenStart;

   ListingLexer(Reader reader) {
      this.reader = reader;
   }

   public ListingToken getToken() {
      return this.token;
   }

   public boolean tryConsumeIdentifier(String value) throws IOException, ListingParseException {
      if (this.token == ListingToken.IDENTIFIER && value.equals(this.tokenValue)) {
         this.nextToken();
         return true;
      } else {
         return false;
      }
   }

   public Object getTokenValue() {
      return this.tokenValue;
   }

   public int getIndex() {
      return this.index;
   }

   public int getTokenStart() {
      return this.tokenStart;
   }

   public void nextToken() throws IOException, ListingParseException {
      if (this.token != ListingToken.EOF) {
         this.tokenStart = this.index;
         if (this.index < 0) {
            this.tokenStart = 0;
            this.nextChar();
         }

         this.skipWhiteSpace();
         switch (this.c) {
            case -1:
               this.token = ListingToken.EOF;
               break;
            case 10:
               this.token = ListingToken.EOL;
               this.nextChar();
               break;
            case 13:
               this.token = ListingToken.EOL;
               this.nextChar();
               if (this.c == 10) {
                  this.nextChar();
               }
               break;
            case 33:
               this.nextChar();
               this.expect('=');
               if (this.c == 61) {
                  this.token = ListingToken.REFERENCE_NOT_EQUAL;
                  this.nextChar();
               } else {
                  this.token = ListingToken.NOT_EQUAL;
               }
               break;
            case 36:
               this.readLabel();
               break;
            case 37:
               this.nextChar();
               this.token = ListingToken.REMAINDER;
               break;
            case 38:
               this.nextChar();
               this.token = ListingToken.AND;
               break;
            case 39:
               this.readString();
               break;
            case 42:
               this.nextChar();
               this.token = ListingToken.MULTIPLY;
               break;
            case 43:
               this.nextChar();
               this.token = ListingToken.ADD;
               break;
            case 44:
               this.nextChar();
               this.token = ListingToken.COMMA;
               break;
            case 45:
               this.nextChar();
               this.token = ListingToken.SUBTRACT;
               break;
            case 46:
               this.nextChar();
               this.token = ListingToken.DOT;
               break;
            case 47:
               this.nextChar();
               if (this.c == 47) {
                  this.skipComment();
               } else {
                  this.token = ListingToken.DIVIDE;
               }
               break;
            case 58:
               this.nextChar();
               this.expect('=');
               this.token = ListingToken.ASSIGN;
               break;
            case 60:
               this.nextChar();
               if (this.c == 61) {
                  this.nextChar();
                  this.token = ListingToken.LESS_OR_EQUAL;
               } else if (this.c == 60) {
                  this.nextChar();
                  this.token = ListingToken.SHIFT_LEFT;
               } else {
                  this.token = ListingToken.LESS;
               }
               break;
            case 61:
               this.nextChar();
               this.expect('=');
               if (this.c == 61) {
                  this.token = ListingToken.REFERENCE_EQUAL;
                  this.nextChar();
               } else {
                  this.token = ListingToken.EQUAL;
               }
               break;
            case 62:
               this.nextChar();
               if (this.c == 61) {
                  this.nextChar();
                  this.token = ListingToken.GREATER_OR_EQUAL;
               } else if (this.c == 62) {
                  this.nextChar();
                  if (this.c == 62) {
                     this.nextChar();
                     this.token = ListingToken.SHIFT_RIGHT_UNSIGNED;
                  } else {
                     this.token = ListingToken.SHIFT_RIGHT;
                  }
               } else {
                  this.token = ListingToken.GREATER;
               }
               break;
            case 64:
               this.readVariable();
               break;
            case 91:
               this.nextChar();
               this.token = ListingToken.LEFT_SQUARE_BRACKET;
               break;
            case 93:
               this.nextChar();
               this.token = ListingToken.RIGHT_SQUARE_BRACKET;
               break;
            case 94:
               this.nextChar();
               this.token = ListingToken.XOR;
               break;
            case 96:
               this.nextChar();
               this.token = ListingToken.IDENTIFIER;
               this.readEscapedIdentifier();
               break;
            case 124:
               this.nextChar();
               this.token = ListingToken.OR;
               break;
            default:
               if (isIdentifierStart(this.c)) {
                  this.readIdentifier();
               } else if (this.c >= 48 && this.c <= 57) {
                  this.readNumber();
               } else {
                  this.unexpected();
               }
         }
      }
   }

   private void readVariable() throws IOException {
      this.nextChar();
      this.token = ListingToken.VARIABLE;
      this.readIdentifierLike();
   }

   private void readLabel() throws IOException {
      this.nextChar();
      this.token = ListingToken.LABEL;
      this.readIdentifierLike();
   }

   private void readIdentifierLike() throws IOException {
      StringBuilder sb = new StringBuilder();

      while (isIdentifierPart(this.c)) {
         sb.append((char)this.c);
         this.nextChar();
      }

      this.tokenValue = sb.toString();
   }

   private void readIdentifier() throws IOException {
      this.token = ListingToken.IDENTIFIER;
      StringBuilder sb = new StringBuilder();
      sb.append((char)this.c);
      this.nextChar();

      while (isIdentifierPart(this.c)) {
         sb.append((char)this.c);
         this.nextChar();
      }

      this.tokenValue = sb.toString();
   }

   private void readEscapedIdentifier() throws IOException, ListingParseException {
      StringBuilder sb = new StringBuilder();

      while (this.c != 96) {
         if (this.c < 32) {
            throw new ListingParseException("Unexpected char in escaped identifier", this.index);
         }

         sb.append((char)this.c);
         this.nextChar();
      }

      this.nextChar();
      this.tokenValue = sb.toString();
   }

   static boolean isIdentifierStart(int c) {
      if ((c < 97 || c > 122) && (c < 65 || c > 90)) {
         switch (c) {
            case 95:
               return true;
            default:
               return false;
         }
      } else {
         return true;
      }
   }

   static boolean isIdentifierPart(int c) {
      if (!isIdentifierStart(c) && (c < 48 || c > 57)) {
         switch (c) {
            case 35:
            case 36:
            case 46:
               return true;
            default:
               return false;
         }
      } else {
         return true;
      }
   }

   private void readString() throws IOException, ListingParseException {
      this.nextChar();
      this.token = ListingToken.STRING;
      StringBuilder sb = new StringBuilder();

      while (true) {
         switch (this.c) {
            case 39:
               this.nextChar();
               this.tokenValue = sb.toString();
               return;
            case 92:
               this.nextChar();
               switch (this.c) {
                  case 39:
                  case 92:
                     sb.append(this.c);
                     break;
                  case 110:
                     sb.append('\n');
                     break;
                  case 116:
                     sb.append('\t');
                     break;
                  case 117:
                     int codePoint = 0;

                     for (int i = 0; i < 4; i++) {
                        this.nextChar();
                        if (this.c == -1) {
                           throw new ListingParseException("Wrong escape sequence", this.index);
                        }

                        int digit = Character.digit((char)this.c, 16);
                        if (digit < 0) {
                           throw new ListingParseException("Wrong escape sequence", this.index);
                        }

                        codePoint = codePoint * 16 + digit;
                     }

                     sb.appendCodePoint(codePoint);
                     break;
                  default:
                     throw new ListingParseException("Wrong escape sequence", this.index);
               }

               this.nextChar();
               break;
            default:
               if (this.c < 32) {
                  throw new ListingParseException("Unexpected character in string literal: " + this.c, this.index);
               }

               sb.append((char)this.c);
               this.nextChar();
         }
      }
   }

   private void readNumber() throws IOException, ListingParseException {
      StringBuilder sb = new StringBuilder();
      this.token = ListingToken.INTEGER;
      if (this.c == 45) {
         sb.append(this.c);
         this.nextChar();
      }

      while (this.c >= 48 && this.c <= 57) {
         sb.append((char)this.c);
         this.nextChar();
      }

      if (this.c == 46) {
         sb.append('.');
         this.token = ListingToken.DOUBLE;
         this.nextChar();
         if (this.c < 48 || this.c > 57) {
            throw new ListingParseException("Wrong number", this.index);
         }

         while (this.c >= 48 && this.c <= 57) {
            sb.append((char)this.c);
            this.nextChar();
         }
      }

      if (this.c == 69 || this.c == 101) {
         sb.append('e');
         this.nextChar();
         if (this.c == 43 || this.c == 45) {
            sb.append((char)this.c);
            this.nextChar();
         }

         if (this.c < 48 || this.c > 57) {
            throw new ListingParseException("Wrong number", this.index);
         }

         while (this.c >= 48 && this.c <= 57) {
            sb.append((char)this.c);
            this.nextChar();
         }
      }

      if (this.c == 70 || this.c == 102) {
         this.nextChar();
         this.token = ListingToken.FLOAT;
      } else if (this.c != 108 && this.c != 76) {
         if (isIdentifierStart(this.c)) {
            throw new ListingParseException("Wrong number", this.index);
         }
      } else {
         this.nextChar();
         this.token = ListingToken.LONG;
      }

      switch (this.token) {
         case INTEGER:
            this.tokenValue = Integer.parseInt(sb.toString());
            break;
         case LONG:
            this.tokenValue = Long.parseLong(sb.toString());
            break;
         case FLOAT:
            this.tokenValue = Float.parseFloat(sb.toString());
            break;
         case DOUBLE:
            this.tokenValue = Double.parseDouble(sb.toString());
      }
   }

   private void expect(char expected) throws IOException, ListingParseException {
      if (this.c != expected) {
         this.unexpected();
      }

      this.nextChar();
   }

   private void unexpected() throws ListingParseException {
      throw new ListingParseException("Unexpected character: " + (char)this.c, this.index);
   }

   private void skipWhiteSpace() throws IOException {
      while (true) {
         switch (this.c) {
            case 9:
            case 32:
               this.nextChar();
               break;
            default:
               return;
         }
      }
   }

   private void skipComment() throws IOException {
      while (true) {
         switch (this.c) {
            case -1:
               this.token = ListingToken.EOF;
               return;
            case 10:
               this.nextChar();
               this.token = ListingToken.EOL;
               return;
            default:
               this.nextChar();
         }
      }
   }

   private void nextChar() throws IOException {
      this.c = this.reader.read();
      this.index++;
   }
}
