import re
import sys

TITLE = re.compile(r"^(fix|feat|chore)(\([a-z0-9-]+\))?: .+")
def classify(subject):
    match = TITLE.fullmatch(subject.replace("!:", ":", 1))
    return {"fix": "patch", "feat": "minor"}.get(match[1], "none") if match else "none"

def bump(version, kind):
    major, minor, patch = map(int, version.split("."))
    if kind == "minor":
        minor, patch = minor + 1, 0
    else:
        patch += 1
    assert minor < 1000 and patch < 1000, "minor and patch must be below 1000"
    return f"{major}.{minor}.{patch}"

if __name__ == "__main__":
    if sys.argv[1] == "check":
        sys.exit(0 if TITLE.fullmatch(sys.argv[2]) else 1)
    if sys.argv[1] == "bump":
        print(bump(sys.argv[2], sys.argv[3]))
    else:
        print(classify(sys.argv[2]))
