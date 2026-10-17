"""Write a GitHub job summary without additional token permissions or packages."""

import argparse
import glob
import html
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def render_summary(title, steps, reports, artifact_url):
    lines = [f"## {html.escape(title)}", "", "| Check | Result |",
             "| --- | --- |"]
    for name, step in steps.items():
        lines.append(f"| {html.escape(name)} | {step['outcome']} |")

    if reports:
        lines.extend(["", "| Tests | Total | Passed | Failed | Skipped |",
                      "| --- | ---: | ---: | ---: | ---: |"])
    for label, pattern in reports:
        files = sorted(glob.glob(pattern, recursive=True))
        counts = dict(total=0, passed=0, failed=0, skipped=0)
        try:
            for file in files:
                for case in ET.parse(file).iter("testcase"):
                    counts['total'] += 1
                    if case.find("failure") is not None or case.find("error") is not None:
                        counts['failed'] += 1
                    elif case.find("skipped") is not None:
                        counts['skipped'] += 1
                    else:
                        counts['passed'] += 1
        except (ET.ParseError, OSError):
            files = []
        if files:
            lines.append(f"| {html.escape(label)} | {counts['total']} | "
                         f"{counts['passed']} | {counts['failed']} | {counts['skipped']} |")
        else:
            lines.append(f"| {html.escape(label)} | Unavailable | — | — | — |")

    lines.append("")
    if artifact_url:
        lines.append(f"[Download this job's reports and logs]({artifact_url}) (retained for 14 days).")
    else:
        lines.append("No artifact was uploaded. Check the setup and upload steps for details.")
    if reports:
        lines.extend(["", "Unavailable results mean the test report was not produced or could not be read; "
                      "check the job log. Test counts alone do not include lint, coverage, or setup failures."])
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("title")
    parser.add_argument("--junit", action="append", nargs=2, default=[],
                        metavar=("LABEL", "GLOB"))
    args = parser.parse_args()
    summary = render_summary(args.title, json.loads(os.environ.get("CI_STEPS", "{}")),
                             args.junit, os.environ.get("CI_ARTIFACT_URL", ""))
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as output:
        output.write(summary)


if __name__ == "__main__":
    main()
