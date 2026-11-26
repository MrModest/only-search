from subject import bump, classify, TITLE

cases = {
    "fix: x": "patch", "fix(search): x": "patch",
    "feat(widgets): x": "minor", "chore: x": "none",
    "chore(ci): x": "none", "chore(release): v0.2.110": "none",
    "docs: x": "none", "ci: x": "none", "test: x": "none",
    "Fix top-hit …": "none", "fixup! x": "none", "fix x": "none",
}
for subject, expected in cases.items():
    assert classify(subject) == expected
    print(f"{subject} => {expected}")
assert classify("feat(widgets)!: x") == "minor"
assert not TITLE.fullmatch("feat(widgets)!: x")
assert bump("0.2.109", "patch") == "0.2.110"
assert bump("0.2.109", "minor") == "0.3.0"
for version in ("0.2.109", "0.3.0"):
    major, minor, patch = map(int, version.split("."))
    code = major * 1_000_000 + minor * 1_000 + patch
    assert code == {"0.2.109": 2109, "0.3.0": 3000}[version]
    print(f"{version} => versionCode {code}")
try:
    bump("0.999.0", "minor")
except AssertionError:
    pass
else:
    raise AssertionError("overflow accepted")
