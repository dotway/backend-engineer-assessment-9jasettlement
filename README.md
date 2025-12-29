# Wallet Service

A simple backend service that supports wallet operations including creating wallets, processing transactions (credit/debit), and transferring funds between wallets.

## Features

- **Create Wallet** - Create new wallets with optional initial balance
- **Credit/Debit Transactions** - Add or remove funds from a wallet with idempotency protection
- **Atomic Transfers** - Transfer funds between wallets in a single atomic operation
- **Get Wallet Details** - Retrieve wallet information and balance

## Technical Highlights

- **Spring Boot 3.2** with Java 17
- **H2 Database** (in-memory) for development, PostgreSQL-ready for production
- **Money in Minor Units** - All amounts stored as integers (cents) to avoid floating-point issues
- **Idempotency** - Duplicate requests with the same key return the original result
- **Atomic Transactions** - Database transactions ensure data consistency
- **Pessimistic Locking** - Prevents concurrent modification issues

## Prerequisites

- Java 17 or higher
- Maven 3.6+

## Quick Start

### 1. Clone and Build

```bash
# Clone the repository
cd codeInt

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The service will start at `http://localhost:8080`

### Production Deployment

For production, use PostgreSQL:

1. Create the database:
```sql
CREATE DATABASE walletdb;
```

2. Run with PostgreSQL profile:
```bash
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### 2. Access H2 Console (Development)

- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:walletdb`
- Username: `sa`
- Password: (empty)

## API Endpoints

### 1. Create Wallet

**POST** `/wallets`

Creates a new wallet.

```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Wallet",
    "currency": "USD",
    "initialBalance": 10000
  }'
```

Response (201 Created):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Wallet",
  "balance": 10000,
  "formattedBalance": "100.00",
  "currency": "USD",
  "createdAt": "2025-01-15T10:30:00",
  "updatedAt": "2025-01-15T10:30:00"
}
```

### 2. Get Wallet Details

**GET** `/wallets/{id}`

Retrieves wallet information.

```bash
curl http://localhost:8080/wallets/550e8400-e29b-41d4-a716-446655440000
```

Response (200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "My Wallet",
  "balance": 10000,
  "formattedBalance": "100.00",
  "currency": "USD",
  "createdAt": "2025-01-15T10:30:00",
  "updatedAt": "2025-01-15T10:30:00"
}
```

### 3. Credit or Debit Transaction

**POST** `/transactions`

Process a credit (add funds) or debit (remove funds) transaction.

**Credit Example:**
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "walletId": "550e8400-e29b-41d4-a716-446655440000",
    "type": "CREDIT",
    "amount": 5000,
    "idempotencyKey": "tx-001",
    "description": "Deposit"
  }'
```

**Debit Example:**
```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "walletId": "550e8400-e29b-41d4-a716-446655440000",
    "type": "DEBIT",
    "amount": 2000,
    "idempotencyKey": "tx-002",
    "description": "Withdrawal"
  }'
```

Response (201 Created):
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CREDIT",
  "amount": 5000,
  "formattedAmount": "50.00",
  "balanceAfter": 15000,
  "formattedBalanceAfter": "150.00",
  "idempotencyKey": "tx-001",
  "description": "Deposit",
  "createdAt": "2025-01-15T10:35:00",
  "duplicate": false
}
```

**Idempotency:** Sending the same request with the same `idempotencyKey` will return the original transaction with `"duplicate": true` and status 200.

### 4. Transfer Between Wallets

**POST** `/transactions/transfer`

Atomically transfer funds from one wallet to another.

```bash
curl -X POST http://localhost:8080/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "fromWalletId": "550e8400-e29b-41d4-a716-446655440000",
    "toWalletId": "770e8400-e29b-41d4-a716-446655440000",
    "amount": 3000,
    "idempotencyKey": "transfer-001",
    "description": "Payment to friend"
  }'
```

Response (201 Created):
```json
{
  "transferId": "880e8400-e29b-41d4-a716-446655440000",
  "senderTransaction": {
    "id": "990e8400-e29b-41d4-a716-446655440001",
    "walletId": "550e8400-e29b-41d4-a716-446655440000",
    "type": "TRANSFER_OUT",
    "amount": 3000,
    "balanceAfter": 7000
  },
  "receiverTransaction": {
    "id": "990e8400-e29b-41d4-a716-446655440002",
    "walletId": "770e8400-e29b-41d4-a716-446655440000",
    "type": "TRANSFER_IN",
    "amount": 3000,
    "balanceAfter": 3000
  },
  "amount": 3000,
  "formattedAmount": "30.00",
  "idempotencyKey": "transfer-001",
  "duplicate": false
}
```

## Error Responses

### Insufficient Balance (400)
```json
{
  "status": 400,
  "code": "INSUFFICIENT_BALANCE",
  "message": "Insufficient balance. Current: 1000, Requested: 5000",
  "timestamp": "2025-01-15T10:40:00"
}
```

### Wallet Not Found (404)
```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Wallet not found with ID: 550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-01-15T10:40:00"
}
```

### Validation Error (400)
```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed: {amount=Amount must be positive}",
  "timestamp": "2025-01-15T10:40:00"
}
```

## Complete Test Script

Here's a complete script to test all endpoints:

```bash
#!/bin/bash
BASE_URL="http://localhost:8080"

echo "=== Creating Wallet 1 ==="
WALLET1=$(curl -s -X POST $BASE_URL/wallets \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Wallet", "currency": "USD", "initialBalance": 10000}')
echo $WALLET1 | jq .
WALLET1_ID=$(echo $WALLET1 | jq -r '.id')

echo -e "\n=== Creating Wallet 2 ==="
WALLET2=$(curl -s -X POST $BASE_URL/wallets \
  -H "Content-Type: application/json" \
  -d '{"name": "Bob Wallet", "currency": "USD", "initialBalance": 5000}')
echo $WALLET2 | jq .
WALLET2_ID=$(echo $WALLET2 | jq -r '.id')

echo -e "\n=== Credit Wallet 1 (+$25.00) ==="
curl -s -X POST $BASE_URL/transactions \
  -H "Content-Type: application/json" \
  -d "{\"walletId\": \"$WALLET1_ID\", \"type\": \"CREDIT\", \"amount\": 2500, \"idempotencyKey\": \"credit-001\"}" | jq .

echo -e "\n=== Debit Wallet 1 (-$10.00) ==="
curl -s -X POST $BASE_URL/transactions \
  -H "Content-Type: application/json" \
  -d "{\"walletId\": \"$WALLET1_ID\", \"type\": \"DEBIT\", \"amount\": 1000, \"idempotencyKey\": \"debit-001\"}" | jq .

echo -e "\n=== Transfer $30.00 from Wallet 1 to Wallet 2 ==="
curl -s -X POST $BASE_URL/transactions/transfer \
  -H "Content-Type: application/json" \
  -d "{\"fromWalletId\": \"$WALLET1_ID\", \"toWalletId\": \"$WALLET2_ID\", \"amount\": 3000, \"idempotencyKey\": \"transfer-001\"}" | jq .

echo -e "\n=== Get Wallet 1 (Final Balance) ==="
curl -s $BASE_URL/wallets/$WALLET1_ID | jq .

echo -e "\n=== Get Wallet 2 (Final Balance) ==="
curl -s $BASE_URL/wallets/$WALLET2_ID | jq .

echo -e "\n=== Test Idempotency (Duplicate Credit) ==="
curl -s -X POST $BASE_URL/transactions \
  -H "Content-Type: application/json" \
  -d "{\"walletId\": \"$WALLET1_ID\", \"type\": \"CREDIT\", \"amount\": 2500, \"idempotencyKey\": \"credit-001\"}" | jq .

echo -e "\n=== Test Insufficient Balance ==="
curl -s -X POST $BASE_URL/transactions \
  -H "Content-Type: application/json" \
  -d "{\"walletId\": \"$WALLET1_ID\", \"type\": \"DEBIT\", \"amount\": 9999999, \"idempotencyKey\": \"debit-fail\"}" | jq .
```

## Project Structure

```
src/main/java/com/wallet/
├── WalletServiceApplication.java    # Main application entry point
├── controller/
│   ├── WalletController.java        # Wallet REST endpoints
│   └── TransactionController.java   # Transaction REST endpoints
├── dto/
│   ├── CreateWalletRequest.java     # Wallet creation request
│   ├── WalletResponse.java          # Wallet response
│   ├── TransactionRequest.java      # Credit/Debit request
│   ├── TransactionResponse.java     # Transaction response
│   ├── TransferRequest.java         # Transfer request
│   └── TransferResponse.java        # Transfer response
├── entity/
│   ├── Wallet.java                  # Wallet entity
│   └── Transaction.java             # Transaction entity
├── exception/
│   ├── GlobalExceptionHandler.java  # Global error handling
│   ├── WalletNotFoundException.java
│   ├── InsufficientBalanceException.java
│   └── InvalidTransactionException.java
├── repository/
│   ├── WalletRepository.java        # Wallet data access
│   └── TransactionRepository.java   # Transaction data access
└── service/
    └── WalletService.java           # Business logic
```

## Production Deployment

For production, use PostgreSQL:

1. Create the database:
```sql
CREATE DATABASE walletdb;
```

2. Run with PostgreSQL profile:
```bash
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Design Decisions

1. **Minor Units for Money**: All monetary values are stored as `Long` in minor units (cents) to avoid floating-point precision issues.

2. **Idempotency Keys**: Stored in the database with a unique constraint. Duplicate requests return the original transaction.

3. **Pessimistic Locking**: Used during balance updates to prevent race conditions in concurrent scenarios.

4. **Ordered Locking for Transfers**: Wallets are locked in a consistent order (by UUID) to prevent deadlocks.

5. **Atomic Transfers**: Both debit and credit happen in a single database transaction. If either fails, the entire operation rolls back.

## License

MIT License

