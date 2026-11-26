import re
import subprocess
from subject import classify

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

previous = git("describe", "--tags", "--match", "v*", "--abbrev=0", "HEAD^")
subjects = git("log", "--first-parent", "--format=%s", f"{previous}..HEAD").splitlines()
for kind, heading in [("minor", "New"), ("patch", "Fixes")]:
    lines = [re.sub(r"^[^:]+: ", "", s, count=1) for s in subjects if classify(s) == kind]
    if lines:
        print(f"## {heading}")
        print("\n".join(f"- {line}" for line in lines))
        print()
