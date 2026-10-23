#!/usr/bin/env python3
"""Serve the debug web build and proxy /api/v1 to the local MedTrack backend."""
from http.client import HTTPConnection
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

WEB = Path(__file__).resolve().parents[1] / 'build/web'
HOP_HEADERS = {'connection', 'keep-alive', 'proxy-authenticate',
               'proxy-authorization', 'te', 'trailer', 'transfer-encoding', 'upgrade'}


class PreviewHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB), **kwargs)

    def log_message(self, *args):
        # Do not log request URLs, credentials or API bodies.
        pass

    def handle_request(self):
        if self.headers.get('Host') not in ('127.0.0.1:7357', 'localhost:7357'):
            self.send_error(403)
            return
        if not (self.path == '/api/v1' or self.path.startswith('/api/v1/')):
            if self.command == 'GET':
                super().do_GET()
            elif self.command == 'HEAD':
                super().do_HEAD()
            else:
                self.send_error(405)
            return
        upstream = HTTPConnection('127.0.0.1', 8080, timeout=30)
        try:
            body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            headers = {k: v for k, v in self.headers.items()
                       if k.lower() not in HOP_HEADERS | {'host', 'content-length'}}
            upstream.request(self.command, self.path, body=body, headers=headers)
            response = upstream.getresponse()
            data = response.read()
            self.send_response(response.status)
            for key, value in response.getheaders():
                if key.lower() not in HOP_HEADERS | {'content-length'}:
                    self.send_header(key, value)
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            if self.command != 'HEAD':
                self.wfile.write(data)
        except (OSError, ValueError):
            self.send_error(502, 'Local backend unavailable')
        finally:
            upstream.close()

    do_GET = do_HEAD = do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = handle_request


if __name__ == '__main__':
    if not (WEB / 'index.html').is_file():
        raise SystemExit('Build the debug web app first; see mobile/README.md.')
    print('MedTrack local preview: http://127.0.0.1:7357', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 7357), PreviewHandler).serve_forever()
