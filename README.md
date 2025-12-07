# ChatGPT Web Server

A fully custom-built HTTP web server in Java that provides a ChatGPT API wrapper with admin interface, authentication, logging, and multi-threading support.

![Java](https://img.shields.io/badge/Java-11+-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

## 🌟 Features

### Core HTTP Server
- **Custom HTTP Parser** - Hand-built request/response parsing without frameworks
- **Multi-threaded** - Thread pool for handling concurrent connections
- **GET, HEAD, POST** - Full support for required HTTP methods
- **Static File Server** - Serves HTML, CSS, JS, and other static files
- **MIME Type Detection** - Automatic content-type headers

### ChatGPT Integration
- **REST API Wrapper** - Clean API for ChatGPT interactions
- **Session Management** - Track and retrieve chat sessions
- **Model Switching** - Change ChatGPT models at runtime
- **Token Tracking** - Full usage metadata in responses

### Admin Interface
- **Secure Dashboard** - Web-based admin panel
- **Cookie-based Auth** - Session management with HttpOnly cookies
- **SHA-256 Passwords** - Secure password hashing
- **2FA Support** - Optional two-factor authentication
- **Real-time Monitoring** - Server status, sessions, and logs

### Security
- **Path Traversal Prevention** - Blocks `../` attacks
- **No Hardcoded Secrets** - Environment variable support
- **IP Lockout** - Brute force protection
- **Secure Tokens** - Cryptographically random session tokens

## 📁 Project Structure

```
ChatBot/
├── src/
│   ├── server/
│   │   ├── WebServer.java          # Main server entry point
│   │   ├── ClientHandler.java      # Per-connection handler
│   │   ├── RequestParser.java      # HTTP request parsing
│   │   ├── ResponseBuilder.java    # HTTP response construction
│   │   ├── RequestRouter.java      # URL routing
│   │   ├── HttpRequest.java        # Request model
│   │   └── HttpResponse.java       # Response model
│   ├── chat/
│   │   ├── ChatHandler.java        # /chat endpoint handler
│   │   ├── ChatGPTClient.java      # OpenAI API client
│   │   ├── ChatSession.java        # Chat session model
│   │   ├── ChatSessionManager.java # Session storage
│   │   └── ResponseMetadata.java   # API response metadata
│   ├── admin/
│   │   ├── AdminHandler.java       # /admin endpoint handler
│   │   ├── AuthManager.java        # Authentication logic
│   │   └── AdminSession.java       # Admin session model
│   ├── logging/
│   │   └── ServerLogger.java       # Logging system
│   └── util/
│       ├── ConfigLoader.java       # Configuration management
│       └── SecurityUtils.java      # Security utilities
├── public/
│   ├── index.html                  # Main chat interface
│   ├── login.html                  # Admin login page
│   ├── admin.html                  # Admin dashboard
│   ├── styles.css                  # Stylesheet
│   └── app.js                      # Frontend JavaScript
├── config/
│   └── server.conf                 # Server configuration
├── logs/                           # Log files (created at runtime)
└── README.md                       # This file
```

## 🚀 Getting Started

### Prerequisites
- Java 11 or higher
- OpenAI API key

### Configuration

1. **Set your OpenAI API key:**
   
   Option A - Environment variable (recommended):
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```
   
   Option B - Config file:
   Edit `config/server.conf` and set `openai.api_key`

2. **Set admin password:**
   
   Option A - Environment variable:
   ```bash
   export ADMIN_PASSWORD=your-secure-password
   ```
   
   Option B - Config file:
   Generate SHA-256 hash and set in `admin.password_hash`:
   ```bash
   echo -n "yourpassword" | sha256sum
   ```

### Compilation

```bash
# Navigate to project root
cd ChatBot

# Compile all Java files
javac -d out -sourcepath src src/server/WebServer.java
```

### Running

```bash
# Run the server
java -cp out server.WebServer

# Or with custom config file
java -cp out server.WebServer path/to/config.conf
```

The server will start on port 8080 by default. Access it at:
- **Chat Interface:** http://localhost:8080
- **Admin Login:** http://localhost:8080/admin/login

## 📡 API Reference

### Chat API

#### Create Chat Session
```http
POST /chat
Content-Type: application/json

{"prompt": "Hello, how are you?"}
```

**Response:**
```http
HTTP/1.1 201 Created
Location: /chat/abc123def456

id=abc123def456
```

#### Get Chat Response
```http
GET /chat/{id}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/plain
X-Model: gpt-3.5-turbo
X-Prompt-Tokens: 15
X-Completion-Tokens: 42
X-Total-Tokens: 57
X-Created: 1701234567

Hello! I'm doing well, thank you for asking...
```

#### Get Headers Only
```http
HEAD /chat/{id}
```

Returns same headers as GET, but no body.

### Admin API

All admin endpoints require authentication (cookie: `admin_session`).

#### Server Status
```http
GET /admin/api/status
```

**Response:**
```json
{
  "uptime": 3600000,
  "uptimeFormatted": "1h 0m 0s",
  "totalRequests": 1234,
  "totalChatSessions": 56,
  "currentModel": "gpt-3.5-turbo",
  "activeConnections": 3,
  "chatEnabled": true
}
```

#### List Sessions
```http
GET /admin/api/sessions?limit=50
```

**Response:**
```json
[
  {
    "id": "abc123",
    "promptPreview": "Hello, how are...",
    "model": "gpt-3.5-turbo",
    "totalTokens": 57,
    "timestamp": 1701234567000
  }
]
```

#### Get Logs
```http
GET /admin/api/logs?lines=100
```

**Response:**
```json
{
  "logs": "[2024-01-01 12:00:00] [INFO] Server started..."
}
```

#### Change Model
```http
POST /admin/api/model
Content-Type: application/json

{"model": "gpt-4"}
```

#### Toggle Chat
```http
POST /admin/api/toggle-chat
Content-Type: application/json

{"enabled": false}
```

## 🔐 Security Notes

### Production Recommendations

1. **Never commit API keys** - Always use environment variables
2. **Change default password** - Update `admin.password_hash` immediately
3. **Use HTTPS** - Deploy behind a reverse proxy with SSL
4. **Enable 2FA** - Set `admin.2fa_enabled=true` in production
5. **Restrict access** - Use firewall rules for admin endpoints

### Password Hashing

Generate a password hash:
```bash
# Linux/Mac
echo -n "your-password" | sha256sum | cut -d' ' -f1

# Windows PowerShell
$bytes = [System.Text.Encoding]::UTF8.GetBytes("your-password")
$hash = [System.Security.Cryptography.SHA256]::Create()
$hashBytes = $hash.ComputeHash($bytes)
[BitConverter]::ToString($hashBytes) -replace '-','' | ForEach-Object { $_.ToLower() }
```

## 📊 Logging

Logs are written to both:
- Console (stdout/stderr)
- File (configured in `server.log_file`)

Log format:
```
[2024-01-01 12:00:00.000] [LEVEL] [IP] [METHOD] [PATH] Message
```

Log levels:
- `INFO` - General information
- `ERROR` - Errors and exceptions
- `REQUEST` - Incoming HTTP requests
- `RESPONSE` - Outgoing HTTP responses
- `ADMIN` - Admin actions
- `CHAT` - Chat session events

## 🛠️ Development

### Adding New Endpoints

1. Create a handler class in the appropriate package
2. Register route in `RequestRouter.route()`
3. Implement request handling logic

### Extending ChatGPT Integration

The `ChatGPTClient` class can be extended to support:
- Streaming responses
- Function calling
- Different OpenAI endpoints
- Other AI providers

## 📝 License

This project is for educational purposes. MIT License.

## 🙏 Acknowledgments

- OpenAI for the ChatGPT API
- Course CSE389 for project requirements

---

**Note:** This is a university networking/web-server design project. Not intended for production use without additional security hardening.
