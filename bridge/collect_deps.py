"""Copy every NEEDED .so reachable from the libs in OUT, searching the given dirs. System libs are skipped."""
import os, shutil, subprocess, sys

out, libcxx, *dirs = sys.argv[1:]
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so", "libEGL.so", "libGLESv2.so", "libjnigraphics.so"}
shutil.copy(libcxx, out)
index = {}
for d in dirs:
    for name in os.listdir(d):
        if name.endswith(".so"):
            index.setdefault(name, os.path.join(d, name))

todo = list(os.listdir(out))
while todo:
    lib = todo.pop()
    dyn = subprocess.run(["readelf", "-d", os.path.join(out, lib)], capture_output=True, text=True, check=True).stdout
    for line in dyn.splitlines():
        if "(NEEDED)" not in line:
            continue
        dep = line.split("[")[1].rstrip("]")
        if dep in SYSTEM or os.path.exists(os.path.join(out, dep)):
            continue
        if dep not in index:
            sys.exit(f"missing dependency {dep} (needed by {lib})")
        shutil.copy(index[dep], out)
        todo.append(dep)
print(out, sorted(os.listdir(out)))
