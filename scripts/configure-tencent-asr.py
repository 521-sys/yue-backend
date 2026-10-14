"""Run over an interactive SSH TTY. Never pass credentials as arguments."""
import getpass
import json
import os
from pathlib import Path
import re
import sys
import tempfile


def updated_env(original, secret_id, secret_key):
    if not re.fullmatch(r"AKID[A-Za-z0-9]{8,124}", secret_id):
        raise ValueError("Invalid SecretId format; nothing was changed.")
    if not re.fullmatch(r"[A-Za-z0-9]{16,128}", secret_key):
        raise ValueError("Invalid SecretKey format; nothing was changed.")
    names = {"TENCENTCLOUD_SECRET_ID", "TENCENTCLOUD_SECRET_KEY"}
    lines = [line for line in original.splitlines()
             if line.partition("=")[0].strip() not in names]
    return "\n".join(lines + ["TENCENTCLOUD_SECRET_ID=" + secret_id,
                              "TENCENTCLOUD_SECRET_KEY=" + secret_key]) + "\n"


def main():
    piped = sys.argv[1:] == ["--stdin"]
    if (not piped and not sys.stdin.isatty()) or os.geteuid() != 0:
        raise RuntimeError("Interactive SSH terminal and sudo are required.")
    path = Path("/opt/yue/backend.env")
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("Expected a regular backend.env file; nothing was changed.")
    original = path.read_text(encoding="utf-8")
    if piped:
        payload = json.loads(sys.stdin.read(4097))
        secret_id = payload["secretId"].strip()
        secret_key = payload["secretKey"].strip()
    else:
        print("Configure Tencent ASR credentials on the Yue server.")
        print("Paste each value, then press Enter. Input is hidden.")
        print("This only saves credentials. It does not enable ASR or restart services.")
        secret_id = getpass.getpass("SecretId (hidden): ").strip()
        secret_key = getpass.getpass("SecretKey (hidden): ").strip()
    updated = updated_env(original, secret_id, secret_key)
    # Avoid overwriting an operator's concurrent change while the user was typing.
    if path.read_text(encoding="utf-8") != original:
        raise RuntimeError("Configuration changed concurrently. Please run again.")
    backup_dir = path.parent / "credential-backups"
    backup_dir.mkdir(mode=0o700, exist_ok=True)
    if backup_dir.is_symlink():
        raise RuntimeError("Unexpected backup directory; nothing was changed.")
    backup_dir.chmod(0o700)
    backup_fd, _ = tempfile.mkstemp(prefix="backend-before-tencent-", suffix=".env", dir=backup_dir)
    with os.fdopen(backup_fd, "w", encoding="utf-8") as backup:
        backup.write(original)
        backup.flush()
        os.fsync(backup.fileno())
    info = path.stat()
    fd, temporary = tempfile.mkstemp(prefix=".backend-tencent-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            os.fchown(output.fileno(), info.st_uid, info.st_gid)
            output.write(updated)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        print("SUCCESS: credentials saved. No service was restarted.")
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


if __name__ == "__main__":
    try:
        main()
    except (KeyboardInterrupt, EOFError):
        print("Cancelled. No credentials were displayed.")
        sys.exit(1)
    except Exception:
        # Never print exception details, source lines, or credential values.
        print("Configuration was not completed. Check input and SSH access, then retry.")
        sys.exit(1)
