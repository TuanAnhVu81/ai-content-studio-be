# AI Content Studio
![Spring Boot](https://img.shields.io/badge/Spring_Boot-F2F4F9?style=for-the-badge&logo=spring-boot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/redis-%23DD0031.svg?&style=for-the-badge&logo=redis&logoColor=white)


An end-to-end AI-assisted content production platform that helps marketers and small teams plan campaigns, generate platform-specific content, review SEO quality, manage banner previews, and monitor AI usage in one workspace.

This project is built as a **product-oriented fullstack application** with a deployed frontend, deployed backend, real database, real Redis session store, real AI integration, and production-style authentication.

## Live Demo

- Frontend: `https://avt-aicontentstudio.vercel.app/`
- Backend API: `https://ai-content-studio-be.onrender.com/`
- Swagger / API Docs: `https://ai-content-studio-be.onrender.com/api/v1/docs`

## Demo Accounts

Use these accounts to review the product from both user and admin perspectives.

### User account

- Email: `anhvt@gmail.com`
- Password: `12345678`

### Admin account

- Email: `admin@gmail.com`
- Password: `12345678`

---

## Why This Project Matters

Most marketers and content teams still jump across multiple tools to:

- write copy with AI
- review SEO quality
- manage campaign content
- prepare banner assets
- track usage cost

**AI Content Studio** turns that fragmented workflow into a single product:

- define a campaign
- generate content with AI from structured inputs
- refine the result in an editor
- validate quality with platform-aware scoring
- attach banner metadata
- monitor activity and AI usage from an admin panel

This is not just an API demo. It is a **deployed product** built with real authentication, real persistence, and production deployment concerns in mind.

---

## What I Built

### Core product features

- Campaign management with ownership isolation
- AI-assisted content generation using **Google AI Studio (Gemini)**
- Platform-aware prompt orchestration for:
  - Website Blog
  - Facebook Page
  - Instagram Post
  - Email Marketing
  - TikTok Script
  - Google Ads
- Rich text content editing and persistence
- Platform-aware SEO / content quality scoring from the frontend
- Banner image flow using **Cloudinary**
- User dashboard with personal usage metrics
- Admin dashboard for:
  - user management
  - campaign monitoring
  - recent content monitoring
  - AI token usage analytics

### Production-minded engineering features

- Stateless JWT access token authentication
- Refresh token rotation using **HttpOnly cookie + Redis session model**
- CSRF protection for cookie-backed auth endpoints
- Session revocation on logout, password change, and user disable
- Refresh token reuse detection
- Soft delete support for content and campaign records
- Health endpoints for deployment monitoring:
  - `/api/v1/health/live`
  - `/api/v1/health/ready`

---

## What This Project Demonstrates

This project was designed to show strengths that match a real fullstack/product-oriented role:

- **Product thinking**: features are built around a realistic marketer workflow, not isolated CRUD screens
- **Frontend + Backend ownership**: a real UI, a real API, and deployed infrastructure
- **AI-assisted development and AI product integration**: AI is used both as a user-facing feature and as part of the engineering workflow
- **SEO awareness**: built-in content quality checks and structured content generation
- **Production readiness**: cookie auth, Redis-backed sessions, environment-based configuration, health checks, deployment debugging
- **Independent execution**: architecture, implementation, troubleshooting, and deployment were handled end-to-end

---

## Architecture

### High-level flow

1. User logs in with email/password
2. Backend issues:
   - JWT access token
   - refresh token in HttpOnly cookie
3. User creates or selects a campaign
4. User submits structured content inputs:
   - platform
   - tone
   - target keyword
   - length
5. Backend assembles a **platform-aware master prompt**
6. Gemini returns generated content
7. Backend persists content + AI usage log
8. Frontend analyzes content quality with platform-aware rules
9. User refines content and saves edits
10. Banner preview is uploaded to Cloudinary and linked back to content

### Backend architecture

- Modular Monolith
- Layered architecture:
  - Controller
  - Service
  - Repository
- Feature-based packages
- PostgreSQL + Flyway for schema evolution
- Redis for refresh sessions

---

## Tech Stack

### Frontend

- React 18
- Vite
- Tailwind CSS
- TanStack Query
- Zustand
- React Hook Form + Zod
- Axios
- React Quill
- Recharts
- html-to-image

### Backend

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Spring Data Redis
- Flyway
- MapStruct
- Hypersistence Utils (JSONB mapping)
- JUnit 5 + Mockito + AssertJ

### Infrastructure / Services

- Vercel for frontend hosting
- Render for backend hosting
- Supabase PostgreSQL
- Redis Cloud
- Cloudinary
- Google AI Studio (Gemini)

---

## AI Workflow

One of the strongest parts of this project is the **workflow-centric prompt orchestration**.

The user does not write raw prompts manually. Instead, the backend builds a master prompt from structured UI inputs:

- platform
- tone
- keyword
- length limit
- language

The backend then adjusts generation rules by platform profile:

- long-form SEO article
- social post
- email copy
- short script
- ad copy

This keeps the product experience simple for users while still producing more targeted AI output.

---

## Security Design

This project uses a more production-oriented auth flow than a typical student CRUD app.

### Access token

- JWT
- short-lived
- sent in `Authorization: Bearer ...`

### Refresh token

- stored in **HttpOnly cookie**
- not exposed in JSON response
- backed by **Redis session state**

### Security mechanisms

- refresh token rotation
- reuse detection
- revoke current session on logout
- revoke all sessions on password change
- revoke all sessions when a user is disabled
- CSRF protection for cookie-backed auth endpoints

---

## SEO / Content Quality Layer

The project includes a platform-aware content analysis flow.

Examples:

- **Website Blog**
  - H1 / H2 structure
  - keyword density
  - meta title / meta description
  - long-form length

- **Google Ads**
  - opening keyword placement
  - concise ad copy
  - CTA presence

- **TikTok Script**
  - opening hook
  - spoken structure
  - CTA / prompt

This makes the analyzer more realistic than a one-size-fits-all “SEO score”.

---

## Admin & Monitoring

The admin area is designed for operational visibility, not just CRUD.

### Admin capabilities

- manage user status
- prevent self-deactivation
- monitor campaign ownership
- monitor recent content activity
- inspect AI usage consumption
- track top users by token usage

### Monitoring / operations

- readiness endpoint for DB + Redis health
- liveness endpoint for keep-alive monitoring
- structured API error handling
- deployed environment troubleshooting

---

## Deployment Topology

### Current deployment setup

- Frontend: **Vercel**
- Backend: **Render**
- Database: **Supabase PostgreSQL**
- Redis: **Redis Cloud**
- Media: **Cloudinary**
- AI Provider: **Google AI Studio**

### Production concerns addressed

- environment-based configuration
- CORS for deployed frontend origin
- cross-site cookie auth
- health endpoints for uptime monitoring
- migration-based schema management

---

## My Role

This is a solo project where I handled the end-to-end development lifecycle:
- Designed the product scope, database schema, and feature flow.
- Implemented the responsive React frontend and Spring Boot backend.
- Integrated external services: Google AI Studio, Cloudinary, Redis, Supabase.
- Handled production deployment, CI/CD, and security hardening (Cookie Auth, CSRF).

---

## API Highlights

### Auth

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `PATCH /api/v1/auth/change-password`
- `GET /api/v1/auth/me`
- `GET /api/v1/auth/csrf`

### Campaigns

- `POST /api/v1/campaigns`
- `GET /api/v1/campaigns`
- `GET /api/v1/campaigns/{id}`
- `PUT /api/v1/campaigns/{id}`
- `DELETE /api/v1/campaigns/{id}`

### Content

- `POST /api/v1/contents/generate`
- `GET /api/v1/contents?campaignId={id}`
- `GET /api/v1/contents/{id}`
- `PUT /api/v1/contents/{id}`
- `PUT /api/v1/contents/{id}/banner`
- `DELETE /api/v1/contents/{id}`

### Dashboard / Admin

- `GET /api/v1/dashboard/user`
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/campaigns`
- `GET /api/v1/admin/contents/recent`
- `GET /api/v1/admin/stats/ai-usage`
- `GET /api/v1/admin/stats/top-users`

---

## Local Setup

### Requirements

- Java 17+
- Maven Wrapper
- PostgreSQL
- Redis

### Run locally

```bash
./mvnw spring-boot:run
```

Or on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

### Environment variables

You can configure the application via `.env` or system environment variables.

Important examples:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_USERNAME`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `GEMINI_API_KEY`
- `GEMINI_MODEL`
- `ALLOWED_ORIGINS`

---

## Testing

The project includes service-layer and auth/security-related tests using:

- JUnit 5
- Mockito
- AssertJ

Examples of covered areas:

- auth service flow
- refresh session logic
- content generation service
- dashboard service
- admin services

---

## Roadmap

- improve Redis Cloud production configuration hardening
- finalize production deploy checklist
- add richer observability and error monitoring
- continue refining platform-aware AI output

---

## Contact

- Name: `Vu Tuan Anh`
- Email: `tuananhvu1123@gmail.com`