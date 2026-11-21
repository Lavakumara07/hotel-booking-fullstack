# Hotel Booking System

A full-stack hotel booking application with a responsive guest website, a staff dashboard, room availability search, JWT authentication, and a Razorpay-compatible checkout flow.

> **Development defaults:** the application uses MySQL user/password `root`/`root`, a development-only JWT signing secret, and a simulated Razorpay checkout by default. Override these values with environment variables before deploying. The seeded administrator password is also intended only for local development.

## Features

- Browse room details and prices; search availability by check-in/check-out dates.
- Detect booking date overlaps and prevent double-booking during payment checkout.
- Register and sign in as a customer; passwords are stored using BCrypt and sessions use JWT bearer tokens.
- Create a booking only after payment verification; checkout shows the configured GST breakdown.
- Use a built-in simulated payment mode or Razorpay Checkout with configured keys.
- View and cancel your own bookings; administrator accounts can view all bookings.
- Manage rooms from the staff dashboard: add rooms, update prices, and delete rooms without booking history.
- Seed four sample rooms when the room table is empty, and seed a default administrator if that account does not exist.
- Responsive guest site and admin dashboard served directly by Spring Boot.

## Tech stack

| Area | Technologies |
| --- | --- |
| Backend | Java 17, Spring Boot 4.2, Spring MVC, Spring Data JPA |
| Authentication | Spring Security, JWT (JJWT), BCrypt |
| Database | MySQL 8+, Hibernate |
| Guest and staff UI | HTML5, CSS3, JavaScript |
| Payments | Razorpay Orders API and Checkout, with local simulated test mode |
| Build and tests | Maven Wrapper, JUnit 5 |

## Database schema

Hibernate creates or updates the schema at startup (`spring.jpa.hibernate.ddl-auto=update`).

| Table | Main columns | Purpose |
| --- | --- | --- |
| `users` | `id`, `full_name`, `email` (unique), `password`, `role` | Customer and administrator accounts. Passwords are BCrypt hashes; roles are `ROLE_CUSTOMER` or `ROLE_ADMIN`. |
| `rooms` | `id`, `room_number` (unique), `room_type`, `price_per_night`, `description`, `image_url`, `available` | Hotel inventory. |
| `payment_orders` | `id`, `order_id` (unique), `user_email`, `room_id`, guest details, stay dates, `subtotal`, `gst_amount`, `total_amount`, `status`, `payment_id`, `booking_id` | Pending and completed checkout records; pending orders reserve their selected dates until cancelled or paid. |
| `bookings` | `id`, guest details, `check_in_date`, `check_out_date`, `total_price`, `status`, `payment_status`, `payment_id`, `room_id` | Confirmed reservations. `total_price` is the amount paid, including configured GST; `payment_status` is `PENDING` or `PAID`. |

## API endpoints

All protected requests use `Authorization: Bearer <JWT>`.

| Method | Endpoint | Access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Register a customer account; roles cannot be selected by the client. |
| `POST` | `/api/auth/login` | Public | Authenticate and return a JWT. |
| `GET` | `/api/rooms` | Public | List all rooms. |
| `GET` | `/api/rooms/available?checkInDate=YYYY-MM-DD&checkOutDate=YYYY-MM-DD` | Public | Search rooms available for the date range. |
| `POST` | `/api/rooms` | Admin | Add a room. |
| `PATCH` | `/api/rooms/{roomId}/price` | Admin | Update a room's nightly price. |
| `DELETE` | `/api/rooms/{roomId}` | Admin | Delete a room with no booking or payment history. |
| `POST` | `/api/payment/create-order` | Signed-in user | Validate and create a payment order; returns subtotal, GST, and total. |
| `POST` | `/api/payment/verify` | Signed-in user | Verify payment and create the confirmed booking. |
| `POST` | `/api/payment/cancel-order?orderId={orderId}` | Signed-in owner | Cancel an unpaid checkout order and release its dates. |
| `GET` | `/api/bookings` | Admin | View all customer bookings. |
| `GET` | `/api/bookings/mine?guestEmail={email}` | Signed-in owner | View bookings belonging to the signed-in email. |
| `PATCH` | `/api/bookings/{bookingId}/cancel` | Owner or admin | Cancel a booking. |

Direct booking creation is intentionally not exposed; reservations are created by `/api/payment/verify` after successful payment verification.

## Local setup

### Prerequisites

- JDK 17
- MySQL 8 or later, running locally
- Git

The Maven Wrapper is included, so a separate Maven installation is not required.

### Configure the environment

The defaults in `application.properties` are for local development. Set a persistent, randomly generated JWT secret and your database settings in the environment. The following PowerShell example creates a strong secret for the current terminal session:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()

$env:DB_URL = "jdbc:mysql://localhost:3306/hotel_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-local-mysql-password"
```

Keep the same `JWT_SECRET` between application restarts; changing it invalidates issued tokens. Configure it through a persistent environment or secret manager for regular use.

Before the first startup, set a strong administrator password if you do not want the development default:

```powershell
$env:ADMIN_INITIAL_PASSWORD = "replace-with-a-strong-password"
```

The seed operation creates `admin@hotel.com` only if that email is absent. If the account already exists, changing `ADMIN_INITIAL_PASSWORD` will not reset its password.

For the built-in payment simulation, leave both `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` unset; both default to `test_mode`. To use Razorpay's test checkout, set both variables to the test key ID and secret from your Razorpay dashboard. Never commit live or test secrets.

The GST rate defaults to 18 percent and can be changed with `HOTEL_GST_RATE`. Confirm the correct rate for the property and applicable tax rules before taking real payments.

### Run the application

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

Open:

- Guest site: <http://localhost:8080/>
- Staff dashboard: <http://localhost:8080/admin.html>

Default development admin credentials:

| Email | Password |
| --- | --- |
| `admin@hotel.com` | `admin123` |

Change the initial administrator password before exposing the application outside a local development environment.

### Run tests

```powershell
.\mvnw.cmd test
```

The integration tests use an in-memory H2 database and a test-only JWT signing key; running the application itself requires MySQL.

## Security and deployment notes

- Registration always assigns `ROLE_CUSTOMER`. Room management and all-bookings access require `ROLE_ADMIN`.
- The staff page is served publicly, but its administrative API calls require an administrator JWT.
- The default JWT key and database credentials are development fallbacks, not production credentials. Always override them in deployed environments.
- Razorpay payments use server-side order creation, signature verification, and captured-payment checks. The simulated mode creates test-only payment IDs and must not be used for real reservations.
- Use HTTPS, configure the applicable GST rate, and review database backups and migration strategy before production use.
