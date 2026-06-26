from flask import Flask, jsonify


def create_app() -> Flask:
    app = Flask(__name__)

    @app.get("/health")
    def health():
        return jsonify({"status": "UP", "service": "reelshort-content-provider"})

    return app


if __name__ == "__main__":
    create_app().run(host="127.0.0.1", port=5000)

