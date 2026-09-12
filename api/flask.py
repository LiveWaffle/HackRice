import os 
from flask import Flask, jsonify, request

def create_app():
    app = Flask(__name__)

    @app.route('/api/hello', methods=['GET'])
    def hello():
        return jsonify({'message': 'Hello, World!'})

    @app.route('/api/echo', methods=['POST'])
    def echo():
        data = request.get_json()
        return jsonify({'you_sent': data})

    return app