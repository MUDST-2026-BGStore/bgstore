"""Check failure and partial-run reporting independently of GitHub Actions."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    "ci_summary", Path(__file__).resolve().parents[1] / "ci-summary.py")
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class CiSummaryTest(unittest.TestCase):
    def test_nested_suites_are_counted_once_and_errors_are_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "a.xml").write_text('''<testsuites tests="4"><testsuite tests="4">
              <testcase name="pass"/>
              <testcase name="fail"><failure/></testcase>
              <testcase name="error"><error/></testcase>
              <testcase name="skip"><skipped/></testcase>
            </testsuite></testsuites>''')
            (root / "b.xml").write_text('<testsuite><testcase name="pass"/></testsuite>')
            text = summary.render_summary("Quality", {"checks": {"outcome": "failure"}},
                                          [("API", f"{root}/*.xml")], "https://example.com/artifact")
        self.assertIn("| API | 5 | 2 | 2 | 1 |", text)
        self.assertIn("| checks | failure |", text)
        self.assertIn("[Download this job's reports and logs](https://example.com/artifact)", text)

    def test_setup_failure_does_not_look_like_passing_tests(self):
        text = summary.render_summary("Browser", {"tests": {"outcome": "skipped"}},
                                      [("Playwright", "/nonexistent/bgstore/*.xml")], "")
        self.assertIn("| tests | skipped |", text)
        self.assertIn("| Playwright | Unavailable |", text)
        self.assertIn("No artifact was uploaded", text)

    def test_truncated_xml_marks_results_unavailable(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "junit.xml"
            report.write_text('<testsuite><testcase name="unfinished">')
            text = summary.render_summary("Browser", {}, [("Playwright", str(report))], "")
        self.assertIn("| Playwright | Unavailable |", text)


if __name__ == "__main__":
    unittest.main()
