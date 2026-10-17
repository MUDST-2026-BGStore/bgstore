"""Run a command with a POSIX process-group deadline (macOS and Linux)."""

import os
import signal
import subprocess
import sys


class Interrupted(Exception):
    def __init__(self, signum):
        self.signum = signum


def send_group(process, signum):
    try:
        os.killpg(process.pid, signum)
    except ProcessLookupError:
        pass


def terminate(process, grace):
    send_group(process, signal.SIGTERM)
    try:
        process.wait(timeout=grace)
    except subprocess.TimeoutExpired:
        pass
    finally:
        # Descendants may survive even after their immediate parent exits.
        send_group(process, signal.SIGKILL)
        process.wait()


def main():
    seconds, grace = map(float, sys.argv[1:3])
    process = subprocess.Popen(sys.argv[3:], start_new_session=True)

    def interrupted(signum, _frame):
        raise Interrupted(signum)

    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, interrupted)
    try:
        status = process.wait(timeout=seconds)
        return status if status >= 0 else 128 - status
    except (subprocess.TimeoutExpired, Interrupted) as error:
        # Allow cleanup to finish if the terminal sends another interrupt.
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, signal.SIG_IGN)
        terminate(process, grace)
        if isinstance(error, Interrupted):
            return 128 + error.signum
        print(f"Command exceeded its {seconds:g}s deadline: {sys.argv[3]}", file=sys.stderr)
        return 124


if __name__ == "__main__":
    sys.exit(main())
