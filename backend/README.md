# SafeRoad AI — Backend

Spring Boot backend for the AI-Powered Smart Road Safety & Accident Prevention System.

## Current phase

Backend foundation only:

- Spring Boot application
- REST API structure
- PostgreSQL configuration
- JPA/Hibernate
- User entity
- User repository
- Spring Security foundation
- BCrypt password encoder
- Development CORS
- Health-check API

## Requirements

- Java 17 or newer
- Maven 3.6.3 or newer
- PostgreSQL

## PostgreSQL setup

Create the database:

```sql
CREATE DATABASE road_safety_db;
```

Then open:

```text
src/main/resources/application.properties
```

and change:

```properties
spring.datasource.username=postgres
spring.datasource.password=CHANGE_ME
```

to your PostgreSQL credentials.

## Run the backend

From the `backend` folder:

```bash
mvn spring-boot:run
```

The API will start on:

```text
http://localhost:8080
```

## Test the health endpoint

Open:

```text
http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "SafeRoad AI Backend",
  "timestamp": "..."
}
```

## Next backend step

After this foundation is running successfully, we will implement:

1. User registration API
2. OTP verification flow
3. Login API
4. JWT authentication
5. Protected API routes
6. Accident report APIs
7. Risk prediction APIs
8. Safety alert APIs
9. SOS APIs
10. Frontend-to-backend integration


mvn clean compile