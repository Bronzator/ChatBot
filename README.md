# ChatGPT Web Server

A fully custom-built HTTP web server in Java that provides a ChatGPT API wrapper with user authentication, database persistence, chat history, Docker deployment, and admin interface.

![Java](https://img.shields.io/badge/Java-17+-orange)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue)
![Docker](https://img.shields.io/badge/Docker-Ready-blue)
![License](https://img.shields.io/badge/License-MIT-green)

## 🌟 Features

### Core HTTP Server
- **Custom HTTP Parser** - Hand-built request/response parsing without frameworks
- **Multi-threaded** - Thread pool for handling concurrent connections
- **GET, HEAD, POST, PUT, DELETE** - Full HTTP method support
- **Static File Server** - Serves HTML, CSS, JS, and other static files
- **MIME Type Detection** - Automatic content-type headers

### User Authentication
- **Email/Password Registration** - Secure bcrypt password hashing
- **JWT Tokens** - Stateless authentication with HMAC-SHA256
- **Google OAuth 2.0** - Sign in with Google integration
- **Session Management** - Database-backed user sessions

### ChatGPT Integration
- **REST API Wrapper** - Clean API for ChatGPT interactions
- **Conversation History** - Full chat history support
- **Multiple Chats** - Create and manage multiple conversations
- **Model Selection** - Choose between GPT-3.5 and GPT-4 models
- **Token Tracking** - Full usage metadata in responses

### Database & Persistence
- **PostgreSQL** - Production-ready database
- **Connection Pooling** - Efficient database connections
- **Chat History** - Persistent conversation storage
- **User Profiles** - User data and preferences

### Docker Support
- **Multi-stage Build** - Optimized container images
- **Docker Compose** - One-command deployment
- **Persistent Volumes** - Data survives container restarts
- **Environment Config** - Easy configuration via `.env`

### Admin Interface
- **Secure Dashboard** - Web-based admin panel
- **User Management** - View and manage users
- **Server Statistics** - Real-time monitoring
- **System Logs** - View and clear logs
- **Configuration** - Update settings from UI

### Security
- **BCrypt Passwords** - Industry-standard password hashing
- **JWT Authentication** - Secure token-based auth
- **Path Traversal Prevention** - Blocks `../` attacks
- **No Hardcoded Secrets** - Environment variable support
- **SQL Injection Prevention** - Prepared statements

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
│   │   ├── ChatHandler.java        # Legacy /chat endpoint
│   │   ├── ChatApiHandler.java     # /api/chat authenticated endpoints
│   │   ├── ChatGPTClient.java      # OpenAI API client
│   │   ├── ChatSession.java        # Chat session model
│   │   ├── ChatSessionManager.java # Session storage
│   │   └── ResponseMetadata.java   # API response metadata
│   ├── auth/
│   │   ├── JWTManager.java         # JWT token generation/validation
│   │   ├── GoogleOAuthClient.java  # Google OAuth 2.0 integration
│   │   └── UserAuthHandler.java    # /auth endpoint handler
│   ├── database/
│   │   ├── DatabaseManager.java    # Connection pooling & init
│   │   ├── UserDAO.java            # User data access
│   │   ├── ChatDAO.java            # Chat data access
│   │   ├── MessageDAO.java         # Message data access
│   │   ├── User.java               # User entity
│   │   ├── Chat.java               # Chat entity
│   │   └── Message.java            # Message entity
│   ├── admin/
│   │   ├── AdminHandler.java       # /admin endpoint handler
│   │   ├── AuthManager.java        # Admin authentication
│   │   └── AdminSession.java       # Admin session model
│   ├── logging/
│   │   └── ServerLogger.java       # Logging system
│   └── util/
│       ├── ConfigLoader.java       # Configuration management
│       └── SecurityUtils.java      # Security utilities
├── sql/
│   └── schema.sql                  # PostgreSQL database schema
├── public/
│   ├── index.html                  # Main chat interface
│   ├── admin.html                  # Admin dashboard
│   └── styles.css                  # Modern dark theme styles
├── config/
│   └── server.conf                 # Server configuration
├── Dockerfile                      # Multi-stage Docker build
├── docker-compose.yml              # Full stack deployment
├── .env.example                    # Environment template
├── .gitignore                      # Git ignore rules
└── README.md                       # This file
```

## 🚀 Getting Started

### Option 1: Docker Deployment (Recommended)

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Bronzator/ChatBot.git
   cd ChatBot
   ```

2. **Create environment file:**
   ```bash
   cp .env.example .env
   ```

3. **Edit `.env` with your settings:**
   ```env
   # Required
   OPENAI_API_KEY=sk-your-openai-api-key
   JWT_SECRET=your-32-character-secret-key-here
   
   # Optional - Google OAuth
   GOOGLE_CLIENT_ID=your-google-client-id
   GOOGLE_CLIENT_SECRET=your-google-client-secret
   ```

4. **Start the stack:**
   ```bash
   docker-compose up -d
   ```

5. **Access the application:**
   - Chat Interface: http://localhost:8080
   - Admin Panel: http://localhost:8080/admin.html

### Option 2: Manual Deployment

#### Prerequisites
- Java 17 or higher
- PostgreSQL 15 or higher
- OpenAI API key

#### Database Setup

1. **Create PostgreSQL database:**
   ```sql
   CREATE DATABASE chatbot;
   CREATE USER chatbot_user WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE chatbot TO chatbot_user;
   ```

2. **Run the schema:**
   ```bash
   psql -U chatbot_user -d chatbot -f sql/schema.sql
   ```

#### Configuration

Edit `config/server.conf`:
```properties
# Server
server.port=8080
server.threads=10

# Database
database.url=jdbc:postgresql://localhost:5432/chatbot
database.user=chatbot_user
database.password=your_password

# OpenAI
openai.api_key=sk-your-api-key
openai.model=gpt-3.5-turbo

# JWT
jwt.secret=your-32-character-secret-key-here
jwt.expiry_hours=24

# Google OAuth (optional)
google.client_id=your-client-id
google.client_secret=your-client-secret
google.redirect_uri=http://localhost:8080/auth/google/callback

# Admin
admin.username=admin
admin.password_hash=your-sha256-hash
```

#### Compilation & Running

```bash
# Compile
cd ChatBot
javac -d out -sourcepath src src/server/WebServer.java

# Run
java -cp "out:lib/*" server.WebServer
```

## 📡 API Reference

### Authentication API

#### Register User
```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securepassword123",
  "name": "John Doe"
}
```

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

#### Login
```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securepassword123"
}
```

**Response:**
```json
{
  "success": true,
  "token": "eyJhbGciOiJIUzI1NiIs..."
}
```

#### Google OAuth
```http
GET /auth/google
```
Redirects to Google OAuth consent screen.

#### Get Current User
```http
GET /auth/me
Authorization: Bearer <token>
```

**Response:**
```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "John Doe"
}
```

### Chat API (Authenticated)

All `/api/chat` endpoints require `Authorization: Bearer <token>` header.

#### List User's Chats
```http
GET /api/chat/list
Authorization: Bearer <token>
```

**Response:**
```json
{
  "chats": [
    {
      "id": 1,
      "title": "New Chat",
      "model": "gpt-3.5-turbo",
      "created_at": "2024-01-15T10:30:00Z",
      "updated_at": "2024-01-15T12:45:00Z"
    }
  ]
}
```

#### Create New Chat
```http
POST /api/chat/create
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Help with Python",
  "model": "gpt-4"
}
```

**Response:**
```json
{
  "id": 2,
  "title": "Help with Python",
  "model": "gpt-4"
}
```

#### Get Chat Messages
```http
GET /api/chat/{chatId}/messages
Authorization: Bearer <token>
```

**Response:**
```json
{
  "messages": [
    {
      "id": 1,
      "role": "user",
      "content": "Hello!",
      "created_at": "2024-01-15T10:30:00Z"
    },
    {
      "id": 2,
      "role": "assistant",
      "content": "Hi there! How can I help you?",
      "created_at": "2024-01-15T10:30:05Z"
    }
  ]
}
```

#### Send Message
```http
POST /api/chat/{chatId}/send
Authorization: Bearer <token>
Content-Type: application/json

{
  "message": "How do I sort a list in Python?"
}
```

**Response:**
```json
{
  "response": "To sort a list in Python, you can use...",
  "model": "gpt-3.5-turbo",
  "tokens": {
    "prompt": 25,
    "completion": 150,
    "total": 175
  }
}
```

#### Delete Chat
```http
DELETE /api/chat/{chatId}
Authorization: Bearer <token>
```

### Legacy Chat API (Public)

#### Create Chat Session
```http
POST /chat
Content-Type: application/json

{"prompt": "Hello, how are you?"}
```

#### Get Chat Response
```http
GET /chat/{sessionId}
```

### Admin API

All admin endpoints require authentication.

#### Login
```http
POST /admin/login
Content-Type: application/json

{
  "username": "admin",
  "password": "your-password"
}
```

#### Get Stats
```http
GET /admin/stats
Authorization: Bearer <token>
```

#### Get Users
```http
GET /admin/users
Authorization: Bearer <token>
```

#### Get Logs
```http
GET /admin/logs
Authorization: Bearer <token>
```

## 🔐 Security Configuration

### JWT Secret
Generate a secure 32+ character secret:
```bash
openssl rand -base64 32
```

### Admin Password Hash
Generate SHA-256 hash:
```bash
# Linux/Mac
echo -n "your-password" | sha256sum | cut -d' ' -f1

# Windows PowerShell
$bytes = [System.Text.Encoding]::UTF8.GetBytes("your-password")
$hash = [System.Security.Cryptography.SHA256]::Create()
$hashBytes = $hash.ComputeHash($bytes)
[BitConverter]::ToString($hashBytes) -replace '-','' | ForEach-Object { $_.ToLower() }
```

### Google OAuth Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable "Google+ API" and "Google Identity"
4. Create OAuth 2.0 credentials
5. Add authorized redirect URI: `http://localhost:8080/auth/google/callback`
6. Copy Client ID and Client Secret to your `.env`

## 🐳 Docker Commands

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Rebuild after code changes
docker-compose up -d --build

# Full cleanup (removes volumes)
docker-compose down -v
```

## 📊 Logging

Logs are written to:
- Console (stdout/stderr)
- File (configured in `server.log_file`)

Log format:
```
[2024-01-15 12:00:00.000] [LEVEL] [IP] [METHOD] [PATH] Message
```

Log levels: `INFO`, `ERROR`, `REQUEST`, `RESPONSE`, `ADMIN`, `CHAT`, `AUTH`

## 🛠️ Development

### Adding New Endpoints

1. Create a handler class in the appropriate package
2. Register route in `RequestRouter.route()`
3. Implement request handling logic

### Database Migrations

Add new migrations to `sql/` directory and update schema version.

## 📝 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `OPENAI_API_KEY` | Yes | OpenAI API key |
| `JWT_SECRET` | Yes | JWT signing secret (32+ chars) |
| `DATABASE_URL` | No | PostgreSQL connection URL |
| `DATABASE_USER` | No | Database username |
| `DATABASE_PASSWORD` | No | Database password |
| `GOOGLE_CLIENT_ID` | No | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | No | Google OAuth client secret |
| `ADMIN_USERNAME` | No | Admin username (default: admin) |
| `ADMIN_PASSWORD` | No | Admin password |

## 📝 License

MIT License - see LICENSE file for details.

## 🙏 Acknowledgments

- OpenAI for the ChatGPT API
- PostgreSQL for the database
- Docker for containerization

---

Built with ❤️ for CSE389 Web Server Design Project
