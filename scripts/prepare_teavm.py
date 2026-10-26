#!/usr/bin/env python3
"""Apply a narrow, pinned compiler-memory patch to Apache-2.0 TeaVM sources.

Only generated, ignored output contains the upstream source. No game inputs.
"""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.16.0"
SHA256 = "687cabf23e0d17a15be30fbc070809c2931714340aca224120fe31579f95519b"
SOURCE = "org/teavm/reflection/ReflectionDependencyListener.java"


def main():
    archive = ROOT / f".toolchain/downloads/teavm-core-{VERSION}-sources.jar"
    if not archive.exists():
        archive.parent.mkdir(parents=True, exist_ok=True)
        url = f"https://repo.maven.apache.org/maven2/org/teavm/teavm-core/{VERSION}/{archive.name}"
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != SHA256:
            raise RuntimeError("Unexpected TeaVM source archive digest")
        archive.write_bytes(data)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError("TeaVM source archive digest mismatch")
    with zipfile.ZipFile(archive) as package:
        source = package.read(SOURCE).decode("utf-8")
        # DependencyAnalyzer interns types using a unique, monotonically assigned
        # index per analysis. Each consumer/set belongs to that same analysis.
        analyzer = package.read("org/teavm/dependency/DependencyAnalyzer.java").decode("utf-8")
    if "new DependencyType(valueType, types.size())" not in analyzer:
        raise RuntimeError("TeaVM type-index invariant changed")
    old = "private Set<DependencyType> knownTypes = new HashSet<>();"
    new = "private org.teavm.dependency.CompactTypeSet knownTypes = new org.teavm.dependency.CompactTypeSet();"
    if source.count(old) != 3:
        raise RuntimeError("Unexpected TeaVM reflection cache declarations")
    patched = "// Local change: compact seen-type caches; reflection semantics unchanged.\n" + source.replace(old, new)
    destination = ROOT / "port/target/generated-sources/teavm" / SOURCE
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(patched, encoding="utf-8")
    print(f"TeaVM {VERSION}: compact reflection caches (3); source SHA-256 {SHA256}")


if __name__ == "__main__":
    main()
