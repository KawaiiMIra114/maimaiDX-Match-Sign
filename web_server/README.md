# Game Sign Web Server

A Flask-based web server for tournament management.

## Prerequisites

*   Python 3.8+
*   pip

## Installation

1.  Navigate to this directory:
    ```bash
    cd web_server
    ```

2.  Install dependencies:
    ```bash
    pip install -r requirements.txt
    ```

## Configuration

*   **Secret Key**: Change `app.secret_key` in `app.py` for production.
*   **Admin Token**: Set `ADMIN_API_TOKEN` environment variable or modify the default in `api_routes.py`.

## Running the Server

1.  Initialize the database (first time):
    ```bash
    python app.py
    ```
    (Note: The app is configured to create tables if they don't exist on startup).

2.  Run the server:
    ```bash
    python app.py
    ```

    By default, it runs on `http://0.0.0.0:5000`.

## Admin Panel

Access `http://localhost:5000/admin` to manage the tournament.
Default admin credentials might need to be configured in `app.py` or `admin_login.html` logic.

## API

The server provides REST APIs for the Android app under `/api/v1/`.
