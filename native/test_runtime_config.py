"""Offline checks for explicit device-wrapper configuration; never contact a device."""
import contextlib
import io
import unittest
from unittest.mock import patch

import test as runtime


class RuntimeConfigurationTest(unittest.TestCase):
    def test_missing_wrapper_fails_before_dispatch(self):
        for adb, session in (("", "/fixture/session"), ("/fixture/adb", "")):
            with self.subTest(adb=bool(adb), session=bool(session)):
                with patch.object(runtime, "ADB", adb), patch.object(runtime, "SESSION_WRAPPER", session), \
                     patch("sys.argv", ["test.py"]), patch.object(runtime.subprocess, "call") as dispatch, \
                     contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                    runtime.main()
                self.assertEqual(error.exception.code, 2)
                dispatch.assert_not_called()

    def test_configured_wrapper_preserves_acceptance_options(self):
        with patch.object(runtime, "ADB", "/fixture/adb"), \
             patch.object(runtime, "SESSION_WRAPPER", "/fixture/session"), \
             patch("sys.argv", ["test.py", "--abi", "arm64-v8a", "--configuration", "release", "--opencode-go", "--openrouter"]), \
             patch.object(runtime.subprocess, "call", return_value=0) as dispatch:
            self.assertEqual(runtime.main(), 0)
        command = dispatch.call_args.args[0]
        self.assertEqual(command[0], "/fixture/session")
        self.assertEqual(command[3:], ["--abi", "arm64-v8a", "--configuration", "release", "--locked", "--opencode-go", "--openrouter"])


if __name__ == "__main__":
    unittest.main()
