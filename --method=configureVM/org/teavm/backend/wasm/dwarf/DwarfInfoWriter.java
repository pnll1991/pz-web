package org.teavm.backend.wasm.dwarf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.teavm.common.binary.BinaryDataConsumer;
import org.teavm.common.binary.Blob;
import org.teavm.common.binary.BlobReader;
import org.teavm.common.binary.Marker;

public class DwarfInfoWriter {
   private Blob output = new Blob();
   private List<DwarfAbbreviation> abbreviations = new ArrayList<>();
   private List<DwarfInfoWriter.Placement> placements = new ArrayList<>();
   private Marker placeholderMarker;
   private Blob targetBlob;
   private BinaryDataConsumer targetBlobWritingConsumer = (data, offset, limit) -> this.targetBlob.write(data, offset, limit);

   public DwarfInfoWriter write(byte[] data) {
      this.output.write(data);
      return this;
   }

   public DwarfInfoWriter write(byte[] data, int offset, int limit) {
      this.output.write(data, offset, limit);
      return this;
   }

   public DwarfInfoWriter writeInt(int value) {
      this.output.writeInt(value);
      return this;
   }

   public DwarfInfoWriter writeShort(int value) {
      this.output.writeShort(value);
      return this;
   }

   public DwarfInfoWriter writeByte(int value) {
      this.output.write((byte)value);
      return this;
   }

   public DwarfInfoWriter writeLEB(int value) {
      this.output.writeLEB(value);
      return this;
   }

   public DwarfInfoWriter skip(int count) {
      this.output.skip(count);
      return this;
   }

   public Marker marker() {
      return this.output.marker();
   }

   public DwarfAbbreviation abbreviation(int tag, boolean hasChildren, Consumer<Blob> blob) {
      DwarfAbbreviation abbr = new DwarfAbbreviation(tag, hasChildren, blob);
      this.abbreviations.add(abbr);
      return abbr;
   }

   public DwarfInfoWriter tag(final DwarfAbbreviation abbreviation) {
      this.placements.add(new DwarfInfoWriter.Placement(this.output.ptr()) {
         @Override
         void write(Blob blob) {
            blob.writeLEB(abbreviation.index);
         }
      });
      abbreviation.count++;
      return this;
   }

   public DwarfInfoWriter emptyTag() {
      this.output.write((byte)0);
      return this;
   }

   public DwarfPlaceholder placeholder(int size) {
      return new DwarfPlaceholder(size);
   }

   public DwarfInfoWriter ref(final DwarfPlaceholder placeholder, final DwarfPlaceholderWriter writer) {
      this.placements.add(new DwarfInfoWriter.Placement(this.output.ptr()) {
         @Override
         void write(Blob blob) {
            if (placeholder.ptr >= 0) {
               DwarfInfoWriter.this.placeholderMarker.update();
               writer.write(blob, placeholder.ptr);
               DwarfInfoWriter.this.placeholderMarker.rewind();
               blob.skip(placeholder.size);
            } else {
               placeholder.addForwardRef(writer, blob.marker());
               blob.skip(placeholder.size);
            }
         }
      });
      return this;
   }

   public DwarfInfoWriter mark(final DwarfPlaceholder placeholder) {
      Objects.requireNonNull(placeholder);
      this.placements.add(new DwarfInfoWriter.Placement(this.output.ptr()) {
         @Override
         void write(Blob blob) {
            if (placeholder.ptr >= 0) {
               throw new IllegalStateException();
            }

            placeholder.ptr = blob.ptr();
            if (placeholder.forwardReferences != null) {
               DwarfInfoWriter.this.placeholderMarker.update();

               for (DwarfPlaceholder.ForwardRef forwardRef : placeholder.forwardReferences) {
                  forwardRef.marker.rewind();
                  forwardRef.writer.write(blob, placeholder.ptr);
               }

               placeholder.forwardReferences = null;
               DwarfInfoWriter.this.placeholderMarker.rewind();
            }
         }
      });
      return this;
   }

   public void buildAbbreviations(Blob target) {
      ArrayList<DwarfAbbreviation> orderedAbbreviations = new ArrayList<>(this.abbreviations);
      orderedAbbreviations.sort(Comparator.comparingInt(a -> -a.count));
      int sz = orderedAbbreviations.size();

      while (sz > 0 && orderedAbbreviations.get(sz - 1).count == 0) {
         sz--;
      }

      for (int i = 0; i < sz; i++) {
         DwarfAbbreviation abbrev = orderedAbbreviations.get(i);
         abbrev.index = i + 1;
         target.writeLEB(abbrev.index).writeLEB(abbrev.tag).writeByte(abbrev.hasChildren ? 1 : 0);
         abbrev.writer.accept(target);
         target.writeByte(0).writeByte(0);
      }

      target.writeByte(0);
   }

   public void build(Blob target) {
      this.placeholderMarker = target.marker();
      this.targetBlob = target;
      BlobReader reader = this.output.newReader(this.targetBlobWritingConsumer);

      for (DwarfInfoWriter.Placement placement : this.placements) {
         reader.advance(placement.offset);
         placement.write(target);
      }

      reader.advance(this.output.size());
      this.targetBlob = null;
      this.placeholderMarker = null;
   }

   private abstract static class Placement {
      int offset;

      Placement(int offset) {
         this.offset = offset;
      }

      abstract void write(Blob var1);
   }
}
