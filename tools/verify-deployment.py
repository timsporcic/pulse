#!/usr/bin/env python3
"""Check deployment rendering and Caddy authentication locally; requires tofu and caddy."""
import base64
import configparser
import http.server
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs).stdout


def evaluate(directory, expression):
    return json.loads(run("tofu", f"-chdir={directory}", "console", "-no-color", input=expression + "\n"))


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class Backend(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"protected backend")

    do_POST = do_GET
    do_DELETE = do_GET

    def log_message(self, *_):
        pass


def main():
    with tempfile.TemporaryDirectory(prefix="pulse-deploy-check-") as directory:
        work = Path(directory)
        password_hash = run("caddy", "hash-password", "--plaintext", "local-test-only").strip()
        params = {
            "volume_device": "/dev/test-volume",
            "deploy_bucket_url": "https://example.invalid/deploy",
            "pulse_service": (ROOT / "ops/pulse.service").read_text(),
            "restore_service": (ROOT / "ops/pulse-restore.service").read_text(),
            "litestream_service": (ROOT / "ops/litestream.service").read_text(),
            "litestream_env_b64": base64.b64encode(b"LITESTREAM_ACCESS_KEY_ID=test\nLITESTREAM_SECRET_ACCESS_KEY=test\n").decode(),
            "caddy_users_b64": base64.b64encode(f"demo {password_hash}\n".encode()).decode(),
        }
        expression = f'jsonencode(yamldecode(templatefile({json.dumps(str(ROOT / "ops/cloud-init.yml"))}, {json.dumps(params)})))'
        cloud = json.loads(evaluate(work, expression))
        files = {item["path"]: item for item in cloud["write_files"]}
        for name in ("pulse", "litestream", "pulse-restore"):
            content = files[f"/etc/systemd/system/{name}.service"]["content"]
            assert content.rstrip() == (ROOT / f"ops/{name}.service").read_text().rstrip()
            unit = configparser.ConfigParser(interpolation=None)
            unit.read_string(content)
            assert unit["Unit"]["RequiresMountsFor"] == "/var/lib/pulse"
            assert unit["Service"]["ExecStartPre"] == "/usr/bin/mountpoint -q /var/lib/pulse"
            if name == "pulse-restore":
                assert unit["Service"]["Type"] == "oneshot"
                assert "-if-db-not-exists -if-replica-exists" in unit["Service"]["ExecStart"]
                assert unit["Service"]["EnvironmentFile"] == "/etc/litestream.env"
            else:
                assert "pulse-restore.service" in unit["Unit"]["Requires"]
                assert "pulse-restore.service" in unit["Unit"]["After"]
        for path in ("/etc/caddy/pulse-users", "/etc/litestream.env"):
            assert files[path]["permissions"] == "0600"
            assert files[path]["encoding"] == "b64"
        assert all(isinstance(command, str) for command in cloud["runcmd"])
        run("sh", "-n", input="\n".join(cloud["runcmd"]))
        print("PASS: cloud-init YAML, canonical units, mount and restore dependencies, private credentials")

        auth = work / "pulse-users"
        auth.write_bytes(base64.b64decode(files["/etc/caddy/pulse-users"]["content"]))
        backend = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Backend)
        threading.Thread(target=backend.serve_forever, daemon=True).start()
        try:
            for source in (ROOT / "ops/Caddyfile", ROOT / "infra/templates/Caddyfile.tftpl"):
                port = free_port()
                config = source.read_text().replace("pulse.example.org", f"http://127.0.0.1:{port}")
                config = config.replace("${domain}", f"http://127.0.0.1:{port}")
                config = config.replace("/etc/caddy/pulse-users", str(auth))
                config = config.replace("127.0.0.1:7070", f"127.0.0.1:{backend.server_port}")
                path = work / "Caddyfile"
                path.write_text("{\n admin off\n auto_https off\n}\n" + config)
                run("caddy", "validate", "--config", str(path), "--adapter", "caddyfile")
                with (work / "caddy.log").open("w") as log:
                    process = subprocess.Popen(["caddy", "run", "--config", str(path), "--adapter", "caddyfile"], stdout=log, stderr=log)
                    try:
                        def request(method, route, credentials=None):
                            headers = {} if credentials is None else {"Authorization": "Basic " + base64.b64encode(credentials.encode()).decode()}
                            req = urllib.request.Request(f"http://127.0.0.1:{port}{route}", method=method, headers=headers)
                            try:
                                with urllib.request.urlopen(req, timeout=2) as response:
                                    return response.status
                            except urllib.error.HTTPError as error:
                                return error.code
                        deadline = time.monotonic() + 10
                        while True:
                            try:
                                assert request("GET", "/") == 401
                                break
                            except urllib.error.URLError:
                                if time.monotonic() > deadline:
                                    raise
                                time.sleep(0.1)
                        for method, route in (("GET", "/"), ("GET", "/api/monitors"), ("POST", "/monitors"), ("DELETE", "/monitors/1")):
                            assert request(method, route) == 401
                            assert request(method, route, "demo:wrong") == 401
                            assert request(method, route, "demo:local-test-only") == 200
                        assert request("GET", "/metrics", "demo:local-test-only") == 403
                    finally:
                        process.terminate()
                        process.wait(timeout=5)
                print(f"PASS: {source.relative_to(ROOT)} denies anonymous and bad credentials; permits authenticated routes; blocks metrics")
        finally:
            backend.shutdown()
            backend.server_close()


if __name__ == "__main__":
    main()
