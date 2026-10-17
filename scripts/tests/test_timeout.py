import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest

HELPER = Path(__file__).resolve().parents[1] / "lib/timeout.py"


class TimeoutTest(unittest.TestCase):
    def command(self, code, seconds="5"):
        return [sys.executable, str(HELPER), seconds, "0.2", sys.executable, "-c", code]

    def test_preserves_exit_status(self):
        result = subprocess.run(self.command("raise SystemExit(37)"))
        self.assertEqual(result.returncode, 37)

    def test_bounds_command_ignoring_term(self):
        start = time.monotonic()
        result = subprocess.run(self.command(
            "import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); time.sleep(30)",
            seconds="0.5"))
        self.assertEqual(result.returncode, 124)
        self.assertLess(time.monotonic() - start, 5)

    def test_deadline_kills_descendants(self):
        with tempfile.TemporaryDirectory() as temp:
            marker = Path(temp) / "survived"
            child = f"import time; time.sleep(1); open({str(marker)!r}, 'w').close()"
            parent = f"import subprocess,sys,time; subprocess.Popen([sys.executable,'-c',{child!r}]); time.sleep(30)"
            result = subprocess.run(self.command(parent, seconds="0.3"))
            self.assertEqual(result.returncode, 124)
            time.sleep(1)
            self.assertFalse(marker.exists())

    def test_interrupt_forwards_and_stops_child(self):
        with tempfile.TemporaryDirectory() as temp:
            ready = Path(temp) / "ready"
            code = f"import time; open({str(ready)!r}, 'w').close(); time.sleep(30)"
            process = subprocess.Popen(self.command(code))
            self.addCleanup(lambda: process.poll() is None and process.kill())
            deadline = time.monotonic() + 5
            while not ready.exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue(ready.exists())
            os.kill(process.pid, signal.SIGTERM)
            self.assertEqual(process.wait(timeout=5), 143)


if __name__ == "__main__":
    unittest.main()
