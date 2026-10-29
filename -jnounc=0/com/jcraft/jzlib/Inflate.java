package com.jcraft.jzlib;

import java.io.ByteArrayOutputStream;

final class Inflate {
   private static final int MAX_WBITS = 15;
   private static final int PRESET_DICT = 32;
   static final int Z_NO_FLUSH = 0;
   static final int Z_PARTIAL_FLUSH = 1;
   static final int Z_SYNC_FLUSH = 2;
   static final int Z_FULL_FLUSH = 3;
   static final int Z_FINISH = 4;
   private static final int Z_DEFLATED = 8;
   private static final int Z_OK = 0;
   private static final int Z_STREAM_END = 1;
   private static final int Z_NEED_DICT = 2;
   private static final int Z_ERRNO = -1;
   private static final int Z_STREAM_ERROR = -2;
   private static final int Z_DATA_ERROR = -3;
   private static final int Z_MEM_ERROR = -4;
   private static final int Z_BUF_ERROR = -5;
   private static final int Z_VERSION_ERROR = -6;
   private static final int METHOD = 0;
   private static final int FLAG = 1;
   private static final int DICT4 = 2;
   private static final int DICT3 = 3;
   private static final int DICT2 = 4;
   private static final int DICT1 = 5;
   private static final int DICT0 = 6;
   private static final int BLOCKS = 7;
   private static final int CHECK4 = 8;
   private static final int CHECK3 = 9;
   private static final int CHECK2 = 10;
   private static final int CHECK1 = 11;
   private static final int DONE = 12;
   private static final int BAD = 13;
   private static final int HEAD = 14;
   private static final int LENGTH = 15;
   private static final int TIME = 16;
   private static final int OS = 17;
   private static final int EXLEN = 18;
   private static final int EXTRA = 19;
   private static final int NAME = 20;
   private static final int COMMENT = 21;
   private static final int HCRC = 22;
   private static final int FLAGS = 23;
   static final int INFLATE_ANY = 1073741824;
   int mode;
   int method;
   long was = -1L;
   long need;
   int marker;
   int wrap;
   int wbits;
   InfBlocks blocks;
   private final ZStream z;
   private int flags;
   private int need_bytes = -1;
   private byte[] crcbuf = new byte[4];
   GZIPHeader gheader = null;
   private static byte[] mark = new byte[]{0, 0, -1, -1};
   private ByteArrayOutputStream tmp_string = null;

   int inflateReset() {
      if (this.z == null) {
         return -2;
      }

      this.z.total_in = this.z.total_out = 0L;
      this.z.msg = null;
      this.mode = 14;
      this.need_bytes = -1;
      this.blocks.reset();
      return 0;
   }

   int inflateEnd() {
      if (this.blocks != null) {
         this.blocks.free();
      }

      return 0;
   }

   Inflate(ZStream z) {
      this.z = z;
   }

   int inflateInit(int w) {
      this.z.msg = null;
      this.blocks = null;
      this.wrap = 0;
      if (w < 0) {
         w = -w;
      } else if ((w & 1073741824) != 0) {
         this.wrap = 4;
         w &= -1073741825;
         if (w < 48) {
            w &= 15;
         }
      } else if ((w & -32) != 0) {
         this.wrap = 4;
         w &= 15;
      } else {
         this.wrap = (w >> 4) + 1;
         if (w < 48) {
            w &= 15;
         }
      }

      if (w >= 8 && w <= 15) {
         if (this.blocks != null && this.wbits != w) {
            this.blocks.free();
            this.blocks = null;
         }

         this.wbits = w;
         this.blocks = new InfBlocks(this.z, 1 << w);
         this.inflateReset();
         return 0;
      } else {
         this.inflateEnd();
         return -2;
      }
   }

   int inflate(int f) {
      int hold = 0;
      if (this.z != null && this.z.next_in != null) {
         f = f == 4 ? -5 : 0;
         int r = -5;

         while (true) {
            label360:
            while (true) {
               label376: {
                  label377: {
                     label356:
                     while (true) {
                        label379: {
                           label380: {
                              label381: {
                                 switch (this.mode) {
                                    case 3:
                                       break label380;
                                    case 4:
                                       break label377;
                                    case 5:
                                       break label360;
                                    case 6:
                                       this.mode = 13;
                                       this.z.msg = "need dictionary";
                                       this.marker = 0;
                                       return -2;
                                    case 7:
                                       r = this.blocks.proc(r);
                                       if (r == -3) {
                                          this.mode = 13;
                                          this.marker = 0;
                                          continue;
                                       } else {
                                          if (r == 0) {
                                             r = f;
                                          }

                                          if (r != 1) {
                                             return r;
                                          }

                                          r = f;
                                          this.was = this.z.adler.getValue();
                                          this.blocks.reset();
                                          if (this.wrap == 0) {
                                             this.mode = 12;
                                             continue;
                                          } else {
                                             this.mode = 8;
                                          }
                                       }
                                    case 8:
                                       if (this.z.avail_in == 0) {
                                          return r;
                                       }

                                       r = f;
                                       this.z.avail_in--;
                                       this.z.total_in++;
                                       this.need = (this.z.next_in[this.z.next_in_index++] & 255) << 24 & 4278190080L;
                                       this.mode = 9;
                                    case 9:
                                       if (this.z.avail_in == 0) {
                                          return r;
                                       }

                                       r = f;
                                       this.z.avail_in--;
                                       this.z.total_in++;
                                       this.need = this.need + ((this.z.next_in[this.z.next_in_index++] & 255) << 16 & 16711680L);
                                       this.mode = 10;
                                    case 10:
                                       if (this.z.avail_in == 0) {
                                          return r;
                                       }

                                       r = f;
                                       this.z.avail_in--;
                                       this.z.total_in++;
                                       this.need = this.need + ((this.z.next_in[this.z.next_in_index++] & 255) << 8 & 65280L);
                                       this.mode = 11;
                                    case 11:
                                       break;
                                    case 12:
                                       return 1;
                                    case 13:
                                       return -3;
                                    case 14:
                                       if (this.wrap == 0) {
                                          this.mode = 7;
                                          continue;
                                       }

                                       try {
                                          r = this.readBytes(2, r, f);
                                       } catch (Inflate.Return e) {
                                          return e.r;
                                       }

                                       if ((this.wrap == 4 || (this.wrap & 2) != 0) && this.need == 35615L) {
                                          if (this.wrap == 4) {
                                             this.wrap = 2;
                                          }

                                          this.z.adler = new CRC32();
                                          this.checksum(2, this.need);
                                          if (this.gheader == null) {
                                             this.gheader = new GZIPHeader();
                                          }

                                          this.mode = 23;
                                          continue;
                                       } else if ((this.wrap & 2) != 0) {
                                          this.mode = 13;
                                          this.z.msg = "incorrect header check";
                                          continue;
                                       } else {
                                          this.flags = 0;
                                          this.method = (int)this.need & 0xFF;
                                          int b = (int)(this.need >> 8) & 0xFF;
                                          if (((this.wrap & 1) == 0 || ((this.method << 8) + b) % 31 != 0) && (this.method & 15) != 8) {
                                             if (this.wrap == 4) {
                                                this.z.next_in_index -= 2;
                                                this.z.avail_in += 2;
                                                this.z.total_in -= 2L;
                                                this.wrap = 0;
                                                this.mode = 7;
                                             } else {
                                                this.mode = 13;
                                                this.z.msg = "incorrect header check";
                                             }
                                             continue;
                                          } else if ((this.method & 15) != 8) {
                                             this.mode = 13;
                                             this.z.msg = "unknown compression method";
                                             continue;
                                          } else {
                                             if (this.wrap == 4) {
                                                this.wrap = 1;
                                             }

                                             if ((this.method >> 4) + 8 > this.wbits) {
                                                this.mode = 13;
                                                this.z.msg = "invalid window size";
                                                continue;
                                             } else {
                                                this.z.adler = new Adler32();
                                                if ((b & 32) == 0) {
                                                   this.mode = 7;
                                                   continue;
                                                } else {
                                                   this.mode = 2;
                                                }
                                             }
                                          }
                                       }
                                    case 2:
                                       if (this.z.avail_in == 0) {
                                          return r;
                                       }

                                       r = f;
                                       this.z.avail_in--;
                                       this.z.total_in++;
                                       this.need = (this.z.next_in[this.z.next_in_index++] & 255) << 24 & 4278190080L;
                                       this.mode = 3;
                                       break label380;
                                    case 15:
                                       break label379;
                                    case 20:
                                       break label381;
                                    case 21:
                                       break label356;
                                    case 22:
                                       break label376;
                                    case 23:
                                       try {
                                          r = this.readBytes(2, r, f);
                                       } catch (Inflate.Return e) {
                                          return e.r;
                                       }

                                       this.flags = (int)this.need & 65535;
                                       if ((this.flags & 0xFF) != 8) {
                                          this.z.msg = "unknown compression method";
                                          this.mode = 13;
                                          continue;
                                       } else if ((this.flags & 57344) != 0) {
                                          this.z.msg = "unknown header flags set";
                                          this.mode = 13;
                                          continue;
                                       } else {
                                          if ((this.flags & 512) != 0) {
                                             this.checksum(2, this.need);
                                          }

                                          this.mode = 16;
                                       }
                                    case 16:
                                       try {
                                          r = this.readBytes(4, r, f);
                                       } catch (Inflate.Return e) {
                                          return e.r;
                                       }

                                       if (this.gheader != null) {
                                          this.gheader.time = this.need;
                                       }

                                       if ((this.flags & 512) != 0) {
                                          this.checksum(4, this.need);
                                       }

                                       this.mode = 17;
                                    case 17:
                                       try {
                                          r = this.readBytes(2, r, f);
                                       } catch (Inflate.Return e) {
                                          return e.r;
                                       }

                                       if (this.gheader != null) {
                                          this.gheader.xflags = (int)this.need & 0xFF;
                                          this.gheader.os = (int)this.need >> 8 & 0xFF;
                                       }

                                       if ((this.flags & 512) != 0) {
                                          this.checksum(2, this.need);
                                       }

                                       this.mode = 18;
                                    case 18:
                                       if ((this.flags & 1024) != 0) {
                                          try {
                                             r = this.readBytes(2, r, f);
                                          } catch (Inflate.Return e) {
                                             return e.r;
                                          }

                                          if (this.gheader != null) {
                                             this.gheader.extra = new byte[(int)this.need & 65535];
                                          }

                                          if ((this.flags & 512) != 0) {
                                             this.checksum(2, this.need);
                                          }
                                       } else if (this.gheader != null) {
                                          this.gheader.extra = null;
                                       }

                                       this.mode = 19;
                                    case 19:
                                       if ((this.flags & 1024) != 0) {
                                          try {
                                             r = this.readBytes(r, f);
                                             if (this.gheader != null) {
                                                byte[] foo = this.tmp_string.toByteArray();
                                                this.tmp_string = null;
                                                if (foo.length != this.gheader.extra.length) {
                                                   this.z.msg = "bad extra field length";
                                                   this.mode = 13;
                                                   continue;
                                                }

                                                System.arraycopy(foo, 0, this.gheader.extra, 0, foo.length);
                                             }
                                          } catch (Inflate.Return e) {
                                             return e.r;
                                          }
                                       } else if (this.gheader != null) {
                                          this.gheader.extra = null;
                                       }

                                       this.mode = 20;
                                       break label381;
                                    default:
                                       return -2;
                                 }

                                 if (this.z.avail_in == 0) {
                                    return r;
                                 }

                                 r = f;
                                 this.z.avail_in--;
                                 this.z.total_in++;
                                 this.need = this.need + (this.z.next_in[this.z.next_in_index++] & 255L);
                                 if (this.flags != 0) {
                                    this.need = (
                                          (this.need & -16777216L) >> 24
                                             | (this.need & 16711680L) >> 8
                                             | (this.need & 65280L) << 8
                                             | (this.need & 65535L) << 24
                                       )
                                       & 4294967295L;
                                 }

                                 if ((int)this.was != (int)this.need) {
                                    this.z.msg = "incorrect data check";
                                 } else if (this.flags != 0 && this.gheader != null) {
                                    this.gheader.crc = this.need;
                                 }

                                 this.mode = 15;
                                 break label379;
                              }

                              if ((this.flags & 2048) != 0) {
                                 try {
                                    r = this.readString(r, f);
                                    if (this.gheader != null) {
                                       this.gheader.name = this.tmp_string.toByteArray();
                                    }

                                    this.tmp_string = null;
                                 } catch (Inflate.Return e) {
                                    return e.r;
                                 }
                              } else if (this.gheader != null) {
                                 this.gheader.name = null;
                              }

                              this.mode = 21;
                              break;
                           }

                           if (this.z.avail_in == 0) {
                              return r;
                           }

                           r = f;
                           this.z.avail_in--;
                           this.z.total_in++;
                           this.need = this.need + ((this.z.next_in[this.z.next_in_index++] & 255) << 16 & 16711680L);
                           this.mode = 4;
                           break label377;
                        }

                        if (this.wrap != 0 && this.flags != 0) {
                           try {
                              r = this.readBytes(4, r, f);
                           } catch (Inflate.Return e) {
                              return e.r;
                           }

                           if (this.z.msg != null && this.z.msg.equals("incorrect data check")) {
                              this.mode = 13;
                              this.marker = 5;
                              continue;
                           }

                           if (this.need != (this.z.total_out & 4294967295L)) {
                              this.z.msg = "incorrect length check";
                              this.mode = 13;
                              continue;
                           }

                           this.z.msg = null;
                        } else if (this.z.msg != null && this.z.msg.equals("incorrect data check")) {
                           this.mode = 13;
                           this.marker = 5;
                           continue;
                        }

                        this.mode = 12;
                        return 1;
                     }

                     if ((this.flags & 4096) != 0) {
                        try {
                           r = this.readString(r, f);
                           if (this.gheader != null) {
                              this.gheader.comment = this.tmp_string.toByteArray();
                           }

                           this.tmp_string = null;
                        } catch (Inflate.Return e) {
                           return e.r;
                        }
                     } else if (this.gheader != null) {
                        this.gheader.comment = null;
                     }

                     this.mode = 22;
                     break label376;
                  }

                  if (this.z.avail_in == 0) {
                     return r;
                  }

                  r = f;
                  this.z.avail_in--;
                  this.z.total_in++;
                  this.need = this.need + ((this.z.next_in[this.z.next_in_index++] & 255) << 8 & 65280L);
                  this.mode = 5;
                  break;
               }

               if ((this.flags & 512) != 0) {
                  try {
                     r = this.readBytes(2, r, f);
                  } catch (Inflate.Return e) {
                     return e.r;
                  }

                  if (this.gheader != null) {
                     this.gheader.hcrc = (int)(this.need & 65535L);
                  }

                  if (this.need != (this.z.adler.getValue() & 65535L)) {
                     this.mode = 13;
                     this.z.msg = "header crc mismatch";
                     this.marker = 5;
                     continue;
                  }
               }

               this.z.adler = new CRC32();
               this.mode = 7;
            }

            if (this.z.avail_in == 0) {
               return r;
            }

            this.z.avail_in--;
            this.z.total_in++;
            this.need = this.need + (this.z.next_in[this.z.next_in_index++] & 255L);
            this.z.adler.reset(this.need);
            this.mode = 6;
            return 2;
         }
      } else {
         return f == 4 && this.mode == 14 ? 0 : -2;
      }
   }

   int inflateSetDictionary(byte[] dictionary, int dictLength) {
      if (this.z != null && (this.mode == 6 || this.wrap == 0)) {
         int index = 0;
         int length = dictLength;
         if (this.mode == 6) {
            long adler_need = this.z.adler.getValue();
            this.z.adler.reset();
            this.z.adler.update(dictionary, 0, dictLength);
            if (this.z.adler.getValue() != adler_need) {
               return -3;
            }
         }

         this.z.adler.reset();
         if (length >= 1 << this.wbits) {
            length = (1 << this.wbits) - 1;
            index = dictLength - length;
         }

         this.blocks.set_dictionary(dictionary, index, length);
         this.mode = 7;
         return 0;
      } else {
         return -2;
      }
   }

   int inflateSync() {
      if (this.z == null) {
         return -2;
      }

      if (this.mode != 13) {
         this.mode = 13;
         this.marker = 0;
      }

      int n = this.z.avail_in;
      if (this.z.avail_in == 0) {
         return -5;
      }

      int p = this.z.next_in_index;

      int m;
      for (m = this.marker; n != 0 && m < 4; n--) {
         if (this.z.next_in[p] == mark[m]) {
            m++;
         } else if (this.z.next_in[p] != 0) {
            m = 0;
         } else {
            m = 4 - m;
         }

         p++;
      }

      this.z.total_in = this.z.total_in + (p - this.z.next_in_index);
      this.z.next_in_index = p;
      this.z.avail_in = n;
      this.marker = m;
      if (m != 4) {
         return -3;
      }

      long r = this.z.total_in;
      long w = this.z.total_out;
      this.inflateReset();
      this.z.total_in = r;
      this.z.total_out = w;
      this.mode = 7;
      return 0;
   }

   int inflateSyncPoint() {
      return this.z != null && this.blocks != null ? this.blocks.sync_point() : -2;
   }

   private int readBytes(int n, int r, int f) throws Inflate.Return {
      if (this.need_bytes == -1) {
         this.need_bytes = n;
         this.need = 0L;
      }

      while (this.need_bytes > 0) {
         if (this.z.avail_in == 0) {
            throw new Inflate.Return(r);
         }

         r = f;
         this.z.avail_in--;
         this.z.total_in++;
         this.need = this.need | (this.z.next_in[this.z.next_in_index++] & 255) << (n - this.need_bytes) * 8;
         this.need_bytes--;
      }

      if (n == 2) {
         this.need &= 65535L;
      } else if (n == 4) {
         this.need &= 4294967295L;
      }

      this.need_bytes = -1;
      return r;
   }

   private int readString(int r, int f) throws Inflate.Return {
      if (this.tmp_string == null) {
         this.tmp_string = new ByteArrayOutputStream();
      }

      int b = 0;

      while (this.z.avail_in != 0) {
         r = f;
         this.z.avail_in--;
         this.z.total_in++;
         int var4 = this.z.next_in[this.z.next_in_index];
         if (var4 != 0) {
            this.tmp_string.write(this.z.next_in, this.z.next_in_index, 1);
         }

         this.z.adler.update(this.z.next_in, this.z.next_in_index, 1);
         this.z.next_in_index++;
         if (var4 == 0) {
            return r;
         }
      }

      throw new Inflate.Return(r);
   }

   private int readBytes(int r, int f) throws Inflate.Return {
      if (this.tmp_string == null) {
         this.tmp_string = new ByteArrayOutputStream();
      }

      int b = 0;

      while (this.need > 0L) {
         if (this.z.avail_in == 0) {
            throw new Inflate.Return(r);
         }

         r = f;
         this.z.avail_in--;
         this.z.total_in++;
         int var4 = this.z.next_in[this.z.next_in_index];
         this.tmp_string.write(this.z.next_in, this.z.next_in_index, 1);
         this.z.adler.update(this.z.next_in, this.z.next_in_index, 1);
         this.z.next_in_index++;
         this.need--;
      }

      return r;
   }

   private void checksum(int n, long v) {
      for (int i = 0; i < n; i++) {
         this.crcbuf[i] = (byte)(v & 255L);
         v >>= 8;
      }

      this.z.adler.update(this.crcbuf, 0, n);
   }

   public GZIPHeader getGZIPHeader() {
      return this.gheader;
   }

   boolean inParsingHeader() {
      switch (this.mode) {
         case 2:
         case 3:
         case 4:
         case 5:
         case 14:
         case 16:
         case 17:
         case 18:
         case 19:
         case 20:
         case 21:
         case 22:
         case 23:
            return true;
         case 6:
         case 7:
         case 8:
         case 9:
         case 10:
         case 11:
         case 12:
         case 13:
         case 15:
         default:
            return false;
      }
   }

   class Return extends Exception {
      int r;

      Return(int r) {
         this.r = r;
      }
   }
}
