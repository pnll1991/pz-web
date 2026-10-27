package port.shim;

import port.runtime.memory.NativeMemory;

/**
 * Browser stand-in for the parts of libffi that only need to exist as data. A wasm module cannot
 * build a native closure, so {@code Upcalls.upcallCreate} (rewritten in IR by WindowCompatibility)
 * never prepares a CIF or a closure: the CIF objects produced by {@code APIUtil.apiCreateCIF} are
 * read by nobody. They are still real arena-backed structs because
 * {@code Pointer$Default.<init>} raises a NullPointerException for address 0 while
 * {@code Checks.CHECKS} is true, so a fake null would fail the game's own guard instead of
 * reporting honestly.
 *
 * {@code typeAddress} builds the twenty-one {@code ffi_type} records of this port's own memory
 * model (LP64, little-endian): size, alignment, type code and elements at the offsets computed by
 * the untouched {@code FFIType.<clinit>} (which derives them from the adapted {@code Pointer}
 * sizes). The type codes match the libffi 3.5.0 constants verified with javap against the pinned
 * jar; the records are never read by this backend, so a code differing from the host's libffi
 * would not change behaviour, and STATUS.md records that doubt instead of pretending it is known.
 */
public final class BrowserLibFFI {
    /** ffi_type layout as FFIType.&lt;clinit&gt; computes it for 64-bit pointers: size@0, align@8, type@10, elements@12. */
    private static final int RECORD_BYTES = 24;
    private static final int SIZE_OFFSET = 0, ALIGNMENT_OFFSET = 8, TYPE_OFFSET = 10, ELEMENTS_OFFSET = 12;
    public static final int VOID = 0, UINT8 = 1, SINT8 = 2, UINT16 = 3, SINT16 = 4, UINT32 = 5,
        SINT32 = 6, UINT64 = 7, SINT64 = 8, UCHAR = 9, SCHAR = 10, USHORT = 11, SSHORT = 12,
        UINT = 13, SINT = 14, ULONG = 15, SLONG = 16, FLOAT = 17, DOUBLE = 18, LONGDOUBLE = 19, POINTER = 20;
    private static final int KIND_COUNT = 21;
    /** One record per kind, allocated lazily and never freed, so earlier struct views stay valid. */
    private static final long[] addresses = new long[KIND_COUNT];
    // {size, alignment, libffi type code} per kind. Unsigned/signed long are 8 bytes on LP64.
    private static final int[] RECORDS = {
        0, 0, 0,   // void
        1, 1, 5,   // uint8
        1, 1, 6,   // sint8
        2, 2, 7,   // uint16
        2, 2, 8,   // sint16
        4, 4, 9,   // uint32
        4, 4, 10,  // sint32
        8, 8, 11,  // uint64
        8, 8, 12,  // sint64
        1, 1, 5,   // uchar
        1, 1, 6,   // schar
        2, 2, 7,   // ushort
        2, 2, 8,   // sshort
        4, 4, 9,   // uint
        4, 4, 10,  // sint
        8, 8, 11,  // ulong
        8, 8, 12,  // slong
        4, 4, 2,   // float
        8, 8, 3,   // double
        16, 16, 4, // longdouble
        8, 8, 14,  // pointer
    };
    private BrowserLibFFI() { }
    public static long typeAddress(int kind) {
        if (kind < 0 || kind >= KIND_COUNT) throw new IllegalArgumentException("Unknown browser libffi type kind: " + kind);
        long address = addresses[kind];
        if (address == 0) {
            address = NativeMemory.nmemAllocChecked(RECORD_BYTES);
            NativeMemory.memPutAddress(address, RECORDS[kind * 3]);
            NativeMemory.memPutInt(address + ALIGNMENT_OFFSET, RECORDS[kind * 3 + 1]);
            NativeMemory.memPutShort(address + TYPE_OFFSET, (short) RECORDS[kind * 3 + 2]);
            NativeMemory.memPutInt(address + ELEMENTS_OFFSET, 0);
            addresses[kind] = address;
        }
        return address;
    }
    /** A fresh zeroed ffi_cif record. Nothing reads it: closures do not exist in wasm. */
    public static long newCIFAddress() { return NativeMemory.nmemCallocChecked(1, 40); }
}
