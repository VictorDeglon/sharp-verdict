#!/usr/bin/env python3
"""Turns the JUnit XML that Gradle writes into a plain text list of results.

    python3 tools/release/test-summary.py build/test-results/test [--title TEXT] [--markdown]

Plain text (the default) lists every test with its result, for the file attached to a release.
--markdown prints only the totals and any failures, for the CI job summary.
Exits 1 when a test failed, 2 when there are no results to read.
"""

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET


def read_results(folder):
    results = []
    for path in sorted(glob.glob(os.path.join(folder, "*.xml"))):
        for case in ET.parse(path).getroot().iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                outcome = "FAILED"
            elif case.find("skipped") is not None:
                outcome = "SKIPPED"
            else:
                outcome = "passed"
            results.append((case.get("classname"), case.get("name"), outcome))
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("folder")
    parser.add_argument("--title", default="Unit test results")
    parser.add_argument("--markdown", action="store_true")
    args = parser.parse_args()

    results = read_results(args.folder)
    if not results:
        print(f"No test results in {args.folder}.")
        return 2

    failed = [r for r in results if r[2] == "FAILED"]
    skipped = [r for r in results if r[2] == "SKIPPED"]
    passed = len(results) - len(failed) - len(skipped)
    totals = f"{len(results)} tests: {passed} passed, {len(failed)} failed, {len(skipped)} skipped"

    if args.markdown:
        print(f"### {args.title}\n\n{totals}\n")
        for classname, name, _ in failed:
            print(f"- FAILED `{classname.rsplit('.', 1)[-1]}.{name}`")
        return 1 if failed else 0

    print(args.title)
    print(totals)
    current = None
    for classname, name, outcome in results:
        if classname != current:
            current = classname
            print(f"\n{classname}")
        print(f"  {outcome:7}  {name}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
